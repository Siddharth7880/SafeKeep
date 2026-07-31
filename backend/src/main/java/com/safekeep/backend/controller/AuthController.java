package com.safekeep.backend.controller;

import com.safekeep.backend.dto.request.LoginRequest;
import com.safekeep.backend.dto.request.RegisterRequest;
import com.safekeep.backend.dto.request.VerifyEmailRequest;
import com.safekeep.backend.dto.response.ApiResponse;
import com.safekeep.backend.dto.response.AuthResponse;
import com.safekeep.backend.dto.response.UserProfileResponse;
import com.safekeep.backend.repository.UserRepository;
import com.safekeep.backend.service.impl.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration and login")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Account created successfully"));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        AuthResponse response = authService.login(request, ipAddress);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get current user profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
        return ResponseEntity.ok(ApiResponse.success(
                authService.getProfile(userId), "Profile retrieved successfully"));
    }

    @PostMapping("/delete")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete current user account")
    public ResponseEntity<ApiResponse<String>> deleteAccount(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody com.safekeep.backend.dto.request.DeleteAccountRequest request) {
        UUID userId = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
        authService.deleteUser(userId, request.getPassword());
        return ResponseEntity.ok(ApiResponse.success("Account deleted", "Account successfully deleted"));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify user email address")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request) {
        AuthResponse response = authService.verifyEmail(request.getEmail(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success(response, "Email verified successfully"));
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        return xfHeader != null ? xfHeader.split(",")[0].trim() : request.getRemoteAddr();
    }
}
