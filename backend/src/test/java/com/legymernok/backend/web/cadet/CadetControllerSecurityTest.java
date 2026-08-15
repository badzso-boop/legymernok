package com.legymernok.backend.web.cadet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.config.SecurityConfig;
import com.legymernok.backend.dto.cadet.CadetResponse;
import com.legymernok.backend.dto.cadet.CreateCadetRequest;
import com.legymernok.backend.security.JwtAuthenticationFilter;
import com.legymernok.backend.security.JwtService;
import com.legymernok.backend.service.cadet.CadetService;
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
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CadetController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class CadetControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CadetService cadetService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private CadetResponse mockResponse;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mockResponse = CadetResponse.builder()
                .id(userId)
                .username("testuser")
                .email("test@test.com")
                .roles(Set.of("ROLE_CADET"))
                .build();
    }

    @Test
    @DisplayName("GET /api/users - Admin should see all users")
    @WithMockUser(username = "admin", authorities = {"user:read"})
    void getAllCadets_AsAdmin_ShouldSucceed() throws Exception {
        when(cadetService.getAllCadets()).thenReturn(List.of(mockResponse));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("testuser"));
    }

    @Test
    @DisplayName("PUT /api/users/{id} - Admin can update user")
    @WithMockUser(username = "admin", authorities = {"user:edit"})
    void updateCadet_AsAdmin_ShouldSucceed() throws Exception {
        CreateCadetRequest request = new CreateCadetRequest();
        request.setEmail("new@email.com");
        request.setFullName("Updated Name");

        when(cadetService.updateCadet(eq(userId), any(CreateCadetRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(put("/api/users/{id}", userId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /api/users/{id} - Admin can delete user")
    @WithMockUser(username = "admin", authorities = {"user:delete"})
    void deleteCadet_AsAdmin_ShouldSucceed() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", userId).with(csrf()))
                .andExpect(status().isNoContent());

        verify(cadetService).deleteCadet(userId);
    }

    @Test
    @DisplayName("DELETE /api/users/{id} - Cadet cannot delete user")
    @WithMockUser(username = "cadet", authorities = {"user:read"})
    void deleteCadet_AsCadet_ShouldFail() throws Exception {
        mockMvc.perform(delete("/api/users/{id}", userId).with(csrf()))
                .andExpect(status().isForbidden());
    }
}