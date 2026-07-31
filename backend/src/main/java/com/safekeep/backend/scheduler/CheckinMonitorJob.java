package com.safekeep.backend.scheduler;

import com.safekeep.backend.entity.User;
import com.safekeep.backend.enums.UserStatus;
import com.safekeep.backend.repository.UserRepository;
import com.safekeep.backend.service.UserStatusTransitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Runs every 15 minutes.
 * Scans all ACTIVE users whose nextCheckinDeadline has passed.
 * Transitions them to MISSED_CHECKIN and starts grace period countdown.
 *
 * @DisallowConcurrentExecution prevents duplicate runs if the job takes longer than the trigger interval.
 */
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
@Slf4j
public class CheckinMonitorJob implements Job {

    private final UserRepository userRepository;
    private final EmailNotificationService emailNotificationService;
    private final SmsNotificationService smsNotificationService;
    private final UserStatusTransitionService userStatusTransitionService;

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("Running CheckinMonitorJob...");

        LocalDateTime now = LocalDateTime.now();
        List<User> users = userRepository.findAllByStatus(UserStatus.ACTIVE);

        for (User user : users) {
            long daysUntilDeadline = java.time.temporal.ChronoUnit.DAYS.between(now, user.getNextCheckinDeadline());

            // Transition to MISSED_CHECKIN and GRACE_PERIOD if deadline passed
            if (daysUntilDeadline < 0) {
                userStatusTransitionService.triggerMissedCheckin(user);
                
                // Send urgent reminder via Email and SMS
                if (Boolean.TRUE.equals(user.getEmailNotificationsEnabled())) {
                    emailNotificationService.sendUrgentReminder(user);
                }
                if (Boolean.TRUE.equals(user.getSmsNotificationsEnabled())) {
                    smsNotificationService.sendUrgentReminder(user);
                }
                
                userStatusTransitionService.triggerGracePeriod(user);
                userRepository.save(user);
                continue;
            }

            // Send reminders
            if (daysUntilDeadline == 3 || daysUntilDeadline == 1) {
                if (Boolean.TRUE.equals(user.getEmailNotificationsEnabled())) {
                    emailNotificationService.sendCheckinReminder(user, (int) daysUntilDeadline);
                }
                if (Boolean.TRUE.equals(user.getSmsNotificationsEnabled())) {
                    smsNotificationService.sendCheckinReminder(user, (int) daysUntilDeadline);
                }
                log.info("Sent check-in reminder ({} days) to user {}", daysUntilDeadline, user.getId());
            }
        }
    }
}
