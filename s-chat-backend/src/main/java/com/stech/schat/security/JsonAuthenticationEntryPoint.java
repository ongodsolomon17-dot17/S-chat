package com.stech.schat.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stech.schat.dto.ApiError;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Fires only when Spring Security has already decided a request needs authentication
 * and none was found/valid — i.e. strictly after the authorizeHttpRequests rules in
 * SecurityConfig have run. That's what makes this safe to add: public endpoints
 * (/api/auth/**, /actuator/health, /ws/**) are never touched by this, no matter what
 * a client sends in its Authorization header, because Spring Security never calls it
 * for those routes in the first place.
 *
 * Replaces Spring Security's default entry point (which returns an empty 401 body)
 * with the same ApiError shape every other error response in this API already uses,
 * so the frontend's errorFromBody() (which reads body.messages[0]) shows something
 * meaningful instead of falling back to a generic message.
 */
@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException, ServletException {
        Object reasonAttr = request.getAttribute(JwtAuthFilter.AUTH_FAILURE_REASON_ATTR);
        String message = (reasonAttr instanceof String)
                ? (String) reasonAttr
                : "Authentication is required to access this resource.";

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                objectMapper.writeValueAsString(ApiError.of(401, "UNAUTHORIZED", message)));
    }
}
