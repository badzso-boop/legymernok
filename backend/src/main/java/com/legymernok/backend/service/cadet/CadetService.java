package com.legymernok.backend.service.cadet;

import com.legymernok.backend.dto.cadet.CadetResponse;
import com.legymernok.backend.dto.cadet.CreateCadetRequest;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.ConnectTable.CadetMission;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.ConnectTables.CadetMissionRepository;
import com.legymernok.backend.repository.auth.RoleRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class CadetService {

    private final CadetRepository cadetRepository;
    private final CadetMissionRepository cadetMissionRepository;
    private final StarSystemRepository starSystemRepository;
    private final MissionRepository missionRepository;
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
        log.info("Saved Cadet: {}", savedCadet);
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

        Cadet inheritanceAdmin = cadetRepository.findFirstByRoles_Permissions_Name("system:inheritance")
                .orElse(null);

        if (inheritanceAdmin != null) {
            // 2. Transfer star systems
            List<StarSystem> systemsToReassign = starSystemRepository.findAllByOwnerId(id);
            for (StarSystem system : systemsToReassign) {
                system.setOwner(inheritanceAdmin);
                starSystemRepository.save(system);
            }

            // 3. Transfer missions
            List<Mission> missionsToReassign = missionRepository.findAllByOwnerId(id);
            for (Mission mission : missionsToReassign) {
                mission.setOwner(inheritanceAdmin);
                missionRepository.save(mission);
            }
        } else {
            // If there is no "heir", delete the content too (or throw an error)
            // A design decision. For now, treat this as an error.
            log.warn("Could not delete user {}, no inheritance admin found. Content remains orphaned.", cadet.getUsername());
            // An OperationNotAllowedException could be thrown here
        }

        try {
            giteaService.deleteGiteaUser(cadet.getUsername());
        } catch (Exception e) {
            System.err.println("Gitea user deletion failed for " + cadet.getUsername() + ": " + e.getMessage());
        }

        cadetMissionRepository.deleteAllByCadetId(id);
        log.info("Deleted Cadet: {}", cadet);

        cadetRepository.delete(cadet);
    }

    @Transactional
    public CadetResponse updateCadet(UUID id, CreateCadetRequest request) {
        Cadet cadetToUpdate = cadetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "id", id));

        // Update email if provided and different from the current one
        if (request.getEmail() != null && !request.getEmail().equals(cadetToUpdate.getEmail())) {
            // Check if the new email address is already taken
            if (cadetRepository.existsByEmail(request.getEmail())) {
                throw new ResourceConflictException("Cadet", "email", request.getEmail());
            }
            cadetToUpdate.setEmail(request.getEmail());
            // TODO: Update Gitea email if GiteaService supports it
        }

        // Update password only if a new one is provided
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            cadetToUpdate.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            // TODO: Update Gitea password if GiteaService supports it
        }

        // Update role only if it actually changed
        if (request.getRole() != null && !request.getRole().isBlank()) {
            // Get the user's current (first) role name
            String currentRoleName = cadetToUpdate.getRoles().stream()
                    .map(Role::getName)
                    .findFirst()
                    .orElse(null);

            // Only update if the requested role differs from the current one
            if (!request.getRole().equals(currentRoleName)) {
                Role newRole = roleRepository.findByName(request.getRole())
                        .orElseThrow(() -> new ResourceNotFoundException("Role", "name", request.getRole()));

                cadetToUpdate.getRoles().clear();
                cadetToUpdate.getRoles().add(newRole);
            }
        }

        cadetToUpdate.setFullName(request.getFullName());

        Cadet updatedCadet = cadetRepository.save(cadetToUpdate);
        log.info("Updated Cadet: {}", updatedCadet);
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
