package com.legymernok.backend.service.users;

import com.legymernok.backend.dto.user.LoginRequest;
import com.legymernok.backend.dto.user.LoginResponse;
import com.legymernok.backend.dto.user.RegisterRequest;
import com.legymernok.backend.dto.user.RegisterResponse;
import com.legymernok.backend.exception.BadCredentialsException;
import com.legymernok.backend.exception.UserAlreadyExistsException;
import com.legymernok.backend.exception.UserNotFoundException;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.repository.auth.RoleRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.security.JwtService;
import com.legymernok.backend.service.user.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CadetRepository cadetRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private GiteaService giteaService;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private AuthService authService;

    @Test
    void login_Success() {
        LoginRequest request = new LoginRequest();
        request.setUsername("han");
        request.setPassword("solo");
        Cadet cadet = Cadet.builder().username("han").passwordHash("hashed_solo").build();
        when(cadetRepository.findByUsername("han")).thenReturn(Optional.of(cadet));
        when(passwordEncoder.matches("solo", "hashed_solo")).thenReturn(true);
        when(jwtService.generateToken(cadet)).thenReturn("fake-jwt-token");
        LoginResponse response = authService.login(request);
        assertNotNull(response);
        assertEquals("fake-jwt-token", response.getToken());
    }

    @Test
    void login_UserNotFound_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("unknown");
        when(cadetRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> authService.login(request));
    }

    @Test
    void login_WrongPassword_ThrowsException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("han");
        request.setPassword("wrong");
        Cadet cadet = Cadet.builder().username("han").passwordHash("hashed_solo").build();
        when(cadetRepository.findByUsername("han")).thenReturn(Optional.of(cadet));
        when(passwordEncoder.matches("wrong", "hashed_solo")).thenReturn(false);
        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void register_Success() {
        // Arrange
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setEmail("new@example.com");
        request.setPassword("password");
        request.setFullName("New User");

        Role role = Role.builder().name("ROLE_CADET").build();

        when(cadetRepository.existsByUsername("newuser")).thenReturn(false);
        when(cadetRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(giteaService.createGiteaUser("newuser", "new@example.com", "password")).thenReturn(
                100L);
        when(roleRepository.findByName("ROLE_CADET")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("password")).thenReturn("hashed_password");

        when(cadetRepository.save(any(Cadet.class))).thenAnswer(invocation -> {
            Cadet c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        when(jwtService.generateToken(any(Cadet.class))).thenReturn("jwt_token");

        // Act
        RegisterResponse response = authService.register(request);

        // Assert
        assertNotNull(response);
        assertEquals("newuser", response.getUsername());
        assertEquals("new@example.com", response.getEmail());
        assertEquals("jwt_token", response.getToken());

        verify(giteaService).createGiteaUser("newuser", "new@example.com", "password");
        verify(cadetRepository).save(any(Cadet.class));
    }

    @Test
    void register_UsernameExists_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("existing");

        when(cadetRepository.existsByUsername("existing")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));

        verify(giteaService, never()).createGiteaUser(any(), any(), any());
        verify(cadetRepository, never()).save(any());
    }

    @Test
    void register_EmailExists_ThrowsException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setEmail("existing@example.com");

        when(cadetRepository.existsByUsername("newuser")).thenReturn(false);
        when(cadetRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> authService.register(request));

        verify(giteaService, never()).createGiteaUser(any(), any(), any());
        verify(cadetRepository, never()).save(any());
    }
}