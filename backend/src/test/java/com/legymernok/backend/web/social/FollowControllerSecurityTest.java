package com.legymernok.backend.web.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.config.SecurityConfig;
import com.legymernok.backend.dto.social.CadetProfileResponse;
import com.legymernok.backend.security.JwtAuthenticationFilter;
import com.legymernok.backend.security.JwtService;
import com.legymernok.backend.service.social.CadetProfileService;
import com.legymernok.backend.service.social.FollowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FollowController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class FollowControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private FollowService followService;
    @MockitoBean private CadetProfileService cadetProfileService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    @DisplayName("POST /api/cadets/{id}/follow - Anonymous request is rejected")
    void follow_Unauthenticated_Rejected() throws Exception {
        mockMvc.perform(post("/api/cadets/{id}/follow", UUID.randomUUID()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/cadets/{id}/follow - Any authenticated cadet can follow")
    @WithMockUser
    void follow_Authenticated_Success() throws Exception {
        mockMvc.perform(post("/api/cadets/{id}/follow", UUID.randomUUID()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/cadets/{id}/follow - Any authenticated cadet can unfollow")
    @WithMockUser
    void unfollow_Authenticated_Success() throws Exception {
        mockMvc.perform(delete("/api/cadets/{id}/follow", UUID.randomUUID()).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("GET /api/cadets/search - Anonymous request is rejected")
    void search_Unauthenticated_Rejected() throws Exception {
        mockMvc.perform(get("/api/cadets/search").param("username", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/cadets/{id}/profile - Any authenticated cadet can view another's profile")
    @WithMockUser
    void getProfile_Authenticated_Success() throws Exception {
        UUID id = UUID.randomUUID();
        when(cadetProfileService.getProfile(id)).thenReturn(CadetProfileResponse.builder().id(id).build());

        mockMvc.perform(get("/api/cadets/{id}/profile", id)).andExpect(status().isOk());
    }
}
