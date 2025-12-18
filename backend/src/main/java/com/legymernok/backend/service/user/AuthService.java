package com.legymernok.backend.service.user;

import com.legymernok.backend.dto.user.LoginRequest;
import com.legymernok.backend.dto.user.LoginResponse;
import com.legymernok.backend.dto.user.RegisterRequest;
import com.legymernok.backend.dto.user.RegisterResponse;
import com.legymernok.backend.exception.UserAlreadyExistsException;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.repository.auth.RoleRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.security.JwtService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.legymernok.backend.exception.BadCredentialsException;
import com.legymernok.backend.exception.UserNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final CadetRepository cadetRepository;
    private final PasswordEncoder passwordEncoder;
    private final GiteaService giteaService;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        Cadet cadet = cadetRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User with username '" + request.getUsername() + "' not found"));

        if (!passwordEncoder.matches(request.getPassword(), cadet.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials: password does not match for user '" + request.getUsername() + "'");
        }

        String token = jwtService.generateToken(cadet);

        return new LoginResponse(token, cadet.getUsername());
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // 1. Validáció (Service szinten is, bár a Controller @Valid is elkaphatná)
        if (cadetRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username already exists");
        }
        if (cadetRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email already exists");
        }

        // 2. Gitea User létrehozása
        Long giteaId = giteaService.createGiteaUser(
                request.getUsername(),
                request.getEmail(),
                request.getPassword()
        );

        // 3. Role beállítása (Alapértelmezett: ROLE_CADET)
        Role cadetRole = roleRepository.findByName("ROLE_CADET")
                .orElseThrow(() -> new RuntimeException("Default role not found"));
        Set<Role> roles = new HashSet<>();
        roles.add(cadetRole);

        // 4. Cadet mentése
        Cadet cadet = Cadet.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .roles(roles)
                .giteaUserId(giteaId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Cadet savedCadet = cadetRepository.save(cadet);

        String token = jwtService.generateToken(savedCadet);

        // 5. Válasz összeállítása (Csak a publikus adatok)
        return RegisterResponse.builder()
                .username(savedCadet.getUsername())
                .email(savedCadet.getEmail())
                .token(token)
                .build();
    }
}