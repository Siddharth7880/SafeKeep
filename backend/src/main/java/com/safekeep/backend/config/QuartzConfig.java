package com.safekeep.backend.config;

import com.safekeep.backend.scheduler.CheckinMonitorJob;
import com.safekeep.backend.scheduler.GracePeriodMonitorJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    // ==================== CHECK-IN MONITOR (every 15 minutes) ====================

    @Bean
    public JobDetail checkinMonitorJobDetail() {
        return JobBuilder.newJob(CheckinMonitorJob.class)
                .withIdentity("checkinMonitorJob")
                .withDescription("Scans for users who missed their check-in deadline")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger checkinMonitorTrigger(JobDetail checkinMonitorJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(checkinMonitorJobDetail)
                .withIdentity("checkinMonitorTrigger")
                .withDescription("Every 15 minutes")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0/15 * * * ?"))
                .build();
    }

    // ==================== GRACE PERIOD MONITOR (every 30 minutes) ====================

    @Bean
    public JobDetail gracePeriodMonitorJobDetail() {
        return JobBuilder.newJob(GracePeriodMonitorJob.class)
                .withIdentity("gracePeriodMonitorJob")
                .withDescription("Scans for users whose grace period has expired and triggers release")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger gracePeriodMonitorTrigger(JobDetail gracePeriodMonitorJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(gracePeriodMonitorJobDetail)
                .withIdentity("gracePeriodMonitorTrigger")
                .withDescription("Every 30 minutes")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0/30 * * * ?"))
                .build();
    }
}
