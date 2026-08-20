package com.stech.schat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TurnCredentialService {
    private final String turnUrls;
    private final String sharedSecret;
    private final long ttlSeconds;

    public TurnCredentialService(
            @Value("${app.turn.urls:}") String turnUrls,
            @Value("${app.turn.shared-secret:}") String sharedSecret,
            @Value("${app.turn.ttl-seconds:3600}") long ttlSeconds) {
        this.turnUrls = turnUrls == null ? "" : turnUrls.trim();
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret.trim();
        this.ttlSeconds = Math.max(300, Math.min(ttlSeconds, 86400));
    }

    public Map<String, Object> issue(UUID userId) {
        List<Map<String, Object>> servers = new java.util.ArrayList<>();
        servers.add(Map.of("urls", "stun:stun.l.google.com:19302"));
        servers.add(Map.of("urls", "stun:stun1.l.google.com:19302"));

        if (!turnUrls.isBlank() && !sharedSecret.isBlank()) {
            long expiry = Instant.now().getEpochSecond() + ttlSeconds;
            String username = expiry + ":" + userId;
            String credential = hmacSha1Base64(sharedSecret, username);
            Map<String, Object> turn = new LinkedHashMap<>();
            turn.put("urls", List.of(turnUrls.split("\\s*,\\s*")));
            turn.put("username", username);
            turn.put("credential", credential);
            servers.add(turn);
        }

        return Map.of("iceServers", servers, "expiresAt", Instant.now().plusSeconds(ttlSeconds).toString());
    }

    private String hmacSha1Base64(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create TURN credentials");
        }
    }
}
