package com.safekeep.backend.scheduler;

import com.safekeep.backend.entity.User;
import com.safekeep.backend.repository.UserRepository;
import com.safekeep.backend.service.UserStatusTransitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Runs every 30 minutes.
 * Scans GRACE_PERIOD users whose grace period has expired.
 * Triggers irreversible content release.
 */
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class GracePeriodMonitorJob implements Job {

    private final UserRepository userRepository;
    private final UserStatusTransitionService stateMachine;
    private final ContentReleaseJob contentReleaseJob;

    @Override
    @Transactional
    public void execute(JobExecutionContext context) {
        LocalDateTime now = LocalDateTime.now();
        List<User> expiredUsers = userRepository.findExpiredGracePeriodUsers(now);

        log.warn("GracePeriodMonitorJob: {} users have expired grace periods at {}", expiredUsers.size(), now);

        for (User user : expiredUsers) {
            try {
                // Double-check idempotency guard
                if (user.getReleasedAt() != null) {
                    log.warn("User {} already released, skipping", user.getId());
                    continue;
                }
                stateMachine.triggerRelease(user);
                userRepository.save(user);
                contentReleaseJob.releaseContent(user);
                log.warn("🔓 CONTENT RELEASED for user {}", user.getId());
            } catch (Exception e) {
                log.error("CRITICAL: Failed to release content for user {}: {}", user.getId(), e.getMessage(), e);
            }
        }
    }
}
