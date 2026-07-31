package com.safekeep.backend.entity;

import com.safekeep.backend.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "checkin_interval_days", nullable = false)
    @Builder.Default
    private Integer checkinIntervalDays = 7;

    @Column(name = "grace_period_days", nullable = false)
    @Builder.Default
    private Integer gracePeriodDays = 3;

    @Column(name = "next_checkin_deadline")
    private LocalDateTime nextCheckinDeadline;

    @Column(name = "grace_period_start")
    private LocalDateTime gracePeriodStart;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "last_checkin_at")
    private LocalDateTime lastCheckinAt;

    @Column(name = "checkin_count")
    @Builder.Default
    private Integer checkinCount = 0;

    @Column(name = "streak_days")
    @Builder.Default
    private Integer streakDays = 0;

    // Encryption: Master key derived from password, stores encrypted DEK
    @Column(name = "encrypted_master_key_salt", length = 512)
    private String encryptedMasterKeySalt;

    @Column(name = "reminder_count")
    @Builder.Default
    private Integer reminderCount = 0;

    @Column(name = "email_notifications_enabled")
    @Builder.Default
    private Boolean emailNotificationsEnabled = true;

    @Column(name = "sms_notifications_enabled")
    @Builder.Default
    private Boolean smsNotificationsEnabled = false;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "email_verification_token")
    private String emailVerificationToken;

    @Version
    @Column(name = "version")
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Recipient> recipients = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<VaultItem> vaultItems = new ArrayList<>();
}
