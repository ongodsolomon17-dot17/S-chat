package com.stech.schat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long accessTokenTtlMs;
    private final long refreshTokenTtlMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-ttl-minutes:15}") long accessTtlMinutes,
            @Value("${app.jwt.refresh-ttl-days:7}") long refreshTtlDays
    ) {
        if (secret == null || secret.length() < 32) {
            // Fail fast on boot rather than silently signing tokens with a weak key
            throw new IllegalStateException(
                    "app.jwt.secret must be set and at least 32 characters. " +
                    "Set the JWT_SECRET environment variable on Render.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenTtlMs = accessTtlMinutes * 60_000;
        this.refreshTokenTtlMs = refreshTtlDays * 24 * 60 * 60_000;
    }

    public String generateAccessToken(UUID userId, String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("username", username)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessTokenTtlMs))
                .signWith(signingKey)
                .compact();
    }

    public String generateRefreshToken(UUID userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshTokenTtlMs))
                .signWith(signingKey)
                .compact();
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlMs / 1000;
    }

    /** Throws JwtException/subclasses if invalid or expired — callers should catch and reject the request. */
    public Claims parseAndValidate(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
