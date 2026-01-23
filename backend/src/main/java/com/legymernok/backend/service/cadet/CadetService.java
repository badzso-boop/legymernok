package com.legymernok.backend.service.cadet;

import com.legymernok.backend.dto.cadet.CadetResponse;
import com.legymernok.backend.dto.cadet.CreateCadetRequest;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.exception.UserNotFoundException;
import com.legymernok.backend.model.ConnectTable.CadetMission;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.repository.ConnectTables.CadetMissionRepository;
import com.legymernok.backend.repository.auth.RoleRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.exception.UserAlreadyExistsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.legymernok.backend.integration.GiteaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CadetService {

    private final CadetRepository cadetRepository;
    private final CadetMissionRepository cadetMissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final GiteaService giteaService;
    private final RoleRepository roleRepository;

    @Transactional
    public CadetResponse createCadet(CreateCadetRequest request) {
        if (cadetRepository.existsByUsername(request.getUsername())) {
            throw new ResourceConflictException("Cadet", "username", request.getUsername());
        }
        if (cadetRepository.existsByEmail(request.getEmail())) {
            throw new ResourceConflictException("Cadet", "email", request.getEmail());
        }

        Long giteaId = giteaService.createGiteaUser(
            request.getUsername(),
            request.getEmail(),
            request.getPassword()
        );

        Role cadetRole = roleRepository.findByName(request.getRole())
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", request.getRole()));

        Set<Role> roles = new HashSet<>();
        roles.add(cadetRole);

        Cadet cadet = Cadet.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .roles(roles)
                .giteaUserId(giteaId)
                .build();

        Cadet savedCadet = cadetRepository.save(cadet);
        return mapToResponse(savedCadet);
    }

    @Transactional(readOnly = true)
    public List<CadetResponse> getAllCadets() {
        return cadetRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CadetResponse getCadetById(UUID id) {
        Cadet cadet = cadetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "id", id));
        return mapToResponse(cadet);
    }

    @Transactional
    public void deleteCadet(UUID id) {
        Cadet cadet = cadetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "id", id));

        try {
            giteaService.deleteGiteaUser(cadet.getUsername());
        } catch (Exception e) {
            System.err.println("Gitea user deletion failed for " + cadet.getUsername() + ": " + e.getMessage());
        }

        cadetMissionRepository.deleteAllByCadetId(id);

        cadetRepository.delete(cadet);
    }

    @Transactional
    public CadetResponse updateCadet(UUID id, CreateCadetRequest request) {
        Cadet cadetToUpdate = cadetRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        // Email frissítése, ha meg van adva és nem egyezik a régivel
        if (request.getEmail() != null && !request.getEmail().equals(cadetToUpdate.getEmail())) {
            // Ellenőrizzük, hogy az új email cím foglalt-e már
            if (cadetRepository.existsByEmail(request.getEmail())) {
                throw new ResourceConflictException("Cadet", "email", request.getEmail());
            }
            cadetToUpdate.setEmail(request.getEmail());
            // TODO: Gitea email frissítése, ha a GiteaService támogatja
        }

        // Jelszó frissítése, csak ha megadtak újat
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            cadetToUpdate.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            // TODO: Gitea jelszó frissítése, ha a GiteaService támogatja
        }

        // Szerepkör frissítése, csak ha tényleg változott
        if (request.getRole() != null && !request.getRole().isBlank()) {
            // Lekérdezzük a felhasználó jelenlegi (első) szerepkörének nevét
            String currentRoleName = cadetToUpdate.getRoles().stream()
                    .map(Role::getName)
                    .findFirst()
                    .orElse(null);

            // Csak akkor módosítunk, ha a kérésben lévő szerepkör eltér a jelenlegitől
            if (!request.getRole().equals(currentRoleName)) {
                Role newRole = roleRepository.findByName(request.getRole())
                        .orElseThrow(() -> new ResourceNotFoundException("Role", "name", request.getRole()));

                cadetToUpdate.getRoles().clear();
                cadetToUpdate.getRoles().add(newRole);
            }
        }

        cadetToUpdate.setFullName(request.getFullName());

        Cadet updatedCadet = cadetRepository.save(cadetToUpdate);
        return mapToResponse(updatedCadet);
    }

    private CadetResponse mapToResponse(Cadet cadet) {
        return CadetResponse.builder()
                .id(cadet.getId())
                .username(cadet.getUsername())
                .email(cadet.getEmail())
                .fullName(cadet.getFullName())
                .roles(cadet.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .giteaUserId(cadet.getGiteaUserId())
                .createdAt(cadet.getCreatedAt())
                .updatedAt(cadet.getUpdatedAt())
                .build();
    }
}
