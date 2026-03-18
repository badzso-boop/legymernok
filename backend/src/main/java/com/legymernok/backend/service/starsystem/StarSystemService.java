package com.legymernok.backend.service.starsystem;

import com.legymernok.backend.dto.mission.MissionResponse;
import com.legymernok.backend.dto.starsystem.CreateStarSystemRequest;
import com.legymernok.backend.dto.starsystem.StarSystemResponse;
import com.legymernok.backend.dto.starsystem.StarSystemWithMissionResponse;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.exception.UnauthorizedAccessException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import com.legymernok.backend.service.mission.MissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StarSystemService {

    private final StarSystemRepository starSystemRepository;
    private final MissionService missionService;
    private final CadetRepository cadetRepository;

    @Transactional
    public StarSystemResponse createStarSystem(CreateStarSystemRequest request) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        if (starSystemRepository.findByName(request.getName()).isPresent()) {
            throw new ResourceConflictException("StarSystem", "name", request.getName());
        }

        StarSystem starSystem = StarSystem.builder()
                .name(request.getName())
                .description(request.getDescription())
                .iconUrl(request.getIconUrl())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .owner(currentUser)
                .build();

        StarSystem savedStarSystem = starSystemRepository.save(starSystem);
        log.info("StarSystem created: {}", savedStarSystem);
        return mapToResponse(savedStarSystem);
    }

    @Transactional(readOnly = true)
    public List<StarSystemResponse> getAllStarSystems() {
        return starSystemRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StarSystemResponse getStarSystemById(UUID id) {
        StarSystem starSystem = starSystemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StarSystem", "id", id));
        return mapToResponse(starSystem);
    }

    @Transactional
    public void deleteStarSystem(UUID id) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        if (!starSystemRepository.existsById(id)) {
            throw new ResourceNotFoundException("StarSystem", "id", id);
        }

        StarSystem systemToDelete = starSystemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StarSystem", "id", id));

        if (!systemToDelete.getOwner().getId().equals(currentUser.getId()) && !hasAuthority(currentUser, "starsystem:delete_any")) {
            throw new UnauthorizedAccessException("You are not the owner of this star system.");
        }

        log.info("Deleting StarSystem with ID: {}", id);
        starSystemRepository.deleteById(id);
    }

    @Transactional
    public StarSystemResponse updateStarSystem(UUID id, CreateStarSystemRequest request) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        StarSystem starSystemToUpdate = starSystemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StarSystem", "id", id));

        if (!starSystemToUpdate.getOwner().getId().equals(currentUser.getId()) && !hasAuthority(currentUser, "starsystem:edit_any")) {
            throw new UnauthorizedAccessException("You are not the owner of this star system.");
        }

        if (!starSystemToUpdate.getName().equals(request.getName()) &&
                starSystemRepository.findByName(request.getName()).isPresent()) {
            throw new ResourceConflictException("StarSystem", "name", request.getName());
        }

        starSystemToUpdate.setName(request.getName());
        starSystemToUpdate.setDescription(request.getDescription());
        starSystemToUpdate.setIconUrl(request.getIconUrl());
        starSystemToUpdate.setUpdatedAt(Instant.now());

        StarSystem updatedStarSystem = starSystemRepository.save(starSystemToUpdate);
        return mapToResponse(updatedStarSystem);
    }

    public List<StarSystemResponse> getSystemsByCurrentUser() {
        Cadet currentUser = getCurrentAuthenticatedUser();
        return starSystemRepository.findAllByOwnerId(currentUser.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StarSystemWithMissionResponse getStarSystemWithMissions(UUID id) {
        // 1. Csillagrendszer lekérdezése
        StarSystem starSystem = starSystemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StarSystem", "id", id));

        // 2. Küldetések lekérdezése a MissionService segítségével
        List<MissionResponse> missions = missionService.getMissionsByStarSystem(id);

        // 3. A komplex válasz DTO összeállítása
        return StarSystemWithMissionResponse.builder()
                .id(starSystem.getId())
                .name(starSystem.getName())
                .description(starSystem.getDescription())
                .iconUrl(starSystem.getIconUrl())
                .createdAt(starSystem.getCreatedAt())
                .updatedAt(starSystem.getUpdatedAt())
                .missions(missions)
                .build();
    }

    // Segédmetódus a StarSystem entitás Response DTO-vá alakításához
    private StarSystemResponse mapToResponse(StarSystem starSystem) {
        return StarSystemResponse.builder()
                .id(starSystem.getId())
                .name(starSystem.getName())
                .description(starSystem.getDescription())
                .iconUrl(starSystem.getIconUrl())
                .createdAt(starSystem.getCreatedAt())
                .updatedAt(starSystem.getUpdatedAt())
                .build();
    }

    private Cadet getCurrentAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return cadetRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }

    private boolean hasAuthority(Cadet user, String authorityName) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals(authorityName));
    }
}