package com.safekeep.backend.scheduler;

import com.safekeep.backend.entity.Recipient;
import com.safekeep.backend.entity.ReleaseToken;
import com.safekeep.backend.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private final TemplateEngine templateEngine;
    private final RestTemplate restTemplate; // Injected with timeouts from AsyncConfig

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${brevo.api-key}")
    private String brevoApiKey;

    @Value("${brevo.sender.email:noreply@safekeep.com}")
    private String fromEmail;

    @Value("${brevo.sender.name:SafeKeep — Digital Legacy}")
    private String fromName;

    public void sendCheckinReminder(User user, int hoursRemaining) {
        try {
            Context ctx = new Context();
            ctx.setVariable("userName", user.getFullName());
            ctx.setVariable("hoursRemaining", hoursRemaining);
            ctx.setVariable("checkinUrl", baseUrl + "/dashboard");
            ctx.setVariable("nextDeadline", user.getNextCheckinDeadline());

            String html = templateEngine.process("email/checkin-reminder", ctx);
            sendEmail(user.getEmail(), "⚠️ SafeKeep — Check-In Required (" + hoursRemaining + " hours remaining)", html);
        } catch (Exception e) {
            log.error("Failed to send reminder to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    @org.springframework.scheduling.annotation.Async
    public void sendVerificationEmail(User user, String code) {
        try {
            log.info("📧 Verification Code for {}: {}", user.getEmail(), code); // VERY IMPORTANT FOR RENDER DEPLOYMENTS
            
            Context ctx = new Context();
            ctx.setVariable("userName", user.getFullName());
            ctx.setVariable("verificationCode", code);
            String verificationLink = baseUrl + "/verify-email?email=" + user.getEmail() + "&code=" + code;
            ctx.setVariable("verificationLink", verificationLink);

            String html = templateEngine.process("email/email-verification", ctx);
            sendEmail(user.getEmail(), "Verify your SafeKeep Account", html);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    public void sendWelcomeEmail(User user) {
        try {
            Context ctx = new Context();
            ctx.setVariable("userName", user.getFullName());
            ctx.setVariable("dashboardUrl", baseUrl + "/dashboard");

            String html = templateEngine.process("email/email-welcome", ctx);
            sendEmail(user.getEmail(), "Welcome to SafeKeep! Account Activated 🎉", html);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    public void sendUrgentReminder(User user) {
        try {
            Context ctx = new Context();
            ctx.setVariable("userName", user.getFullName());
            ctx.setVariable("checkinUrl", baseUrl + "/dashboard");

            String html = templateEngine.process("email/urgent-reminder", ctx);
            sendEmail(user.getEmail(), "🚨 URGENT: SafeKeep — Final Check-In Warning", html);
        } catch (Exception e) {
            log.error("Failed to send urgent reminder to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    public void sendReleaseNotification(Recipient recipient, User user, byte[] zipBytes, String zipPassword) {
        try {
            Context ctx = new Context();
            ctx.setVariable("recipientName", recipient.getName());
            ctx.setVariable("userName", user.getFullName());
            ctx.setVariable("zipPassword", zipPassword);
            ctx.setVariable("baseUrl", baseUrl);

            String html = templateEngine.process("email/release-notification", ctx);
            sendEmailWithAttachment(recipient.getEmail(),
                    "📬 Important Message from " + user.getFullName() + " — SafeKeep",
                    html, zipBytes, "SecureVault.zip");
            log.info("Release notification sent to {}", recipient.getEmail());
        } catch (Exception e) {
            log.error("Failed to send release notification to {}: {}", recipient.getEmail(), e.getMessage());
        }
    }

    private void sendEmail(String to, String subject, String htmlContent) throws Exception {
        sendEmailWithAttachment(to, subject, htmlContent, null, null);
    }

    private void sendEmailWithAttachment(String to, String subject, String htmlContent, byte[] attachment, String attachmentName) throws Exception {
        String url = "https://api.brevo.com/v3/smtp/email";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("api-key", brevoApiKey);

        Map<String, Object> body = new HashMap<>();
        body.put("sender", Map.of("name", fromName, "email", fromEmail));
        body.put("to", List.of(Map.of("email", to)));
        body.put("subject", subject);
        body.put("htmlContent", htmlContent);

        if (attachment != null && attachmentName != null) {
            String base64Content = Base64.getEncoder().encodeToString(attachment);
            body.put("attachment", List.of(Map.of(
                    "name", attachmentName,
                    "content", base64Content
            )));
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        
        if (response.getStatusCode().is2xxSuccessful()) {
            log.debug("Email sent to {} with subject: {} via Brevo", to, subject);
        } else {
            log.error("Failed to send email via Brevo. Status: {}, Response: {}", response.getStatusCode(), response.getBody());
            throw new RuntimeException("Brevo API error: " + response.getBody());
        }
    }

    @org.springframework.scheduling.annotation.Async
    public void sendPasswordResetEmail(User user, String token) {
        try {
            log.info("Password reset link for {}: {}/reset-password?token={}", user.getEmail(), baseUrl, token);
            Context ctx = new Context();
            ctx.setVariable("userName", user.getFullName());
            ctx.setVariable("resetLink", baseUrl + "/reset-password?token=" + token);
            String html = templateEngine.process("email/password-reset", ctx);
            sendEmail(user.getEmail(), "Reset your SafeKeep Password", html);
        } catch (Exception e) {
            log.error("Failed to send password reset email to {}: {}", user.getEmail(), e.getMessage());
        }
    }
}
