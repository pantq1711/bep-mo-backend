package com.bepmo.config;

import com.bepmo.security.filter.JwtAuthFilter;
import com.bepmo.security.filter.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private List<String> allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Không cấu hình httpBasic()/formLogin() (đúng, app JWT-only) nên Spring
                // Security tự fallback về Http403ForbiddenEntryPoint mặc định -> MỌI
                // request thiếu/sai token đều 403, kể cả trường hợp phải là 401 (chưa xác
                // thực). Ghi đè bằng entry point riêng để trả đúng 401 — bug này làm
                // tính năng "silent refresh" ở frontend không hoạt động, vì frontend chỉ
                // tự refresh khi thấy đúng status 401.
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth

                        // Public — Swagger
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Public — Auth endpoints
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout"
                        ).permitAll()

                        // Admin only
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // Signed media capability is owner-only.
                        .requestMatchers(HttpMethod.POST, "/api/v1/media/**")
                                .hasRole("RESTAURANT_OWNER")

                        // Owner API — declare before public GET wildcard because Spring Security
                        // applies the first matching rule. Keeping writes OWNER-only prevents an
                        // authenticated ADMIN account from accidentally using owner workflows.
                        .requestMatchers(HttpMethod.GET, "/api/v1/restaurants/me")
                                .hasRole("RESTAURANT_OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/restaurants/**")
                                .hasRole("RESTAURANT_OWNER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/restaurants/**")
                                .hasRole("RESTAURANT_OWNER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/restaurants/**")
                                .hasRole("RESTAURANT_OWNER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/restaurants/**")
                                .hasRole("RESTAURANT_OWNER")

                        // Public — Visitor read-only. Individual services still enforce
                        // restaurant visibility so nested resources of HIDDEN restaurants
                        // are not exposed to anonymous visitors.
                        .requestMatchers(HttpMethod.GET, "/api/v1/restaurants/**").permitAll()

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Frontend dev server (Vite) chạy khác origin với backend. Danh sách origin
    // được cấu hình qua app.cors.allowed-origins / APP_CORS_ALLOWED_ORIGINS thay vì
    // hard-code trong security config. Không dùng "*" khi allowCredentials(true).
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        if (origins.contains("*")) {
            throw new IllegalStateException("CORS wildcard '*' is not allowed when credentials are enabled");
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}