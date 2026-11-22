package com.stech.schat.controller;

import com.stech.schat.dto.RecoveryRequestDto;
import com.stech.schat.dto.RecoveryResetRequest;
import com.stech.schat.exception.RateLimitExceededException;
import com.stech.schat.security.AuthRateLimiter;
import com.stech.schat.service.RecoveryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Public endpoints (under /api/auth/**) for "I forgot my password". */
@RestController
@RequestMapping("/api/auth/recovery")
public class RecoveryController {

    private final RecoveryService recoveryService;
    private final AuthRateLimiter rateLimiter;

    public RecoveryController(RecoveryService recoveryService, AuthRateLimiter rateLimiter) {
        this.recoveryService = recoveryService;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/channels")
    public ResponseEntity<Map<String, List<String>>> channels() {
        return ResponseEntity.ok(Map.of("channels", recoveryService.availableChannels().stream().map(Enum::name).toList()));
    }

    @PostMapping("/request")
    public ResponseEntity<Map<String, String>> request(@Valid @RequestBody RecoveryRequestDto body, HttpServletRequest http) {
        enforceRateLimit(http);
        recoveryService.request(body.identifier(), body.channel());
        // Same answer whether or not the account exists.
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("message", "If an account matches, we've sent a 6-digit code. It expires shortly."));
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody RecoveryResetRequest body, HttpServletRequest http) {
        enforceRateLimit(http);
        recoveryService.reset(body.identifier(), body.code(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    private void enforceRateLimit(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : http.getRemoteAddr();
        if (!rateLimiter.tryConsume(ip)) {
            throw new RateLimitExceededException("Too many attempts. Please wait a minute and try again.");
        }
    }
}
