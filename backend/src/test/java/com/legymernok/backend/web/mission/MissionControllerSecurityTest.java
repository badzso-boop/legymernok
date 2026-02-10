package com.legymernok.backend.web.mission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.config.SecurityConfig;
import com.legymernok.backend.dto.mission.CreateMissionInitialRequest;
import com.legymernok.backend.dto.mission.CreateMissionRequest;
import com.legymernok.backend.dto.mission.MissionForgeContentRequest;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
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

    private CreateMissionInitialRequest createForgeInitialRequest() {
        CreateMissionInitialRequest request = new CreateMissionInitialRequest();
        request.setStarSystemId(UUID.randomUUID());
        request.setName("Test Forge Mission");
        request.setTemplateLanguage("javascript");
        request.setDifficulty(Difficulty.EASY);
        request.setMissionType(MissionType.CODING);
        request.setOrderInSystem(1);
        return request;
    }

    private MissionForgeContentRequest createForgeContentRequest() {
        MissionForgeContentRequest request = new MissionForgeContentRequest();
        // request.setMissionId(UUID.randomUUID()); // Ezt az URL-ből kapja
        request.setFiles(Map.of("solution.js", "function add(){return 1;}"));
        return request;
    }

    @Test
    @DisplayName("POST /api/missions/forge/initialize - with mission:create authority should return CREATED")
    @WithMockUser(authorities = "mission:create") // Cadet joga van létrehozni
    void initializeForgeMission_withValidAuthority_shouldReturnCreated() throws Exception {
        when(missionService.initializeForgeMission(any(CreateMissionInitialRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/missions/forge/initialize") // ÚJ VÉGPONT
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createForgeInitialRequest())))
                .andExpect(status().isCreated());

        verify(missionService).initializeForgeMission(any(CreateMissionInitialRequest.class));
    }

    @Test
    @DisplayName("POST /api/missions/forge/initialize - without mission:create authority should return FORBIDDEN")
    @WithMockUser // Nincs joga
    void initializeForgeMission_withoutAuthority_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/missions/forge/initialize")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createForgeInitialRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/missions/forge/initialize - when not authenticated should return FORBIDDEN")
    void initializeForgeMission_whenNotAuthenticated_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/missions/forge/initialize")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createForgeInitialRequest())))
                .andExpect(status().isForbidden());
    }

    // --- saveForgeMissionContent (POST /api/missions/{missionId}/forge/save) TESZTEK ---
    @Test
    @DisplayName("POST /api/missions/{missionId}/forge/save - with mission:edit authority should return OK")
    @WithMockUser(authorities = "mission:edit") // Edit joggal menthet
    void saveForgeMissionContent_withValidAuthority_shouldReturnOk() throws Exception {
        when(missionService.saveForgeMissionContent(any(MissionForgeContentRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/missions/{missionId}/forge/save", missionId) // ÚJ VÉGPONT
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createForgeContentRequest())))
                .andExpect(status().isOk());

        verify(missionService).saveForgeMissionContent(any(MissionForgeContentRequest.class));
    }

    @Test
    @DisplayName("POST /api/missions/{missionId}/forge/save - without mission:edit authority should return FORBIDDEN")
    @WithMockUser // Nincs joga
    void saveForgeMissionContent_withoutAuthority_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/missions/{missionId}/forge/save", missionId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createForgeContentRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/missions/{missionId}/forge/save - when not authenticated should return FORBIDDEN")
    void saveForgeMissionContent_whenNotAuthenticated_shouldReturnForbidden() throws Exception {
        mockMvc.perform(post("/api/missions/{missionId}/forge/save", missionId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createForgeContentRequest())))
                .andExpect(status().isForbidden());
    }

    // --- getMissionFiles (GET /api/missions/{missionId}/forge/files) TESZTEK ---
    @Test
    @DisplayName("GET /api/missions/{missionId}/forge/files - with mission:read authority should return OK")
    @WithMockUser(authorities = "mission:read") // Read joggal olvashat
    void getMissionFiles_withValidAuthority_shouldReturnOk() throws Exception {
        when(missionService.getMissionFiles(any(UUID.class))).thenReturn(Map.of("solution.js", "code"));

        mockMvc.perform(get("/api/missions/{missionId}/forge/files", missionId)) // ÚJ VÉGPONT
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.['solution.js']").value("code"));

        verify(missionService).getMissionFiles(eq(missionId));
    }

    @Test
    @DisplayName("GET /api/missions/{missionId}/forge/files - without mission:read authority should return FORBIDDEN")
    @WithMockUser // Nincs joga
    void getMissionFiles_withoutAuthority_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/missions/{missionId}/forge/files", missionId))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/missions/{missionId}/forge/files - when not authenticated should return FORBIDDEN")
    void getMissionFiles_whenNotAuthenticated_shouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/missions/{missionId}/forge/files", missionId))
                .andExpect(status().isForbidden());
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