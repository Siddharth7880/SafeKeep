package com.safekeep.backend.service;

import com.safekeep.backend.entity.User;
import com.safekeep.backend.enums.UserStatus;
import com.safekeep.backend.exception.InvalidStateTransitionException;
import com.safekeep.backend.repository.AuditLogRepository;
import com.safekeep.backend.entity.AuditLog;
import com.safekeep.backend.enums.AuditEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * State machine service for user status transitions.
 *
 * Valid transitions:
 *   ACTIVE          → MISSED_CHECKIN (scheduler: deadline passed)
 *   ACTIVE          → PAUSED         (user request)
 *   MISSED_CHECKIN  → GRACE_PERIOD   (scheduler: reminder sent, grace started)
 *   GRACE_PERIOD    → RELEASED       (scheduler: grace period expired)
 *   Any             → ACTIVE         (user checks in)
 *   PAUSED          → ACTIVE         (user resumes)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserStatusTransitionService {

    private final AuditLogRepository auditLogRepository;

    public void triggerMissedCheckin(User user) {
        requireStatus(user, UserStatus.ACTIVE, "triggerMissedCheckin");
        UserStatus previous = user.getStatus();
        user.setStatus(UserStatus.MISSED_CHECKIN);
        user.setGracePeriodStart(LocalDateTime.now());
        logTransition(user, previous, UserStatus.MISSED_CHECKIN, "SCHEDULER", "Deadline passed without check-in");
        log.info("User {} transitioned ACTIVE → MISSED_CHECKIN", user.getId());
    }

    public void triggerGracePeriod(User user) {
        requireStatus(user, UserStatus.MISSED_CHECKIN, "triggerGracePeriod");
        UserStatus previous = user.getStatus();
        user.setStatus(UserStatus.GRACE_PERIOD);
        if (user.getGracePeriodStart() == null) {
            user.setGracePeriodStart(LocalDateTime.now());
        }
        logTransition(user, previous, UserStatus.GRACE_PERIOD, "SCHEDULER", "First reminder sent, grace period started");
        log.info("User {} transitioned MISSED_CHECKIN → GRACE_PERIOD", user.getId());
    }

    public void triggerRelease(User user) {
        if (user.getStatus() != UserStatus.GRACE_PERIOD && user.getStatus() != UserStatus.MISSED_CHECKIN) {
            throw new InvalidStateTransitionException(
                "Cannot release user in status: " + user.getStatus());
        }
        // Idempotency guard
        if (user.getReleasedAt() != null) {
            log.warn("Release attempted on already-released user {}, skipping", user.getId());
            return;
        }
        UserStatus previous = user.getStatus();
        user.setStatus(UserStatus.RELEASED);
        user.setReleasedAt(LocalDateTime.now());
        logTransition(user, previous, UserStatus.RELEASED, "SCHEDULER", "Grace period expired — content released");
        log.warn("🔓 User {} RELEASED — content distributed to recipients", user.getId());
    }

    public void resetToActive(User user) {
        if (user.getStatus() == UserStatus.RELEASED) {
            throw new InvalidStateTransitionException("Cannot check in: account has already been released");
        }
        UserStatus previous = user.getStatus();
        
        LocalDateTime now = LocalDateTime.now();
        if (user.getLastCheckinAt() == null || user.getLastCheckinAt().toLocalDate().isBefore(now.toLocalDate())) {
            if (previous == UserStatus.ACTIVE || previous == UserStatus.PAUSED) {
                user.setStreakDays(user.getStreakDays() + 1);
            } else {
                user.setStreakDays(1);
            }
        }
        user.setCheckinCount(user.getCheckinCount() + 1);

        user.setStatus(UserStatus.ACTIVE);
        user.setLastCheckinAt(now);
        user.setNextCheckinDeadline(now.plusDays(user.getCheckinIntervalDays()));
        user.setReminderCount(0);
        user.setGracePeriodStart(null);
        logTransition(user, previous, UserStatus.ACTIVE, "USER", "User checked in");
        log.info("User {} checked in — next deadline: {}", user.getId(), user.getNextCheckinDeadline());
    }

    public void pause(User user) {
        requireStatus(user, UserStatus.ACTIVE, "pause");
        UserStatus previous = user.getStatus();
        user.setStatus(UserStatus.PAUSED);
        logTransition(user, previous, UserStatus.PAUSED, "USER", "User paused the switch");
    }

    public void resume(User user) {
        requireStatus(user, UserStatus.PAUSED, "resume");
        UserStatus previous = user.getStatus();
        user.setStatus(UserStatus.ACTIVE);
        user.setNextCheckinDeadline(LocalDateTime.now().plusDays(user.getCheckinIntervalDays()));
        user.setReminderCount(0);
        logTransition(user, previous, UserStatus.ACTIVE, "USER", "User resumed the switch");
    }

    private void requireStatus(User user, UserStatus required, String operation) {
        if (user.getStatus() != required) {
            throw new InvalidStateTransitionException(
                String.format("Operation '%s' requires status %s but user is %s",
                    operation, required, user.getStatus()));
        }
    }

    private void logTransition(User user, UserStatus from, UserStatus to, String triggeredBy, String details) {
        AuditLog log = AuditLog.builder()
                .userId(user.getId())
                .eventType(AuditEventType.STATUS_TRANSITION)
                .previousStatus(from)
                .newStatus(to)
                .triggeredBy(triggeredBy)
                .details(details)
                .build();
        auditLogRepository.save(log);
    }
}
