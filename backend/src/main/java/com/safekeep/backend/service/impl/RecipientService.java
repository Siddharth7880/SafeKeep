package com.safekeep.backend.service.impl;

import com.safekeep.backend.dto.request.AssignVaultItemsRequest;
import com.safekeep.backend.dto.request.CreateRecipientRequest;
import com.safekeep.backend.dto.response.RecipientResponse;
import com.safekeep.backend.entity.Recipient;
import com.safekeep.backend.entity.User;
import com.safekeep.backend.entity.VaultItem;
import com.safekeep.backend.enums.AuditEventType;
import com.safekeep.backend.exception.ResourceNotFoundException;
import com.safekeep.backend.repository.RecipientRepository;
import com.safekeep.backend.repository.UserRepository;
import com.safekeep.backend.repository.VaultItemRepository;
import com.safekeep.backend.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecipientService {

    private final RecipientRepository recipientRepository;
    private final UserRepository userRepository;
    private final VaultItemRepository vaultItemRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public RecipientResponse createRecipient(UUID userId, CreateRecipientRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (recipientRepository.existsByUserIdAndEmail(userId, request.getEmail())) {
            throw new IllegalArgumentException("Recipient with this email already exists");
        }

        Recipient recipient = Recipient.builder()
                .user(user)
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .relationship(request.getRelationship())
                .notifyOnRelease(request.getNotifyOnRelease())
                .build();

        recipientRepository.save(recipient);
        auditLogService.log(userId, AuditEventType.RECIPIENT_ADDED, "USER",
                "Recipient added: " + request.getName() + " <" + request.getEmail() + ">");

        return mapToResponse(recipient);
    }

    @Transactional(readOnly = true)
    public List<RecipientResponse> listRecipients(UUID userId) {
        return recipientRepository.findAllByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public RecipientResponse updateRecipient(UUID userId, UUID recipientId, CreateRecipientRequest request) {
        Recipient recipient = recipientRepository.findByIdAndUserId(recipientId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));

        recipient.setName(request.getName());
        recipient.setEmail(request.getEmail());
        recipient.setPhone(request.getPhone());
        recipient.setRelationship(request.getRelationship());
        recipient.setNotifyOnRelease(request.getNotifyOnRelease());

        recipientRepository.save(recipient);
        auditLogService.log(userId, AuditEventType.RECIPIENT_UPDATED, "USER",
                "Recipient updated: " + recipient.getName());

        return mapToResponse(recipient);
    }

    @Transactional
    public void deleteRecipient(UUID userId, UUID recipientId) {
        Recipient recipient = recipientRepository.findByIdAndUserId(recipientId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));

        recipientRepository.delete(recipient);
        auditLogService.log(userId, AuditEventType.RECIPIENT_DELETED, "USER",
                "Recipient deleted: " + recipient.getName());
    }

    @Transactional
    public RecipientResponse assignVaultItems(UUID userId, UUID recipientId, AssignVaultItemsRequest request) {
        Recipient recipient = recipientRepository.findByIdAndUserId(recipientId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));

        List<VaultItem> items = request.getVaultItemIds() == null
                ? List.of()
                : request.getVaultItemIds().stream()
                    .map(itemId -> vaultItemRepository.findByIdAndUserIdAndIsActiveTrue(itemId, userId)
                            .orElseThrow(() -> new ResourceNotFoundException("Vault item not found: " + itemId)))
                    .collect(Collectors.toList());

        // Update the owning side (VaultItem.recipients)
        // Remove this recipient from all items that are no longer in the list
        List<VaultItem> currentlyLinked = vaultItemRepository.findAllByUserIdAndIsActiveTrue(userId).stream()
                .filter(vi -> vi.getRecipients().contains(recipient))
                .collect(Collectors.toList());

        for (VaultItem vi : currentlyLinked) {
            vi.getRecipients().remove(recipient);
            vaultItemRepository.save(vi);
        }

        // Add recipient to newly assigned items
        for (VaultItem vi : items) {
            if (!vi.getRecipients().contains(recipient)) {
                vi.getRecipients().add(recipient);
                vaultItemRepository.save(vi);
            }
        }

        auditLogService.log(userId, AuditEventType.RECIPIENT_UPDATED, "USER",
                "Vault items assigned to recipient: " + recipient.getName() + " (" + items.size() + " items)");

        // Refresh recipient from DB to get updated vaultItems
        Recipient refreshed = recipientRepository.findByIdAndUserId(recipientId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));
        return mapToResponse(refreshed);
    }

    private RecipientResponse mapToResponse(Recipient r) {
        return RecipientResponse.builder()
                .id(r.getId())
                .name(r.getName())
                .email(r.getEmail())
                .phone(r.getPhone())
                .relationship(r.getRelationship())
                .notifyOnRelease(r.getNotifyOnRelease())
                .assignedVaultItemCount(r.getVaultItems() != null ? (int) r.getVaultItems().stream().filter(vi -> vi.getIsActive()).count() : 0)
                .createdAt(r.getCreatedAt())
                .build();
    }
}
