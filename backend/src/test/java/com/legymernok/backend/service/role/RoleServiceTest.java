package com.legymernok.backend.service.role;

import com.legymernok.backend.dto.Roles.CreateRoleRequest;
import com.legymernok.backend.dto.Roles.RoleResponse;
import com.legymernok.backend.model.auth.Permission;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.repository.auth.PermissionRepository;
import com.legymernok.backend.repository.auth.RoleRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private CadetRepository cadetRepository;

    @InjectMocks
    private RoleService roleService;

    @Test
    void createRole_Success() {
        // Arrange
        UUID permId = UUID.randomUUID();
        CreateRoleRequest request = new CreateRoleRequest();
        request.setName("ROLE_TEST");
        request.setDescription("Test role");
        request.setPermissionIds(Set.of(permId));

        Permission permission = new Permission();
        permission.setId(permId);
        permission.setName("test:read");

        when(roleRepository.findByName("ROLE_TEST")).thenReturn(Optional.empty());
        when(permissionRepository.findAllById(any())).thenReturn(List.of(permission));

        when(roleRepository.save(any(Role.class))).thenAnswer(i -> {
            Role r = i.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        // Act
        RoleResponse response = roleService.createRole(request);

        // Assert
        assertNotNull(response);
        assertEquals("ROLE_TEST", response.getName());
        assertEquals(1, response.getPermissions().size());
        verify(roleRepository).save(any(Role.class));
    }

    @Test
    void createRole_DuplicateName_ThrowsException() {
        CreateRoleRequest request = new CreateRoleRequest();
        request.setName("ROLE_EXISTING");

        when(roleRepository.findByName("ROLE_EXISTING")).thenReturn(Optional.of(new Role()));

        assertThrows(RuntimeException.class, () -> roleService.createRole(request));
        verify(roleRepository, never()).save(any());
    }

    @Test
    void deleteRole_Success() {
        UUID roleId = UUID.randomUUID();
        Role role = new Role();
        role.setId(roleId);

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(roleRepository.existsById(roleId)).thenReturn(true);
        when(cadetRepository.findAllByRoles_Id(roleId)).thenReturn(Collections.emptyList());

        roleService.deleteRole(roleId);

        verify(roleRepository).deleteById(roleId);
    }
}
