package com.legymernok.backend.service.mission;

import com.legymernok.backend.dto.mission.CreateForgeMissionRequest;
import com.legymernok.backend.dto.mission.CreateMissionRequest;
import com.legymernok.backend.dto.mission.MissionResponse;
import com.legymernok.backend.exception.ExternalServiceException;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.exception.UnauthorizedAccessException;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.ConnectTable.CadetMission;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionStatus;
import com.legymernok.backend.model.mission.VerificationStatus;
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
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${gitea.template.js.owner}")
    private String jsTemplateRepoOwner;

    @Value("${gitea.template.js.repo}")
    private String jsTemplateRepoName;

    @Value("${gitea.template.python.owner}")
    private String pythonTemplateRepoOwner;

    @Value("${gitea.template.python.repo}")
    private String pythonTemplateRepoName;

    /**
     * Létrehoz egy új missziót a Mission Forge mechanizmuson keresztül.
     * Ez a metódus intézi a Gitea repository létrehozását és a fájlok feltöltését is.
     *
     * @param request A kérés DTO, ami tartalmazza a misszió adatait és a fájlok tartalmát.
     * @return A létrehozott misszió válasz DTO-ja.
     * @throws ResourceNotFoundException Ha a csillagrendszer nem található.
     * @throws ResourceConflictException Ha már létezik azonos nevű misszió.
     * @throws UnauthorizedAccessException Ha a user nem jogosult a műveletre.
     * @throws ExternalServiceException Ha Gitea hiba történik.
     */
    @Transactional
    public MissionResponse createMissionFromForge(CreateForgeMissionRequest request) {
        Cadet currentUser = getCurrentAuthenticatedUser(); // Hitelesített user lekérése
        StarSystem starSystem = starSystemRepository.findById(request.getStarSystemId())
                .orElseThrow(() -> new ResourceNotFoundException("StarSystem", "id", request.getStarSystemId()));

        // Ellenőrzés: A user csak a saját rendszerébe tehet missziót, vagy ha van create_any_system joga
        if (!starSystem.getOwner().getId().equals(currentUser.getId()) && !hasAuthority(currentUser, "mission:create_any_system")) {
            throw new UnauthorizedAccessException("You can only add missions to your own star systems or if you have 'mission:create_any_system' permission.");
        }

        // Ellenőrzés: Név egyedisége az adott rendszerben
        if (missionRepository.existsByStarSystemIdAndName(request.getStarSystemId(), request.getName())) {
            throw new ResourceConflictException("Mission", "name", request.getName());
        }

        // Ellenőrzés: Sorrend ütközés (ha a sorrend már foglalt, eltoljuk a többit)
        if (missionRepository.existsByStarSystemIdAndOrderInSystem(request.getStarSystemId(), request.getOrderInSystem())) {
            missionRepository.shiftOrdersUp(request.getStarSystemId(), request.getOrderInSystem());
            missionRepository.flush();
        }

        // 1. Gitea Repository Létrehozása a template alapján
        // A repo neve legyen a Mission UUID-ja, hogy a Gitea Action vissza tudjon jelezni!
        UUID newMissionId = UUID.randomUUID(); // Generálunk egy ID-t előre
        String newRepoName = newMissionId.toString(); // Ez lesz a repo neve is

        String templateRepositoryUrl = giteaService.createMissionRepository(newRepoName, request.getTemplateLanguage(), currentUser);

        // 2. User által megadott fájlok feltöltése (felülírja a template fájlokat)
        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            for (Map.Entry<String, String> entry : request.getFiles().entrySet()) {
                String fileName = entry.getKey();
                String content = entry.getValue();
                giteaService.uploadFile(giteaService.getAdminUsername(), newRepoName, fileName, content);
            }
        } else {
            log.warn("Mission '{}' created without any files.", request.getName());
        }

        // 3. Misszió mentése az adatbázisba
        Mission mission = Mission.builder()
                .id(newMissionId) // A generált ID-t használjuk
                .starSystem(starSystem)
                .name(request.getName())
                .descriptionMarkdown(request.getDescriptionMarkdown())
                .missionType(request.getMissionType())
                .difficulty(request.getDifficulty())
                .orderInSystem(request.getOrderInSystem())
                .templateRepositoryUrl(templateRepositoryUrl) // Ez most az admin által birtokolt user-specifikus repo URL-je
                .owner(currentUser)
                .verificationStatus(VerificationStatus.PENDING) // Kezdetben PENDING
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Mission savedMission = missionRepository.save(mission);
        log.info("New mission '{}' created by user '{}' with repo '{}'. Initial status: PENDING.",
                savedMission.getName(), currentUser.getUsername(), templateRepositoryUrl);

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

        // 3. User Repó Létrehozása Giteán (a mission template alapján)
        // A repo neve: cadet-[username]-[missionId]
        String userRepoName = "cadet-" + cadet.getUsername() + "-" + mission.getId().toString();
        String sourceMissionRepoUrl = mission.getTemplateRepositoryUrl(); // Ez az admin által birtokolt user-specifikus repó URL-je
        String sourceRepoOwner = giteaService.getAdminUsername(); // Az admin, mert övé a mission template repó
        String sourceRepoName = extractRepoNameFromUrl(sourceMissionRepoUrl); // Repó név kinyerése

        if (sourceRepoName == null) {
            throw new ExternalServiceException("Gitea", "Could not extract repository name from mission template URL: " + sourceMissionRepoUrl);
        }

        // Létrehozzuk az üres repót a usernek az admin alatt
        String userRepoUrl = giteaService.createEmptyRepository(userRepoName, true);

        // Átmásoljuk az eredeti misszió repójának tartalmát az új user-specifikus repóba
        giteaService.copyRepositoryContents(sourceRepoOwner, sourceRepoName, userRepoName);

        // User hozzáadása kollaborátorként (write joggal)
        giteaService.addCollaborator(userRepoName, cadet.getUsername(), "write");

        // Mentés az adatbázisba
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
    public void updateMissionVerificationStatus(UUID missionId, VerificationStatus newStatus) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));

        mission.setVerificationStatus(newStatus);
        missionRepository.save(mission);
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
                .ownerId(mission.getOwner() != null ? mission.getOwner().getId() : null)
                .ownerUsername(mission.getOwner() != null ? mission.getOwner().getUsername() : null)
                .verificationStatus(mission.getVerificationStatus())
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