package com.safekeep.backend.controller;

import com.safekeep.backend.dto.request.CreateVaultItemRequest;
import com.safekeep.backend.dto.request.UpdateVaultItemRequest;
import com.safekeep.backend.dto.response.ApiResponse;
import com.safekeep.backend.dto.response.VaultItemResponse;
import com.safekeep.backend.repository.UserRepository;
import com.safekeep.backend.service.impl.VaultService;
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

/**
 * Vault REST controller.
 *
 * Zero-knowledge contract:
 *   - No vault password is ever sent to or processed by this controller.
 *   - All content arriving in requests is already AES-256-GCM encrypted by the browser.
 *   - All content returned in responses is still AES-256-GCM encrypted — the browser decrypts it.
 *   - The server's role is: store ciphertext, wrap the raw DEK with server key for release path,
 *     enforce authentication via JWT, and return encrypted blobs on request.
 *
 * Authentication: JWT Bearer token (user identity only, NOT vault access control).
 */
@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Vault", description = "Zero-knowledge encrypted content storage")
public class VaultController {

    private final VaultService vaultService;
    private final UserRepository userRepository;

    /**
     * Stores a new vault item.
     * The request body contains only ciphertext — the browser has already encrypted everything.
     * The server wraps the provided rawDEK with the server key for the release path,
     * then discards the rawDEK immediately.
     */
    @PostMapping("/items")
    @Operation(summary = "Store a new encrypted vault item (client-side encryption)")
    public ResponseEntity<ApiResponse<VaultItemResponse>> createItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid CreateVaultItemRequest request) {
        UUID userId = resolveUserId(userDetails);
        VaultItemResponse response = vaultService.createVaultItem(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Vault item stored"));
    }

    /**
     * Returns a list of vault items with metadata only (no encrypted blobs).
     * The browser uses this for the vault list view — no decryption needed.
     */
    @GetMapping("/items")
    @Operation(summary = "List vault items (metadata only — no ciphertext)")
    public ResponseEntity<ApiResponse<List<VaultItemResponse>>> listItems(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(vaultService.listVaultItems(userId)));
    }

    /**
     * Returns the full encrypted blob for a single vault item.
     * The browser uses ciphertext, iv, encryptedDEK, dekIv, and salt to perform
     * local AES-256-GCM decryption — no decryption happens server-side.
     */
    @GetMapping("/items/{itemId}")
    @Operation(summary = "Retrieve encrypted vault item blob (browser decrypts locally)")
    public ResponseEntity<ApiResponse<VaultItemResponse>> getItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID itemId) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                vaultService.getVaultItem(userId, itemId)));
    }

    /**
     * Updates an existing vault item.
     * The browser re-encrypts content with the existing DEK and sends the new ciphertext.
     * The rawDEK is provided so the server can re-wrap its release-path copy.
     */
    @PutMapping("/items/{itemId}")
    @Operation(summary = "Update an encrypted vault item (client-side re-encryption)")
    public ResponseEntity<ApiResponse<VaultItemResponse>> updateItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID itemId,
            @RequestBody @Valid UpdateVaultItemRequest request) {
        UUID userId = resolveUserId(userDetails);
        VaultItemResponse response = vaultService.updateVaultItem(userId, itemId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Vault item updated"));
    }

    /**
     * Soft-deletes a vault item.
     * Authentication via JWT is sufficient for delete authorization.
     * The browser verifies the vault password client-side before calling this endpoint.
     */
    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Soft-delete a vault item")
    public ResponseEntity<ApiResponse<Void>> deleteItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID itemId) {
        UUID userId = resolveUserId(userDetails);
        vaultService.deleteVaultItem(userId, itemId);
        return ResponseEntity.ok(ApiResponse.success(null, "Vault item deleted"));
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
    }
}
