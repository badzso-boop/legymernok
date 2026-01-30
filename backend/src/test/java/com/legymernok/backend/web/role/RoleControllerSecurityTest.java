package com.legymernok.backend.web.role;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.config.SecurityConfig;
import com.legymernok.backend.dto.Roles.CreateRoleRequest;
import com.legymernok.backend.security.JwtAuthenticationFilter;
import com.legymernok.backend.security.JwtService;
import com.legymernok.backend.service.role.RoleService;
import com.legymernok.backend.web.Role.RoleController;
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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoleController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class RoleControllerSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private RoleService roleService;
    @MockitoBean private JwtService jwtService;
    @MockitoBean private UserDetailsService userDetailsService;


    @Test
    @DisplayName("GET /api/roles/permissions - Admin can see all permissions")
    @WithMockUser(authorities = {"role:read"})
    void listPermissions_Success() throws Exception {
        mockMvc.perform(get("/api/roles/permissions")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/roles - Admin can create role")
    @WithMockUser(authorities = {"role:write"})
    void createRole_Success() throws Exception {
        CreateRoleRequest req = new CreateRoleRequest();
        req.setName("ROLE_TEST");

        mockMvc.perform(post("/api/roles")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("DELETE /api/roles/{id} - Cadet cannot delete role")
    @WithMockUser(authorities = {"role:read"})
    void deleteRole_Forbidden() throws Exception {
        mockMvc.perform(delete("/api/roles/{id}", UUID.randomUUID()).with(csrf()))
                .andExpect(status().isForbidden());
    }
}