package com.legymernok.backend.web;

import com.legymernok.backend.config.SecurityConfig;
import com.legymernok.backend.dto.admin.AdminStatsResponse;
import com.legymernok.backend.security.JwtAuthenticationFilter;
import com.legymernok.backend.security.JwtService;
import com.legymernok.backend.service.admin.AdminStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminStatsController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminStatsControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminStatsService adminStatsService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /api/admin/stats - Admin (user:read) should succeed")
    @WithMockUser(username = "admin", authorities = {"user:read"})
    void getStats_AsAdmin_ShouldSucceed() throws Exception {
        when(adminStatsService.getStats()).thenReturn(
                AdminStatsResponse.builder()
                        .totalCadets(3)
                        .totalStarSystems(2)
                        .totalMissions(5)
                        .newCadetsThisWeek(1)
                        .openFeedbackCount(0)
                        .recentCadets(List.of())
                        .mostStartedStarSystems(List.of())
                        .build());

        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/admin/stats - Cadet (no permission) should fail")
    @WithMockUser(username = "cadet", authorities = {"mission:read"})
    void getStats_AsCadet_ShouldFail() throws Exception {
        mockMvc.perform(get("/api/admin/stats"))
                .andExpect(status().isForbidden());
    }
}
