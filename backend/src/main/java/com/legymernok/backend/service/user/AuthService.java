package com.legymernok.backend.service.user;

import com.legymernok.backend.dto.cadet.CadetResponse;
import com.legymernok.backend.dto.user.LoginRequest;
import com.legymernok.backend.dto.user.LoginResponse;
import com.legymernok.backend.dto.user.RegisterRequest;
import com.legymernok.backend.dto.user.RegisterResponse;
import com.legymernok.backend.exception.*;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.repository.auth.RoleRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.security.JwtService;
import com.legymernok.backend.service.cadet.CadetService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final CadetRepository cadetRepository;
    private final PasswordEncoder passwordEncoder;
    private final GiteaService giteaService;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        Cadet cadet = cadetRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "username", request.getUsername()));

        if (!passwordEncoder.matches(request.getPassword(), cadet.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials: password does not match for user '" + request.getUsername() + "'");
        }

        String token = jwtService.generateToken(cadet);

        log.info("User logged in successfully: {}", request.getUsername());

        return new LoginResponse(token, cadet.getUsername());
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // 1. Validáció (Service szinten is, bár a Controller @Valid is elkaphatná)
        if (cadetRepository.existsByUsername(request.getUsername())) {
            throw new ResourceConflictException("Cadet", "username", request.getUsername());
        }
        if (cadetRepository.existsByEmail(request.getEmail())) {
            throw new ResourceConflictException("Cadet", "email", request.getEmail());
        }

        // 2. Gitea User létrehozása
        Long giteaId = giteaService.createGiteaUser(
                request.getUsername(),
                request.getEmail(),
                request.getPassword()
        );

        // 3. Role beállítása (Alapértelmezett: ROLE_CADET)
        Role cadetRole = roleRepository.findByName("ROLE_CADET")
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", "ROLE_CADET"));
        Set<Role> roles = new HashSet<>();
        roles.add(cadetRole);

        // 4. Cadet mentése
        Cadet cadet = Cadet.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .roles(roles)
                .giteaUserId(giteaId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Cadet savedCadet = cadetRepository.save(cadet);

        String token = jwtService.generateToken(savedCadet);

        log.info("New user registered: {} ({})", savedCadet.getUsername(), savedCadet.getEmail());

        // 5. Válasz összeállítása (Csak a publikus adatok)
        return RegisterResponse.builder()
                .username(savedCadet.getUsername())
                .email(savedCadet.getEmail())
                .token(token)
                .build();
    }

    public CadetResponse getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Cadet cadet = cadetRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "username", username));

        return mapToResponse(cadet);
    }

    private CadetResponse mapToResponse(Cadet cadet) {
        return CadetResponse.builder()
                .id(cadet.getId())
                .username(cadet.getUsername())
                .email(cadet.getEmail())
                .fullName(cadet.getFullName())
                // A role objektumok neveit szedjük ki (pl. "ROLE_ADMIN")
                .roles(cadet.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toSet()))
                .giteaUserId(cadet.getGiteaUserId())
                .createdAt(cadet.getCreatedAt())
                .updatedAt(cadet.getUpdatedAt())
                .build();
    }
}