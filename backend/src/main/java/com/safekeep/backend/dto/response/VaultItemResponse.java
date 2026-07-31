package com.safekeep.backend.dto.response;

import com.safekeep.backend.enums.ContentType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class VaultItemResponse {
    private UUID id;
    private String label;
    private ContentType contentType;
    private String content;          // Only populated on single-item fetch (decrypted)
    private Boolean hasContent;      // True in list view (content not returned)
    private String originalFileName; // Present if file attached
    private Boolean hasFile;
    private List<RecipientResponse> recipients;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
