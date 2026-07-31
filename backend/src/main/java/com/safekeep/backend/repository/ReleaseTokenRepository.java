package com.safekeep.backend.repository;

import com.safekeep.backend.entity.ReleaseToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReleaseTokenRepository extends JpaRepository<ReleaseToken, UUID> {

    Optional<ReleaseToken> findByTokenAndIsUsedFalseAndExpiresAtAfter(String token, LocalDateTime now);

    List<ReleaseToken> findAllByUserIdAndIsUsedFalse(UUID userId);
}
