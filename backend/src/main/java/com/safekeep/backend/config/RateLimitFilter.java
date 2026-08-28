package com.safekeep.backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiting filter using Bucket4j token-bucket algorithm.
 *
 * Limits applied:
 *   POST /api/auth/login           — 10 attempts / 15 minutes  (brute-force guard)
 *   POST /api/auth/register        — 5  attempts / hour        (account creation spam guard)
 *   POST /api/auth/forgot-password — 3  attempts / 15 minutes  (email enumeration guard)
 *   /api/vault/**                  — 60 requests / minute      (vault operation throttle)
 *
 * Implementation: in-memory ConcurrentHashMap<IP + endpoint_key, Bucket>.
 * This is correct for a single-instance deployment. For clustered deployments,
 * replace with Bucket4j's JCache/Redis backend so buckets are shared across nodes.
 *
 * IP extraction: reads X-Forwarded-For first (for reverse proxy / load balancer deployments),
 * falls back to getRemoteAddr() for direct connections.
 */
@Component
@Order(1) // runs before JWT filter
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    // Separate bucket maps per limit policy — cleaner than a single shared map with complex keys
    private final Map<String, Bucket> loginBuckets        = new ConcurrentHashMap<>();
    private final Map<String, Bucket> registerBuckets     = new ConcurrentHashMap<>();
    private final Map<String, Bucket> forgotPasswordBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> vaultBuckets        = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String ip  = extractClientIp(request);
        String uri = request.getRequestURI();
        String method = request.getMethod();

        Bucket bucket = resolveBucket(ip, uri, method);

        if (bucket == null) {
            // Endpoint is not rate-limited — pass through
            filterChain.doFilter(request, response);
            return;
        }

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded — ip={} uri={}", ip, uri);
            sendTooManyRequestsResponse(response, uri);
        }
    }

    // ==================== Bucket Resolution ====================

    /**
     * Returns the correct bucket for the request, or null if the endpoint is not rate-limited.
     */
    private Bucket resolveBucket(String ip, String uri, String method) {
        // POST /api/auth/login — 10 per 15 minutes
        if ("POST".equalsIgnoreCase(method) && uri.equals("/api/auth/login")) {
            return loginBuckets.computeIfAbsent(ip, k -> buildBucket(10, Duration.ofMinutes(15)));
        }

        // POST /api/auth/register — 5 per hour
        if ("POST".equalsIgnoreCase(method) && uri.equals("/api/auth/register")) {
            return registerBuckets.computeIfAbsent(ip, k -> buildBucket(5, Duration.ofHours(1)));
        }

        // POST /api/auth/forgot-password — 3 per 15 minutes (anti-enumeration)
        if ("POST".equalsIgnoreCase(method) && uri.equals("/api/auth/forgot-password")) {
            return forgotPasswordBuckets.computeIfAbsent(ip, k -> buildBucket(3, Duration.ofMinutes(15)));
        }

        // /api/vault/** (all methods) — 60 per minute
        if (uri.startsWith("/api/vault/")) {
            return vaultBuckets.computeIfAbsent(ip, k -> buildBucket(60, Duration.ofMinutes(1)));
        }

        return null; // not rate-limited
    }

    /**
     * Builds a greedy-refill token bucket.
     * Greedy refill: tokens are added continuously (not in bursts) — smoother throttling.
     *
     * @param capacity  max tokens in the bucket (equals the max burst)
     * @param period    time window over which capacity refills fully
     */
    private Bucket buildBucket(long capacity, Duration period) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, period)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    // ==================== Helpers ====================

    /**
     * Extracts the real client IP, accounting for reverse proxies.
     * Reads X-Forwarded-For first (set by Nginx, AWS ALB, Cloudflare, etc.),
     * takes only the leftmost IP (the original client), and falls back to
     * getRemoteAddr() for direct connections.
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may contain a chain: "client, proxy1, proxy2"
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Writes a RFC-7807-style JSON 429 response.
     */
    private void sendTooManyRequestsResponse(HttpServletResponse response, String uri)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_OK); // set first to avoid default error page
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":429,\"error\":\"Too Many Requests\"," +
                "\"message\":\"You have exceeded the rate limit for this endpoint. Please wait before retrying.\","+
                "\"path\":\"" + uri + "\"}"
        );
    }
}
