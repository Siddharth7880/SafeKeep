package com.safekeep.backend.scheduler;

import com.safekeep.backend.entity.Recipient;
import com.safekeep.backend.entity.ReleaseToken;
import com.safekeep.backend.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

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

    public void sendVerificationEmail(User user, String code) {
        try {
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
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail, "SafeKeep — Digital Legacy");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        mailSender.send(message);
        log.debug("Email sent to {} with subject: {}", to, subject);
    }

    private void sendEmailWithAttachment(String to, String subject, String htmlContent, byte[] attachment, String attachmentName) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(fromEmail, "SafeKeep — Digital Legacy");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlContent, true);
        
        if (attachment != null) {
            helper.addAttachment(attachmentName, new org.springframework.core.io.ByteArrayResource(attachment));
        }
        
        mailSender.send(message);
        log.debug("Email sent to {} with subject: {} and attachment", to, subject);
    }
}
