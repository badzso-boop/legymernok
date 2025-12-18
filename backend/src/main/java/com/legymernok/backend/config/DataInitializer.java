package com.legymernok.backend.config;

import com.legymernok.backend.model.auth.Permission;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.repository.auth.PermissionRepository;
import com.legymernok.backend.repository.auth.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        // 1. Jogok létrehozása (ha még nincsenek)
        Permission missionRead = createPermissionIfNotFound("mission:read", "Küldetések megtekintés");
        Permission missionStart = createPermissionIfNotFound("mission:start", "Küldetés indítása");
        Permission missionCreate = createPermissionIfNotFound("mission:create", "Küldetés létrehozása");
        Permission userDelete = createPermissionIfNotFound("user:delete", "Felhasználó törlése");
        Permission userCreate = createPermissionIfNotFound("user:create", "Felhasználó létrehozása");
        Permission starSystemRead = createPermissionIfNotFound("starsystem:read", "Csillagrendszer megtekintés");
        Permission starSystemCreate = createPermissionIfNotFound("starsystem:create", "Csillagrendszer létrehozása");

        // 2. Szerepkörök létrehozása és jogok hozzárendelése

        // CADET: Csak olvasni és indítani tud
        Set<Permission> cadetPermissions = new HashSet<>();
        cadetPermissions.add(missionRead);
        cadetPermissions.add(missionStart);
        createRoleIfNotFound("ROLE_CADET", cadetPermissions);

        // ADMIN: Mindent tud
        Set<Permission> adminPermissions = new HashSet<>();
        adminPermissions.add(missionRead);
        adminPermissions.add(missionStart);
        adminPermissions.add(missionCreate);
        adminPermissions.add(userDelete);
        adminPermissions.add(userCreate);
        adminPermissions.add(starSystemRead);
        adminPermissions.add(starSystemCreate);
        createRoleIfNotFound("ROLE_ADMIN", adminPermissions);

        System.out.println("--- Alap jogok és szerepkörök inicializálva ---");
    }

    private Permission createPermissionIfNotFound(String name, String description) {
        return permissionRepository.findByName(name)
                .orElseGet(() -> permissionRepository.save(
                        Permission.builder().name(name).description(description).build()
                ));
    }

    private Role createRoleIfNotFound(String name, Set<Permission> permissions) {
        return roleRepository.findByName(name)
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name(name).permissions(permissions).build()
                ));
    }
}