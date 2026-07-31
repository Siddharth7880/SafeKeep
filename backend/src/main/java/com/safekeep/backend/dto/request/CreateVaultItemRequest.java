package com.safekeep.backend.dto.request;

import com.safekeep.backend.enums.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class CreateVaultItemRequest {

    @NotBlank(message = "Label is required")
    @Size(max = 255)
    private String label;

    @NotNull(message = "Content type is required")
    private ContentType contentType;

    private String content;  // Plaintext — encrypted server-side before storage (optional if file provided)

    private List<UUID> recipientIds = new ArrayList<>();
}
