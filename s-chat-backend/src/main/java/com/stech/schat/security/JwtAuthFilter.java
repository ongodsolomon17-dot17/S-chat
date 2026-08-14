package com.stech.schat.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    /** Request attribute JsonAuthenticationEntryPoint reads to give a more specific 401 message. */
    public static final String AUTH_FAILURE_REASON_ATTR = "schat.authFailureReason";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parseAndValidate(token);

                if ("access".equals(claims.get("type", String.class))) {
                    String userId = claims.getSubject();
                    String role = claims.get("role", String.class);

                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
                    var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    request.setAttribute(AUTH_FAILURE_REASON_ATTR, "Invalid access token");
                }
            } catch (ExpiredJwtException ex) {
                // Deliberately does NOT short-circuit the chain here: this filter's only job is to
                // populate (or not populate) the security context. Whether a 401 actually gets
                // returned is still Spring Security's call (authorizeHttpRequests below) — that's
                // what keeps public endpoints (/api/auth/**, /actuator/health, /ws/**) working even
                // if a stale/expired Authorization header happens to be present on the request.
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_FAILURE_REASON_ATTR, "Your session has expired. Please log in again.");
            } catch (JwtException | IllegalArgumentException ex) {
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_FAILURE_REASON_ATTR, "Invalid access token");
            }
        }

        filterChain.doFilter(request, response);
    }
}
