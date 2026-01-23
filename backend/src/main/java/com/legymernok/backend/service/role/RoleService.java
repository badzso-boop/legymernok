package com.legymernok.backend.service.role;

import com.legymernok.backend.dto.Permission.PermissionResponse;
import com.legymernok.backend.dto.Roles.CreateRoleRequest;
import com.legymernok.backend.dto.Roles.RoleResponse;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.auth.Permission;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.repository.auth.PermissionRepository;
import com.legymernok.backend.repository.auth.RoleRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final CadetRepository cadetRepository;

    @Transactional
    public RoleResponse createRole(CreateRoleRequest request) {
        // Validáció: Név egyedisége
        if (roleRepository.findByName(request.getName()).isPresent()) {
            throw new ResourceConflictException("Role", "name", request.getName());
        }

        // Engedélyek lekérése ID alapján
        Set<Permission> permissions = fetchPermissionsByIds(request.getPermissionIds());

        Role role = Role.builder()
                .name(request.getName())
                .description(request.getDescription())
                .permissions(permissions)
                .build();

        Role savedRole = roleRepository.save(role);
        return mapRoleToResponse(savedRole);
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::mapRoleToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoleResponse getRoleById(UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));
        return mapRoleToResponse(role);
    }

    @Transactional
    public RoleResponse updateRole(UUID id, CreateRoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));

        // Név egyediségének ellenőrzése, ha változott
        if (!role.getName().equals(request.getName()) &&
                roleRepository.findByName(request.getName()).isPresent()) {
            throw new ResourceConflictException("Role", "name", request.getName());
        }

        // Engedélyek frissítése
        Set<Permission> permissions = fetchPermissionsByIds(request.getPermissionIds());

        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setPermissions(permissions);

        Role updatedRole = roleRepository.save(role);
        return mapRoleToResponse(updatedRole);
    }

    @Transactional
    public void deleteRole(UUID id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "id", id));

        // 1. Megkeressük azokat a felhasználókat, akiknek ez a szerepkörük megvan

        List<Cadet> usersWithRole = cadetRepository.findAllByRoles_Id(id);

        // 2. Levesszük róluk a szerepkört
        for (Cadet cadet : usersWithRole) {
            cadet.getRoles().remove(role);
            cadetRepository.save(cadet); // Frissítjük a kapcsolatot
        }

        // 3. Most már biztonságosan törölhetjük a szerepkört
        roleRepository.delete(role);
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
                .map(this::mapPermissionToResponse)
                .collect(Collectors.toList());
    }

    private Set<Permission> fetchPermissionsByIds(Set<UUID> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new HashSet<>();
        }
        return new HashSet<>(permissionRepository.findAllById(permissionIds));
    }

    private RoleResponse mapRoleToResponse(Role role) {
        Set<PermissionResponse> permissionResponses = role.getPermissions().stream()
                .map(this::mapPermissionToResponse)
                .collect(Collectors.toSet());

        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .permissions(permissionResponses)
                .build();
    }

    private PermissionResponse mapPermissionToResponse(Permission permission) {
        return PermissionResponse.builder()
                .id(permission.getId())
                .name(permission.getName())
                .description(permission.getDescription())
                .build();
    }
}
