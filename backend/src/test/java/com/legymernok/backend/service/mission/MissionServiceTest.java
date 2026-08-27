package com.legymernok.backend.service.mission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.dto.mission.*;
import com.legymernok.backend.dto.quiz.QuizDefinition;
import com.legymernok.backend.exception.ExternalServiceException;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.exception.UnauthorizedAccessException;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.service.rag.ContentChunkingService;
import com.legymernok.backend.model.ConnectTable.CadetMission;
import com.legymernok.backend.model.mission.MissionStatus;
import com.legymernok.backend.model.auth.Permission;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.Difficulty;
import com.legymernok.backend.model.mission.Mission;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
    @Mock private com.legymernok.backend.service.streak.StreakService streakService;
    @Mock private CadetMissionRepository cadetMissionRepository;
    @Mock private GiteaService giteaService;
    @Mock private CadetRepository cadetRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private MissionGroupStepCompletionRepository stepCompletionRepository;
    @Mock private MissionResultRepository missionResultRepository;
    @Mock private QuizSessionRepository quizSessionRepository;
    @Mock private FillInBlankAnswerDetailRepository fillInBlankAnswerDetailRepository;
    @Mock private FillInBlankAttemptRepository fillInBlankAttemptRepository;
    @Mock private FillInBlankOptionRepository fillInBlankOptionRepository;
    @Mock private FillInBlankBlankRepository fillInBlankBlankRepository;
    @Mock private FillInBlankDefinitionRepository fillInBlankDefinitionRepository;
    @Mock private MissionGroupRepository missionGroupRepository;
    @Mock private ContentChunkingService contentChunkingService;
    @InjectMocks private MissionService missionService;

    private Cadet testUser;
    private StarSystem testStarSystem;
    @Mock private Authentication mockAuthentication;

    // Minta quiz JSON helyes válaszokkal – több teszthez is felhasználjuk
    private static final String SAMPLE_QUIZ_JSON = """
            {
              "config": {"timeLimitSeconds": 300, "allowNavigation": true, "showSolutions": false},
              "questions": [{
                "id": "q1",
                "text": "Mi 2+2?",
                "points": 10,
                "options": [
                  {"id": "o1", "text": "3", "isCorrect": false},
                  {"id": "o2", "text": "4", "isCorrect": true}
                ]
              }]
            }
            """;

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

        lenient().when(giteaService.getAdminUsername()).thenReturn("legymernok_admin");
    }

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

    // =========================================================================
    // initializeForgeMission tesztek
    // =========================================================================

    @Test
    void initializeForgeMission_whenUserIsOwnerOfStarSystem_shouldSucceedAndCreateGiteaRepo() {
        setupAuthentication(testUser);

        CreateMissionInitialRequest request = new CreateMissionInitialRequest();
        request.setStarSystemId(testStarSystem.getId());
        request.setName("New Forge Mission");
        request.setTemplateLanguage("javascript");
        request.setDifficulty(Difficulty.EASY);
        request.setMissionType(MissionType.CODING);
        request.setOrderIndex(1);

        when(starSystemRepository.findById(testStarSystem.getId())).thenReturn(Optional.of(testStarSystem));
        when(missionRepository.existsByStarSystemIdAndName(any(), anyString())).thenReturn(false);
        when(missionRepository.existsByStarSystemIdAndOrderIndex(any(), any())).thenReturn(false);
        when(giteaService.createMissionRepository(anyString(), eq("javascript"), eq(testUser), eq(MissionType.CODING))).thenReturn("http://gitea/repo.git");
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> {
            Mission m = i.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            return m;
        });

        MissionResponse response = missionService.initializeForgeMission(request);

        assertNotNull(response);
        assertEquals(VerificationStatus.DRAFT, response.getVerificationStatus());
        verify(giteaService).createMissionRepository(anyString(), eq("javascript"), eq(testUser), eq(MissionType.CODING));
        verify(missionRepository, times(2)).save(any(Mission.class));
    }

    @Test
    void initializeForgeMission_inAnotherUsersSystemWithoutPermission_shouldThrowUnauthorized() {
        setupAuthentication(testUser);

        StarSystem anotherUsersSystem = new StarSystem();
        anotherUsersSystem.setId(UUID.randomUUID());
        Cadet anotherCadet = new Cadet();
        anotherCadet.setId(UUID.randomUUID());
        anotherUsersSystem.setOwner(anotherCadet);

        CreateMissionInitialRequest request = new CreateMissionInitialRequest();
        request.setStarSystemId(anotherUsersSystem.getId());
        request.setName("Another Forge Mission");
        request.setTemplateLanguage("javascript");
        request.setDifficulty(Difficulty.EASY);
        request.setMissionType(MissionType.CODING);
        request.setOrderIndex(1);

        when(starSystemRepository.findById(anotherUsersSystem.getId())).thenReturn(Optional.of(anotherUsersSystem));

        assertThrows(UnauthorizedAccessException.class, () -> missionService.initializeForgeMission(request));
        verify(giteaService, never()).createMissionRepository(anyString(), anyString(), any(Cadet.class), any());
    }

    @Test
    void initializeForgeMission_withDuplicateName_shouldThrowConflict() {
        setupAuthentication(testUser);

        CreateMissionInitialRequest request = new CreateMissionInitialRequest();
        request.setStarSystemId(testStarSystem.getId());
        request.setName("Duplicate Mission");
        request.setTemplateLanguage("javascript");
        request.setDifficulty(Difficulty.EASY);
        request.setMissionType(MissionType.CODING);
        request.setOrderIndex(1);

        when(starSystemRepository.findById(testStarSystem.getId())).thenReturn(Optional.of(testStarSystem));
        when(missionRepository.existsByStarSystemIdAndName(testStarSystem.getId(), "Duplicate Mission")).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> missionService.initializeForgeMission(request));
        verify(giteaService, never()).createMissionRepository(anyString(), anyString(), any(Cadet.class), any());
    }

    @Test
    void initializeForgeMission_withSmartInsert_shouldShiftOthers() {
        setupAuthentication(testUser);

        CreateMissionInitialRequest request = new CreateMissionInitialRequest();
        request.setStarSystemId(testStarSystem.getId());
        request.setName("Shifted Mission");
        request.setOrderIndex(2);
        request.setTemplateLanguage("javascript");
        request.setDifficulty(Difficulty.EASY);
        request.setMissionType(MissionType.CODING);

        when(starSystemRepository.findById(testStarSystem.getId())).thenReturn(Optional.of(testStarSystem));
        when(missionRepository.existsByStarSystemIdAndOrderIndex(testStarSystem.getId(), 2)).thenReturn(true);
        when(giteaService.createMissionRepository(anyString(), anyString(), any(Cadet.class), any())).thenReturn("http://gitea/repo.url");
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> {
            Mission m = i.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            return m;
        });

        missionService.initializeForgeMission(request);

        verify(missionRepository).shiftOrdersUp(testStarSystem.getId(), 2);
        verify(missionRepository).flush();
        // FIX: a service kétszer hív save()-t (1. PENDING_INITIALIZATION URL-lel, 2. valódi URL-lel)
        verify(missionRepository, times(2)).save(any(Mission.class));
    }

    @Test
    void initializeForgeMission_withQuizType_shouldCallCreateWithQuizType() {
        setupAuthentication(testUser);

        CreateMissionInitialRequest request = new CreateMissionInitialRequest();
        request.setStarSystemId(testStarSystem.getId());
        request.setName("Quiz Mission");
        request.setTemplateLanguage("quiz");
        request.setDifficulty(Difficulty.MEDIUM);
        request.setMissionType(MissionType.QUIZ);
        request.setOrderIndex(1);

        when(starSystemRepository.findById(testStarSystem.getId())).thenReturn(Optional.of(testStarSystem));
        when(missionRepository.existsByStarSystemIdAndName(any(), anyString())).thenReturn(false);
        when(missionRepository.existsByStarSystemIdAndOrderIndex(any(), any())).thenReturn(false);
        when(giteaService.createMissionRepository(anyString(), eq("quiz"), eq(testUser), eq(MissionType.QUIZ))).thenReturn("http://gitea/quiz-repo.git");
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> {
            Mission m = i.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            return m;
        });

        MissionResponse response = missionService.initializeForgeMission(request);

        assertNotNull(response);
        // A QUIZ típussal kell meghívni a createMissionRepository-t
        verify(giteaService).createMissionRepository(anyString(), eq("quiz"), eq(testUser), eq(MissionType.QUIZ));
    }

    @Test
    void initializeForgeMission_whenGiteaFails_shouldThrowExternalServiceException() {
        setupAuthentication(testUser);

        CreateMissionInitialRequest request = new CreateMissionInitialRequest();
        request.setStarSystemId(testStarSystem.getId());
        request.setName("Failing Mission");
        request.setTemplateLanguage("javascript");
        request.setDifficulty(Difficulty.EASY);
        request.setMissionType(MissionType.CODING);
        request.setOrderIndex(1);

        when(starSystemRepository.findById(testStarSystem.getId())).thenReturn(Optional.of(testStarSystem));
        when(missionRepository.existsByStarSystemIdAndName(any(), anyString())).thenReturn(false);
        when(missionRepository.existsByStarSystemIdAndOrderIndex(any(), any())).thenReturn(false);
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> {
            Mission m = i.getArgument(0);
            if (m.getId() == null) m.setId(UUID.randomUUID());
            return m;
        });
        when(giteaService.createMissionRepository(anyString(), anyString(), any(Cadet.class), any()))
                .thenThrow(new RuntimeException("Gitea connection refused"));

        assertThrows(ExternalServiceException.class, () -> missionService.initializeForgeMission(request));
    }

    // =========================================================================
    // saveForgeMissionContent tesztek
    // =========================================================================

    @Test
    void saveForgeMissionContent_byOwner_shouldSucceedAndUploadFiles() {
        setupAuthentication(testUser);

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
        // FIX: a service uploadFiles()-t hív (batch), nem uploadFile()-t (egyedi)
        doNothing().when(giteaService).uploadFiles(anyString(), anyString(), anyMap(), anyString(), any(Cadet.class));
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArgument(0));

        MissionResponse response = missionService.saveForgeMissionContent(request);

        assertNotNull(response);
        assertEquals(VerificationStatus.PENDING, response.getVerificationStatus());
        verify(giteaService, times(1)).uploadFiles(eq("legymernok_admin"), eq(missionId.toString()), anyMap(), anyString(), eq(testUser));
        verify(giteaService, times(1)).getAdminUsername();
        verify(missionRepository).save(mission);
    }

    @Test
    void saveForgeMissionContent_byNonOwnerWithoutPermission_shouldThrowUnauthorized() {
        setupAuthentication(testUser);

        UUID missionId = UUID.randomUUID();
        Cadet anotherUser = new Cadet();
        anotherUser.setId(UUID.randomUUID());
        Mission mission = Mission.builder()
                .id(missionId)
                .owner(anotherUser)
                .starSystem(testStarSystem)
                .build();

        MissionForgeContentRequest request = new MissionForgeContentRequest();
        request.setMissionId(missionId);
        request.setFiles(Map.of("solution.js", "new code"));

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

        assertThrows(UnauthorizedAccessException.class, () -> missionService.saveForgeMissionContent(request));
        verify(giteaService, never()).uploadFiles(anyString(), anyString(), anyMap(), anyString(), any(Cadet.class));
    }

    @Test
    void saveForgeMissionContent_byAdminWithEditAnyPermission_shouldSucceed() {
        setupAuthentication(testUser);

        UUID missionId = UUID.randomUUID();
        Cadet anotherUser = new Cadet();
        anotherUser.setId(UUID.randomUUID());
        Mission mission = Mission.builder()
                .id(missionId)
                .owner(anotherUser)
                .starSystem(testStarSystem)
                .verificationStatus(VerificationStatus.DRAFT)
                .build();

        mockUserAuthorities("mission:edit_any");

        MissionForgeContentRequest request = new MissionForgeContentRequest();
        request.setMissionId(missionId);
        request.setFiles(Map.of("solution.js", "new code"));

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        // FIX: batch upload hívás
        doNothing().when(giteaService).uploadFiles(anyString(), anyString(), anyMap(), anyString(), any(Cadet.class));
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArgument(0));

        MissionResponse response = missionService.saveForgeMissionContent(request);

        assertNotNull(response);
        assertEquals(VerificationStatus.PENDING, response.getVerificationStatus());
        verify(giteaService, times(1)).uploadFiles(eq("legymernok_admin"), eq(missionId.toString()), anyMap(), anyString(), eq(testUser));
        verify(missionRepository).save(mission);
    }

    @Test
    void saveForgeMissionContent_withEmptyFiles_shouldUpdateStatusWithoutUpload() {
        setupAuthentication(testUser);

        UUID missionId = UUID.randomUUID();
        Mission mission = Mission.builder()
                .id(missionId)
                .owner(testUser)
                .starSystem(testStarSystem)
                .verificationStatus(VerificationStatus.DRAFT)
                .build();

        MissionForgeContentRequest request = new MissionForgeContentRequest();
        request.setMissionId(missionId);
        request.setFiles(Map.of()); // üres fájllista

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArgument(0));

        MissionResponse response = missionService.saveForgeMissionContent(request);

        assertNotNull(response);
        assertEquals(VerificationStatus.PENDING, response.getVerificationStatus());
        // Üres fájlokkal nem hívódik Gitea
        verify(giteaService, never()).uploadFiles(anyString(), anyString(), anyMap(), anyString(), any(Cadet.class));
        verify(missionRepository).save(mission);
    }

    // =========================================================================
    // getMissionFiles tesztek
    // =========================================================================

    @Test
    void getMissionFiles_byOwner_shouldReturnFilesContent() {
        setupAuthentication(testUser);

        UUID missionId = UUID.randomUUID();
        Mission mission = Mission.builder()
                .id(missionId)
                .owner(testUser)
                .starSystem(testStarSystem)
                .build();

        List<GiteaService.GiteaContent> giteaContents = Arrays.asList(
                new GiteaService.GiteaContent("solution.js", "solution.js", "dummy-sha", "file", "encoded", "url"),
                new GiteaService.GiteaContent("README.md", "README.md", "dummy-sha", "file", "encoded", "url")
        );

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(giteaService.getRepoContents(eq("legymernok_admin"), eq(missionId.toString()), eq(""))).thenReturn(giteaContents);
        when(giteaService.getFileContent(eq("legymernok_admin"), eq(missionId.toString()), eq("solution.js"))).thenReturn("function add(){}");
        when(giteaService.getFileContent(eq("legymernok_admin"), eq(missionId.toString()), eq("README.md"))).thenReturn("# Readme");

        Map<String, String> files = missionService.getMissionFiles(missionId);

        assertNotNull(files);
        assertEquals(2, files.size());
        assertTrue(files.containsKey("solution.js"));
        assertTrue(files.containsKey("README.md"));
        assertEquals("function add(){}", files.get("solution.js"));
        verify(giteaService, times(1)).getAdminUsername();
    }

    @Test
    void getMissionFiles_byNonOwnerWithoutPermission_shouldThrowUnauthorized() {
        setupAuthentication(testUser);

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
        setupAuthentication(testUser);

        UUID missionId = UUID.randomUUID();
        Cadet anotherUser = new Cadet();
        anotherUser.setId(UUID.randomUUID());
        Mission mission = Mission.builder()
                .id(missionId)
                .owner(anotherUser)
                .starSystem(testStarSystem)
                .build();

        mockUserAuthorities("mission:read");

        List<GiteaService.GiteaContent> giteaContents = List.of(
                new GiteaService.GiteaContent("solution.js", "solution.js", "dummy-sha", "file", "encoded", "url")
        );
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(giteaService.getRepoContents(anyString(), anyString(), anyString())).thenReturn(giteaContents);
        when(giteaService.getFileContent(anyString(), anyString(), anyString())).thenReturn("code");

        Map<String, String> files = missionService.getMissionFiles(missionId);

        assertNotNull(files);
        assertEquals(1, files.size());
        assertEquals("code", files.get("solution.js"));
        verify(giteaService, times(1)).getAdminUsername();
    }

    @Test
    void getMissionFiles_forQuizMission_byNonOwner_shouldStripAnswers() throws Exception {
        setupAuthentication(testUser);
        mockUserAuthorities("mission:read"); // van olvasási joga, de nem owner és nem admin

        UUID missionId = UUID.randomUUID();
        Cadet owner = new Cadet();
        owner.setId(UUID.randomUUID());

        Mission mission = Mission.builder()
                .id(missionId)
                .owner(owner)
                .missionType(MissionType.QUIZ)
                .starSystem(testStarSystem)
                .build();

        List<GiteaService.GiteaContent> giteaContents = List.of(
                new GiteaService.GiteaContent("quiz.json", "quiz.json", "sha", "file", "encoded", "url")
        );

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(giteaService.getRepoContents(eq("legymernok_admin"), eq(missionId.toString()), eq(""))).thenReturn(giteaContents);
        when(giteaService.getFileContent(eq("legymernok_admin"), eq(missionId.toString()), eq("quiz.json"))).thenReturn(SAMPLE_QUIZ_JSON);

        Map<String, String> files = missionService.getMissionFiles(missionId);

        assertTrue(files.containsKey("quiz.json"));
        QuizDefinition returnedQuiz = objectMapper.readValue(files.get("quiz.json"), QuizDefinition.class);
        // Nem-owner nem láthatja a helyes válaszokat
        returnedQuiz.getQuestions().forEach(q ->
                q.getOptions().forEach(o -> assertNull(o.getIsCorrect(),
                        "isCorrect mezőnek null-nak kell lennie nem-owner számára"))
        );
    }

    @Test
    void getMissionFiles_forQuizMission_byOwner_shouldKeepAnswers() throws Exception {
        setupAuthentication(testUser); // testUser az owner

        UUID missionId = UUID.randomUUID();
        Mission mission = Mission.builder()
                .id(missionId)
                .owner(testUser) // owner!
                .missionType(MissionType.QUIZ)
                .starSystem(testStarSystem)
                .build();

        List<GiteaService.GiteaContent> giteaContents = List.of(
                new GiteaService.GiteaContent("quiz.json", "quiz.json", "sha", "file", "encoded", "url")
        );

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(giteaService.getRepoContents(anyString(), anyString(), eq(""))).thenReturn(giteaContents);
        when(giteaService.getFileContent(anyString(), anyString(), eq("quiz.json"))).thenReturn(SAMPLE_QUIZ_JSON);

        Map<String, String> files = missionService.getMissionFiles(missionId);

        assertTrue(files.containsKey("quiz.json"));
        QuizDefinition returnedQuiz = objectMapper.readValue(files.get("quiz.json"), QuizDefinition.class);
        // Owner látja a helyes válaszokat
        boolean hasCorrectAnswer = returnedQuiz.getQuestions().get(0).getOptions().stream()
                .anyMatch(o -> Boolean.TRUE.equals(o.getIsCorrect()));
        assertTrue(hasCorrectAnswer, "Ownernek látnia kell a helyes válaszokat");
    }

    @Test
    void getMissionFiles_forQuizMission_byAdminWithEditAny_shouldKeepAnswers() throws Exception {
        setupAuthentication(testUser);
        mockUserAuthorities("mission:edit_any"); // admin

        UUID missionId = UUID.randomUUID();
        Cadet owner = new Cadet();
        owner.setId(UUID.randomUUID());

        Mission mission = Mission.builder()
                .id(missionId)
                .owner(owner)
                .missionType(MissionType.QUIZ)
                .starSystem(testStarSystem)
                .build();

        List<GiteaService.GiteaContent> giteaContents = List.of(
                new GiteaService.GiteaContent("quiz.json", "quiz.json", "sha", "file", "encoded", "url")
        );

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(giteaService.getRepoContents(anyString(), anyString(), eq(""))).thenReturn(giteaContents);
        when(giteaService.getFileContent(anyString(), anyString(), eq("quiz.json"))).thenReturn(SAMPLE_QUIZ_JSON);

        Map<String, String> files = missionService.getMissionFiles(missionId);

        assertTrue(files.containsKey("quiz.json"));
        QuizDefinition returnedQuiz = objectMapper.readValue(files.get("quiz.json"), QuizDefinition.class);
        // Admin (mission:edit_any) látja a helyes válaszokat
        boolean hasCorrectAnswer = returnedQuiz.getQuestions().get(0).getOptions().stream()
                .anyMatch(o -> Boolean.TRUE.equals(o.getIsCorrect()));
        assertTrue(hasCorrectAnswer, "Adminnak látnia kell a helyes válaszokat");
    }

    // =========================================================================
    // deleteMission tesztek
    // =========================================================================

    @Test
    void deleteMission_byOwner_shouldSucceed() {
        setupAuthentication(testUser);

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
        setupAuthentication(testUser);

        Cadet anotherUser = new Cadet();
        anotherUser.setId(UUID.randomUUID());
        Mission mission = Mission.builder()
                .id(UUID.randomUUID())
                .owner(anotherUser)
                .starSystem(testStarSystem)
                .templateRepositoryUrl("http://gitea:3000/legymernok_admin/another-mission-repo")
                .build();

        mockUserAuthorities("mission:delete_any");

        when(missionRepository.findById(mission.getId())).thenReturn(Optional.of(mission));
        doNothing().when(giteaService).deleteAdminRepository(anyString());

        missionService.deleteMission(mission.getId());

        verify(missionRepository).delete(mission);
        verify(giteaService).deleteAdminRepository("another-mission-repo");
    }

    @Test
    void deleteMission_whenUserIsNotOwnerAndNoPermission_shouldThrowUnauthorized() {
        setupAuthentication(testUser);

        Cadet anotherUser = new Cadet();
        anotherUser.setId(UUID.randomUUID());
        Mission mission = Mission.builder()
                .id(UUID.randomUUID())
                .owner(anotherUser)
                .starSystem(testStarSystem)
                .templateRepositoryUrl("http://gitea:3000/legymernok_admin/other-mission-repo")
                .build();

        mockUserAuthorities();

        when(missionRepository.findById(mission.getId())).thenReturn(Optional.of(mission));

        assertThrows(UnauthorizedAccessException.class, () -> missionService.deleteMission(mission.getId()));
        verify(missionRepository, never()).delete(any());
        verify(giteaService, never()).deleteAdminRepository(anyString());
        verify(giteaService, never()).getAdminUsername();
    }

    @Test
    void deleteMission_whenGiteaDeleteFails_shouldStillDeleteFromDb() {
        // Dokumentálja a jelenlegi viselkedést: a Gitea hiba el van nyelve,
        // a DB törlés mégis megtörténik. (Ismert technikai adósság.)
        setupAuthentication(testUser);

        Mission mission = Mission.builder()
                .id(UUID.randomUUID())
                .owner(testUser)
                .starSystem(testStarSystem)
                .templateRepositoryUrl("http://gitea:3000/legymernok_admin/failing-repo")
                .build();

        when(missionRepository.findById(mission.getId())).thenReturn(Optional.of(mission));
        doThrow(new RuntimeException("Gitea unavailable")).when(giteaService).deleteAdminRepository(anyString());

        // Nem dob kivételt – a Gitea hiba el van nyelve
        assertDoesNotThrow(() -> missionService.deleteMission(mission.getId()));
        // A DB rekord mégis törlődik
        verify(missionRepository).delete(mission);
    }

    // =========================================================================
    // updateMissionVerificationStatus tesztek
    // =========================================================================

    @Test
    void updateMissionVerificationStatus_shouldUpdateStatus() {
        UUID missionId = UUID.randomUUID();
        Mission mission = Mission.builder().id(missionId).verificationStatus(VerificationStatus.PENDING).build();

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArgument(0));

        missionService.updateMissionVerificationStatus(missionId, VerificationStatus.SUCCESS);

        assertEquals(VerificationStatus.SUCCESS, mission.getVerificationStatus());
        verify(missionRepository).save(mission);
    }

    @Test
    void updateMissionVerificationStatus_whenMissionNotFound_shouldThrowNotFound() {
        UUID missionId = UUID.randomUUID();
        when(missionRepository.findById(missionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> missionService.updateMissionVerificationStatus(missionId, VerificationStatus.SUCCESS));
        verify(missionRepository, never()).save(any());
    }

    // =========================================================================
    // Teljes workflow integrációs teszt
    // =========================================================================

    @Test
    void shouldSuccessfullyCompleteMissionForgeWorkflow() {
        setupAuthentication(testUser);

        CreateMissionInitialRequest initialRequest = new CreateMissionInitialRequest();
        initialRequest.setStarSystemId(testStarSystem.getId());
        initialRequest.setName("New Full Workflow Mission");
        initialRequest.setTemplateLanguage("javascript");
        initialRequest.setDifficulty(Difficulty.EASY);
        initialRequest.setMissionType(MissionType.CODING);
        initialRequest.setOrderIndex(1);

        when(starSystemRepository.findById(testStarSystem.getId())).thenReturn(Optional.of(testStarSystem));
        when(missionRepository.existsByStarSystemIdAndName(any(), anyString())).thenReturn(false);
        when(missionRepository.existsByStarSystemIdAndOrderIndex(any(), any())).thenReturn(false);
        when(giteaService.createMissionRepository(anyString(), eq("javascript"), eq(testUser), eq(MissionType.CODING))).thenReturn("http://gitea/init_repo.git");

        when(missionRepository.save(any(Mission.class)))
                .thenAnswer(invocation -> {
                    Mission m = invocation.getArgument(0);
                    if (m.getId() == null) m.setId(UUID.randomUUID());
                    return m;
                })
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<GiteaService.GiteaContent> initialGiteaContents = Arrays.asList(
                new GiteaService.GiteaContent("solution.js", "solution.js", "dummy-sha", "file", "encoded", "url"),
                new GiteaService.GiteaContent("README.md", "README.md", "dummy-sha", "file", "encoded", "url")
        );
        when(giteaService.getRepoContents(eq("legymernok_admin"), anyString(), eq(""))).thenReturn(initialGiteaContents);
        when(giteaService.getFileContent(eq("legymernok_admin"), anyString(), eq("solution.js"))).thenReturn("console.log('initial code');");
        when(giteaService.getFileContent(eq("legymernok_admin"), anyString(), eq("README.md"))).thenReturn("# Initial Readme");

        // FIX: batch upload mock
        doNothing().when(giteaService).uploadFiles(anyString(), anyString(), anyMap(), anyString(), any(Cadet.class));

        // 1. Inicializálás
        MissionResponse initialResponse = missionService.initializeForgeMission(initialRequest);
        assertNotNull(initialResponse);
        UUID missionId = initialResponse.getId();
        assertNotNull(missionId);

        Mission missionAfterInit = Mission.builder()
                .id(missionId)
                .owner(testUser)
                .starSystem(testStarSystem)
                .templateRepositoryUrl(initialResponse.getTemplateRepositoryUrl())
                .name(initialResponse.getName())
                .verificationStatus(initialResponse.getVerificationStatus())
                .missionType(MissionType.CODING)
                .build();
        when(missionRepository.findById(missionId)).thenReturn(Optional.of(missionAfterInit));

        // 2. Fájlok betöltése
        Map<String, String> loadedFiles = missionService.getMissionFiles(missionId);
        assertNotNull(loadedFiles);
        assertEquals(2, loadedFiles.size());
        assertTrue(loadedFiles.containsKey("solution.js"));
        assertEquals("console.log('initial code');", loadedFiles.get("solution.js"));

        // 3. Fájlok mentése
        MissionForgeContentRequest saveRequest = new MissionForgeContentRequest();
        saveRequest.setMissionId(missionId);
        saveRequest.setFiles(Map.of("solution.js", "console.log('updated code');", "new_file.txt", "This is a new file."));

        MissionResponse finalResponse = missionService.saveForgeMissionContent(saveRequest);

        // Assert – Gitea hívások
        verify(giteaService, times(1)).createMissionRepository(anyString(), eq("javascript"), eq(testUser), eq(MissionType.CODING));
        verify(giteaService, times(1)).getRepoContents(eq("legymernok_admin"), eq(missionId.toString()), eq(""));
        verify(giteaService, times(1)).getFileContent(eq("legymernok_admin"), eq(missionId.toString()), eq("solution.js"));
        verify(giteaService, times(1)).getFileContent(eq("legymernok_admin"), eq(missionId.toString()), eq("README.md"));
        // FIX: batch upload verify – 1 hívás a teljes map-pel
        verify(giteaService, times(1)).uploadFiles(eq("legymernok_admin"), eq(missionId.toString()), anyMap(), anyString(), eq(testUser));
        // getMissionFiles: 1x, saveForgeMissionContent: 1x
        verify(giteaService, times(2)).getAdminUsername();

        // Assert – repository hívások
        verify(missionRepository, times(2)).findById(missionId);

        ArgumentCaptor<Mission> missionCaptor = ArgumentCaptor.forClass(Mission.class);
        verify(missionRepository, times(3)).save(missionCaptor.capture());
        List<Mission> capturedMissions = missionCaptor.getAllValues();

        Mission firstSaved = capturedMissions.get(0);
        assertNotNull(firstSaved.getId());
        assertEquals("New Full Workflow Mission", firstSaved.getName());
        assertEquals(VerificationStatus.DRAFT, firstSaved.getVerificationStatus());
        assertNotNull(firstSaved.getTemplateRepositoryUrl());

        Mission secondSaved = capturedMissions.get(1);
        assertEquals(missionId, secondSaved.getId());
        assertEquals(VerificationStatus.DRAFT, secondSaved.getVerificationStatus());

        assertNotNull(finalResponse);
        assertEquals(VerificationStatus.PENDING, finalResponse.getVerificationStatus());
        assertEquals("New Full Workflow Mission", finalResponse.getName());
    }

    // =========================================================================
    // canViewMissionLogs tesztek (StompAuthChannelInterceptor /ws-mission-logs)
    // =========================================================================

    @Test
    void canViewMissionLogs_whenUserIsOwner_shouldReturnTrue() {
        Mission mission = Mission.builder()
                .id(UUID.randomUUID())
                .owner(testUser)
                .build();
        when(missionRepository.findById(mission.getId())).thenReturn(Optional.of(mission));

        assertTrue(missionService.canViewMissionLogs(mission.getId(), testUser));
    }

    @Test
    void canViewMissionLogs_whenUserIsNotOwnerAndHasNoEditAny_shouldReturnFalse() {
        Cadet otherOwner = new Cadet();
        otherOwner.setId(UUID.randomUUID());
        otherOwner.setUsername("other_owner");

        Mission mission = Mission.builder()
                .id(UUID.randomUUID())
                .owner(otherOwner)
                .build();
        when(missionRepository.findById(mission.getId())).thenReturn(Optional.of(mission));

        assertFalse(missionService.canViewMissionLogs(mission.getId(), testUser));
    }

    @Test
    void canViewMissionLogs_whenUserHasEditAny_shouldReturnTrue() {
        Cadet otherOwner = new Cadet();
        otherOwner.setId(UUID.randomUUID());
        otherOwner.setUsername("other_owner");

        Mission mission = Mission.builder()
                .id(UUID.randomUUID())
                .owner(otherOwner)
                .build();
        when(missionRepository.findById(mission.getId())).thenReturn(Optional.of(mission));
        mockUserAuthorities("mission:edit_any");

        assertTrue(missionService.canViewMissionLogs(mission.getId(), testUser));
    }

    @Test
    void canViewMissionLogs_whenMissionDoesNotExist_shouldReturnFalse() {
        UUID missionId = UUID.randomUUID();
        when(missionRepository.findById(missionId)).thenReturn(Optional.empty());

        assertFalse(missionService.canViewMissionLogs(missionId, testUser));
    }

    // =========================================================================
    // startMission — CODING missziónál szűrt (solution/starter) másolás kell,
    // ne a teljes copyRepositoryContents (lásd #47 — a régi viselkedés a
    // referenciamegoldást is 1:1 átmásolta a kadét saját repójába)
    // =========================================================================

    @Test
    void startMission_whenCoding_shouldUseFilteredCadetCopyNotFullCopy() {
        Mission mission = Mission.builder()
                .id(UUID.randomUUID())
                .missionType(MissionType.CODING)
                .templateRepositoryUrl("http://gitea/mission-repo.git")
                .name("Add two numbers")
                .build();
        when(missionRepository.findById(mission.getId())).thenReturn(Optional.of(mission));
        when(cadetRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
        when(cadetMissionRepository.findByCadetIdAndMissionId(testUser.getId(), mission.getId()))
                .thenReturn(Optional.empty());
        when(giteaService.createEmptyRepository(anyString(), eq(true))).thenReturn("http://gitea/cadet-repo.git");

        missionService.startMission(mission.getId(), "test_user");

        verify(giteaService).copyMissionRepositoryForCadet(eq("legymernok_admin"), eq("mission-repo"), anyString(), eq(mission.getId().toString()));
        verify(giteaService, never()).copyRepositoryContents(anyString(), anyString(), anyString());
    }

    @Test
    void startMission_whenQuiz_shouldUseFullCopy() {
        Mission mission = Mission.builder()
                .id(UUID.randomUUID())
                .missionType(MissionType.QUIZ)
                .templateRepositoryUrl("http://gitea/quiz-repo.git")
                .name("Quiz mission")
                .build();
        when(missionRepository.findById(mission.getId())).thenReturn(Optional.of(mission));
        when(cadetRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
        when(cadetMissionRepository.findByCadetIdAndMissionId(testUser.getId(), mission.getId()))
                .thenReturn(Optional.empty());
        when(giteaService.createEmptyRepository(anyString(), eq(true))).thenReturn("http://gitea/cadet-repo.git");

        missionService.startMission(mission.getId(), "test_user");

        verify(giteaService).copyRepositoryContents(eq("legymernok_admin"), eq("quiz-repo"), anyString());
        verify(giteaService, never()).copyMissionRepositoryForCadet(anyString(), anyString(), anyString(), anyString());
    }

    // =========================================================================
    // Play fájlkezelés — a küldetés készítője által adott tesztfájlok
    // írásvédettek a kadét munkarepójában, nem trükközhet velük a CI ellen.
    // =========================================================================

    private CadetMission mockPlayRepository(String repositoryUrl) {
        Mission mission = Mission.builder()
                .id(UUID.randomUUID())
                .missionType(MissionType.CODING)
                .build();
        CadetMission cadetMission = CadetMission.builder()
                .id(UUID.randomUUID())
                .cadet(testUser)
                .mission(mission)
                .status(MissionStatus.IN_PROGRESS)
                .repositoryUrl(repositoryUrl)
                .build();
        setupAuthentication(testUser);
        lenient().when(cadetMissionRepository.findByCadetIdAndMissionId(testUser.getId(), mission.getId()))
                .thenReturn(Optional.of(cadetMission));
        return cadetMission;
    }

    @Test
    void savePlayFiles_shouldFilterOutProtectedTestFiles() {
        CadetMission cadetMission = mockPlayRepository("http://gitea/cadet-repo.git");

        Map<String, String> files = new HashMap<>();
        files.put("solution.js", "export function add(a, b) { return a + b; }");
        files.put("solution.test.js", "// tampered test content");

        missionService.savePlayFiles(cadetMission.getMission().getId(), files);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(giteaService).uploadFiles(eq("legymernok_admin"), eq("cadet-repo"), captor.capture(), anyString(), eq(testUser));
        assertTrue(captor.getValue().containsKey("solution.js"));
        assertFalse(captor.getValue().containsKey("solution.test.js"));
    }

    @Test
    void savePlayFiles_whenOnlyProtectedFilesInBatch_shouldNotCallUpload() {
        CadetMission cadetMission = mockPlayRepository("http://gitea/cadet-repo.git");

        Map<String, String> files = new HashMap<>();
        files.put("test_solution.py", "# tampered");

        missionService.savePlayFiles(cadetMission.getMission().getId(), files);

        verify(giteaService, never()).uploadFiles(anyString(), anyString(), anyMap(), anyString(), any());
    }

    @Test
    void createPlayFile_whenProtectedFileName_shouldThrow() {
        CadetMission cadetMission = mockPlayRepository("http://gitea/cadet-repo.git");

        assertThrows(UnauthorizedAccessException.class,
                () -> missionService.createPlayFile(cadetMission.getMission().getId(), "solution.test.js"));
        verify(giteaService, never()).uploadFile(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void deletePlayFile_whenProtectedFileName_shouldThrow() {
        CadetMission cadetMission = mockPlayRepository("http://gitea/cadet-repo.git");

        assertThrows(UnauthorizedAccessException.class,
                () -> missionService.deletePlayFile(cadetMission.getMission().getId(), "test_solution.py"));
        verify(giteaService, never()).deleteFile(anyString(), anyString(), anyString(), any());
    }

    @Test
    void renamePlayFile_whenTargetIsProtectedFileName_shouldThrow() {
        CadetMission cadetMission = mockPlayRepository("http://gitea/cadet-repo.git");

        assertThrows(UnauthorizedAccessException.class,
                () -> missionService.renamePlayFile(cadetMission.getMission().getId(), "utils.js", "solution.test.js"));
        verify(giteaService, never()).renameFile(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void renamePlayFile_whenNeitherNameProtected_shouldSucceed() {
        CadetMission cadetMission = mockPlayRepository("http://gitea/cadet-repo.git");

        missionService.renamePlayFile(cadetMission.getMission().getId(), "utils.js", "helpers.js");

        verify(giteaService).renameFile("legymernok_admin", "cadet-repo", "utils.js", "helpers.js", testUser);
    }
}
