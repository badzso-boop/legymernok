package com.legymernok.backend.web.sector;

import com.legymernok.backend.config.SecurityConfig;
import com.legymernok.backend.dto.sector.SectorResponse;
import com.legymernok.backend.security.JwtAuthenticationFilter;
import com.legymernok.backend.security.JwtService;
import com.legymernok.backend.service.sector.SectorService;
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
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SectorController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SectorControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SectorService sectorService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /api/sectors - Cadet (sector:read) should succeed")
    @WithMockUser(username = "cadet", authorities = {"sector:read"})
    void getAllSectors_AsCadet_ShouldSucceed() throws Exception {
        when(sectorService.getAllSectors()).thenReturn(List.of());

        mockMvc.perform(get("/api/sectors"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/sectors - no matching authority should fail")
    @WithMockUser(username = "nobody", authorities = {"mission:read"})
    void getAllSectors_NoAuthority_ShouldFail() throws Exception {
        mockMvc.perform(get("/api/sectors"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/sectors - Admin (sector:write) should succeed")
    @WithMockUser(username = "admin", authorities = {"sector:write"})
    void createSector_AsAdmin_ShouldSucceed() throws Exception {
        when(sectorService.createSector(org.mockito.ArgumentMatchers.any()))
                .thenReturn(SectorResponse.builder().id(UUID.randomUUID()).name("Fizika").build());

        mockMvc.perform(post("/api/sectors")
                        .contentType("application/json")
                        .content("{\"name\":\"Fizika\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/sectors - Cadet (only sector:read) should fail")
    @WithMockUser(username = "cadet", authorities = {"sector:read"})
    void createSector_AsCadet_ShouldFail() throws Exception {
        mockMvc.perform(post("/api/sectors")
                        .contentType("application/json")
                        .content("{\"name\":\"Fizika\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/sectors/{id} - Cadet (only sector:read) should fail")
    @WithMockUser(username = "cadet", authorities = {"sector:read"})
    void deleteSector_AsCadet_ShouldFail() throws Exception {
        mockMvc.perform(delete("/api/sectors/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
