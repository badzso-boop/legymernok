package com.legymernok.backend.service.mission;

import com.legymernok.backend.dto.mission.CreateMissionRequest;
import com.legymernok.backend.dto.mission.MissionResponse;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.exception.UnauthorizedAccessException;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.ConnectTable.CadetMission;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionStatus;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.ConnectTables.CadetMissionRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionService {

    private final MissionRepository missionRepository;
    private final StarSystemRepository starSystemRepository;
    private final CadetMissionRepository cadetMissionRepository;
    private final CadetRepository cadetRepository;
    private final GiteaService giteaService;

    @Transactional
    public MissionResponse createMission(CreateMissionRequest request) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        StarSystem starSystem = starSystemRepository.findById(request.getStarSystemId())
                .orElseThrow(() -> new ResourceNotFoundException("StarSystem", "id", request.getStarSystemId()));

        if (!starSystem.getOwner().getId().equals(currentUser.getId()) && !hasAuthority(currentUser, "mission:create_any_system")) {
            throw new UnauthorizedAccessException("You can only add missions to your own star systems or if you have 'mission:create_any_system' permission.");
        }

        if (missionRepository.existsByStarSystemIdAndName(request.getStarSystemId(),request.getName())) {
            throw new ResourceConflictException("Mission", "name", request.getName());
        }

        if (missionRepository.existsByStarSystemIdAndOrderInSystem(request.getStarSystemId(),request.getOrderInSystem())) {
            missionRepository.shiftOrdersUp(request.getStarSystemId(), request.getOrderInSystem());
            missionRepository.flush();
        }

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
                .owner(currentUser)
                .build();

        Mission savedMission = missionRepository.save(mission);
        log.info("New mission created: '{}' in StarSystem ID: {}", savedMission.getName(),savedMission.getStarSystem().getId());
        return mapToResponse(savedMission);
    }

    @Transactional
    public MissionResponse updateMission(UUID id, CreateMissionRequest request) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        Mission missionToUpdate = missionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", id));

        if (!missionToUpdate.getOwner().getId().equals(currentUser.getId()) && !hasAuthority(currentUser, "mission:edit_any")) {
            throw new UnauthorizedAccessException("You do not have permission to edit this mission.");
        }

        StarSystem newStarSystem = starSystemRepository.findById(request.getStarSystemId())
                .orElseThrow(() -> new ResourceNotFoundException("StarSystem", "id", request.getStarSystemId()));

        if (!newStarSystem.getOwner().getId().equals(currentUser.getId()) && !hasAuthority(currentUser, "mission:create_any_system")) {
            throw new UnauthorizedAccessException("You can only move missions to your own star systems or if you have 'mission:create_any_system' permission.");
        }

        // Név ütközés ellenőrzése (ha a név változik, és már létezik a célrendszerben)
        if (!missionToUpdate.getName().equals(request.getName()) &&
                missionRepository.existsByStarSystemIdAndName(request.getStarSystemId(), request.getName())) {
            throw new ResourceConflictException("Mission", "name", request.getName());
        }

        // Sorrend ütközés (ha a sorrend változik)
        if (missionToUpdate.getOrderInSystem() != request.getOrderInSystem()) {
            if (missionRepository.existsByStarSystemIdAndOrderInSystem(request.getStarSystemId(), request.getOrderInSystem())) {
                missionRepository.shiftOrdersUp(request.getStarSystemId(), request.getOrderInSystem());
                missionRepository.flush(); // Biztosítjuk, hogy a shift lefusson
            }
        }

        // Fájlok frissítése a Gitea-ban (opcionális, ha az update request is tartalmazza)
        // Ez egy komplexebb rész lehet: fájl SHA lekérése, PUT hívás a Gitea API-ra.
        // Most feltételezzük, hogy az update request NEM tartalmazza a fájl tartalmát,
        // hanem a Mission Forge majd közvetlenül hívja a GiteaService-t, ha kell.
        // Ha mégis, akkor a GiteaService.updateFile() metódusát kell használni.

        missionToUpdate.setStarSystem(newStarSystem);
        missionToUpdate.setName(request.getName());
        missionToUpdate.setDescriptionMarkdown(request.getDescriptionMarkdown());
        missionToUpdate.setMissionType(request.getMissionType());
        missionToUpdate.setDifficulty(request.getDifficulty());
        missionToUpdate.setOrderInSystem(request.getOrderInSystem());
        missionToUpdate.setUpdatedAt(Instant.now());

        Mission updatedMission = missionRepository.save(missionToUpdate);
        return mapToResponse(updatedMission);
    }

    @Transactional
    public String startMission(UUID missionId, String username) {
        // 1. Adatok és User lekérése
        Mission mission = missionRepository.findById(missionId).orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));

        Cadet cadet = cadetRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Cadet", "username", username));

        // 2. Ellenőrzés: Már elkezdte?
        Optional<CadetMission> existing = cadetMissionRepository.findByCadetIdAndMissionId(cadet.getId(), mission.getId());
        if (existing.isPresent()) {
            log.info("User '{}' resumed mission '{}'", username, mission.getName());
            return existing.get().getRepositoryUrl();
        }

        // 3. Template Repo nevének kinyerése az URL-ből
        // Feltételezzük: http://localhost:3001/admin/repo-name.git
        String templateRepoUrl = mission.getTemplateRepositoryUrl();
        // Egyszerűsített logika: az utolsó "/" utáni rész a név (.git nélkül)
        String templateRepoName = templateRepoUrl.substring(templateRepoUrl.lastIndexOf('/') + 1);
        if (templateRepoName.endsWith(".git")) {
            templateRepoName = templateRepoName.substring(0, templateRepoName.length() - 4);
        }

        // 4. Új Diák Repo Létrehozása (pl. cadet-username-mission-name)
        // Egyedivé tesszük timestamp-pel vagy UUID-vel, ha kell, de a username+mission elég lehet
        String userRepoName = "cadet-" + cadet.getUsername() + "-" + templateRepoName;

        // Ha véletlenül már létezne ilyen nevű repo (de nincs DB bejegyzés), akkor kezelni kéne,
        // de most feltételezzük, hogy a Gitea dob egy hibát vagy létrehozza.
        // A createRepository visszadja az URL-t.
        String userRepoUrl = giteaService.createRepository(userRepoName);

        // 5. Jogosultság adása a diáknak
        giteaService.addCollaborator(userRepoName, cadet.getUsername(), "write");

        // 6. Fájlok átmásolása (SMART COPY)
        // Lekérjük a template repo gyökerét
        List<GiteaService.GiteaContent> files = giteaService.getRepoContents(templateRepoName, "");

        for (GiteaService.GiteaContent file : files) {
            // Itt szűrhetünk: pl. ".solution"-t kihagyjuk
            if ("file".equals(file.getType())) {
                String content = giteaService.getFileContent(templateRepoName, file.getPath());
                giteaService.createFile(userRepoName, file.getName(), content);
            }
            // TODO: Mappák rekurzív másolása (ha a feladat mappákból áll)
            // Most az MVP-ben csak a gyökérfájlokat másoljuk.
        }

        // 7. Mentés az adatbázisba
        CadetMission cadetMission = CadetMission.builder()
                .cadet(cadet)
                .mission(mission)
                .status(MissionStatus.IN_PROGRESS)
                .repositoryUrl(userRepoUrl)
                .startedAt(Instant.now())
                .build();

        cadetMissionRepository.save(cadetMission);
        log.info("User '{}' started mission '{}'. Repo: {}", username, mission.getName(),userRepoName);
        return userRepoUrl;
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
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", id));
        return mapToResponse(mission);
    }

    @Transactional(readOnly = true)
    public List<MissionResponse> getMissionsByStarSystem(UUID starSystemId) {
        return missionRepository.findAllByStarSystemIdOrderByOrderInSystemAsc(starSystemId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public Integer getNextOrderForStarSystem(UUID starSystemId) {
        return missionRepository.findMaxOrderInSystem(starSystemId) + 1;
    }

    @Transactional
    public void deleteMission(UUID id) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        Mission mission = missionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", id));

        if (!mission.getOwner().getId().equals(currentUser.getId()) && !hasAuthority(currentUser, "mission:delete_any")) {
            throw new UnauthorizedAccessException("You do not have permission to delete this mission.");
        }

        UUID starSystemId = mission.getStarSystem().getId();
        Integer deletedOrder = mission.getOrderInSystem();
        String repoUrl = mission.getTemplateRepositoryUrl();

        // 1. Gitea Repo törlése (Best Effort - ha nem sikerül, nem állítjuk meg a folyamatot, csak logolunk)
        try {
            // URL-ből név kinyerése: http://gitea:3000/legymernok_admin/repo-name.git -> repo-name
            // Feltételezzük, hogy a saját adminunk a tulajdonos
            String repoName = extractRepoNameFromUrl(repoUrl);
            if (repoName != null) {
                giteaService.deleteAdminRepository(repoName);
            }
        } catch (Exception e) {
            System.err.println("Failed to delete Gitea repo: " + e.getMessage());
            // Nem dobunk hibát, hogy a DB törlés attól még végbemenjen
        }

        // 2. DB törlés
        missionRepository.delete(mission);
        log.info("Mission deleted: ID {}, Name '{}'", id, mission.getName());

        // 3. Smart Delete: Sorszámok rendezése (hézag megszüntetése)
        missionRepository.shiftOrdersDown(starSystemId, deletedOrder);
        missionRepository.flush();
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private String extractRepoNameFromUrl(String url) {
        if (url == null || url.isEmpty()) return null;
        // Utolsó "/" utáni rész
        String lastPart = url.substring(url.lastIndexOf('/') + 1);
        // ".git" levágása
        if (lastPart.endsWith(".git")) {
            return lastPart.substring(0, lastPart.length() - 4);
        }
        return lastPart;
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