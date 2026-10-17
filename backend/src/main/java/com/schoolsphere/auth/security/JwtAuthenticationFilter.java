package com.schoolsphere.auth.security;

import com.schoolsphere.auth.internal.JwtService;
import com.schoolsphere.auth.internal.User;
import com.schoolsphere.auth.internal.UserRepository;
import com.schoolsphere.common.security.CurrentPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates the JWT access token on every request. The token version recorded in the token must
 * match the user's current version; a mismatch (password change, forced logout) invalidates the
 * token immediately (spec 8.1).
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String DEVICE_HEADER = "X-Device-Id";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parse(token);
                UUID userId = UUID.fromString(claims.getSubject());
                Integer tokenVersion = claims.get("ver", Integer.class);
                User user = userRepository.findById(userId)
                        .filter(u -> u.getDeletedAt() == null && u.getStatus().equals("active"))
                        .filter(u -> tokenVersion == null || tokenVersion == u.getTokenVersion())
                        .orElse(null);
                if (user != null) {
                    CurrentPrincipal principal = new CurrentPrincipal(
                            user.getId(),
                            user.getDisplayName(),
                            user.getEmail(),
                            null,
                            null,
                            Set.of(),
                            request.getHeader(DEVICE_HEADER));
                    var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    static SimpleGrantedAuthority authority(String permission) {
        return new SimpleGrantedAuthority(permission);
    }
}
