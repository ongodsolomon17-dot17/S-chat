package com.stech.schat.controller;

import com.stech.schat.dto.AuthResponse;
import com.stech.schat.dto.LoginRequest;
import com.stech.schat.dto.RefreshTokenRequest;
import com.stech.schat.dto.SignupRequest;
import com.stech.schat.exception.RateLimitExceededException;
import com.stech.schat.security.AuthRateLimiter;
import com.stech.schat.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;

    public AuthController(AuthService authService, AuthRateLimiter rateLimiter) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request, HttpServletRequest http) {
        enforceRateLimit(http);
        return ResponseEntity.ok(authService.signup(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        enforceRateLimit(http);
        return ResponseEntity.ok(authService.login(request));
    }

    private void enforceRateLimit(HttpServletRequest http) {
        String clientIp = extractClientIp(http);
        if (!rateLimiter.tryConsume(clientIp)) {
            throw new RateLimitExceededException("Too many attempts. Please wait a minute and try again.");
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        // Render sits behind a proxy; X-Forwarded-For carries the real client IP
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
