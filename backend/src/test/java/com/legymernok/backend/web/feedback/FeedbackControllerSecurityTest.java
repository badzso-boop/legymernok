package com.legymernok.backend.web.feedback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.config.SecurityConfig;
import com.legymernok.backend.dto.feedback.FeedbackIssueResponse;
import com.legymernok.backend.security.JwtAuthenticationFilter;
import com.legymernok.backend.security.JwtService;
import com.legymernok.backend.service.feedback.FeedbackService;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FeedbackController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class FeedbackControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FeedbackService feedbackService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private FeedbackIssueResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockResponse = FeedbackIssueResponse.builder()
                .number(1)
                .title("Sample")
                .bodyPreview("Sample body")
                .url("https://github.com/badzso-boop/legymernok/issues/1")
                .state("open")
                .authorUsername("qa_cadet")
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("GET /api/feedback - when not authenticated should return FORBIDDEN")
    void listFeedback_whenNotAuthenticated_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/feedback"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/feedback - any authenticated cadet should be able to list")
    @WithMockUser
    void listFeedback_whenAuthenticated_shouldReturnOk() throws Exception {
        when(feedbackService.listFeedback()).thenReturn(List.of(mockResponse));

        mockMvc.perform(get("/api/feedback"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/feedback - when not authenticated should return FORBIDDEN")
    void submitFeedback_whenNotAuthenticated_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/feedback")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Missing feature\",\"description\":\"Please add X\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/feedback - any authenticated cadet (no special permission) should be able to submit")
    @WithMockUser(username = "qa_cadet")
    void submitFeedback_whenAuthenticated_shouldReturnCreated() throws Exception {
        when(feedbackService.submitFeedback(any(), anyString())).thenReturn(mockResponse);

        mockMvc.perform(post("/api/feedback")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Missing feature\",\"description\":\"Please add X\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/feedback - blank title should return BAD_REQUEST")
    @WithMockUser
    void submitFeedback_withBlankTitle_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/feedback")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"description\":\"Please add X\"}"))
                .andExpect(status().isBadRequest());
    }
}
