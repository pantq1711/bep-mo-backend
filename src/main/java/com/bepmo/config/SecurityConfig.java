package com.bepmo.config;

import com.bepmo.security.filter.JwtAuthFilter;
import com.bepmo.security.filter.JwtAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
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
                    "/api/v1/auth/refresh"
                ).permitAll()

                // Owner only — phải khai báo TRƯỚC rule wildcard public bên dưới,
                // Spring Security match theo thứ tự, rule đầu tiên khớp sẽ thắng.
                // Không có dòng này thì "/api/v1/restaurants/me" bị lọt vào pattern
                // GET "/api/v1/restaurants/**" permitAll ở dưới -> thành public nhầm.
                .requestMatchers(HttpMethod.GET, "/api/v1/restaurants/me").authenticated()

                // Public — Visitor read-only
                .requestMatchers(HttpMethod.GET, "/api/v1/restaurants/**").permitAll()

                // Admin only
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

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
}
