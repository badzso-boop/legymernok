package com.legymernok.backend.service.role;

import com.legymernok.backend.dto.Roles.CreateRoleRequest;
import com.legymernok.backend.dto.Roles.RoleResponse;
import com.legymernok.backend.model.auth.Permission;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.model.cadet.Cadet;
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

        // Mockoljuk, hogy nincs még ilyen nevű role
        when(roleRepository.findByName("ROLE_TEST")).thenReturn(Optional.empty());
        // Mockoljuk az engedélyek lekérését
        when(permissionRepository.findAllById(any())).thenReturn(List.of(permission));

        // Mockoljuk a mentést
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
        // Arrange
        CreateRoleRequest request = new CreateRoleRequest();
        request.setName("ROLE_EXISTING");

        // Mockoljuk, hogy MÁR LÉTEZIK
        when(roleRepository.findByName("ROLE_EXISTING")).thenReturn(Optional.of(new Role()));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> roleService.createRole(request));
        verify(roleRepository, never()).save(any());
    }

    @Test
    void deleteRole_Success() {
        // Arrange
        UUID roleId = UUID.randomUUID();
        Role role = new Role();
        role.setId(roleId);
        role.setName("ROLE_TO_DELETE");

        // Létrehozunk egy usert, akinek megvan ez a role
        Cadet cadet = new Cadet();
        cadet.setId(UUID.randomUUID());
        cadet.setRoles(new HashSet<>(Set.of(role)));

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));

        // Mockoljuk, hogy találunk usert ezzel a role-lal
        when(cadetRepository.findAllByRoles_Id(roleId)).thenReturn(List.of(cadet));

        // Act
        roleService.deleteRole(roleId);

        // Assert
        // 1. Ellenőrizzük, hogy a usert elmentettük (frissítettük a role-jait)
        verify(cadetRepository).save(cadet);
        // Ellenőrizzük, hogy a user role-jai közül kikerült-e a törölt role
        assertTrue(cadet.getRoles().isEmpty());

        // 2. Ellenőrizzük, hogy magát a role-t töröltük
        verify(roleRepository).delete(role);
    }

    @Test
    void deleteRole_NotFound_ThrowsException() {
        UUID roleId = UUID.randomUUID();
        when(roleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> roleService.deleteRole(roleId));
    }

    @Test
    void updateRole_Success() {
        // Arrange
        UUID roleId = UUID.randomUUID();
        CreateRoleRequest request = new CreateRoleRequest();
        request.setName("ROLE_UPDATED");
        request.setDescription("Updated desc");
        request.setPermissionIds(Collections.emptySet());

        Role existingRole = new Role();
        existingRole.setId(roleId);
        existingRole.setName("ROLE_OLD");

        when(roleRepository.findById(roleId)).thenReturn(Optional.of(existingRole));
        // Mivel a név változott, ellenőrzi az egyediséget -> nincs ütközés
        when(roleRepository.findByName("ROLE_UPDATED")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        RoleResponse response = roleService.updateRole(roleId, request);

        // Assert
        assertEquals("ROLE_UPDATED", response.getName());
        assertEquals("Updated desc", response.getDescription());
        verify(roleRepository).save(existingRole);
    }
}
