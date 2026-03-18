package com.legymernok.backend.service.starsystem;

import com.legymernok.backend.dto.starsystem.CreateStarSystemRequest;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.exception.UnauthorizedAccessException;
import com.legymernok.backend.model.auth.Permission;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StarSystemServiceTest {

    @Mock private StarSystemRepository starSystemRepository;
    @Mock private CadetRepository cadetRepository;
    @InjectMocks private StarSystemService starSystemService;

    private Cadet owner;
    private Cadet otherUser;
    private Cadet adminUser;

    @BeforeEach
    void setUp() {
        owner = new Cadet();
        owner.setId(UUID.randomUUID());
        owner.setUsername("owner_user");

        otherUser = new Cadet();
        otherUser.setId(UUID.randomUUID());
        otherUser.setUsername("other_user");

        adminUser = new Cadet();
        adminUser.setId(UUID.randomUUID());
        adminUser.setUsername("admin_user");

        // JAVÍTÁS: Létrehozzuk a Permission objektumot, és beállítjuk a nevét
        Permission editAnyPermission = new Permission();
        editAnyPermission.setName("starsystem:edit_any");

        Permission deleteAnyPermission = new Permission();
        deleteAnyPermission.setName("starsystem:delete_any");

        Role adminRole = new Role();
        adminRole.setName("ROLE_ADMIN"); // Set role name
        adminRole.setPermissions(Set.of(editAnyPermission, deleteAnyPermission));
        adminUser.setRoles(Set.of(adminRole));

        Authentication auth = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    private void mockAuthenticatedUser(String username, Cadet user) {
        when(SecurityContextHolder.getContext().getAuthentication().getName()).thenReturn(username);
        when(cadetRepository.findByUsername(username)).thenReturn(Optional.of(user));
    }

    @Test
    void createStarSystem_Success() {
        mockAuthenticatedUser("owner_user", owner);
        CreateStarSystemRequest request = new CreateStarSystemRequest();
        request.setName("New System");

        when(starSystemRepository.findByName("New System")).thenReturn(Optional.empty());
        when(starSystemRepository.save(any(StarSystem.class))).thenAnswer(i -> i.getArgument(0));

        starSystemService.createStarSystem(request);

        verify(starSystemRepository).save(argThat(system -> system.getOwner().equals(owner)));
    }

    @Test
    void updateStarSystem_byAdmin_shouldSucceed() {
        mockAuthenticatedUser("admin_user", adminUser);

        StarSystem systemToUpdate = new StarSystem();
        systemToUpdate.setOwner(owner); // Másé a rendszer
        systemToUpdate.setName("Original Name");
        systemToUpdate.setId(UUID.randomUUID());

        CreateStarSystemRequest request = new CreateStarSystemRequest();
        request.setName("Updated By Admin");

        when(starSystemRepository.findById(systemToUpdate.getId())).thenReturn(Optional.of(systemToUpdate));
        when(starSystemRepository.save(any(StarSystem.class))).thenReturn(systemToUpdate);

        assertDoesNotThrow(() -> starSystemService.updateStarSystem(systemToUpdate.getId(), request));
    }

    @Test
    void deleteStarSystem_byOwner_shouldSucceed() {
        mockAuthenticatedUser("owner_user", owner);
        StarSystem system = new StarSystem();
        system.setOwner(owner);
        system.setId(UUID.randomUUID());

        // JAVÍTÁS: A mockolásnál a konkrét ID-t használjuk
        when(starSystemRepository.findById(system.getId())).thenReturn(Optional.of(system));
        when(starSystemRepository.existsById(system.getId())).thenReturn(true);

        assertDoesNotThrow(() -> starSystemService.deleteStarSystem(system.getId()));

        verify(starSystemRepository).deleteById(system.getId());
    }

    @Test
    void deleteStarSystem_byAdmin_shouldSucceed() {
        mockAuthenticatedUser("admin_user", adminUser);
        StarSystem system = new StarSystem();
        system.setOwner(owner);
        system.setId(UUID.randomUUID());

        // JAVÍTÁS: A mockolásnál a konkrét ID-t használjuk
        when(starSystemRepository.findById(system.getId())).thenReturn(Optional.of(system));
        when(starSystemRepository.existsById(system.getId())).thenReturn(true);

        assertDoesNotThrow(() -> starSystemService.deleteStarSystem(system.getId()));

        verify(starSystemRepository).deleteById(system.getId());
    }

    @Test
    void deleteStarSystem_whenUserIsNotOwner_shouldThrowUnauthorized() {
        mockAuthenticatedUser("other_user", otherUser);
        StarSystem system = new StarSystem();
        system.setOwner(owner);
        system.setId(UUID.randomUUID());

        // JAVÍTÁS: A findById itt is a konkrét ID-t várja
        when(starSystemRepository.findById(system.getId())).thenReturn(Optional.of(system));
        when(starSystemRepository.existsById(system.getId())).thenReturn(true);

        assertThrows(UnauthorizedAccessException.class, () -> starSystemService.deleteStarSystem(system.getId()));
    }
}