package com.legymernok.backend.service.mission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.dto.mission.*;
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

    @Value("${gitea.template.js.owner}")
    private String jsTemplateRepoOwner;

    @Value("${gitea.template.js.repo}")
    private String jsTemplateRepoName;

    @Value("${gitea.template.python.owner}")
    private String pythonTemplateRepoOwner;

    @Value("${gitea.template.python.repo}")
    private String pythonTemplateRepoName;

    @Value("${features.circuit.gitea-enabled:false}")
    private boolean circuitGiteaEnabled;

    /**
     * Initializes a new mission through the Mission Forge mechanism.
     * Creates the database record and the Gitea repository based on the template.
     *
     * @param request The request DTO containing the mission's basic data and selected language.
     * @return The response DTO of the created mission.
     * @throws ResourceNotFoundException If the star system is not found.
     * @throws ResourceConflictException If a mission with the same name already exists.
     * @throws UnauthorizedAccessException If the user is not authorized for the operation.
     * @throws ExternalServiceException If a Gitea error occurs.
     */
    @Transactional
    public MissionResponse initializeForgeMission(CreateMissionInitialRequest request) {
        Cadet currentUser = getCurrentAuthenticatedUser(); // Get the authenticated user
        StarSystem starSystem = starSystemRepository.findById(request.getStarSystemId())
                .orElseThrow(() -> new ResourceNotFoundException("StarSystem", "id", request.getStarSystemId()));

        // Check: The user can only add missions to their own star system, unless they have create_any_system permission
        if (!starSystem.getOwner().getId().equals(currentUser.getId()) && !hasAuthority(currentUser, "mission:create_any_system")) {
            throw new UnauthorizedAccessException("You can only add missions to your own star systems or if you have 'mission:create_any_system' permission.");
        }

        // Check: Name uniqueness within the given star system
        if (missionRepository.existsByStarSystemIdAndName(request.getStarSystemId(), request.getName())) {
            throw new ResourceConflictException("Mission", "name", request.getName());
        }

        // Check: Order conflict (if the order is already taken, shift the others)
        if (missionRepository.existsByStarSystemIdAndOrderInSystem(request.getStarSystemId(), request.getOrderInSystem())) {
            missionRepository.shiftOrdersUp(request.getStarSystemId(), request.getOrderInSystem());
            missionRepository.flush();
        }

        Mission mission = Mission.builder()
                .starSystem(starSystem)
                .name(request.getName())
                .descriptionMarkdown(request.getDescriptionMarkdown())
                .missionType(request.getMissionType())
                .difficulty(request.getDifficulty())
                .orderInSystem(request.getOrderInSystem())
                .owner(currentUser)
                .verificationStatus(VerificationStatus.DRAFT)
                .templateRepositoryUrl("PENDING_INITIALIZATION")
                .build();

        Mission savedMission = missionRepository.save(mission);

        String newRepoName = savedMission.getId().toString();

        if (savedMission.getMissionType() == MissionType.CIRCUIT_SIMULATION && !circuitGiteaEnabled) {
            log.info("Circuit Gitea repo creation is disabled (features.circuit.gitea-enabled=false). Skipping for mission {}.", newRepoName);
            savedMission.setTemplateRepositoryUrl("N/A");
            savedMission = missionRepository.save(savedMission);
        } else {
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
        }

        log.info("New mission '{}' created by user '{}' with repo '{}'.",
                savedMission.getName(), currentUser.getUsername(), savedMission.getTemplateRepositoryUrl());

        return mapToResponse(savedMission);
    }

    /**
     * Saves the files edited by the user to the Gitea repo and updates the mission status.
     *
     * @param request The DTO containing the mission ID and the file contents.
     * @return The updated mission response DTO.
     * @throws ResourceNotFoundException If the mission is not found.
     * @throws UnauthorizedAccessException If the user is not authorized for the operation.
     * @throws ExternalServiceException If a Gitea error occurs.
     */
    @Transactional
    public MissionResponse saveForgeMissionContent(MissionForgeContentRequest request) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        Mission mission = missionRepository.findById(request.getMissionId())
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", request.getMissionId()));

        // Check: User is the owner, or has edit_any permission
        if (!mission.getOwner().getId().equals(currentUser.getId()) && !hasAuthority(currentUser, "mission:edit_any")) {
            throw new UnauthorizedAccessException("You do not have permission to edit this mission.");
        }

        String repoName = mission.getId().toString(); // The repo name is the Mission UUID
        String repoOwner = giteaService.getAdminUsername(); // Admin is the owner

        // Upload/update files in the Gitea repo
        if (request.getFiles() != null && !request.getFiles().isEmpty()) {
            String commitMsg = "Forge Update - " + OffsetDateTime.now().toString();
            giteaService.uploadFiles(repoOwner, repoName, request.getFiles(), commitMsg, currentUser);
        } else {
            log.warn("Mission '{}' content saved without any files. Mission ID: {}", mission.getName(), mission.getId());
        }

        // Update status to PENDING because new code has been uploaded that needs to be tested.
        mission.setVerificationStatus(VerificationStatus.PENDING);
        mission.setUpdatedAt(Instant.now());
        Mission updatedMission = missionRepository.save(mission);

        log.info("Mission '{}' (ID: {}) content saved by user '{}'. Status set to PENDING.",
                mission.getName(), mission.getId(), currentUser.getUsername());

        return mapToResponse(updatedMission);
    }

    /**
     * Retrieves the contents of the Gitea repo for a given mission for loading into Monaco Editor.
     *
     * @param missionId The mission ID.
     * @return Map<String, String> where the key is the filename and the value is the file content.
     * @throws ResourceNotFoundException If the mission is not found.
     * @throws UnauthorizedAccessException If the user is not authorized for the operation.
     * @throws ExternalServiceException If a Gitea error occurs.
     */
    @Transactional(readOnly = true)
    public Map<String, String> getMissionFiles(UUID missionId) {
        Cadet currentUser = getCurrentAuthenticatedUser();
        Mission mission = missionRepository.findById(missionId)
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));

        // Check: User is the owner, or has read permission
        // Here we can decide: only owner can read, or anyone with "mission:read" permission
        // (but owner-specific files are only visible to the owner)
        // For now: only owner or those with edit permission
        if (!mission.getOwner().getId().equals(currentUser.getId()) && !hasAuthority(currentUser, "mission:edit_any") && !hasAuthority(currentUser, "mission:read")) {
            throw new UnauthorizedAccessException("You do not have permission to view files for this mission.");
        }

        String repoName = mission.getId().toString(); // The repo name is the Mission UUID
        String repoOwner = giteaService.getAdminUsername(); // Admin is the owner

        Map<String, String> filesContent = new HashMap<>();
        List<GiteaService.GiteaContent> contents = giteaService.getRepoContents(repoOwner, repoName, ""); // Root directory

        for (GiteaService.GiteaContent content : contents) {
            // Only read files
            if ("file".equals(content.getType())) {
                String fileContent = giteaService.getFileContent(repoOwner, repoName, content.getPath());
                if (fileContent != null) {
                    filesContent.put(content.getName(), fileContent);
                }
            }
        }

        // For QUIZ-type missions, the correct answers in quiz.json must be filtered out
        // if the requester is not the owner and not an admin — otherwise the answer key leaks.
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

        // Name conflict check (if the name changes and already exists in the target system)
        if (!missionToUpdate.getName().equals(request.getName()) &&
                missionRepository.existsByStarSystemIdAndName(request.getStarSystemId(), request.getName())) {
            throw new ResourceConflictException("Mission", "name", request.getName());
        }

        // Order conflict (if the order changes)
        if (missionToUpdate.getOrderInSystem() != request.getOrderInSystem()) {
            if (missionRepository.existsByStarSystemIdAndOrderInSystem(request.getStarSystemId(), request.getOrderInSystem())) {
                missionRepository.shiftOrdersUp(request.getStarSystemId(), request.getOrderInSystem());
                missionRepository.flush(); // Ensure the shift runs
            }
        }

        // File update in Gitea (optional, if the update request also includes files)
        // This can be a more complex part: fetching file SHA, PUT call to Gitea API.
        // For now we assume the update request does NOT include file content;
        // instead, Mission Forge will directly call GiteaService when needed.
        // If it does, the GiteaService.updateFile() method should be used.

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
        // 1. Load data and user
        Mission mission = missionRepository.findById(missionId).orElseThrow(() -> new ResourceNotFoundException("Mission", "id", missionId));

        Cadet cadet = cadetRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("Cadet", "username", username));

        // 2. Check: Already started?
        Optional<CadetMission> existing = cadetMissionRepository.findByCadetIdAndMissionId(cadet.getId(), mission.getId());
        if (existing.isPresent()) {
            log.info("User '{}' resumed mission '{}'", username, mission.getName());
            return existing.get().getRepositoryUrl();
        }

        // 3. Create user repo on Gitea (based on the mission template)
        // Repo name: cadet-[username]-[missionId]
        String userRepoName = "cadet-" + cadet.getUsername() + "-" + mission.getId().toString();
        String sourceMissionRepoUrl = mission.getTemplateRepositoryUrl(); // The admin-owned user-specific repo URL
        String sourceRepoOwner = giteaService.getAdminUsername(); // Admin owns the mission template repo
        String sourceRepoName = extractRepoNameFromUrl(sourceMissionRepoUrl); // Extract repo name

        if (sourceRepoName == null) {
            throw new ExternalServiceException("Gitea", "Could not extract repository name from mission template URL: " + sourceMissionRepoUrl);
        }

        // Create the empty repo for the user under admin
        String userRepoUrl = giteaService.createEmptyRepository(userRepoName, true);

        // Copy the original mission repo's content into the new user-specific repo
        giteaService.copyRepositoryContents(sourceRepoOwner, sourceRepoName, userRepoName);

        // Add user as collaborator (with write access)
        giteaService.addCollaborator(userRepoName, cadet.getUsername(), "write");

        // Save to database
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
        Integer deletedOrder = mission.getOrderInSystem();
        String repoUrl = mission.getTemplateRepositoryUrl();

        // 1. Delete Gitea repo (Best Effort - if it fails, we don't stop the process, just log)
        try {
            // Extract name from URL: http://gitea:3000/legymernok_admin/repo-name.git -> repo-name
            // Assume our own admin is the owner
            String repoName = extractRepoNameFromUrl(repoUrl);
            if (repoName != null) {
                giteaService.deleteAdminRepository(repoName);
            }
        } catch (Exception e) {
            System.err.println("Failed to delete Gitea repo: " + e.getMessage());
            // Don't throw an error so the DB deletion can still proceed
        }

        // 2. DB deletion
        missionRepository.delete(mission);
        log.info("Mission deleted: ID {}, Name '{}'", id, mission.getName());

        // 3. Smart Delete: Reorder indices (eliminate gaps)
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
        // Part after the last "/"
        String lastPart = url.substring(url.lastIndexOf('/') + 1);
        // Strip ".git"
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