package com.stech.schat.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Works out the caller's IP for rate limiting.
 *
 * X-Forwarded-For is a list the CLIENT can pre-fill, so its first entry is attacker-controlled: trusting it lets
 * anyone dodge the login/recovery limiters by sending a fresh fake IP on every request. Proxies APPEND the address
 * they saw, so we count from the right instead: {@code app.security.trusted-proxy-hops} is how many proxies sit in
 * front of this app (Render's load balancer = 1). If login attempts from different users start sharing one bucket,
 * you have an extra proxy (e.g. a CDN) — raise TRUSTED_PROXY_HOPS to 2. Use 0 to ignore the header completely.
 */
@Component
public class ClientIpResolver {

    private final int trustedProxyHops;

    public ClientIpResolver(@Value("${app.security.trusted-proxy-hops:1}") int trustedProxyHops) {
        this.trustedProxyHops = Math.max(0, trustedProxyHops);
    }

    public String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (trustedProxyHops > 0 && forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            int index = Math.max(0, parts.length - trustedProxyHops);
            String ip = parts[index].trim();
            if (!ip.isEmpty() && ip.length() <= 64) return ip;
        }
        return request.getRemoteAddr();
    }
}
