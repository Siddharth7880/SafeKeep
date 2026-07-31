package com.safekeep.backend.dto.request;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class AssignVaultItemsRequest {
    private List<UUID> vaultItemIds;
}
