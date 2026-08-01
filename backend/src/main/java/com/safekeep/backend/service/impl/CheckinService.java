package com.safekeep.backend.service.impl;

import com.safekeep.backend.dto.request.CheckinSettingsRequest;
import com.safekeep.backend.entity.User;
import com.safekeep.backend.enums.AuditEventType;
import com.safekeep.backend.exception.ResourceNotFoundException;
import com.safekeep.backend.repository.UserRepository;
import com.safekeep.backend.service.AuditLogService;
import com.safekeep.backend.service.UserStatusTransitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckinService {

    private final UserRepository userRepository;
    private final UserStatusTransitionService stateMachine;
    private final AuditLogService auditLogService;

    @Transactional
    public void performCheckin(UUID userId, String ipAddress) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        stateMachine.resetToActive(user);
        userRepository.save(user);

        auditLogService.log(userId, AuditEventType.CHECKIN, user.getStatus(), user.getStatus(),
                "USER", "User performed check-in", ipAddress);

        log.info("User {} checked in successfully. Next deadline: {}", userId, user.getNextCheckinDeadline());
    }

    @Transactional
    public void updateSettings(UUID userId, CheckinSettingsRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (request.getCheckinIntervalDays() != null) {
            user.setCheckinIntervalDays(request.getCheckinIntervalDays());
            // Recalculate next deadline from last check-in
            if (user.getLastCheckinAt() != null) {
                user.setNextCheckinDeadline(
                        user.getLastCheckinAt().plusDays(request.getCheckinIntervalDays()));
            }
        }
        if (request.getGracePeriodDays() != null) {
            user.setGracePeriodDays(request.getGracePeriodDays());
        }
        if (request.getEmailNotificationsEnabled() != null) {
            user.setEmailNotificationsEnabled(request.getEmailNotificationsEnabled());
        }
        if (request.getSmsNotificationsEnabled() != null) {
            user.setSmsNotificationsEnabled(request.getSmsNotificationsEnabled());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        userRepository.save(user);
        auditLogService.log(userId, AuditEventType.SETTINGS_UPDATED, "USER", "Check-in settings updated");
    }

    @Transactional
    public void pauseSwitch(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        stateMachine.pause(user);
        userRepository.save(user);
    }

    @Transactional
    public void resumeSwitch(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        stateMachine.resume(user);
        userRepository.save(user);
    }
}
