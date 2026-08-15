package com.legymernok.backend.web;

import com.legymernok.backend.config.SecurityConfig;
import com.legymernok.backend.security.JwtAuthenticationFilter;
import com.legymernok.backend.security.JwtService;
import com.legymernok.backend.service.admin.LogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminLogController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class AdminLogControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LogService logService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /api/admin/logs - Admin (logs:read) should succeed")
    @WithMockUser(username = "admin", authorities = {"logs:read"})
    void getLogs_AsAdmin_ShouldSucceed() throws Exception {
        when(logService.getLatestLogs(anyInt())).thenReturn(List.of("Log entry 1"));

        mockMvc.perform(get("/api/admin/logs"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/admin/logs - Cadet (no permission) should fail")
    @WithMockUser(username = "cadet", authorities = {"mission:read"})
    void getLogs_AsCadet_ShouldFail() throws Exception {
        mockMvc.perform(get("/api/admin/logs"))
                .andExpect(status().isForbidden());
    }
}