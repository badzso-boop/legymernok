package com.legymernok.backend.service.users;

import com.legymernok.backend.dto.user.LoginRequest;
import com.legymernok.backend.dto.user.LoginResponse;
import com.legymernok.backend.exception.BadCredentialsException;
import com.legymernok.backend.exception.UserNotFoundException;
import com.legymernok.backend.model.cadet.Cadet;
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
}