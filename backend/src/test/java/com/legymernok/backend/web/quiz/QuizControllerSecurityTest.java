package com.legymernok.backend.web.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.config.SecurityConfig;
import com.legymernok.backend.dto.quiz.QuizDefinition;
import com.legymernok.backend.model.mission.MissionResult;
import com.legymernok.backend.security.JwtAuthenticationFilter;
import com.legymernok.backend.security.JwtService;
import com.legymernok.backend.service.quiz.QuizService;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QuizController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class QuizControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private QuizService quizService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private UUID missionId;
    private QuizDefinition mockQuizDefinition;
    private MissionResult mockResult;

    @BeforeEach
    void setUp() {
        missionId = UUID.randomUUID();

        QuizDefinition.QuizConfigDTO config = new QuizDefinition.QuizConfigDTO();
        config.setTimeLimitSeconds(300);
        config.setAllowNavigation(true);
        config.setShowSolutions(false);

        mockQuizDefinition = new QuizDefinition();
        mockQuizDefinition.setConfig(config);
        mockQuizDefinition.setQuestions(List.of());

        mockResult = MissionResult.builder()
                .id(UUID.randomUUID())
                .score(80.0)
                .maxScore(100.0)
                .percentage(80.0)
                .detailedAnswers("{}")
                .isLate(false)
                .build();
    }

    // =========================================================================
    // POST /{missionId}/start
    // =========================================================================

    @Test
    @DisplayName("POST /api/quiz/{missionId}/start - mission:start authority → 200 OK")
    @WithMockUser(authorities = "mission:start")
    void startQuiz_withValidAuthority_shouldReturnOk() throws Exception {
        when(quizService.startQuiz(any(), any())).thenReturn(mockQuizDefinition);

        mockMvc.perform(post("/api/quiz/{missionId}/start", missionId)
                        .with(csrf()))
                .andExpect(status().isOk());

        verify(quizService).startQuiz(eq(missionId), any());
    }

    @Test
    @DisplayName("POST /api/quiz/{missionId}/start - no authority → 403 FORBIDDEN")
    @WithMockUser
    void startQuiz_withoutAuthority_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/quiz/{missionId}/start", missionId)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(quizService, never()).startQuiz(any(), any());
    }

    @Test
    @DisplayName("POST /api/quiz/{missionId}/start - unauthenticated → 403 FORBIDDEN")
    void startQuiz_whenNotAuthenticated_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/quiz/{missionId}/start", missionId)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(quizService, never()).startQuiz(any(), any());
    }

    // =========================================================================
    // PUT /{missionId}/sync
    // =========================================================================

    @Test
    @DisplayName("PUT /api/quiz/{missionId}/sync - mission:start authority → 200 OK")
    @WithMockUser(authorities = "mission:start")
    void syncProgress_withValidAuthority_shouldReturnOk() throws Exception {
        doNothing().when(quizService).syncProgress(any(), any(), any());

        mockMvc.perform(put("/api/quiz/{missionId}/sync", missionId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("q1", List.of("o1")))))
                .andExpect(status().isOk());

        verify(quizService).syncProgress(eq(missionId), any(), any());
    }

    @Test
    @DisplayName("PUT /api/quiz/{missionId}/sync - no authority → 403 FORBIDDEN")
    @WithMockUser
    void syncProgress_withoutAuthority_shouldReturnForbidden() throws Exception {
        mockMvc.perform(put("/api/quiz/{missionId}/sync", missionId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("q1", List.of("o1")))))
                .andExpect(status().isForbidden());

        verify(quizService, never()).syncProgress(any(), any(), any());
    }

    @Test
    @DisplayName("PUT /api/quiz/{missionId}/sync - unauthenticated → 403 FORBIDDEN")
    void syncProgress_whenNotAuthenticated_shouldReturnForbidden() throws Exception {
        mockMvc.perform(put("/api/quiz/{missionId}/sync", missionId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("q1", List.of("o1")))))
                .andExpect(status().isForbidden());

        verify(quizService, never()).syncProgress(any(), any(), any());
    }

    // =========================================================================
    // POST /{missionId}/submit
    // =========================================================================

    @Test
    @DisplayName("POST /api/quiz/{missionId}/submit - mission:start authority → 200 OK")
    @WithMockUser(authorities = "mission:start")
    void submitQuiz_withValidAuthority_shouldReturnOk() throws Exception {
        when(quizService.submitQuiz(any(), any(), any())).thenReturn(mockResult);

        mockMvc.perform(post("/api/quiz/{missionId}/submit", missionId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("q1", List.of("o1")))))
                .andExpect(status().isOk());

        verify(quizService).submitQuiz(eq(missionId), any(), any());
    }

    @Test
    @DisplayName("POST /api/quiz/{missionId}/submit - no authority → 403 FORBIDDEN")
    @WithMockUser
    void submitQuiz_withoutAuthority_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/quiz/{missionId}/submit", missionId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("q1", List.of("o1")))))
                .andExpect(status().isForbidden());

        verify(quizService, never()).submitQuiz(any(), any(), any());
    }

    @Test
    @DisplayName("POST /api/quiz/{missionId}/submit - unauthenticated → 403 FORBIDDEN")
    void submitQuiz_whenNotAuthenticated_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/quiz/{missionId}/submit", missionId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("q1", List.of("o1")))))
                .andExpect(status().isForbidden());

        verify(quizService, never()).submitQuiz(any(), any(), any());
    }

    // =========================================================================
    // DELETE /{missionId}/sessions
    // =========================================================================

    @Test
    @DisplayName("DELETE /api/quiz/{missionId}/sessions - mission:edit authority → 204 NO CONTENT")
    @WithMockUser(authorities = "mission:edit")
    void clearAllSessions_withValidAuthority_shouldReturnNoContent() throws Exception {
        doNothing().when(quizService).clearAllSessions(any(), any());

        mockMvc.perform(delete("/api/quiz/{missionId}/sessions", missionId)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(quizService).clearAllSessions(eq(missionId), any());
    }

    @Test
    @DisplayName("DELETE /api/quiz/{missionId}/sessions - mission:start authority (cadet) → 403 FORBIDDEN")
    @WithMockUser(authorities = "mission:start")
    void clearAllSessions_withCadetAuthority_shouldReturnForbidden() throws Exception {
        mockMvc.perform(delete("/api/quiz/{missionId}/sessions", missionId)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(quizService, never()).clearAllSessions(any(), any());
    }

    @Test
    @DisplayName("DELETE /api/quiz/{missionId}/sessions - no authority → 403 FORBIDDEN")
    @WithMockUser
    void clearAllSessions_withoutAuthority_shouldReturnForbidden() throws Exception {
        mockMvc.perform(delete("/api/quiz/{missionId}/sessions", missionId)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(quizService, never()).clearAllSessions(any(), any());
    }

    @Test
    @DisplayName("DELETE /api/quiz/{missionId}/sessions - unauthenticated → 403 FORBIDDEN")
    void clearAllSessions_whenNotAuthenticated_shouldReturnForbidden() throws Exception {
        mockMvc.perform(delete("/api/quiz/{missionId}/sessions", missionId)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(quizService, never()).clearAllSessions(any(), any());
    }
}
