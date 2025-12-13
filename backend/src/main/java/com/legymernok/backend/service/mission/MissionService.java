package com.legymernok.backend.service.mission;

import com.legymernok.backend.dto.mission.CreateMissionRequest;
import com.legymernok.backend.dto.mission.MissionResponse;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import org.springframework.security.core.Authentication;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MissionService {

    private final MissionRepository missionRepository;
    private final StarSystemRepository starSystemRepository;
    private final GiteaService giteaService;

    @Transactional
    public MissionResponse createMission(CreateMissionRequest request) {
        StarSystem starSystem = starSystemRepository.findById(request.getStarSystemId())
                .orElseThrow(() -> new RuntimeException("StarSystem not found with ID: " + request.getStarSystemId()));

        // 1. Repo nevének generálása (egyedinek kell lennie Giteán belül)
        // Pl. "mission-template-[starSystemName]-[missionName]" (kicsit megtisztítva)
        String safeMissionName = request.getName().toLowerCase().replaceAll("[^a-z0-9]", "-");
        String repoName = "mission-template-" + safeMissionName + "-" + System.currentTimeMillis();

        // 2. Gitea Repo létrehozása
        String repoUrl = giteaService.createRepository(repoName);

        // 3. Template fájlok feltöltése (Map iterálás)
        if (request.getTemplateFiles() != null && !request.getTemplateFiles().isEmpty()) {
            for (Map.Entry<String, String> entry : request.getTemplateFiles().entrySet()) {
                String fileName = entry.getKey();
                String content = entry.getValue();

                giteaService.createFile(repoName, fileName, content);
            }
        }

        // TODO: További validáció, pl. egy StarSystemen belül unique legyen a név, vagy a sorrend

        Mission mission = Mission.builder()
                .starSystem(starSystem)
                .name(request.getName())
                .descriptionMarkdown(request.getDescriptionMarkdown())
                .templateRepositoryUrl(repoUrl)
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

    @Transactional(readOnly = true)
    public List<MissionResponse> getMissionsByStarSystem(UUID starSystemId) {
        return missionRepository.findAllByStarSystemIdOrderByOrderInSystemAsc(starSystemId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ADMIN"));
    }

    private MissionResponse mapToResponse(Mission mission) {
        String repoUrl = null;

        if (isAdmin()) {
            repoUrl = mission.getTemplateRepositoryUrl();
        }

        return MissionResponse.builder()
                .id(mission.getId())
                .starSystemId(mission.getStarSystem().getId())
                .name(mission.getName())
                .descriptionMarkdown(mission.getDescriptionMarkdown())
                .templateRepositoryUrl(repoUrl)
                .missionType(mission.getMissionType())
                .difficulty(mission.getDifficulty())
                .orderInSystem(mission.getOrderInSystem())
                .createdAt(mission.getCreatedAt())
                .build();
    }
}