package com.bepmo.restaurant;

import com.bepmo.config.SecurityConfig;
import com.bepmo.restaurant.controller.RestaurantController;
import com.bepmo.restaurant.dto.RestaurantDtos.RestaurantProfile;
import com.bepmo.restaurant.service.RestaurantService;
import com.bepmo.security.filter.JwtAuthFilter;
import com.bepmo.security.filter.JwtAuthenticationEntryPoint;
import com.bepmo.security.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression test cho bug phát hiện lúc hoàn thiện Tuần 8: GET /restaurants/me vô tình
 * khớp pattern wildcard "GET /api/v1/restaurants/**" permitAll() trong SecurityConfig
 * (rule wildcard được viết trước, dành cho khách vãng lai xem quán công khai — /me thêm
 * sau bị lọt vào cùng pattern). Hệ quả: endpoint "owner only" bị public, không cần token
 * vẫn gọi được — currentUserId sẽ là null, findByOwnerId(null) trả rỗng nên không leak dữ
 * liệu thật, nhưng sai hợp đồng bảo mật (phải là 401, không phải request đi lọt qua rồi
 * mới 404 do tình cờ).
 *
 * Fix: thêm rule cụ thể "/restaurants/me".authenticated() TRƯỚC rule wildcard. Test này
 * khoá lại đúng hành vi để tránh regression nếu sau này có ai thêm route public mới dưới
 * /restaurants/** mà quên thứ tự khai báo rule (Spring Security match theo thứ tự, rule
 * đầu tiên khớp thắng).
 */
@WebMvcTest(RestaurantController.class)
@Import({ SecurityConfig.class, JwtAuthFilter.class, JwtAuthenticationEntryPoint.class })
class RestaurantControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RestaurantService restaurantService;

    // JwtAuthFilter cần JwtUtil để inject — không dùng thật trong test này vì không gửi
    // token nào cả, chỉ mock cho context load được.
    @MockBean
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("GET /restaurants/me không có token -> 401, KHÔNG được lọt qua permitAll")
    void getMine_withoutToken_returns401NotPublic() throws Exception {
        mockMvc.perform(get("/api/v1/restaurants/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /restaurants/{id} vẫn public như thiết kế ban đầu -> fix không siết nhầm route công khai")
    void getById_withoutToken_stillPublic() throws Exception {
        when(restaurantService.getProfile(anyLong())).thenReturn(
                new RestaurantProfile(1L, "Quán Test", "Mô tả", "Địa chỉ test", "Bún phở",
                        null, 50, OffsetDateTime.now())
        );

        mockMvc.perform(get("/api/v1/restaurants/1"))
                .andExpect(status().isOk());
    }
}
