package com.safekeep.backend.dto.request;

import com.safekeep.backend.enums.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateVaultItemRequest {
    @NotBlank(message = "Label is required")
    private String label;

    @NotNull(message = "Content Type is required")
    private ContentType contentType;

    private String content; // Nullable if only updating metadata

    private List<java.util.UUID> recipientIds;
}
