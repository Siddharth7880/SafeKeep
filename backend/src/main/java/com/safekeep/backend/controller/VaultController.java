package com.safekeep.backend.controller;

import com.safekeep.backend.dto.request.CreateVaultItemRequest;
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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/vault")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Vault", description = "Encrypted content storage and retrieval")
public class VaultController {

    private final VaultService vaultService;
    private final UserRepository userRepository;

    @PostMapping(value = "/items", consumes = { "multipart/form-data" })
    @Operation(summary = "Store new encrypted content in the vault")
    public ResponseEntity<ApiResponse<VaultItemResponse>> createItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestPart(value = "request") @Valid CreateVaultItemRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestHeader("X-Vault-Password") String vaultPassword) {
        UUID userId = resolveUserId(userDetails);
        VaultItemResponse response = vaultService.createVaultItem(userId, vaultPassword, request, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Vault item stored securely"));
    }

    @PutMapping(value = "/items/{itemId}")
    @Operation(summary = "Update an existing vault item")
    public ResponseEntity<ApiResponse<VaultItemResponse>> updateItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID itemId,
            @RequestBody @Valid com.safekeep.backend.dto.request.UpdateVaultItemRequest request,
            @RequestHeader("X-Vault-Password") String vaultPassword) {
        UUID userId = resolveUserId(userDetails);
        VaultItemResponse response = vaultService.updateVaultItem(userId, itemId, vaultPassword, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Vault item updated securely"));
    }

    @GetMapping("/items/{itemId}/download")
    @Operation(summary = "Download and decrypt a file attachment")
    public ResponseEntity<Resource> downloadItemFile(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID itemId,
            @RequestHeader("X-Vault-Password") String vaultPassword) {
        UUID userId = resolveUserId(userDetails);
        VaultService.DecryptedFile decryptedFile = vaultService.downloadVaultItemFile(userId, itemId, vaultPassword);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + decryptedFile.originalFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(decryptedFile.bytes()));
    }

    @GetMapping("/items")
    @Operation(summary = "List all vault items (metadata only, no content)")
    public ResponseEntity<ApiResponse<List<VaultItemResponse>>> listItems(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(vaultService.listVaultItems(userId)));
    }

    @GetMapping("/items/{itemId}")
    @Operation(summary = "Retrieve and decrypt a specific vault item")
    public ResponseEntity<ApiResponse<VaultItemResponse>> getItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID itemId,
            @RequestHeader("X-Vault-Password") String vaultPassword) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(ApiResponse.success(
                vaultService.getVaultItem(userId, itemId, vaultPassword)));
    }

    @DeleteMapping("/items/{itemId}")
    @Operation(summary = "Soft-delete a vault item")
    public ResponseEntity<ApiResponse<Void>> deleteItem(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable UUID itemId,
            @RequestHeader("X-Vault-Password") String vaultPassword) {
        UUID userId = resolveUserId(userDetails);
        vaultService.deleteVaultItem(userId, itemId, vaultPassword);
        return ResponseEntity.ok(ApiResponse.success(null, "Vault item deleted"));
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
    }
}
