package com.bepmo.admin;

import com.bepmo.admin.controller.AdminController;
import com.bepmo.admin.dto.AdminDtos.AdminRestaurantSummary;
import com.bepmo.admin.service.AdminService;
import com.bepmo.config.SecurityConfig;
import com.bepmo.restaurant.dto.RestaurantDtos.PagedResponse;
import com.bepmo.restaurant.entity.RestaurantStatus;
import com.bepmo.security.filter.JwtAuthFilter;
import com.bepmo.security.filter.JwtAuthenticationEntryPoint;
import com.bepmo.security.service.JwtBlacklistService;
import com.bepmo.security.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import({ SecurityConfig.class, JwtAuthFilter.class, JwtAuthenticationEntryPoint.class })
class AdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private JwtBlacklistService jwtBlacklistService;

    @Test
    @DisplayName("GET /admin/restaurants không token -> 401")
    void listRestaurants_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/restaurants"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "RESTAURANT_OWNER")
    @DisplayName("GET /admin/restaurants với owner -> 403")
    void listRestaurants_asOwner_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/restaurants"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /admin/restaurants với admin -> 200 và thấy cả trạng thái moderation")
    void listRestaurants_asAdmin_returnsPage() throws Exception {
        var item = new AdminRestaurantSummary(
                1L,
                "Quán Test",
                "Hà Nội",
                "Phở",
                RestaurantStatus.HIDDEN,
                OffsetDateTime.parse("2026-08-13T10:00:00+07:00")
        );
        when(adminService.listRestaurants(0, 20))
                .thenReturn(new PagedResponse<>(List.of(item), 0, 20, 1, 1, true));

        mockMvc.perform(get("/api/v1/admin/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].status").value("HIDDEN"));
    }
}
