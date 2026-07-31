package com.safekeep.backend.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateRecipientRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Size(max = 20, message = "Phone number too long")
    private String phone;

    @Size(max = 100)
    private String relationship;

    private Boolean notifyOnRelease = true;
}
