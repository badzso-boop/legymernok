package com.legymernok.backend.service.mission;

import com.legymernok.backend.dto.mission.CreateMissionRequest;
import com.legymernok.backend.dto.mission.MissionResponse;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository; // <--- Importáld
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MissionService {

    private final MissionRepository missionRepository;
    private final StarSystemRepository starSystemRepository;

    @Transactional
    public MissionResponse createMission(CreateMissionRequest request) {
        StarSystem starSystem = starSystemRepository.findById(request.getStarSystemId())
                .orElseThrow(() -> new RuntimeException("StarSystem not found with ID: " + request.getStarSystemId()));

        // TODO: További validáció, pl. egy StarSystemen belül unique legyen a név, vagy a sorrend

        Mission mission = Mission.builder()
                .starSystem(starSystem)
                .name(request.getName())
                .descriptionMarkdown(request.getDescriptionMarkdown())
                .templateRepositoryUrl(request.getTemplateRepositoryUrl())
                .missionType(request.getMissionType())
                .difficulty(request.getDifficulty())
                .orderInSystem(request.getOrderInSystem())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Mission savedMission = missionRepository.save(mission);
        return mapToResponse(savedMission);
    }

    @Transactional(readOnly = true)
    public List<MissionResponse> getAllMissions() {
        return missionRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MissionResponse getMissionById(UUID id) {
        Mission mission = missionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mission not found with ID: " + id));
        return mapToResponse(mission);
    }

    // Segédmetódus a Mission entitás Response DTO-vá alakításához
    private MissionResponse mapToResponse(Mission mission) {
        return MissionResponse.builder()
                .id(mission.getId())
                .starSystemId(mission.getStarSystem().getId()) // A StarSystem ID-ját adjuk vissza
                .name(mission.getName())
                .descriptionMarkdown(mission.getDescriptionMarkdown())
                .templateRepositoryUrl(mission.getTemplateRepositoryUrl())
                .missionType(mission.getMissionType())
                .difficulty(mission.getDifficulty())
                .orderInSystem(mission.getOrderInSystem())
                .createdAt(mission.getCreatedAt())
                .build();
    }
}