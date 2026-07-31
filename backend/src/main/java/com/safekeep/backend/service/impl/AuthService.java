package com.safekeep.backend.service.impl;

import com.safekeep.backend.dto.request.LoginRequest;
import com.safekeep.backend.dto.request.RegisterRequest;
import com.safekeep.backend.dto.response.AuthResponse;
import com.safekeep.backend.dto.response.UserProfileResponse;
import com.safekeep.backend.entity.User;
import com.safekeep.backend.enums.AuditEventType;
import com.safekeep.backend.exception.ResourceNotFoundException;
import com.safekeep.backend.repository.UserRepository;
import com.safekeep.backend.scheduler.EmailNotificationService;
import com.safekeep.backend.security.JwtUtil;
import com.safekeep.backend.service.AuditLogService;
import com.safekeep.backend.util.AesEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final AuditLogService auditLogService;
    private final AesEncryptionUtil aesEncryptionUtil;

    private final EmailNotificationService emailNotificationService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        java.util.Optional<User> existingUserOpt = userRepository.findByEmail(request.getEmail());
        User user;

        byte[] salt = aesEncryptionUtil.generateSalt();
        String verificationCode = String.format("%06d", new java.util.Random().nextInt(999999));

        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            if (user.getEmailVerified()) {
                throw new IllegalArgumentException("Email already registered");
            }
            // Overwrite unverified user
            user.setFullName(request.getFullName());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setEncryptedMasterKeySalt(aesEncryptionUtil.toBase64(salt));
            user.setEmailVerificationToken(verificationCode);
            user.setNextCheckinDeadline(LocalDateTime.now().plusDays(7));
            user.setLastCheckinAt(LocalDateTime.now());
        } else {
            user = User.builder()
                    .fullName(request.getFullName())
                    .email(request.getEmail())
                    .passwordHash(passwordEncoder.encode(request.getPassword()))
                    .encryptedMasterKeySalt(aesEncryptionUtil.toBase64(salt))
                    .nextCheckinDeadline(LocalDateTime.now().plusDays(7))
                    .lastCheckinAt(LocalDateTime.now())
                    .emailVerified(false)
                    .emailVerificationToken(verificationCode)
                    .build();
        }

        userRepository.save(user);

        auditLogService.log(user.getId(), AuditEventType.REGISTER, "SYSTEM", "New user registered, pending email verification");
        log.info("New user registered, pending verification: {}", user.getEmail());

        emailNotificationService.sendVerificationEmail(user, verificationCode);

        // We do not return tokens yet because the user cannot log in until they verify their email.
        return AuthResponse.builder()
                .user(mapToProfile(user))
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.getEmailVerified()) {
            throw new org.springframework.security.authentication.DisabledException("Please check your email to verify your account before logging in.");
        }

        auditLogService.log(user.getId(), AuditEventType.LOGIN, null, null, "USER", "User logged in", ipAddress);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtUtil.generateToken(userDetails, user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .user(mapToProfile(user))
                .build();
    }

    @Transactional
    public AuthResponse verifyEmail(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getEmailVerified()) {
            throw new IllegalArgumentException("Email is already verified");
        }

        if (user.getEmailVerificationToken() == null || !user.getEmailVerificationToken().equals(code)) {
            throw new IllegalArgumentException("Invalid verification code");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);

        // Send Welcome Email
        emailNotificationService.sendWelcomeEmail(user);
        
        auditLogService.log(user.getId(), AuditEventType.SETTINGS_UPDATED, "USER", "Email verified successfully");

        // Automatically log them in after verifying
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String accessToken = jwtUtil.generateToken(userDetails, user.getId());
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L)
                .user(mapToProfile(user))
                .build();
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return mapToProfile(user);
    }

    @Transactional
    public void deleteUser(UUID userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Incorrect password");
        }
        
        userRepository.deleteById(userId);
    }

    public UserProfileResponse mapToProfile(User user) {
        Long daysUntil = null;
        boolean isOverdue = false;
        
        LocalDateTime lastCheckin = user.getLastCheckinAt() != null ? user.getLastCheckinAt() : user.getCreatedAt();
        if (lastCheckin == null) lastCheckin = LocalDateTime.now();
        
        LocalDateTime nextDeadline = user.getNextCheckinDeadline() != null ? user.getNextCheckinDeadline() : lastCheckin.plusDays(user.getCheckinIntervalDays());

        if (nextDeadline != null) {
            daysUntil = ChronoUnit.DAYS.between(LocalDateTime.now(), nextDeadline);
            isOverdue = daysUntil < 0;
        }

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .status(user.getStatus())
                .checkinIntervalDays(user.getCheckinIntervalDays())
                .gracePeriodDays(user.getGracePeriodDays())
                .nextCheckinDeadline(nextDeadline)
                .lastCheckinAt(lastCheckin)
                .releasedAt(user.getReleasedAt())
                .emailNotificationsEnabled(user.getEmailNotificationsEnabled())
                .smsNotificationsEnabled(user.getSmsNotificationsEnabled())
                .phoneNumber(user.getPhoneNumber())
                .emailVerified(user.getEmailVerified())
                .daysUntilDeadline(daysUntil)
                .isOverdue(isOverdue)
                .checkinCount(user.getCheckinCount())
                .streakDays(user.getStreakDays())
                .build();
    }
}
