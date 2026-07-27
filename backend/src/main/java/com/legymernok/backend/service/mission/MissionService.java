package com.legymernok.backend.service.mission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.dto.mission.*;
import java.util.Arrays;
import com.legymernok.backend.dto.quiz.QuizDefinition;
import com.legymernok.backend.exception.ExternalServiceException;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.exception.UnauthorizedAccessException;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.ConnectTable.CadetMission;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionStatus;
import com.legymernok.backend.model.mission.MissionType;
import com.legymernok.backend.model.mission.VerificationStatus;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.ConnectTables.CadetMissionRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.fillinblank.FillInBlankAnswerDetailRepository;
import com.legymernok.backend.repository.fillinblank.FillInBlankAttemptRepository;
import com.legymernok.backend.repository.fillinblank.FillInBlankBlankRepository;
import com.legymernok.backend.repository.fillinblank.FillInBlankDefinitionRepository;
import com.legymernok.backend.repository.fillinblank.FillInBlankOptionRepository;
import com.legymernok.backend.repository.mission.MissionGroupRepository;
import com.legymernok.backend.repository.mission.MissionGroupStepCompletionRepository;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.repository.mission.MissionResultRepository;
import com.legymernok.backend.repository.quiz.QuizSessionRepository;
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
import java.time.OffsetDateTime;
import java.util.*;
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
    private final ObjectMapper objectMapper;
    private final MissionGroupStepCompletionRepository stepCompletionRepository;
    private final MissionResultRepository missionResultRepository;
    private final QuizSessionRepository quizSessionRepository;
    private final FillInBlankAnswerDetailRepository fillInBlankAnswerDetailRepository;
    private final FillInBlankAttemptRepository fillInBlankAttemptRepository;
    private final FillInBlankOptionRepository fillInBlankOptionRepository;
    private final FillInBlankBlankRepository fillInBlankBlankRepository;
    private final FillInBlankDefinitionRepository fillInBlankDefinitionRepository;
    private final MissionGroupRepository missionGroupRepository;

    @Value("${gitea.template.js.owner}")
    private String jsTemplateRepoOwner;

    @Value("${gitea.template.js.repo}")
    private String jsTemplateRepoName;

    @Value("${gitea.template.python.owner}")
    private String pythonTemplateRepoOwner;

    @Value("${gitea.template.python.repo}")
    private String pythonTemplateRepoName;

    /**
     * Inicializál egy új missziót a Mission Forge mechanizmuson keresztül.
     * Létrehozza az adatbázis rekordot és a Gitea repository-t a template alapján.
     *
     * @param request A kérés DTO, ami tartalmazza a misszió alapadatait és a választott nyelvet.
     * @return A létrehozott misszió válasz DTO-ja.
     * @throws ResourceNotFoundException Ha a csillagrendszer nem található.
     * @throws ResourceConflictException Ha már létezik azonos nevű misszió.
     * @throws UnauthorizedAccessException Ha a user nem jogosult a műveletre.
     * @throws ExternalServiceException Ha Gitea hiba történik.
     */
    @Transactional
    public MissionResponse initializeForgeMission(CreateMissionInitialRequest request) {
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

        // Ellenőrzés: Sorrend ütközés (missions ÉS groups ellen)
        if (missionRepository.existsByStarSystemIdAndOrderIndex(request.getStarSystemId(), request.getOrderIndex())
                || missionGroupRepository.existsByStarSystemIdAndOrderIndex(request.getStarSystemId(), request.getOrderIndex())) {
            missionRepository.shiftOrdersUp(request.getStarSystemId(), request.getOrderIndex());
            missionGroupRepository.shiftOrdersUp(request.getStarSystemId(), request.getOrderIndex());
            missionRepository.flush();
            missionGroupRepository.flush();
        }

        Mission mission = Mission.builder()
                .starSystem(starSystem)
                .name(request.getName())
                .descriptionMarkdown(request.getDescriptionMarkdown())
                .missionType(request.getMissionType())
                .difficulty(request.getDifficulty())
                .orderIndex(request.getOrderIndex())
                .owner(currentUser)
                .verificationStatus(VerificationStatus.DRAFT)
                .build();

        // CONTENT és FILL_IN_BLANK típusú missziókhoz nincs Gitea repo
        if (request.getMissionType() == MissionType.CONTENT
                || request.getMissionType() == MissionType.FILL_IN_BLANK) {
            Mission savedMission = missionRepository.save(mission);
            log.info("New {} mission '{}' created by user '{}'.",
                    request.getMissionType(), savedMission.getName(), currentUser.getUsername());
            return mapToResponse(savedMission);
        }

        mission.setTemplateRepositoryUrl("PENDING_INITIALIZATION");
        Mission savedMission = missionRepository.save(mission);

        String newRepoName = savedMission.getId().toString();

        try {
            String templateRepositoryUrl = giteaService.createMissionRepository(
                    newRepoName,
                    request.getTemplateLanguage(),
                    currentUser,
                    savedMission.getMissionType()
            );

            savedMission.setTemplateRepositoryUrl(templateRepositoryUrl);
            savedMission = missionRepository.save(savedMission);

        } catch (Exception e) {
            log.error("Gitea repository creation failed for mission {}. Error: {}", newRepoName, e.getMessage());
            throw new ExternalServiceException("Gitea", "Failed to create repository: " + e.getMessage());
        }

        log.info("New mission '{}' created by user '{}' with repo '{}'.",
                savedMission.getName(), currentUser.getUsername(), savedMission.getTemplateRepositoryUrl());

        return mapToResponse(savedMission);
    }

    /**
     * Menti a user által szerkesztett fájlokat a Gitea repóba, és frissíti a misszió státuszát.
     *
     * @param request A DTO, ami tartalmazza a misszió ID-jét és a fájlok tartalmát.
     * @return A frissített misszió válasz DTO-ja.
     * @throws ResourceNotFoundException Ha a misszió nem található.
     * @throws UnauthorizedAccessException Ha a user nem jogosult a műveletre.
     * @throws ExternalServiceException Ha Gitea hiba történik.
     */
    @Transactional
    public MissionResponse saveForgeMissionContent(MissionForgeContentRequest request) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        Mission mission = missionRepository.findById(request.getMissionId())
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", request.getMissionId()));

        requireMissionEditAccess(mission, currentUser);

        String repoName = mission.getId().toString(); // A repó neve a Mission UUID-ja
        String repoOwner = giteaService.getAdminUsername(); // Az admin a tulajdonos

        // Fájlok feltöltése/frissítése a Gitea repóban
        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            String commitMsg = "Forge Update - " + OffsetDateTime.now().toString();
            giteaService.uploadFiles(repoOwner, repoName, request.getFiles(), commitMsg, currentUser);
        } else {
            log.warn("Mission '{}' content saved without any files. Mission ID: {}", mission.getName(), mission.getId());
        }

        // Státusz frissítése PENDING-re, mert új kód került feltöltésre, amit tesztelni kell.
        mission.setVerificationStatus(VerificationStatus.PENDING);
        mission.setUpdatedAt(Instant.now());
        Mission updatedMission = missionRepository.save(mission);

        log.info("Mission '{}' (ID: {}) content saved by user '{}'. Status set to PENDING.",
                mission.getName(), mission.getId(), currentUser.getUsername());

        return mapToResponse(updatedMission);
    }

    // Ellenőrzés a mission "saját" (Forge/template) repójának módosításához:
    // tulajdonos vagy edit_any jog kell — ugyanaz a szabály, mint
    // saveForgeMissionContent-nél.
    private void requireMissionEditAccess(Mission mission, Cadet currentUser) {
        if (mission.getOwner() != null
                && !mission.getOwner().getId().equals(currentUser.getId())
                && !hasAuthority(currentUser, "mission:edit_any")) {
            throw new UnauthorizedAccessException("You do not have permission to edit this mission.");
        }
    }

    // Ha egy CODING misszióhoz (akár régi, még createMission()-nel, template
    // repó nélkül létrehozotthoz) még nincs Gitea repója, itt hozzuk létre —
    // Stage 2 "admin előre beállíthatja a fájlstruktúrát" ehhez a lusta
    // provisioninghoz kellett, hogy a meglévő missziók se törjenek.
    private Mission ensureMissionRepository(Mission mission) {
        if (mission.getTemplateRepositoryUrl() != null) return mission;

        String repoName = mission.getId().toString();
        String repoUrl = giteaService.createEmptyRepository(repoName, true);
        mission.setTemplateRepositoryUrl(repoUrl);
        mission.setUpdatedAt(Instant.now());
        return missionRepository.save(mission);
    }

    /**
     * Új (üres) fájlt hoz létre egy misszió saját (template) repójában —
     * admin/tulajdonos állíthatja elő előre a fájlstruktúrát, mielőtt egy
     * kadét elindítaná a missziót.
     */
    @Transactional
    public MissionResponse createMissionFile(UUID missionId, String path) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));
        requireMissionEditAccess(mission, currentUser);

        mission = ensureMissionRepository(mission);
        String repoOwner = giteaService.getAdminUsername();
        String repoName = mission.getId().toString();

        giteaService.uploadFile(repoOwner, repoName, path, "", currentUser);
        log.info("File '{}' created in mission '{}' (ID: {}) template repo by '{}'.",
                path, mission.getName(), mission.getId(), currentUser.getUsername());
        return mapToResponse(mission);
    }

    /** Töröl egy fájlt egy misszió saját (template) repójából. */
    @Transactional
    public void deleteMissionFile(UUID missionId, String path) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));
        requireMissionEditAccess(mission, currentUser);

        String repoOwner = giteaService.getAdminUsername();
        String repoName = mission.getId().toString();
        giteaService.deleteFile(repoOwner, repoName, path, currentUser);
        log.info("File '{}' deleted from mission '{}' (ID: {}) template repo by '{}'.",
                path, mission.getName(), mission.getId(), currentUser.getUsername());
    }

    /** Átnevez egy fájlt egy misszió saját (template) repójában. */
    @Transactional
    public void renameMissionFile(UUID missionId, String oldPath, String newPath) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));
        requireMissionEditAccess(mission, currentUser);

        String repoOwner = giteaService.getAdminUsername();
        String repoName = mission.getId().toString();
        giteaService.renameFile(repoOwner, repoName, oldPath, newPath, currentUser);
        log.info("File '{}' renamed to '{}' in mission '{}' (ID: {}) template repo by '{}'.",
                oldPath, newPath, mission.getName(), mission.getId(), currentUser.getUsername());
    }

    // ─── Play — a kadét SAJÁT (startMission által létrehozott) repója ───
    // Ugyanaz a fájlkezelés, mint a template/Forge oldalon, de itt a
    // CadetMission.repositoryUrl a cél, nem a Mission saját repója —
    // mert egy admin-készítette CODING missziót sok kadét is elindíthat,
    // mindenki a saját (admin-owned, ő maga collaborator) másolatában dolgozik.
    private CadetMission requirePlayRepository(UUID missionId, Cadet cadet) {
        CadetMission cadetMission = cadetMissionRepository.findByCadetIdAndMissionId(cadet.getId(), missionId)
                .orElseThrow(() -> new ResourceNotFoundException("CadetMission", "missionId", missionId));
        if (cadetMission.getRepositoryUrl() == null) {
            throw new ExternalServiceException("Gitea", "This mission has no repository yet — start it first.");
        }
        // Csak CODING missziónál engedélyezett — a QUIZ saját repó-másolata
        // a teljes megoldókulcsot (quiz.json, isCorrect mezőkkel) tartalmazza,
        // amit a getMissionFiles()-nél máshol tudatosan kiszűrünk. Ez a
        // végpontcsalád nem szűr, tehát QUIZ-re nem szabad kiszolgálnia.
        if (cadetMission.getMission().getMissionType() != MissionType.CODING) {
            throw new UnauthorizedAccessException("File management is only available for CODING missions.");
        }
        return cadetMission;
    }

    /** Lekéri a bejelentkezett kadét saját munkarepójának fájljait egy elindított misszióhoz. */
    @Transactional(readOnly = true)
    public Map<String, String> getPlayFiles(UUID missionId) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        CadetMission cadetMission = requirePlayRepository(missionId, currentUser);
        String repoOwner = giteaService.getAdminUsername();
        String repoName = extractRepoNameFromUrl(cadetMission.getRepositoryUrl());

        Map<String, String> filesContent = new HashMap<>();
        for (GiteaService.GiteaContent content : giteaService.getRepoContents(repoOwner, repoName, "")) {
            if ("file".equals(content.getType())) {
                String fileContent = giteaService.getFileContent(repoOwner, repoName, content.getPath());
                if (fileContent != null) filesContent.put(content.getName(), fileContent);
            }
        }
        return filesContent;
    }

    /** Elmenti (batch) a kadét saját munkarepójának fájltartalmait. */
    @Transactional
    public void savePlayFiles(UUID missionId, Map<String, String> files) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        CadetMission cadetMission = requirePlayRepository(missionId, currentUser);
        String repoOwner = giteaService.getAdminUsername();
        String repoName = extractRepoNameFromUrl(cadetMission.getRepositoryUrl());

        if (files == null || files.isEmpty()) return;
        giteaService.uploadFiles(repoOwner, repoName, files, "Save from mission player", currentUser);
    }

    /** Új (üres) fájlt hoz létre a kadét saját munkarepójában. */
    @Transactional
    public void createPlayFile(UUID missionId, String path) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        CadetMission cadetMission = requirePlayRepository(missionId, currentUser);
        String repoOwner = giteaService.getAdminUsername();
        String repoName = extractRepoNameFromUrl(cadetMission.getRepositoryUrl());
        giteaService.uploadFile(repoOwner, repoName, path, "", currentUser);
    }

    /** Töröl egy fájlt a kadét saját munkarepójából. */
    @Transactional
    public void deletePlayFile(UUID missionId, String path) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        CadetMission cadetMission = requirePlayRepository(missionId, currentUser);
        String repoOwner = giteaService.getAdminUsername();
        String repoName = extractRepoNameFromUrl(cadetMission.getRepositoryUrl());
        giteaService.deleteFile(repoOwner, repoName, path, currentUser);
    }

    /** Átnevez egy fájlt a kadét saját munkarepójában. */
    @Transactional
    public void renamePlayFile(UUID missionId, String oldPath, String newPath) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        CadetMission cadetMission = requirePlayRepository(missionId, currentUser);
        String repoOwner = giteaService.getAdminUsername();
        String repoName = extractRepoNameFromUrl(cadetMission.getRepositoryUrl());
        giteaService.renameFile(repoOwner, repoName, oldPath, newPath, currentUser);
    }

    /**
     * Lekéri egy adott misszióhoz tartozó Gitea repó tartalmát a Monaco Editorba való betöltéshez.
     *
     * @param missionId A misszió ID-je.
     * @return Map<String, String>, ahol a kulcs a fájlnév, az érték a fájl tartalma.
     * @throws ResourceNotFoundException Ha a misszió nem található.
     * @throws UnauthorizedAccessException Ha a user nem jogosult a műveletre.
     * @throws ExternalServiceException Ha Gitea hiba történik.
     */
    @Transactional(readOnly = true)
    public Map<String, String> getMissionFiles(UUID missionId) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));

        // Ellenőrzés: User a tulajdonos, vagy van read joga
        // Itt dönthetünk, hogy csak az owner olvashatja, vagy bárki (ha van "mission:read" joga,
        // de az owner-specifikus fájlokat csak az owner látja)
        // Kezdésnek legyen csak owner vagy edit jog
        if (!mission.getOwner().getId().equals(currentUser.getId()) && !hasAuthority(currentUser, "mission:edit_any") && !hasAuthority(currentUser, "mission:read")) {
            throw new UnauthorizedAccessException("You do not have permission to view files for this mission.");
        }

        String repoName = mission.getId().toString(); // A repó neve a Mission UUID-ja
        String repoOwner = giteaService.getAdminUsername(); // Az admin a tulajdonos

        Map<String, String> filesContent = new HashMap<>();
        List<GiteaService.GiteaContent> contents = giteaService.getRepoContents(repoOwner, repoName, ""); // Gyökér mappa

        for (GiteaService.GiteaContent content : contents) {
            // Csak fájlokat olvasunk ki
            if ("file".equals(content.getType())) {
                String fileContent = giteaService.getFileContent(repoOwner, repoName, content.getPath());
                if (fileContent != null) {
                    filesContent.put(content.getName(), fileContent);
                }
            }
        }

        // QUIZ típusú missziónál a quiz.json helyes válaszait le kell szűrni,
        // ha a kérelmező nem az owner és nem admin – különben a megoldókulcs kiszivárog.
        boolean isOwner = mission.getOwner().getId().equals(currentUser.getId());
        boolean isAdmin = hasAuthority(currentUser, "mission:edit_any");
        if (mission.getMissionType() == MissionType.QUIZ && !isOwner && !isAdmin) {
            String quizJson = filesContent.get("quiz.json");
            if (quizJson != null) {
                try {
                    QuizDefinition quizDef = objectMapper.readValue(quizJson, QuizDefinition.class);
                    quizDef.getQuestions().forEach(q ->
                            q.getOptions().forEach(o -> o.setIsCorrect(null)));
                    filesContent.put("quiz.json", objectMapper.writeValueAsString(quizDef));
                } catch (Exception e) {
                    log.error("Failed to strip answers from quiz.json for mission {}", missionId, e);
                }
            }
        }

        log.info("Files for mission '{}' (ID: {}) fetched for user '{}'.", mission.getName(), mission.getId(), currentUser.getUsername());
        return filesContent;
    }

    @Transactional
    public MissionResponse createMission(CreateMissionRequest request) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        StarSystem starSystem = starSystemRepository.findById(request.getStarSystemId())
                .orElseThrow(() -> new ResourceNotFoundException("StarSystem", "id", request.getStarSystemId()));

        if (!starSystem.getOwner().getId().equals(currentUser.getId()) && !hasAuthority(currentUser, "mission:create_any_system")) {
            throw new UnauthorizedAccessException("You can only add missions to your own star systems.");
        }

        if (missionRepository.existsByStarSystemIdAndName(request.getStarSystemId(), request.getName())) {
            throw new ResourceConflictException("Mission", "name", request.getName());
        }

        if (request.getOrderIndex() != null &&
                (missionRepository.existsByStarSystemIdAndOrderIndex(request.getStarSystemId(), request.getOrderIndex())
                || missionGroupRepository.existsByStarSystemIdAndOrderIndex(request.getStarSystemId(), request.getOrderIndex()))) {
            missionRepository.shiftOrdersUp(request.getStarSystemId(), request.getOrderIndex());
            missionGroupRepository.shiftOrdersUp(request.getStarSystemId(), request.getOrderIndex());
            missionRepository.flush();
            missionGroupRepository.flush();
        }

        Mission mission = Mission.builder()
                .starSystem(starSystem)
                .name(request.getName())
                .descriptionMarkdown(request.getDescriptionMarkdown())
                .missionType(request.getMissionType())
                .difficulty(request.getDifficulty())
                .orderIndex(request.getOrderIndex())
                .owner(currentUser)
                .verificationStatus(VerificationStatus.PENDING)
                .content(request.getContent())
                .build();

        Mission saved = missionRepository.save(mission);

        // CODING missziónál rögtön létrehozzuk az üres (admin-owned) template
        // repót is, hogy az admin a mentés után azonnal be tudja állítani a
        // kezdő fájlstruktúrát — enélkül nem lenne mit a kadét saját repójába
        // másolni startMission()-kor.
        if (saved.getMissionType() == MissionType.CODING) {
            saved = ensureMissionRepository(saved);
        }

        log.info("Admin mission '{}' (ID: {}) created by '{}'.", saved.getName(), saved.getId(), currentUser.getUsername());
        return mapToResponse(saved);
    }

    @Transactional
    public MissionResponse updateMission(UUID id, CreateMissionRequest request) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        Mission missionToUpdate = missionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", id));

        requireMissionEditAccess(missionToUpdate, currentUser);

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

        // Sorrend ütközés (ha a sorrend változik — missions ÉS groups ellen)
        if (!java.util.Objects.equals(missionToUpdate.getOrderIndex(), request.getOrderIndex())) {
            if (missionRepository.existsByStarSystemIdAndOrderIndex(request.getStarSystemId(), request.getOrderIndex())
                    || missionGroupRepository.existsByStarSystemIdAndOrderIndex(request.getStarSystemId(), request.getOrderIndex())) {
                missionRepository.shiftOrdersUp(request.getStarSystemId(), request.getOrderIndex());
                missionGroupRepository.shiftOrdersUp(request.getStarSystemId(), request.getOrderIndex());
                missionRepository.flush();
                missionGroupRepository.flush();
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
        missionToUpdate.setOrderIndex(request.getOrderIndex());
        if (request.getContent() != null) {
            missionToUpdate.setContent(request.getContent());
        }
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

        // 2b. Régebbi (a Stage 2 lusta-provisioning bevezetése előtt létrehozott)
        // CODING misszióknak lehet, hogy még nincs template repójuk — enélkül
        // nem lenne mit másolni a kadét saját repójába.
        if (mission.getMissionType() == MissionType.CODING) {
            mission = ensureMissionRepository(mission);
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
        return missionRepository.findAllByStarSystemIdAndGroupIsNullOrderByOrderIndexAsc(starSystemId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public Integer getNextOrderForStarSystem(UUID starSystemId) {
        int missionMax = missionRepository.findMaxOrderIndex(starSystemId);
        int groupMax = missionGroupRepository.findMaxOrderIndex(starSystemId);
        return Math.max(missionMax, groupMax) + 1;
    }

    @Transactional(readOnly = true)
    public ContentPageResponse getContentPage(UUID missionId, int page, int pageSize) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));
        if (mission.getMissionType() != MissionType.CONTENT) {
            throw new IllegalArgumentException("Mission is not CONTENT type");
        }
        String rawContent = mission.getContent();
        String fullContent = (rawContent != null && !rawContent.isBlank())
                ? rawContent
                : (mission.getDescriptionMarkdown() != null ? mission.getDescriptionMarkdown() : "");
        String[] lines = fullContent.split("\n", -1);
        int totalLines = lines.length;
        int totalPages = Math.max(1, (int) Math.ceil((double) totalLines / pageSize));
        int from = page * pageSize;
        int to = Math.min(from + pageSize, totalLines);
        String pageContent = from < totalLines
                ? String.join("\n", Arrays.copyOfRange(lines, from, to))
                : "";
        return ContentPageResponse.builder()
                .missionId(missionId)
                .missionName(mission.getName())
                .content(pageContent)
                .page(page)
                .pageSize(pageSize)
                .totalLines(totalLines)
                .totalPages(totalPages)
                .hasNextPage(page < totalPages - 1)
                .hasPreviousPage(page > 0)
                .build();
    }

    @Transactional
    public MissionResponse updateMissionContent(UUID id, String content) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        Mission mission = missionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", id));

        requireMissionEditAccess(mission, currentUser);

        mission.setContent(content);
        return mapToResponse(missionRepository.save(mission));
    }

    public void updateMissionVerificationStatus(UUID missionId, VerificationStatus newStatus) {
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));

        mission.setVerificationStatus(newStatus);
        missionRepository.save(mission);
    }

    @Transactional(readOnly = true)
    public List<MissionResponse> getMissionsByCurrentUser() {
        Cadet currentUser = getCurrentAuthenticatedUser();
        return missionRepository.findAllByOwnerId(currentUser.getId()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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
        Integer deletedOrder = mission.getOrderIndex();
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

        // 2. DB törlés — child rekordok helyes sorrendben
        fillInBlankAnswerDetailRepository.deleteAllByMissionId(id);
        fillInBlankAttemptRepository.deleteAllByMissionId(id);
        stepCompletionRepository.deleteAllByMissionId(id);
        missionResultRepository.deleteAllByMissionId(id);
        quizSessionRepository.deleteAllByMissionId(id);
        cadetMissionRepository.deleteAllByMissionId(id);
        fillInBlankOptionRepository.deleteAllByMissionId(id);
        fillInBlankBlankRepository.deleteAllByMissionId(id);
        fillInBlankDefinitionRepository.deleteByMissionId(id);
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

    public MissionResponse mapToResponse(Mission mission) {
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
                .orderIndex(mission.getOrderIndex())
                .groupId(mission.getGroup() != null ? mission.getGroup().getId() : null)
                .groupOrder(mission.getGroupOrder())
                .ownerId(mission.getOwner() != null ? mission.getOwner().getId() : null)
                .ownerUsername(mission.getOwner() != null ? mission.getOwner().getUsername() : null)
                .verificationStatus(mission.getVerificationStatus())
                .createdAt(mission.getCreatedAt())
                .content(mission.getContent())
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