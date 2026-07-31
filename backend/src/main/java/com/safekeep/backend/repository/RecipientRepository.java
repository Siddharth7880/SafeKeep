package com.safekeep.backend.repository;

import com.safekeep.backend.entity.Recipient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecipientRepository extends JpaRepository<Recipient, UUID> {

    List<Recipient> findAllByUserId(UUID userId);

    Optional<Recipient> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndEmail(UUID userId, String email);
}
