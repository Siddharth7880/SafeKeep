package com.safekeep.backend.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class CheckinSettingsRequest {

    @Min(value = 1, message = "Check-in interval must be at least 1 day")
    @Max(value = 365, message = "Check-in interval cannot exceed 365 days")
    private Integer checkinIntervalDays;

    @Min(value = 1, message = "Grace period must be at least 1 day")
    @Max(value = 30, message = "Grace period cannot exceed 30 days")
    private Integer gracePeriodDays;

    private Boolean emailNotificationsEnabled;
    private Boolean smsNotificationsEnabled;
    private String phoneNumber;
}
