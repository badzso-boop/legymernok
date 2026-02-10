package com.legymernok.backend.service.mission;

import com.legymernok.backend.dto.mission.*;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.UnauthorizedAccessException;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.auth.Permission;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.Difficulty;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionType;
import com.legymernok.backend.model.mission.VerificationStatus;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @Mock private MissionRepository missionRepository;
    @Mock private StarSystemRepository starSystemRepository;
    @Mock private GiteaService giteaService;
    @Mock private CadetRepository cadetRepository;
    @InjectMocks private MissionService missionService;

    private Cadet testUser;
    private StarSystem testStarSystem;
    @Mock private Authentication mockAuthentication; // Keep as @Mock, will be injected by Mockito

    @BeforeEach
    void setUp() {
        testUser = new Cadet();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("test_user");
        testUser.setRoles(new HashSet<>());

        testStarSystem = new StarSystem();
        testStarSystem.setId(UUID.randomUUID());
        testStarSystem.setOwner(testUser);
        testStarSystem.setName("TestSystem");

        // Removed authentication setup from here
        lenient().when(giteaService.getAdminUsername()).thenReturn("legymernok_admin");
    }

    // Helper method for authentication setup
    private void setupAuthentication(Cadet user) {
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(mockAuthentication);
        SecurityContextHolder.setContext(securityContext);
        when(mockAuthentication.getName()).thenReturn(user.getUsername());
        when(cadetRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
    }

    private void mockUserAuthorities(String... authorities) {
        Set<Permission> permissions = new HashSet<>();
        for (String authName : authorities) {
            Permission p = new Permission();
            p.setName(authName);
            permissions.add(p);
        }

        Role mockRole = new Role();
        mockRole.setName("MOCK_ROLE");
        mockRole.setPermissions(permissions);

        testUser.setRoles(Set.of(mockRole));
    }

    @Test
    void initializeForgeMission_whenUserIsOwnerOfStarSystem_shouldSucceedAndCreateGiteaRepo() {
        setupAuthentication(testUser); // Added authentication setup

        CreateMissionInitialRequest request = new CreateMissionInitialRequest();
        request.setStarSystemId(testStarSystem.getId());
        request.setName("New Forge Mission");
        request.setTemplateLanguage("javascript");
        request.setDifficulty(Difficulty.EASY);
        request.setMissionType(MissionType.CODING);
        request.setOrderInSystem(1);

        when(starSystemRepository.findById(testStarSystem.getId())).thenReturn(Optional.of(testStarSystem));
        when(missionRepository.existsByStarSystemIdAndName(any(), anyString())).thenReturn(false);
        when(missionRepository.existsByStarSystemIdAndOrderInSystem(any(), any())).thenReturn(false);

        when(giteaService.createMissionRepository(anyString(), eq("javascript"), eq(testUser))).thenReturn("http://gitea/repo.git");
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> {
            Mission m = i.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            return m;
        });

        MissionResponse response = missionService.initializeForgeMission(request);

        assertNotNull(response);
        assertEquals(VerificationStatus.DRAFT, response.getVerificationStatus());
        verify(giteaService).createMissionRepository(anyString(), eq("javascript"), eq(testUser));
        verify(missionRepository).save(any(Mission.class));
    }

    @Test
    void initializeForgeMission_inAnotherUsersSystemWithoutPermission_shouldThrowUnauthorized() {
        setupAuthentication(testUser); // Added authentication setup

        StarSystem anotherUsersSystem = new StarSystem();
        anotherUsersSystem.setId(UUID.randomUUID());
        Cadet anotherCadet = new Cadet();
        anotherCadet.setId(UUID.randomUUID());
        anotherUsersSystem.setOwner(anotherCadet); // Másé a rendszer

        CreateMissionInitialRequest request = new CreateMissionInitialRequest();
        request.setStarSystemId(anotherUsersSystem.getId());
        request.setName("Another Forge Mission");
        request.setTemplateLanguage("javascript");
        request.setDifficulty(Difficulty.EASY);
        request.setMissionType(MissionType.CODING);
        request.setOrderInSystem(1);

        when(starSystemRepository.findById(anotherUsersSystem.getId())).thenReturn(Optional.of(anotherUsersSystem));

        assertThrows(UnauthorizedAccessException.class, () -> missionService.initializeForgeMission(request));
        verify(giteaService, never()).createMissionRepository(anyString(), anyString(), any(Cadet.class));
    }

    @Test
    void initializeForgeMission_withDuplicateName_shouldThrowConflict() {
        setupAuthentication(testUser); // Added authentication setup

        CreateMissionInitialRequest request = new CreateMissionInitialRequest();
        request.setStarSystemId(testStarSystem.getId());
        request.setName("Duplicate Mission");
        request.setTemplateLanguage("javascript");
        request.setDifficulty(Difficulty.EASY);
        request.setMissionType(MissionType.CODING);
        request.setOrderInSystem(1);

        when(starSystemRepository.findById(testStarSystem.getId())).thenReturn(Optional.of(testStarSystem));
        when(missionRepository.existsByStarSystemIdAndName(testStarSystem.getId(), "Duplicate Mission")).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> missionService.initializeForgeMission(request));
        verify(giteaService, never()).createMissionRepository(anyString(), anyString(), any(Cadet.class));
    }

    @Test
    void initializeForgeMission_withSmartInsert_shouldShiftOthers() {
        setupAuthentication(testUser); // Added authentication setup

        CreateMissionInitialRequest request = new CreateMissionInitialRequest();
        request.setStarSystemId(testStarSystem.getId());
        request.setName("Shifted Mission");
        request.setOrderInSystem(2);
        request.setTemplateLanguage("javascript");
        request.setDifficulty(Difficulty.EASY);
        request.setMissionType(MissionType.CODING);

        when(starSystemRepository.findById(testStarSystem.getId())).thenReturn(Optional.of(testStarSystem));
        when(missionRepository.existsByStarSystemIdAndOrderInSystem(testStarSystem.getId(), 2)).thenReturn(true);
        when(giteaService.createMissionRepository(anyString(), anyString(), any(Cadet.class))).thenReturn("http://gitea/repo.url");
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> {
            Mission m = i.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            return m;
        });

        missionService.initializeForgeMission(request);

        verify(missionRepository).shiftOrdersUp(testStarSystem.getId(), 2);
        verify(missionRepository).flush();
        verify(missionRepository).save(any(Mission.class));
    }

    @Test
    void saveForgeMissionContent_byOwner_shouldSucceedAndUploadFiles() {
        setupAuthentication(testUser); // Added authentication setup
        
        UUID missionId = UUID.randomUUID();
        Mission mission = Mission.builder()
                .id(missionId)
                .owner(testUser)
                .starSystem(testStarSystem)
                .verificationStatus(VerificationStatus.DRAFT)
                .build();

        MissionForgeContentRequest request = new MissionForgeContentRequest();
        request.setMissionId(missionId);
        request.setFiles(Map.of("solution.js", "new code", "README.md", "updated readme"));

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(giteaService.uploadFile(anyString(), anyString(), anyString(), anyString())).thenReturn("http://gitea/file.js");
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> {
            Mission m = i.getArgument(0);
            return m;
        });

        MissionResponse response = missionService.saveForgeMissionContent(request);

        assertNotNull(response);
        assertEquals(VerificationStatus.PENDING, response.getVerificationStatus());
        verify(giteaService, times(2)).uploadFile(eq("legymernok_admin"), eq(missionId.toString()), anyString(), anyString()); // Fixed verification
        verify(giteaService, times(1)).getAdminUsername(); // Explicit verification
        verify(missionRepository).save(mission);
    }

    @Test
    void saveForgeMissionContent_byNonOwnerWithoutPermission_shouldThrowUnauthorized() {
        setupAuthentication(testUser); // Added authentication setup

        UUID missionId = UUID.randomUUID();
        Cadet anotherUser = new Cadet();
        anotherUser.setId(UUID.randomUUID());
        Mission mission = Mission.builder()
                .id(missionId)
                .owner(anotherUser) // Másé a misszió
                .starSystem(testStarSystem)
                .build();

        MissionForgeContentRequest request = new MissionForgeContentRequest();
        request.setMissionId(missionId);
        request.setFiles(Map.of("solution.js", "new code"));

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

        assertThrows(UnauthorizedAccessException.class, () -> missionService.saveForgeMissionContent(request));
        verify(giteaService, never()).uploadFile(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void saveForgeMissionContent_byAdminWithEditAnyPermission_shouldSucceed() {
        setupAuthentication(testUser); // Added authentication setup

        UUID missionId = UUID.randomUUID();
        Cadet anotherUser = new Cadet();
        anotherUser.setId(UUID.randomUUID());
        Mission mission = Mission.builder()
                .id(missionId)
                .owner(anotherUser) // Másé a misszió
                .starSystem(testStarSystem)
                .verificationStatus(VerificationStatus.DRAFT)
                .build();

        mockUserAuthorities("mission:edit_any"); // Jogosultság hozzáadása

        MissionForgeContentRequest request = new MissionForgeContentRequest();
        request.setMissionId(missionId);
        request.setFiles(Map.of("solution.js", "new code"));

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(giteaService.uploadFile(anyString(), anyString(), anyString(), anyString())).thenReturn("http://gitea/file.js");
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArgument(0));

        MissionResponse response = missionService.saveForgeMissionContent(request);

        assertNotNull(response);
        assertEquals(VerificationStatus.PENDING, response.getVerificationStatus());
        verify(giteaService, times(1)).uploadFile(eq("legymernok_admin"), eq(missionId.toString()), eq("solution.js"), eq("new code"));
        verify(missionRepository).save(mission);
    }

    @Test
    void getMissionFiles_byOwner_shouldReturnFilesContent() {
        setupAuthentication(testUser); // Added authentication setup

        UUID missionId = UUID.randomUUID();
        Mission mission = Mission.builder()
                .id(missionId)
                .owner(testUser)
                .starSystem(testStarSystem)
                .build();

        List<GiteaService.GiteaContent> giteaContents = Arrays.asList(
                new GiteaService.GiteaContent("solution.js", "solution.js", "file", "encoded", "url"),
                new GiteaService.GiteaContent("README.md", "README.md", "file", "encoded", "url")
        );

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(giteaService.getAdminUsername()).thenReturn("legymernok_admin"); // This one remains here as it's used directly in the test's stubbing
        when(giteaService.getRepoContents(eq("legymernok_admin"), eq(missionId.toString()), eq(""))).thenReturn(giteaContents);
        when(giteaService.getFileContent(eq("legymernok_admin"), eq(missionId.toString()), eq("solution.js"))).thenReturn("function add(){}");
        when(giteaService.getFileContent(eq("legymernok_admin"), eq(missionId.toString()), eq("README.md"))).thenReturn("# Readme");

        Map<String, String> files = missionService.getMissionFiles(missionId);

        assertNotNull(files);
        assertEquals(2, files.size());
        assertTrue(files.containsKey("solution.js"));
        assertTrue(files.containsKey("README.md"));
        assertEquals("function add(){}", files.get("solution.js"));
        verify(giteaService, times(1)).getAdminUsername(); // explicit verification
    }

    @Test
    void getMissionFiles_byNonOwnerWithoutPermission_shouldThrowUnauthorized() {
        setupAuthentication(testUser); // Added authentication setup

        UUID missionId = UUID.randomUUID();
        Cadet anotherUser = new Cadet();
        anotherUser.setId(UUID.randomUUID());
        Mission mission = Mission.builder()
                .id(missionId)
                .owner(anotherUser)
                .starSystem(testStarSystem)
                .build();

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

        assertThrows(UnauthorizedAccessException.class, () -> missionService.getMissionFiles(missionId));
        verify(giteaService, never()).getRepoContents(anyString(), anyString(), anyString());
    }

    @Test
    void getMissionFiles_byAdminWithReadAuthority_shouldReturnFilesContent() {
        setupAuthentication(testUser); // Added authentication setup

        UUID missionId = UUID.randomUUID();
        Cadet anotherUser = new Cadet();
        anotherUser.setId(UUID.randomUUID());
        Mission mission = Mission.builder()
                .id(missionId)
                .owner(anotherUser)
                .starSystem(testStarSystem)
                .build();

        mockUserAuthorities("mission:read"); // Adminnak van read joga

        List<GiteaService.GiteaContent> giteaContents = Arrays.asList(
                new GiteaService.GiteaContent("solution.js", "solution.js", "file", "encoded", "url")
        );
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(giteaService.getAdminUsername()).thenReturn("legymernok_admin"); // This one remains here as it's used directly in the test's stubbing
        when(giteaService.getRepoContents(anyString(), anyString(), anyString())).thenReturn(giteaContents);
        when(giteaService.getFileContent(anyString(), anyString(), anyString())).thenReturn("code");

        Map<String, String> files = missionService.getMissionFiles(missionId);

        assertNotNull(files);
        assertEquals(1, files.size());
        assertEquals("code", files.get("solution.js"));
        verify(giteaService, times(1)).getAdminUsername(); // explicit verification
    }

    @Test
    void deleteMission_byOwner_shouldSucceed() {
        setupAuthentication(testUser); // Added authentication setup

        Mission mission = Mission.builder()
                .id(UUID.randomUUID())
                .owner(testUser)
                .starSystem(testStarSystem)
                .templateRepositoryUrl("http://gitea:3000/legymernok_admin/my-mission-repo")
                .build();

        when(missionRepository.findById(mission.getId())).thenReturn(Optional.of(mission));
        doNothing().when(giteaService).deleteAdminRepository(anyString()); 

        missionService.deleteMission(mission.getId());

        verify(missionRepository).delete(mission);
        verify(giteaService).deleteAdminRepository("my-mission-repo");
    }

    @Test
    void deleteMission_byAdminWithPermission_shouldSucceed() {
        setupAuthentication(testUser); // Added authentication setup

        Cadet anotherUser = new Cadet();
        anotherUser.setId(UUID.randomUUID());
        Mission mission = Mission.builder()
                .id(UUID.randomUUID())
                .owner(anotherUser) // Másé a misszió
                .starSystem(testStarSystem)
                .templateRepositoryUrl("http://gitea:3000/legymernok_admin/another-mission-repo")
                .build();

        mockUserAuthorities("mission:delete_any"); // Jogosultság hozzáadása

        when(missionRepository.findById(mission.getId())).thenReturn(Optional.of(mission));
        doNothing().when(giteaService).deleteAdminRepository(anyString());

        missionService.deleteMission(mission.getId());

        verify(missionRepository).delete(mission);
        verify(giteaService).deleteAdminRepository("another-mission-repo");
    }

    @Test
    void deleteMission_whenUserIsNotOwnerAndNoPermission_shouldThrowUnauthorized() {
        setupAuthentication(testUser); // Added authentication setup

        Cadet anotherUser = new Cadet();
        anotherUser.setId(UUID.randomUUID());
        Mission mission = Mission.builder()
                .id(UUID.randomUUID())
                .owner(anotherUser) // Másé a misszió
                .starSystem(testStarSystem)
                .templateRepositoryUrl("http://gitea:3000/legymernok_admin/other-mission-repo")
                .build();

        // Nincs delete_any joga a testUser-nek
        mockUserAuthorities();

        when(missionRepository.findById(mission.getId())).thenReturn(Optional.of(mission));

        assertThrows(UnauthorizedAccessException.class, () -> missionService.deleteMission(mission.getId()));
        verify(missionRepository, never()).delete(any());
        verify(giteaService, never()).deleteAdminRepository(anyString());
        // verify(giteaService, never()).getAdminUsername(); // The service calls it before throwing Unauthorized.
        // It's better to verify if the service actually calls it or not in this flow.
        // In this case, `extractRepoNameFromUrl` which is called in `deleteMission` uses `giteaService.getAdminUsername()` implicitly or explicitly.
        // Let's recheck the service's deleteMission method.
        // It calls `extractRepoNameFromUrl(repoUrl);`
        // `extractRepoNameFromUrl` does NOT call `giteaService.getAdminUsername()`. So `never()` is appropriate here.
        verify(giteaService, never()).getAdminUsername();
    }

    @Test
    void updateMissionVerificationStatus_shouldUpdateStatus() {
        // This test does NOT require authentication setup, as previously determined.
        UUID missionId = UUID.randomUUID();
        Mission mission = Mission.builder().id(missionId).verificationStatus(VerificationStatus.PENDING).build();

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArgument(0));

        missionService.updateMissionVerificationStatus(missionId, VerificationStatus.SUCCESS);

        assertEquals(VerificationStatus.SUCCESS, mission.getVerificationStatus());
        verify(missionRepository).save(mission);
    }
}