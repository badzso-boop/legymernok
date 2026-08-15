package com.legymernok.backend.service.starsystem;

import com.legymernok.backend.dto.group.MissionGroupResponse;
import com.legymernok.backend.dto.group.ReorderResponse;
import com.legymernok.backend.dto.mission.MissionResponse;
import com.legymernok.backend.dto.search.StarSystemSearchResult;
import com.legymernok.backend.dto.starsystem.CreateStarSystemRequest;
import com.legymernok.backend.dto.starsystem.ReorderItemsRequest;
import com.legymernok.backend.dto.starsystem.StarSystemItemResponse;
import com.legymernok.backend.dto.starsystem.StarSystemResponse;
import com.legymernok.backend.dto.starsystem.StarSystemWithItemsResponse;
import com.legymernok.backend.dto.starsystem.StarSystemWithProgressResponse;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.exception.UnauthorizedAccessException;
import com.legymernok.backend.model.ConnectTable.CadetMission;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionGroup;
import com.legymernok.backend.model.mission.MissionGroupProgress;
import com.legymernok.backend.model.mission.MissionStatus;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.model.sector.Sector;
import com.legymernok.backend.repository.ConnectTables.CadetMissionRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.mission.MissionGroupProgressRepository;
import com.legymernok.backend.repository.mission.MissionGroupRepository;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.repository.sector.SectorRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import com.legymernok.backend.service.ai.AiEmbeddingService;
import com.legymernok.backend.service.mission.MissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class StarSystemService {

    private final StarSystemRepository starSystemRepository;
    private final MissionService missionService;
    private final MissionRepository missionRepository;
    private final MissionGroupRepository missionGroupRepository;
    private final MissionGroupProgressRepository missionGroupProgressRepository;
    private final CadetMissionRepository cadetMissionRepository;
    private final CadetRepository cadetRepository;
    private final SectorRepository sectorRepository;
    private final AiEmbeddingService embeddingService;
    private final JdbcTemplate jdbcTemplate;

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
                .sector(resolveSector(request.getSectorId()))
                .build();

        StarSystem savedStarSystem = starSystemRepository.save(starSystem);
        log.info("StarSystem created: {}", savedStarSystem);
        generateAndSaveEmbedding(savedStarSystem.getId());
        return mapToResponse(savedStarSystem);
    }

    @Transactional(readOnly = true)
    public List<StarSystemResponse> getAllStarSystems() {
        return starSystemRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * A Star Map (terv 5.3) node-színezéséhez: minden Star System-hez hozzáadja a jelenleg
     * bejelentkezett kadét összesített állapotát. Szabály: ha a rendszer összes standalone
     * missziója/csoportja teljesítve → COMPLETED; ha legalább egy elindult → IN_PROGRESS;
     * egyébként NOT_STARTED. Egyszerű, star-systemenkénti aggregáció — nem optimalizált nagy
     * rendszerszámra, MVP-elégséges (ld. terv 11. szekció).
     */
    @Transactional(readOnly = true)
    public List<StarSystemWithProgressResponse> getAllStarSystemsWithProgress() {
        Cadet cadet = getCurrentAuthenticatedUser();
        return starSystemRepository.findAll().stream()
                .map(system -> mapToResponseWithProgress(system, cadet.getId()))
                .collect(Collectors.toList());
    }

    private StarSystemWithProgressResponse mapToResponseWithProgress(StarSystem system, UUID cadetId) {
        List<Mission> standaloneMissions = missionRepository
                .findAllByStarSystemIdAndGroupIsNullOrderByOrderIndexAsc(system.getId());
        List<MissionGroup> groups = missionGroupRepository.findAllByStarSystemIdOrderByOrderIndexAsc(system.getId());

        int totalItems = standaloneMissions.size() + groups.size();
        int startedItems = 0;
        int completedItems = 0;

        for (Mission mission : standaloneMissions) {
            Optional<CadetMission> cadetMission = cadetMissionRepository
                    .findByCadetIdAndMissionId(cadetId, mission.getId());
            if (cadetMission.isPresent()) {
                startedItems++;
                if (cadetMission.get().getStatus() == MissionStatus.COMPLETED) {
                    completedItems++;
                }
            }
        }

        for (MissionGroup group : groups) {
            Optional<MissionGroupProgress> progress = missionGroupProgressRepository
                    .findByCadetIdAndGroupId(cadetId, group.getId());
            if (progress.isPresent()) {
                startedItems++;
                if (progress.get().isCompleted()) {
                    completedItems++;
                }
            }
        }

        String status;
        if (totalItems == 0 || startedItems == 0) {
            status = "NOT_STARTED";
        } else if (completedItems == totalItems) {
            status = "COMPLETED";
        } else {
            status = "IN_PROGRESS";
        }

        Sector sector = system.getSector();
        return StarSystemWithProgressResponse.builder()
                .id(system.getId())
                .name(system.getName())
                .description(system.getDescription())
                .iconUrl(system.getIconUrl())
                .sectorId(sector != null ? sector.getId() : null)
                .sectorName(sector != null ? sector.getName() : null)
                .createdAt(system.getCreatedAt())
                .updatedAt(system.getUpdatedAt())
                .status(status)
                .build();
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

        // Missziók és azok child rekordjainak törlése
        missionRepository.findAllByStarSystemId(id)
                .forEach(m -> missionService.deleteMission(m.getId()));

        // Group progress + groupok törlése
        missionGroupRepository.findAllByStarSystemIdOrderByOrderIndexAsc(id)
                .forEach(g -> missionGroupProgressRepository.deleteAllByGroupId(g.getId()));
        missionGroupRepository.deleteAllByStarSystemId(id);

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
        starSystemToUpdate.setSector(resolveSector(request.getSectorId()));
        starSystemToUpdate.setUpdatedAt(Instant.now());

        StarSystem updatedStarSystem = starSystemRepository.save(starSystemToUpdate);
        generateAndSaveEmbedding(updatedStarSystem.getId());
        return mapToResponse(updatedStarSystem);
    }

    public List<StarSystemResponse> getSystemsByCurrentUser() {
        Cadet currentUser = getCurrentAuthenticatedUser();
        return starSystemRepository.findAllByOwnerId(currentUser.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StarSystemWithItemsResponse getStarSystemWithItems(UUID id) {
        StarSystem starSystem = starSystemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StarSystem", "id", id));

        // Standalone mission-ök
        List<StarSystemItemResponse> missionItems = missionRepository
                .findAllByStarSystemIdAndGroupIsNullOrderByOrderIndexAsc(id).stream()
                .map(m -> StarSystemItemResponse.builder()
                        .type("MISSION")
                        .orderIndex(m.getOrderIndex())
                        .mission(missionService.mapToResponse(m))
                        .build())
                .collect(Collectors.toList());

        // Group-ok
        List<StarSystemItemResponse> groupItems = missionGroupRepository
                .findAllByStarSystemIdOrderByOrderIndexAsc(id).stream()
                .map(g -> {
                    List<MissionResponse> groupMissions = missionRepository
                            .findAllByGroupIdOrderByGroupOrderAsc(g.getId()).stream()
                            .map(missionService::mapToResponse)
                            .collect(Collectors.toList());
                    return StarSystemItemResponse.builder()
                            .type("GROUP")
                            .orderIndex(g.getOrderIndex())
                            .group(mapGroupToResponse(g))
                            .groupMissions(groupMissions)
                            .build();
                })
                .collect(Collectors.toList());

        // Összefűzés + rendezés orderIndex szerint
        List<StarSystemItemResponse> items = Stream.concat(missionItems.stream(), groupItems.stream())
                .sorted(Comparator.comparing(StarSystemItemResponse::getOrderIndex,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        Sector sector = starSystem.getSector();
        return StarSystemWithItemsResponse.builder()
                .id(starSystem.getId())
                .name(starSystem.getName())
                .description(starSystem.getDescription())
                .iconUrl(starSystem.getIconUrl())
                .sectorId(sector != null ? sector.getId() : null)
                .sectorName(sector != null ? sector.getName() : null)
                .createdAt(starSystem.getCreatedAt())
                .updatedAt(starSystem.getUpdatedAt())
                .items(items)
                .build();
    }

    @Transactional
    public ReorderResponse reorderItems(UUID starSystemId, ReorderItemsRequest request) {
        Integer idx1;
        Integer idx2;

        MissionGroup group1 = null;
        Mission mission1 = null;
        MissionGroup group2 = null;
        Mission mission2 = null;

        if ("GROUP".equals(request.getItem1Type())) {
            group1 = missionGroupRepository.findById(request.getItem1Id())
                    .orElseThrow(() -> new ResourceNotFoundException("MissionGroup", "id", request.getItem1Id()));
            idx1 = group1.getOrderIndex();
        } else {
            mission1 = missionRepository.findById(request.getItem1Id())
                    .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", request.getItem1Id()));
            idx1 = mission1.getOrderIndex();
        }

        if ("GROUP".equals(request.getItem2Type())) {
            group2 = missionGroupRepository.findById(request.getItem2Id())
                    .orElseThrow(() -> new ResourceNotFoundException("MissionGroup", "id", request.getItem2Id()));
            idx2 = group2.getOrderIndex();
        } else {
            mission2 = missionRepository.findById(request.getItem2Id())
                    .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", request.getItem2Id()));
            idx2 = mission2.getOrderIndex();
        }

        if (group1 != null) { group1.setOrderIndex(idx2); missionGroupRepository.save(group1); }
        else { mission1.setOrderIndex(idx2); missionRepository.save(mission1); }

        if (group2 != null) { group2.setOrderIndex(idx1); missionGroupRepository.save(group2); }
        else { mission2.setOrderIndex(idx1); missionRepository.save(mission2); }

        return ReorderResponse.builder()
                .updated(List.of(
                        ReorderResponse.ReorderItem.builder().id(request.getItem1Id()).orderIndex(idx2).build(),
                        ReorderResponse.ReorderItem.builder().id(request.getItem2Id()).orderIndex(idx1).build()
                ))
                .build();
    }

    private MissionGroupResponse mapGroupToResponse(MissionGroup group) {
        return MissionGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .description(group.getDescription())
                .starSystemId(group.getStarSystem().getId())
                .orderIndex(group.getOrderIndex())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    // Segédmetódus a StarSystem entitás Response DTO-vá alakításához
    private StarSystemResponse mapToResponse(StarSystem starSystem) {
        Sector sector = starSystem.getSector();
        return StarSystemResponse.builder()
                .id(starSystem.getId())
                .name(starSystem.getName())
                .description(starSystem.getDescription())
                .iconUrl(starSystem.getIconUrl())
                .sectorId(sector != null ? sector.getId() : null)
                .sectorName(sector != null ? sector.getName() : null)
                .createdAt(starSystem.getCreatedAt())
                .updatedAt(starSystem.getUpdatedAt())
                .build();
    }

    private Sector resolveSector(UUID sectorId) {
        if (sectorId == null) {
            return null;
        }
        return sectorRepository.findById(sectorId)
                .orElseThrow(() -> new ResourceNotFoundException("Sector", "id", sectorId));
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

    public void generateAndSaveEmbedding(UUID starSystemId) {
        StarSystem ss = starSystemRepository.findById(starSystemId).orElse(null);
        if (ss == null) return;

        List<String> missionNames = missionRepository.findAllByStarSystemId(starSystemId)
                .stream().map(Mission::getName).collect(Collectors.toList());

        String text = ss.getName()
                + (ss.getDescription() != null ? " - " + ss.getDescription() : "")
                + (missionNames.isEmpty() ? "" : ". Missziók: " + String.join(", ", missionNames));

        float[] embedding = embeddingService.embed(text);
        if (embedding == null) {
            log.warn("Embedding generation failed for StarSystem {}", starSystemId);
            return;
        }

        String vectorStr = embeddingService.toVectorString(embedding);
        jdbcTemplate.update(
                "UPDATE star_systems SET content_embedding = ?::vector WHERE id = ?::uuid",
                vectorStr, starSystemId.toString()
        );
        log.info("Embedding saved for StarSystem {}", starSystemId);
    }

    public void deleteEmbedding(UUID starSystemId) {
        jdbcTemplate.update(
                "UPDATE star_systems SET content_embedding = NULL WHERE id = ?::uuid",
                starSystemId.toString()
        );
        log.info("Embedding deleted for StarSystem {}", starSystemId);
    }

    public boolean hasEmbedding(UUID starSystemId) {
        Boolean result = jdbcTemplate.queryForObject(
                "SELECT content_embedding IS NOT NULL FROM star_systems WHERE id = ?::uuid",
                Boolean.class, starSystemId.toString()
        );
        return Boolean.TRUE.equals(result);
    }

    public List<StarSystemSearchResult> searchByEmbedding(String vectorStr, int limit) {
        return jdbcTemplate.query(
                """
                SELECT id, name, description, icon_url,
                       1 - (content_embedding <=> ?::vector) AS similarity
                FROM star_systems
                WHERE content_embedding IS NOT NULL
                ORDER BY similarity DESC
                LIMIT ?
                """,
                (rs, i) -> StarSystemSearchResult.builder()
                        .id(UUID.fromString(rs.getString("id")))
                        .name(rs.getString("name"))
                        .description(rs.getString("description"))
                        .iconUrl(rs.getString("icon_url"))
                        .similarity(rs.getDouble("similarity"))
                        .build(),
                vectorStr, limit
        );
    }

    @Transactional
    public int reindexAllStarSystems() {
        List<StarSystem> all = starSystemRepository.findAll();
        int count = 0;
        for (StarSystem ss : all) {
            generateAndSaveEmbedding(ss.getId());
            count++;
        }
        log.info("Reindex complete: {} star systems processed", count);
        return count;
    }
}