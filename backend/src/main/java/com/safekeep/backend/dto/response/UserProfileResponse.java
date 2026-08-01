package com.safekeep.backend.dto.response;

import com.safekeep.backend.enums.UserStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserProfileResponse {
    private UUID id;
    private String email;
    private String fullName;
    private String profilePhotoUrl;
    private UserStatus status;
    private Integer checkinIntervalDays;
    private Integer gracePeriodDays;
    private LocalDateTime nextCheckinDeadline;
    private LocalDateTime lastCheckinAt;
    private LocalDateTime releasedAt;
    private Boolean emailNotificationsEnabled;
    private Boolean smsNotificationsEnabled;
    private String phoneNumber;
    private Boolean emailVerified;
    private Long daysUntilDeadline;
    private Boolean isOverdue;
    private Integer checkinCount;
    private Integer streakDays;
}
