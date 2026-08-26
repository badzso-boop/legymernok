package com.legymernok.backend.config;

import com.legymernok.backend.model.auth.Permission;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.repository.auth.PermissionRepository;
import com.legymernok.backend.repository.auth.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A szerepkör-jogosultságok seedelését ellenőrzi.
 *
 * <p>A ROLE_CADET jogosultság-halmaza biztonsági határ, nem kényelmi beállítás: ha egy
 * létrehozó jog visszakerülne (akár egy összefésülési hiba miatt), a kadétok által írt
 * szöveg újra bekerülne a RAG-indexbe, és onnan más felhasználók chat-kontextusába — ld.
 * {@code plans/pr0_retrieval_security_2026.md} 1-2. szakasz. Ezért van rá regressziós teszt.
 */
@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionRepository permissionRepository;

    @InjectMocks
    private DataInitializer dataInitializer;

    @BeforeEach
    void setUp() {
        // Minden permission "új": a findByName üresen tér vissza, a save visszaadja, amit kap.
        when(permissionRepository.findByName(any())).thenReturn(Optional.empty());
        when(permissionRepository.save(any(Permission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(roleRepository.findByName(any())).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Set<String> seededPermissionNames(String roleName) throws Exception {
        dataInitializer.run();

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(roleRepository, atLeastOnce()).save(captor.capture());

        List<Role> saved = captor.getAllValues();
        Role role = saved.stream()
                .filter(r -> roleName.equals(r.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(roleName + " nem lett seedelve"));

        return role.getPermissions().stream()
                .map(Permission::getName)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("ROLE_CADET nem kap létrehozó jogot")
    void cadetRoleHasNoCreatePermissions() throws Exception {
        Set<String> cadetPermissions = seededPermissionNames("ROLE_CADET");

        assertThat(cadetPermissions)
                .as("a kadét kizárólag missziókat teljesít, nem hoz létre tartalmat")
                .doesNotContain("mission:create", "starsystem:create", "mission:create_any_system");
    }

    @Test
    @DisplayName("ROLE_CADET megtartja a misszió-teljesítéshez szükséges jogokat")
    void cadetRoleKeepsMissionCompletionPermissions() throws Exception {
        Set<String> cadetPermissions = seededPermissionNames("ROLE_CADET");

        // Ezek nélkül a kadét nem tudna végigmenni egy misszión: lista, indítás, a kvíz és a
        // fill-in-blank beküldése (mission:start), az eredmények, és a böngészés.
        assertThat(cadetPermissions).contains(
                "mission:read",
                "mission:start",
                "quiz:view_results",
                "starsystem:read",
                "sector:read",
                "group:read");
    }

    @Test
    @DisplayName("ROLE_ADMIN továbbra is megkapja a létrehozó jogokat")
    void adminRoleStillHasCreatePermissions() throws Exception {
        Set<String> adminPermissions = seededPermissionNames("ROLE_ADMIN");

        assertThat(adminPermissions)
                .contains("mission:create", "starsystem:create");
    }
}
