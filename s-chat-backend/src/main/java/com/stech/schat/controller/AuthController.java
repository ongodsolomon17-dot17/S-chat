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
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import java.util.UUID;

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
        AuthResponse response = authService.signup(request);
        return withRefreshCookie(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestHeader(name = "X-S-Chat-Client", required = false) String clientHeader,
            @CookieValue(name = "schat_refresh", required = false) String refreshToken) {
        if (!"web".equals(clientHeader)) return ResponseEntity.status(403).build();
        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        return withRefreshCookie(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(name = "X-S-Chat-Client", required = false) String clientHeader,
            @CookieValue(name = "schat_refresh", required = false) String refreshToken) {
        if (!"web".equals(clientHeader)) return ResponseEntity.status(403).build();
        authService.logout(refreshToken);
        return ResponseEntity.noContent().header("Set-Cookie", clearRefreshCookie().toString()).build();
    }

    @PostMapping("/ws-ticket")
    public ResponseEntity<java.util.Map<String, String>> websocketTicket(
            Authentication authentication,
            @RequestHeader(name = "X-S-Chat-Client", required = false) String clientHeader) {
        if (!"web".equals(clientHeader) || authentication == null) return ResponseEntity.status(403).build();
        UUID userId = UUID.fromString((String) authentication.getPrincipal());
        return ResponseEntity.ok(java.util.Map.of("ticket", authService.issueWebSocketTicket(userId)));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        enforceRateLimit(http);
        return withRefreshCookie(authService.login(request));
    }

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthResponse response) {
        // Refresh tokens are HttpOnly and never exposed to JavaScript/localStorage.
        String refreshToken = response.refreshToken();
        return ResponseEntity.ok()
                .header("Set-Cookie", refreshCookie(refreshToken).toString())
                .body(response);
    }

    private ResponseCookie refreshCookie(String token) {
        return ResponseCookie.from("schat_refresh", token)
                .httpOnly(true).secure(true).sameSite("None")
                .path("/api/auth").maxAge(7L * 24 * 60 * 60).build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from("schat_refresh", "")
                .httpOnly(true).secure(true).sameSite("None")
                .path("/api/auth").maxAge(0).build();
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
