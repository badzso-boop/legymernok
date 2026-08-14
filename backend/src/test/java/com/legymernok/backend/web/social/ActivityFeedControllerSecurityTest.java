package com.legymernok.backend.web.social;

import com.legymernok.backend.config.SecurityConfig;
import com.legymernok.backend.security.JwtAuthenticationFilter;
import com.legymernok.backend.security.JwtService;
import com.legymernok.backend.service.social.ActivityFeedService;
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

@WebMvcTest(ActivityFeedController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class ActivityFeedControllerSecurityTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ActivityFeedService activityFeedService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /api/social/activity-feed - Anonymous request is rejected")
    void getActivityFeed_Unauthenticated_Rejected() throws Exception {
        mockMvc.perform(get("/api/social/activity-feed")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/social/activity-feed - Any authenticated cadet can access")
    @WithMockUser
    void getActivityFeed_Authenticated_Success() throws Exception {
        when(activityFeedService.getActivityFeed()).thenReturn(List.of());

        mockMvc.perform(get("/api/social/activity-feed")).andExpect(status().isOk());
    }
}
