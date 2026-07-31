package com.safekeep.backend.repository;

import com.safekeep.backend.entity.User;
import com.safekeep.backend.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Find all ACTIVE users whose check-in deadline has passed
    @Query("SELECT u FROM User u WHERE u.status = 'ACTIVE' AND u.nextCheckinDeadline < :now")
    List<User> findOverdueActiveUsers(@Param("now") LocalDateTime now);

    // Find all users in GRACE_PERIOD whose grace period has expired
    @Query(value = "SELECT * FROM users WHERE status = 'GRACE_PERIOD' AND " +
                   "grace_period_start + (grace_period_days || ' days')::interval < :now",
           nativeQuery = true)
    List<User> findExpiredGracePeriodUsers(@Param("now") LocalDateTime now);

    // Find ACTIVE users approaching their deadline for reminder sending
    @Query("SELECT u FROM User u WHERE u.status = 'ACTIVE' AND " +
           "u.nextCheckinDeadline BETWEEN :now AND :warningThreshold AND u.reminderCount = 0")
    List<User> findUsersNeedingFirstReminder(@Param("now") LocalDateTime now,
                                              @Param("warningThreshold") LocalDateTime warningThreshold);

    @Query("SELECT u FROM User u WHERE u.status = 'ACTIVE' AND " +
           "u.nextCheckinDeadline BETWEEN :now AND :urgentThreshold AND u.reminderCount = 1")
    List<User> findUsersNeedingUrgentReminder(@Param("now") LocalDateTime now,
                                               @Param("urgentThreshold") LocalDateTime urgentThreshold);

    List<User> findAllByStatus(UserStatus status);
}
