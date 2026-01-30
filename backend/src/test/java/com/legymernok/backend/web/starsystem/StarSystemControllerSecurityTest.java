package com.legymernok.backend.web.starsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.config.SecurityConfig;
import com.legymernok.backend.dto.starsystem.CreateStarSystemRequest;
import com.legymernok.backend.dto.starsystem.StarSystemResponse;
import com.legymernok.backend.security.JwtAuthenticationFilter;
import com.legymernok.backend.security.JwtService;
import com.legymernok.backend.service.starsystem.StarSystemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StarSystemController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class StarSystemControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StarSystemService starSystemService;

    // A SecurityConfig miatt kellenek ezek a mockok:
    @MockitoBean
    private JwtService jwtService;
    
    @MockitoBean
    private UserDetailsService userDetailsService;

    private StarSystemResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockResponse = StarSystemResponse.builder()
                .id(UUID.randomUUID())
                .name("Alpha Centauri")
                .description("A nearest star system")
                .iconUrl("http://icon.url")
                .build();
    }

    // --- GET (READ) TESZTEK ---

    @Test
    @DisplayName("GET /api/star-systems - ADMIN should succeed (has read authority)")
    @WithMockUser(username = "admin", authorities = {"starsystem:read"})
    void getAllStarSystems_AsAdmin_ShouldReturnList() throws Exception {
        when(starSystemService.getAllStarSystems()).thenReturn(List.of(mockResponse));

        mockMvc.perform(get("/api/star-systems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].name").value("Alpha Centauri"));
    }

    @Test
    @DisplayName("GET /api/star-systems - CADET should succeed (has read authority)")
    @WithMockUser(username = "cadet", authorities = {"starsystem:read"})
    void getAllStarSystems_AsCadet_ShouldReturnList() throws Exception {
        when(starSystemService.getAllStarSystems()).thenReturn(List.of(mockResponse));

        mockMvc.perform(get("/api/star-systems"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/star-systems - Unauthorized user should fail (401)")
    void getAllStarSystems_NoUser_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/star-systems"))
                .andExpect(status().isForbidden());
    }

    // --- POST (CREATE) TESZTEK ---

    @Test
    @DisplayName("POST /api/star-systems - ADMIN should succeed (has create authority)")
    @WithMockUser(username = "admin", authorities = {"starsystem:create"})
    void createStarSystem_AsAdmin_ShouldReturnCreated() throws Exception {
        CreateStarSystemRequest request = new CreateStarSystemRequest();
        request.setName("New System");
        request.setDescription("Desc");
        request.setIconUrl("url");

        when(starSystemService.createStarSystem(any(CreateStarSystemRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/star-systems")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Alpha Centauri"));
    }

    @Test
    @DisplayName("POST /api/star-systems - CADET should fail (missing create authority)")
    @WithMockUser(username = "cadet", authorities = {"starsystem:read"})
    void createStarSystem_AsCadet_ShouldReturnForbidden() throws Exception {
        CreateStarSystemRequest request = new CreateStarSystemRequest();
        request.setName("Hacker System");

        mockMvc.perform(post("/api/star-systems")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // --- DELETE TESZTEK ---

    @Test
    @DisplayName("DELETE /api/star-systems/{id} - ADMIN should succeed")
    @WithMockUser(username = "admin", authorities = {"starsystem:delete"})
    void deleteStarSystem_AsAdmin_ShouldReturnNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/star-systems/{id}", id)
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(starSystemService).deleteStarSystem(id);
    }

    @Test
    @DisplayName("DELETE /api/star-systems/{id} - CADET should fail")
    @WithMockUser(username = "cadet", authorities = {"starsystem:read"})
    void deleteStarSystem_AsCadet_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(delete("/api/star-systems/{id}", UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }
}