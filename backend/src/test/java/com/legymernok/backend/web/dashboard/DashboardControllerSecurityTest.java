package com.legymernok.backend.web.dashboard;

import com.legymernok.backend.config.SecurityConfig;
import com.legymernok.backend.dto.dashboard.ContinueResponse;
import com.legymernok.backend.security.JwtAuthenticationFilter;
import com.legymernok.backend.security.JwtService;
import com.legymernok.backend.service.dashboard.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class DashboardControllerSecurityTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DashboardService dashboardService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /api/dashboard/continue - Anonymous request is rejected")
    void getContinue_Unauthenticated_Rejected() throws Exception {
        mockMvc.perform(get("/api/dashboard/continue")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/dashboard/continue - Any authenticated cadet can access")
    @WithMockUser
    void getContinue_Authenticated_Success() throws Exception {
        when(dashboardService.getContinue()).thenReturn(ContinueResponse.builder().type("MISSION").build());

        mockMvc.perform(get("/api/dashboard/continue")).andExpect(status().isOk());
    }
}
