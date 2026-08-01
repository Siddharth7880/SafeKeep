package com.safekeep.backend.controller;

import com.safekeep.backend.dto.response.ApiResponse;
import com.safekeep.backend.entity.User;
import com.safekeep.backend.enums.UserStatus;
import com.safekeep.backend.repository.UserRepository;
import com.safekeep.backend.scheduler.ContentReleaseJob;
import com.safekeep.backend.service.UserStatusTransitionService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class TestController {
    
    private final UserRepository userRepository;
    private final UserStatusTransitionService stateMachine;
    private final ContentReleaseJob contentReleaseJob;

    @PostMapping("/trigger-release")
    @Transactional
    public ResponseEntity<ApiResponse<String>> triggerRelease(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
                
        // For testing, always reset the release state so it can be triggered multiple times
        user.setReleasedAt(null);
        user.setStatus(UserStatus.GRACE_PERIOD);
        
        stateMachine.triggerRelease(user);
        userRepository.save(user);
        
        // Execute the actual release job which sends the emails
        contentReleaseJob.releaseContent(user);
        
        return ResponseEntity.ok(ApiResponse.success("Released", "Content release triggered successfully for testing."));
    }
}
