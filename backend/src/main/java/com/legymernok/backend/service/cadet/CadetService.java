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
            // 2. Átruházzuk a csillagrendszereket
            List<StarSystem> systemsToReassign = starSystemRepository.findAllByOwnerId(id);
            for (StarSystem system : systemsToReassign) {
                system.setOwner(inheritanceAdmin);
                starSystemRepository.save(system);
            }

            // 3. Átruházzuk a küldetéseket
            List<Mission> missionsToReassign = missionRepository.findAllByOwnerId(id);
            for (Mission mission : missionsToReassign) {
                mission.setOwner(inheritanceAdmin);
                missionRepository.save(mission);
            }
        } else {
            // Ha nincs "örökös", akkor töröljük a tartalmakat is (vagy dobjunk hibát)
            // Döntés kérdése. Most tegyük fel, hogy ez hiba.
            log.warn("Could not delete user {}, no inheritance admin found. Content remains orphaned.", cadet.getUsername());
            // Itt dobhatnánk egy OperationNotAllowedException-t
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
