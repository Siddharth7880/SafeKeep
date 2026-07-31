package com.safekeep.backend.controller;

import com.safekeep.backend.dto.response.ApiResponse;
import com.safekeep.backend.entity.AuditLog;
import com.safekeep.backend.repository.UserRepository;
import com.safekeep.backend.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Audit Log", description = "Immutable event history")
public class AuditController {

    private final AuditLogService auditLogService;
    private final UserRepository userRepository;

    @GetMapping("/logs")
    @Operation(summary = "Get paginated audit log for authenticated user")
    public ResponseEntity<ApiResponse<Page<AuditLog>>> getLogs(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = resolveUserId(userDetails);
        Page<AuditLog> logs = auditLogService.getUserLogs(userId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return ResponseEntity.ok(ApiResponse.success(logs));
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"))
                .getId();
    }
}
