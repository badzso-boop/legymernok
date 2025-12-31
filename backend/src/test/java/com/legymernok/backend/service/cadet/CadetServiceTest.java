package com.legymernok.backend.service.cadet;

import com.legymernok.backend.dto.cadet.CadetResponse;
import com.legymernok.backend.dto.cadet.CreateCadetRequest;
import com.legymernok.backend.exception.UserAlreadyExistsException;
import com.legymernok.backend.exception.UserNotFoundException;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.cadet.CadetRole;
import com.legymernok.backend.repository.auth.RoleRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.ConnectTables.CadetMissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CadetServiceTest {

    @Mock
    private CadetRepository cadetRepository;

    @Mock
    private CadetMissionRepository cadetMissionRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private GiteaService giteaService;

    @InjectMocks
    private CadetService cadetService;

    private UUID cadetId;
    private Cadet testCadet;
    private Role defaultCadetRole;

    @BeforeEach
    void setUp() {
        cadetId = UUID.randomUUID();
        defaultCadetRole = Role.builder().id(UUID.randomUUID()).name("ROLE_CADET").build();
        Set<Role> roles = spy(new HashSet<>());
        roles.add(defaultCadetRole);

        testCadet = spy(Cadet.builder() // 'spy' használata a setter hívások ellenőrzéséhez
                .id(cadetId)
                .username("testuser")
                .email("test@example.com")
                .passwordHash("hashed_password")
                .fullName("Test User")
                .roles(roles)
                .giteaUserId(123L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    @Test
    void createCadet_Success() {
        // Arrange
        CreateCadetRequest request = new CreateCadetRequest();
        request.setUsername("luke");
        request.setEmail("luke@rebel.com");
        request.setPassword("force");
        request.setRole("ROLE_CADET");

        Role cadetRole = Role.builder().name("ROLE_CADET").build();
        when(roleRepository.findByName("ROLE_CADET")).thenReturn(Optional.of(cadetRole));

        when(cadetRepository.existsByUsername("luke")).thenReturn(false);
        when(cadetRepository.existsByEmail("luke@rebel.com")).thenReturn(false);

        when(passwordEncoder.encode("force")).thenReturn("hashed_force");

        when(giteaService.createGiteaUser("luke", "luke@rebel.com", "force")).thenReturn(42L);

        when(cadetRepository.save(any(Cadet.class))).thenAnswer(invocation -> {
            Cadet c = invocation.getArgument(0);
            c.setId(java.util.UUID.randomUUID());
            return c;
        });

        // Act
        CadetResponse response = cadetService.createCadet(request);

        // Assert
        assertNotNull(response);
        assertEquals("luke", response.getUsername());
        assertTrue(response.getRoles().contains("ROLE_CADET"));
        assertEquals(42L, response.getGiteaUserId());

        verify(giteaService, times(1)).createGiteaUser("luke", "luke@rebel.com", "force");
    }

    @Test
    void createCadet_UsernameExists_ThrowsException() {
        CreateCadetRequest request = new CreateCadetRequest();
        request.setUsername("vader");

        when(cadetRepository.existsByUsername("vader")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> {
            cadetService.createCadet(request);
        });

        verify(giteaService, never()).createGiteaUser(any(), any(), any());
        verify(cadetRepository, never()).save(any());
    }

    @Test
    void deleteCadet_Success() {
        // Arrange
        UUID cadetId = UUID.randomUUID();
        Cadet cadet = new Cadet();
        cadet.setId(cadetId);
        cadet.setUsername("todelete");

        when(cadetRepository.findById(cadetId)).thenReturn(Optional.of(cadet));

        // Act
        cadetService.deleteCadet(cadetId);

        // Assert
        // 1. Gitea User törlése meghívódott
        verify(giteaService, times(1)).deleteGiteaUser("todelete");

        // 2. CadetMission-ök törlése meghívódott
        verify(cadetMissionRepository, times(1)).deleteAllByCadetId(cadetId);

        // 3. Cadet törlése meghívódott
        verify(cadetRepository, times(1)).delete(cadet);
    }

    @Test
    void deleteCadet_UserNotFound() {
        UUID cadetId = UUID.randomUUID();
        when(cadetRepository.findById(cadetId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> cadetService.deleteCadet(cadetId));

        verify(giteaService, never()).deleteGiteaUser(anyString());
        verify(cadetRepository, never()).delete(any());
    }

    @Test
    void updateCadet_Success_AllFieldsUpdated() {
        // Arrange
        CreateCadetRequest request = new CreateCadetRequest();
        request.setEmail("new@example.com");
        request.setPassword("new_password");
        request.setRole("ROLE_ADMIN");
        request.setFullName("Updated Name");

        Role adminRole = Role.builder().id(UUID.randomUUID()).name("ROLE_ADMIN").build();

        when(cadetRepository.findById(cadetId)).thenReturn(Optional.of(testCadet));
        when(cadetRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode("new_password")).thenReturn("hashed_new_password");
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(adminRole));
        when(cadetRepository.save(any(Cadet.class))).thenReturn(testCadet);

        // Act
        cadetService.updateCadet(cadetId, request);

        // Assert
        verify(testCadet, times(1)).setEmail("new@example.com");
        verify(testCadet, times(1)).setFullName("Updated Name");
        verify(testCadet, times(1)).setPasswordHash("hashed_new_password");
        verify(testCadet.getRoles(), times(1)).clear();
        verify(testCadet.getRoles(), times(1)).add(adminRole);
        verify(cadetRepository, times(1)).save(testCadet);
    }

    @Test
    void updateCadet_Success_PasswordNotChangedIfBlank() {
        // Arrange
        CreateCadetRequest request = new CreateCadetRequest();
        request.setPassword(""); // Üres jelszó

        when(cadetRepository.findById(cadetId)).thenReturn(Optional.of(testCadet));
        when(cadetRepository.save(any(Cadet.class))).thenReturn(testCadet);

        // Act
        cadetService.updateCadet(cadetId, request);

        // Assert
        verify(passwordEncoder, never()).encode(anyString()); // A jelszó kódoló nem hívódhat meg
        verify(testCadet, never()).setPasswordHash(anyString()); // A setter nem hívódhat meg
        verify(cadetRepository, times(1)).save(testCadet);
    }

    @Test
    void updateCadet_CadetNotFound_ThrowsException() {
        // Arrange
        CreateCadetRequest request = new CreateCadetRequest(); // Üres request
        when(cadetRepository.findById(cadetId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(UserNotFoundException.class, () -> cadetService.updateCadet(cadetId, request));
        verify(cadetRepository, never()).save(any());
    }

    @Test
    void updateCadet_EmailAlreadyExists_ThrowsException() {
        // Arrange
        CreateCadetRequest request = new CreateCadetRequest();
        request.setEmail("existing@example.com");

        when(cadetRepository.findById(cadetId)).thenReturn(Optional.of(testCadet));
        when(cadetRepository.existsByEmail("existing@example.com")).thenReturn(true);

        // Act & Assert
        assertThrows(UserAlreadyExistsException.class, () -> cadetService.updateCadet(cadetId, request));
        verify(cadetRepository, never()).save(any());
    }
}