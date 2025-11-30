package com.legymernok.backend.service;

import com.legymernok.backend.dto.CreateStarSystemRequest;
import com.legymernok.backend.dto.StarSystemResponse;
import com.legymernok.backend.model.StarSystem;
import com.legymernok.backend.repository.StarSystemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StarSystemService {

    private final StarSystemRepository starSystemRepository;

    @Transactional
    public StarSystemResponse createStarSystem(CreateStarSystemRequest request) {
        // Valamilyen validáció, pl. hogy a név egyedi legyen
        if (starSystemRepository.findByName(request.getName()).isPresent()) {
            throw new RuntimeException("StarSystem with name '" + request.getName() + "' already exists.");
        }

        StarSystem starSystem = StarSystem.builder()
                .name(request.getName())
                .description(request.getDescription())
                .iconUrl(request.getIconUrl())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        StarSystem savedStarSystem = starSystemRepository.save(starSystem);
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
                .orElseThrow(() -> new RuntimeException("StarSystem not found with ID: " + id));
        return mapToResponse(starSystem);
    }

    // Segédmetódus a StarSystem entitás Response DTO-vá alakításához
    private StarSystemResponse mapToResponse(StarSystem starSystem) {
        return StarSystemResponse.builder()
                .id(starSystem.getId())
                .name(starSystem.getName())
                .description(starSystem.getDescription())
                .iconUrl(starSystem.getIconUrl())
                .createdAt(starSystem.getCreatedAt())
                .build();
    }
}