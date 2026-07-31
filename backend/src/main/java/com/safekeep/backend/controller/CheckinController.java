package com.safekeep.backend.controller;

import com.safekeep.backend.dto.request.CheckinSettingsRequest;
import com.safekeep.backend.dto.response.ApiResponse;
import com.safekeep.backend.repository.UserRepository;
import com.safekeep.backend.service.impl.CheckinService;
import com.safekeep.backend.service.impl.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Check-In", description = "Check-in and settings management")
public class CheckinController {

    private final CheckinService checkinService;
    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/checkin")
    @Operation(summary = "Perform a check-in — resets the dead man's switch timer")
    public ResponseEntity<ApiResponse<?>> performCheckin(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {
        UUID userId = resolveUserId(userDetails);
        checkinService.performCheckin(userId, getClientIp(request));
        return ResponseEntity.ok(ApiResponse.success(
                authService.getProfile(userId),
                "Check-in successful! Timer reset."));
    }

    @PutMapping("/settings/checkin")
    @Operation(summary = "Update check-in interval and notification settings")
    public ResponseEntity<ApiResponse<?>> updateSettings(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CheckinSettingsRequest request) {
        UUID userId = resolveUserId(userDetails);
        checkinService.updateSettings(userId, request);
        return ResponseEntity.ok(ApiResponse.success(
                authService.getProfile(userId), "Settings updated successfully"));
    }

    @PostMapping("/switch/pause")
    @Operation(summary = "Temporarily pause the dead man's switch")
    public ResponseEntity<ApiResponse<String>> pauseSwitch(
            @AuthenticationPrincipal UserDetails userDetails) {
        checkinService.pauseSwitch(resolveUserId(userDetails));
        return ResponseEntity.ok(ApiResponse.success("Switch paused", "Switch paused successfully"));
    }

    @PostMapping("/switch/resume")
    @Operation(summary = "Resume the dead man's switch from paused state")
    public ResponseEntity<ApiResponse<String>> resumeSwitch(
            @AuthenticationPrincipal UserDetails userDetails) {
        checkinService.resumeSwitch(resolveUserId(userDetails));
        return ResponseEntity.ok(ApiResponse.success("Switch resumed", "Switch resumed successfully"));
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        return xfHeader != null ? xfHeader.split(",")[0].trim() : request.getRemoteAddr();
    }
}
