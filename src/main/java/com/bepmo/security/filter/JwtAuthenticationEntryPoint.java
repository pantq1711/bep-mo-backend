package com.bepmo.security.filter;

import com.bepmo.common.exception.GlobalExceptionHandler.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

/**
 * Bug phát hiện lúc chạy mvn test thật lần đầu: SecurityConfig không cấu hình
 * httpBasic()/formLogin() (đúng, vì app JWT-only) -> Spring Security fallback về
 * Http403ForbiddenEntryPoint mặc định -> MỌI request thiếu/sai/hết hạn token đều trả
 * 403, không bao giờ trả 401. Hệ quả: frontend (api/client.js) chỉ tự động refresh
 * token khi gặp đúng status 401 -> tính năng "silent refresh" coi như không hoạt động
 * trên thực tế dù code refresh-token đã implement đúng ở tầng AuthService.
 *
 * Entry point này thay thế default, trả đúng 401 cho mọi trường hợp CHƯA xác thực
 * được (thiếu token / token sai / token hết hạn). 403 giờ chỉ còn xảy ra cho trường
 * hợp ĐÃ xác thực nhưng không đủ quyền (vd non-admin gọi /admin/**) — đúng ngữ nghĩa
 * REST chuẩn, tách bạch "chưa đăng nhập" và "không đủ quyền".
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ErrorResponse body = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                "Thiếu token hoặc token không hợp lệ/đã hết hạn",
                OffsetDateTime.now()
        );

        objectMapper.writeValue(response.getWriter(), body);
    }
}
