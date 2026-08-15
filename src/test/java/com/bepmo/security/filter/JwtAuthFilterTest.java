package com.bepmo.security.filter;

import com.bepmo.security.service.JwtBlacklistService;
import com.bepmo.security.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock JwtUtil jwtUtil;
    @Mock JwtBlacklistService jwtBlacklistService;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain filterChain;
    @Mock Claims claims;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validToken_isParsedOnce_andAuthenticates() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwtUtil.parseToken("token")).thenReturn(claims);
        when(claims.getId()).thenReturn("jti-1");
        when(claims.getSubject()).thenReturn("42");
        when(claims.get("role", String.class)).thenReturn("RESTAURANT_OWNER");
        when(jwtBlacklistService.isBlacklisted("jti-1")).thenReturn(false);

        new JwtAuthFilter(jwtUtil, jwtBlacklistService)
                .doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(42L);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_RESTAURANT_OWNER");
        verify(jwtUtil, times(1)).parseToken("token");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidToken_doesNotLeakExceptionOrAuthenticate() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer bad-token");
        when(jwtUtil.parseToken("bad-token")).thenThrow(new MalformedJwtException("bad"));

        assertThatCode(() -> new JwtAuthFilter(jwtUtil, jwtBlacklistService)
                .doFilterInternal(request, response, filterChain))
                .doesNotThrowAnyException();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtBlacklistService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void blacklistedToken_staysUnauthenticated() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer token");
        when(jwtUtil.parseToken("token")).thenReturn(claims);
        when(claims.getId()).thenReturn("jti-1");
        when(claims.getSubject()).thenReturn("42");
        when(claims.get("role", String.class)).thenReturn("RESTAURANT_OWNER");
        when(jwtBlacklistService.isBlacklisted("jti-1")).thenReturn(true);

        new JwtAuthFilter(jwtUtil, jwtBlacklistService)
                .doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
