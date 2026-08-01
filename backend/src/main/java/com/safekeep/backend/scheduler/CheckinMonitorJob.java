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
    private final UserStatusTransitionService userStatusTransitionService;

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("Running CheckinMonitorJob...");

        LocalDateTime now = LocalDateTime.now();
        List<User> users = userRepository.findAllByStatus(UserStatus.ACTIVE);

        for (User user : users) {
            // Transition to MISSED_CHECKIN and GRACE_PERIOD if deadline passed
            if (now.isAfter(user.getNextCheckinDeadline())) {
                userStatusTransitionService.triggerMissedCheckin(user);
                
                // Send urgent reminder via Email
                if (Boolean.TRUE.equals(user.getEmailNotificationsEnabled())) {
                    emailNotificationService.sendUrgentReminder(user);
                }
                
                userStatusTransitionService.triggerGracePeriod(user);
                userRepository.save(user);
                continue;
            }

            // Calculate hours until deadline
            long hoursUntilDeadline = java.time.temporal.ChronoUnit.HOURS.between(now, user.getNextCheckinDeadline());

            // Determine target reminder count based on hours remaining
            int targetReminderCount = 0;
            if (hoursUntilDeadline <= 1) targetReminderCount = 6;
            else if (hoursUntilDeadline <= 5) targetReminderCount = 5;
            else if (hoursUntilDeadline <= 10) targetReminderCount = 4;
            else if (hoursUntilDeadline <= 15) targetReminderCount = 3;
            else if (hoursUntilDeadline <= 20) targetReminderCount = 2;
            else if (hoursUntilDeadline <= 24) targetReminderCount = 1;

            if (targetReminderCount > user.getReminderCount()) {
                int hoursLabel = 24;
                if (targetReminderCount == 6) hoursLabel = 1;
                else if (targetReminderCount == 5) hoursLabel = 5;
                else if (targetReminderCount == 4) hoursLabel = 10;
                else if (targetReminderCount == 3) hoursLabel = 15;
                else if (targetReminderCount == 2) hoursLabel = 20;

                if (Boolean.TRUE.equals(user.getEmailNotificationsEnabled())) {
                    emailNotificationService.sendCheckinReminder(user, hoursLabel);
                }
                log.info("Sent check-in reminder ({} hours) to user {}", hoursLabel, user.getId());
                
                user.setReminderCount(targetReminderCount);
                userRepository.save(user);
            }
        }
    }
}
