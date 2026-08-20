package com.stech.schat.websocket;

import com.stech.schat.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;
    private final WebSocketTicketReplayGuard replayGuard;

    public JwtHandshakeInterceptor(JwtService jwtService, WebSocketTicketReplayGuard replayGuard) {
        this.jwtService = jwtService;
        this.replayGuard = replayGuard;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            response.setStatusCode(org.springframework.http.HttpStatus.BAD_REQUEST);
            return false;
        }

        // Never put the long-lived access JWT in the WebSocket URL: URLs can be logged by
        // proxies/observability systems. The frontend first obtains a 30-second WS ticket
        // over the authenticated HTTPS API, then spends that short-lived ticket once here.
        String token = servletRequest.getServletRequest().getParameter("ticket");
        if (token == null) {
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }

        try {
            Claims claims = jwtService.parseAndValidate(token);
            if (!"ws".equals(claims.get("type", String.class)) || claims.getId() == null || claims.getExpiration() == null) {
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }
            if (!replayGuard.consume(claims.getId(), claims.getExpiration().toInstant().getEpochSecond())) {
                response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put("userId", claims.getSubject());
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
