package com.bepmo.security.filter;

import com.bepmo.security.service.JwtBlacklistService;
import com.bepmo.security.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final JwtBlacklistService jwtBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            try {
                // Parse exactly once. Besides avoiding repeated crypto work, this removes a small
                // expiry-boundary race where token validity could change between multiple parses.
                Claims claims = jwtUtil.parseToken(token);
                String jti = claims.getId();
                String subject = claims.getSubject();
                String role = claims.get("role", String.class);

                if (!StringUtils.hasText(jti)
                        || !StringUtils.hasText(subject)
                        || !StringUtils.hasText(role)) {
                    throw new IllegalArgumentException("JWT is missing required claims");
                }

                if (!jwtBlacklistService.isBlacklisted(jti)) {
                    Long userId = Long.parseLong(subject);

                    // Spring Security expects role prefixed with "ROLE_".
                    var auth = new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                // Leave the request unauthenticated. Protected routes are then handled by the
                // configured AuthenticationEntryPoint and return 401 instead of leaking a 500.
                log.debug("Invalid JWT token: {}", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
