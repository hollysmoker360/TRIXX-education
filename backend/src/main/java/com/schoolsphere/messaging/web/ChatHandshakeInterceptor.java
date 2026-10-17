package com.schoolsphere.messaging.web;

import com.schoolsphere.auth.api.TokenVerifier;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Validates the JWT at the WebSocket handshake (spec 7.4). Browsers cannot set headers on
 * WebSocket upgrades, so the access token may also travel as the access_token query parameter.
 */
@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_USER_ID = "schoolsphere.chat.userId";
    static final String ATTR_DISPLAY_NAME = "schoolsphere.chat.displayName";

    private final TokenVerifier tokenVerifier;

    public ChatHandshakeInterceptor(TokenVerifier tokenVerifier) {
        this.tokenVerifier = tokenVerifier;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = extractToken(request);
        var verified = tokenVerifier.verifyAccessToken(token);
        if (verified.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(ATTR_USER_ID, verified.get().userId().toString());
        attributes.put(ATTR_DISPLAY_NAME, verified.get().displayName());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private static String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        String query = request.getURI().getQuery();
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("access_token=")) {
                    return URLDecoder.decode(part.substring("access_token=".length()), StandardCharsets.UTF_8);
                }
            }
        }
        return null;
    }
}
