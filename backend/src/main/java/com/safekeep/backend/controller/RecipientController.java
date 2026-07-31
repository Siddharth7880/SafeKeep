package com.safekeep.backend.controller;

import com.safekeep.backend.dto.request.AssignVaultItemsRequest;
import com.safekeep.backend.dto.request.CreateRecipientRequest;
import com.safekeep.backend.dto.response.ApiResponse;
import com.safekeep.backend.dto.response.RecipientResponse;
import com.safekeep.backend.repository.UserRepository;
import com.safekeep.backend.service.impl.RecipientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recipients")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Recipients", description = "Manage trusted recipients who receive vault content")
public class RecipientController {

    private final RecipientService recipientService;
    private final UserRepository userRepository;

    @PostMapping
    @Operation(summary = "Add a new trusted recipient")
    public ResponseEntity<ApiResponse<RecipientResponse>> createRecipient(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateRecipientRequest request) {
        UUID userId = resolveUserId(userDetails);
        RecipientResponse response = recipientService.createRecipient(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Recipient added successfully"));
    }

    @GetMapping
    @Operation(summary = "List all recipients")
    public ResponseEntity<ApiResponse<List<RecipientResponse>>> listRecipients(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(recipientService.listRecipients(userId)));
    }

    @PutMapping("/{recipientId}")
    @Operation(summary = "Update a recipient")
    public ResponseEntity<ApiResponse<RecipientResponse>> updateRecipient(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID recipientId,
            @Valid @RequestBody CreateRecipientRequest request) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                recipientService.updateRecipient(userId, recipientId, request)));
    }

    @DeleteMapping("/{recipientId}")
    @Operation(summary = "Remove a recipient")
    public ResponseEntity<ApiResponse<Void>> deleteRecipient(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID recipientId) {
        UUID userId = resolveUserId(userDetails);
        recipientService.deleteRecipient(userId, recipientId);
        return ResponseEntity.ok(ApiResponse.success(null, "Recipient removed"));
    }

    @PutMapping("/{recipientId}/items")
    @Operation(summary = "Assign vault items to a recipient")
    public ResponseEntity<ApiResponse<RecipientResponse>> assignVaultItems(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID recipientId,
            @RequestBody AssignVaultItemsRequest request) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                recipientService.assignVaultItems(userId, recipientId, request),
                "Vault items assigned successfully"));
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
    }
}
