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
        // Mission jogok
        Permission missionRead = createPermissionIfNotFound("mission:read", "Küldetések megtekintése");
        Permission missionStart = createPermissionIfNotFound("mission:start", "Küldetés indítása");
        Permission missionCreate = createPermissionIfNotFound("mission:create", "Küldetés létrehozása");
        Permission missionEdit = createPermissionIfNotFound("mission:edit", "Küldetés szerkesztése");
        Permission missionDelete = createPermissionIfNotFound("mission:delete", "Küldetés törlése");

        // StarSystem jogok
        Permission starSystemRead = createPermissionIfNotFound("starsystem:read", "Csillagrendszer megtekintése");
        Permission starSystemCreate = createPermissionIfNotFound("starsystem:create", "Csillagrendszer létrehozása");
        Permission starSystemEdit = createPermissionIfNotFound("starsystem:edit", "Csillagrendszer szerkesztése");
        Permission starSystemDelete = createPermissionIfNotFound("starsystem:delete", "Csillagrendszer törlése");

        // User jogok
        Permission userRead = createPermissionIfNotFound("user:read", "Felhasználók listázása");
        Permission userCreate = createPermissionIfNotFound("user:create", "Felhasználó létrehozása");
        Permission userEdit = createPermissionIfNotFound("user:edit", "Felhasználó szerkesztése");
        Permission userDelete = createPermissionIfNotFound("user:delete", "Felhasználó törlése");

        // Role jogok (RBAC menedzsment)
        Permission roleRead = createPermissionIfNotFound("role:read", "Szerepkörök megtekintése");
        Permission roleWrite = createPermissionIfNotFound("role:write", "Szerepkörök kezelése (létrehozás, szerkesztés, törlés)");

        // --- 2. SZEREPKÖRÖK (ROLES) LÉTREHOZÁSA ---

        // ROLE_CADET: Alap jogok (Olvasás, Indítás)
        Set<Permission> cadetPermissions = new HashSet<>();
        cadetPermissions.add(missionRead);
        cadetPermissions.add(missionStart);
        cadetPermissions.add(starSystemRead);
        createRoleIfNotFound("ROLE_CADET", cadetPermissions);

        // ROLE_ADMIN: Minden jog (Full Access)
        Set<Permission> adminPermissions = new HashSet<>();
        // Mission
        adminPermissions.add(missionRead);
        adminPermissions.add(missionStart);
        adminPermissions.add(missionCreate);
        adminPermissions.add(missionEdit);
        adminPermissions.add(missionDelete);
        // StarSystem
        adminPermissions.add(starSystemRead);
        adminPermissions.add(starSystemCreate);
        adminPermissions.add(starSystemEdit);
        adminPermissions.add(starSystemDelete);
        // User
        adminPermissions.add(userRead);
        adminPermissions.add(userCreate);
        adminPermissions.add(userEdit);
        adminPermissions.add(userDelete);
        // Role
        adminPermissions.add(roleRead);
        adminPermissions.add(roleWrite);

        createRoleIfNotFound("ROLE_ADMIN", adminPermissions);

        System.out.println("--- Jogosultsági rendszer inicializálva (Permissions & Roles) ---");
    }

    private Permission createPermissionIfNotFound(String name, String description) {
        return permissionRepository.findByName(name)
                .orElseGet(() -> permissionRepository.save(
                        Permission.builder().name(name).description(description).build()
                ));
    }

    private Role createRoleIfNotFound(String name, Set<Permission> permissions) {
        return roleRepository.findByName(name)
                .map(existingRole -> {
                     existingRole.setPermissions(permissions);
                     return roleRepository.save(existingRole);
                })
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name(name).permissions(permissions).build()
                ));
    }
}