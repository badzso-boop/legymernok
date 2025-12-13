package com.legymernok.backend.service;

import com.legymernok.backend.dto.cadet.CadetResponse;
import com.legymernok.backend.dto.cadet.CreateCadetRequest;
import com.legymernok.backend.exception.UserAlreadyExistsException;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.cadet.CadetRole;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.service.cadet.CadetService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CadetServiceTest {

    @Mock
    private CadetRepository cadetRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private GiteaService giteaService;

    @InjectMocks
    private CadetService cadetService;

    @Test
    void createCadet_Success() {
        // Arrange
        CreateCadetRequest request = new CreateCadetRequest();
        request.setUsername("luke");
        request.setEmail("luke@rebel.com");
        request.setPassword("force");

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
        assertEquals(CadetRole.CADET, response.getRole());
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
}