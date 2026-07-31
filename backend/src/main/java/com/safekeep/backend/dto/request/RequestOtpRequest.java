package com.safekeep.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RequestOtpRequest {
    @NotBlank(message = "Phone number is required")
    private String phoneNumber;
}
