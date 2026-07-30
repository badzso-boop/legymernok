package com.legymernok.backend.web.featureflag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.config.SecurityConfig;
import com.legymernok.backend.dto.featureflag.FeatureFlagResponse;
import com.legymernok.backend.dto.featureflag.UpdateFeatureFlagRequest;
import com.legymernok.backend.security.JwtAuthenticationFilter;
import com.legymernok.backend.security.JwtService;
import com.legymernok.backend.service.featureflag.FeatureFlagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FeatureFlagController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class FeatureFlagControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private FeatureFlagService featureFlagService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /api/feature-flags - Admin (feature_flag:read) can list all flags")
    @WithMockUser(authorities = {"feature_flag:read"})
    void listFlags_Success() throws Exception {
        mockMvc.perform(get("/api/feature-flags")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/feature-flags - Cadet without feature_flag:read is forbidden")
    @WithMockUser(authorities = {"mission:read"})
    void listFlags_Forbidden() throws Exception {
        mockMvc.perform(get("/api/feature-flags")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/feature-flags/{key} - Anonymous request is rejected")
    void getFlagByKey_Unauthenticated_Rejected() throws Exception {
        mockMvc.perform(get("/api/feature-flags/ai_chatbot")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/feature-flags/{key} - Any authenticated user (no special permission) can read a flag")
    @WithMockUser(authorities = {"mission:read"})
    void getFlagByKey_AuthenticatedCadet_Success() throws Exception {
        when(featureFlagService.getFlagByKey(eq("ai_chatbot")))
                .thenReturn(FeatureFlagResponse.builder().key("ai_chatbot").enabled(false).build());

        mockMvc.perform(get("/api/feature-flags/ai_chatbot")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/feature-flags/{key} - Admin (feature_flag:write) can update a flag")
    @WithMockUser(authorities = {"feature_flag:write"})
    void updateFlag_Success() throws Exception {
        UpdateFeatureFlagRequest req = new UpdateFeatureFlagRequest();
        req.setEnabled(true);
        req.setDescription("desc");

        when(featureFlagService.updateFlag(eq("ai_chatbot"), any(UpdateFeatureFlagRequest.class)))
                .thenReturn(FeatureFlagResponse.builder().key("ai_chatbot").enabled(true).description("desc").build());

        mockMvc.perform(put("/api/feature-flags/ai_chatbot")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/feature-flags/{key} - Cadet without feature_flag:write is forbidden")
    @WithMockUser(authorities = {"mission:read"})
    void updateFlag_Forbidden() throws Exception {
        UpdateFeatureFlagRequest req = new UpdateFeatureFlagRequest();
        req.setEnabled(true);

        mockMvc.perform(put("/api/feature-flags/ai_chatbot")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }
}
