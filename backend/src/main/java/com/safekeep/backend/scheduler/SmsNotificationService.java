package com.safekeep.backend.scheduler;

import com.safekeep.backend.entity.Recipient;
import com.safekeep.backend.entity.User;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SmsNotificationService {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.phone-number}")
    private String twilioPhoneNumber;

    @Value("${app.base-url}")
    private String baseUrl;

    @PostConstruct
    public void init() {
        if (!"your_twilio_account_sid".equals(accountSid) && !accountSid.isBlank()) {
            try {
                Twilio.init(accountSid, authToken);
                log.info("Twilio SDK initialized successfully.");
            } catch (Exception e) {
                log.error("Failed to initialize Twilio: {}", e.getMessage());
            }
        } else {
            log.warn("Twilio credentials not configured. SMS notifications will be logged to console only.");
        }
    }

    public void sendCheckinReminder(User user, int daysRemaining) {
        String msg = String.format("SafeKeep: Hi %s, you have %d days remaining to check in. Please visit %s/dashboard to check in.",
                user.getFullName(), daysRemaining, baseUrl);
        sendSms(user.getPhoneNumber(), msg);
    }

    public void sendUrgentReminder(User user) {
        String msg = String.format("URGENT (SafeKeep): Final warning %s! Your dead man's switch is about to trigger. Check in immediately at %s/dashboard",
                user.getFullName(), baseUrl);
        sendSms(user.getPhoneNumber(), msg);
    }

    public void sendReleaseNotification(Recipient recipient, User user) {
        String msg = String.format("SafeKeep: Important message from %s. Content has been released to you. Please check your email for the secure access links.",
                user.getFullName());
        sendSms(recipient.getPhone(), msg);
    }

    private void sendSms(String toPhoneNumber, String messageBody) {
        if (toPhoneNumber == null || toPhoneNumber.trim().isEmpty()) {
            log.debug("Skipping SMS because recipient phone number is not set.");
            return;
        }
        
        if ("your_twilio_account_sid".equals(accountSid) || accountSid.isBlank()) {
            log.info("DUMMY SMS to [{}]: {}", toPhoneNumber, messageBody);
            return;
        }

        try {
            Message message = Message.creator(
                    new PhoneNumber(toPhoneNumber),
                    new PhoneNumber(twilioPhoneNumber),
                    messageBody
            ).create();
            log.info("SMS sent to {} with Twilio SID {}", toPhoneNumber, message.getSid());
        } catch (Exception e) {
            log.error("Failed to send SMS to {}: {}", toPhoneNumber, e.getMessage());
        }
    }
}
