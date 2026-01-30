package com.legymernok.backend.web.mission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.config.SecurityConfig;
import com.legymernok.backend.dto.mission.CreateMissionRequest;
import com.legymernok.backend.dto.mission.MissionResponse;
import com.legymernok.backend.model.mission.Difficulty;
import com.legymernok.backend.model.mission.MissionType;
import com.legymernok.backend.security.JwtAuthenticationFilter;
import com.legymernok.backend.security.JwtService;
import com.legymernok.backend.service.mission.MissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MissionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class MissionControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MissionService missionService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private MissionResponse mockResponse;
    private UUID missionId;
    private UUID starSystemId;

    @BeforeEach
    void setUp() {
        missionId = UUID.randomUUID();
        starSystemId = UUID.randomUUID();

        mockResponse = MissionResponse.builder()
                .id(missionId)
                .starSystemId(starSystemId)
                .name("Test Mission")
                .descriptionMarkdown("# Test")
                .missionType(MissionType.CODING)
                .difficulty(Difficulty.EASY)
                .orderInSystem(1)
                .build();
    }

    @Test
    @DisplayName("GET /api/missions - Cadet (read) should succeed")
    @WithMockUser(username = "cadet", authorities = {"mission:read"})
    void getAllMissions_AsCadet_ShouldSucceed() throws Exception {
        when(missionService.getAllMissions()).thenReturn(List.of(mockResponse));

        mockMvc.perform(get("/api/missions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Mission"));
    }

    @Test
    @DisplayName("POST /api/missions - Admin (create) should succeed")
    @WithMockUser(username = "admin", authorities = {"mission:create"})
    void createMission_AsAdmin_ShouldSucceed() throws Exception {
        CreateMissionRequest request = new CreateMissionRequest();
        request.setName("New Mission");
        request.setStarSystemId(starSystemId);
        request.setMissionType(MissionType.CODING);
        request.setDifficulty(Difficulty.HARD);

        when(missionService.createMission(any(CreateMissionRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/missions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/missions - Cadet (no permission) should be Forbidden")
    @WithMockUser(username = "cadet", authorities = {"mission:read"})
    void createMission_AsCadet_ShouldFail() throws Exception {
        CreateMissionRequest request = new CreateMissionRequest();
        request.setName("Hacker Mission");

        mockMvc.perform(post("/api/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/missions/{id} - Admin (delete) should succeed")
    @WithMockUser(username = "admin", authorities = {"mission:delete"})
    void deleteMission_AsAdmin_ShouldSucceed() throws Exception {
        mockMvc.perform(delete("/api/missions/{id}", missionId).with(csrf()))
                .andExpect(status().isNoContent());

        verify(missionService).deleteMission(missionId);
    }

    @Test
    @DisplayName("POST /start - Cadet should succeed")
    @WithMockUser(username = "cadet_john", authorities = {"mission:start"})
    void startMission_AsCadet_ShouldSucceed() throws Exception {
        String mockRepoUrl = "http://gitea/cadet_john/mission-repo.git";
        when(missionService.startMission(eq(missionId), eq("cadet_john"))).thenReturn(mockRepoUrl);

        mockMvc.perform(post("/api/missions/{id}/start", missionId).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(mockRepoUrl));

        verify(missionService).startMission(missionId, "cadet_john");
    }
}