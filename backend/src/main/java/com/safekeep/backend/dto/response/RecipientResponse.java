package com.safekeep.backend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class RecipientResponse {
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private String relationship;
    private Boolean notifyOnRelease;
    private Integer assignedVaultItemCount;
    private LocalDateTime createdAt;
}
