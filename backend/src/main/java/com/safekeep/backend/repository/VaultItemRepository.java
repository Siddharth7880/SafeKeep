package com.safekeep.backend.repository;

import com.safekeep.backend.entity.VaultItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VaultItemRepository extends JpaRepository<VaultItem, UUID> {

    List<VaultItem> findAllByUserIdAndIsActiveTrue(UUID userId);

    Optional<VaultItem> findByIdAndUserIdAndIsActiveTrue(UUID id, UUID userId);

    long countByUserIdAndIsActiveTrue(UUID userId);
}
