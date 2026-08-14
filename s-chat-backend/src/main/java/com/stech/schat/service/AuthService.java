package com.stech.schat.service;

import com.stech.schat.dto.AuthResponse;
import com.stech.schat.dto.LoginRequest;
import com.stech.schat.dto.SignupRequest;
import com.stech.schat.exception.AccountLockedException;
import com.stech.schat.exception.DuplicateUserException;
import com.stech.schat.exception.InvalidCredentialsException;
import com.stech.schat.model.Role;
import com.stech.schat.model.User;
import com.stech.schat.repository.UserRepository;
import com.stech.schat.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserService userService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        JwtService jwtService, UserService userService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userService = userService;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new DuplicateUserException("That username is already taken");
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateUserException("An account with that email already exists");
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email().toLowerCase())
                .phoneNumber(request.phoneNumber())
                .publicId(userService.generateUniquePublicId())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .build();

        userRepository.save(user);
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository
                .findByUsernameIgnoreCaseOrEmailIgnoreCase(request.usernameOrEmail(), request.usernameOrEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Incorrect username/email or password"));

        if (user.isDeleted()) {
            throw new InvalidCredentialsException("Incorrect username/email or password");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new AccountLockedException(user.getLockedUntil());
        }

        if (!user.isAccountEnabled()) {
            throw new InvalidCredentialsException("This account has been disabled");
        }

        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());

        if (!passwordMatches) {
            registerFailedAttempt(user);
            throw new InvalidCredentialsException("Incorrect username/email or password");
        }

        // Success: reset the failure counter and record the login
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return issueTokens(user);
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_MINUTES, ChronoUnit.MINUTES));
        }
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidCredentialsException("Invalid refresh token");
        }

        try {
            Claims claims = jwtService.parseAndValidate(refreshToken);
            if (!"refresh".equals(claims.get("type", String.class))) {
                throw new InvalidCredentialsException("Invalid refresh token");
            }

            java.util.UUID userId = java.util.UUID.fromString(claims.getSubject());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new InvalidCredentialsException("Invalid refresh token"));

            if (user.isDeleted() || !user.isAccountEnabled()) {
                throw new InvalidCredentialsException("Invalid refresh token");
            }

            return issueTokens(user);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidCredentialsException("Invalid refresh token");
        }
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        return new AuthResponse(
                accessToken,
                refreshToken,
                user.getUsername(),
                user.getRole().name(),
                user.getPublicId(),
                jwtService.getAccessTokenTtlSeconds()
        );
    }
}
