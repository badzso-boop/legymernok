package com.legymernok.backend.service.circuit;

import com.legymernok.backend.dto.circuit.CompileCircuitResponse;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.integration.GiteaActionsService;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.circuit.*;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.circuit.CadetCircuitSaveRepository;
import com.legymernok.backend.repository.circuit.CircuitDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ArduinoCompilerService.
 *
 * Note: @Async is NOT applied when using @InjectMocks (no Spring AOP proxy),
 * so compile() runs synchronously here. .join() is used to unwrap the
 * CompletableFuture return value.
 */
@ExtendWith(MockitoExtension.class)
class ArduinoCompilerServiceTest {

    @Mock private CadetCircuitSaveRepository saveRepository;
    @Mock private CircuitDefinitionRepository circuitDefinitionRepository;
    @Mock private CadetRepository cadetRepository;
    @Mock private GiteaService giteaService;
    @Mock private GiteaActionsService giteaActionsService;

    @InjectMocks private ArduinoCompilerService service;

    private UUID missionId;
    private UUID cadetId;
    private UUID defId;
    private UUID saveId;

    private Cadet cadet;
    private CircuitDefinition def;
    private CadetCircuitSave save;

    private static final String GITEA_REPO_URL =
            "http://gitea:3000/legymernok_admin/circuit-abc-cadet1";
    private static final String REPO_NAME = "circuit-abc-cadet1";
    private static final String FAKE_FQBN = "arduino:avr:uno";
    private static final byte[] FAKE_HEX = ":00000001FF\n".getBytes(StandardCharsets.UTF_8);
    private static final GiteaActionsService.WorkflowRunResult SUCCESS_RUN =
            new GiteaActionsService.WorkflowRunResult(true, "success", 42L);
    private static final GiteaActionsService.WorkflowRunResult FAILURE_RUN =
            new GiteaActionsService.WorkflowRunResult(false, "failure", 43L);

    @BeforeEach
    void setUp() {
        missionId = UUID.randomUUID();
        cadetId   = UUID.randomUUID();
        defId     = UUID.randomUUID();
        saveId    = UUID.randomUUID();

        Mission mission = Mission.builder().id(missionId).build();
        cadet = Cadet.builder().id(cadetId).username("cadet1").build();
        def = CircuitDefinition.builder()
                .id(defId).mission(mission)
                .boardType(BoardType.ARDUINO_UNO)
                .status(CircuitDefinitionStatus.PUBLISHED)
                .build();
        save = CadetCircuitSave.builder()
                .id(saveId).cadet(cadet).circuitDefinition(def)
                .giteaRepoUrl(GITEA_REPO_URL)
                .build();
    }

    // -------------------------------------------------------------------------
    // Pre-compile resolution failures
    // -------------------------------------------------------------------------

    @Test
    void compile_cadetNotFound_throwsResourceNotFoundException() {
        when(cadetRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.compile("ghost", missionId));
    }

    @Test
    void compile_definitionNotPublished_throwsResourceNotFoundException() {
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.compile("cadet1", missionId));
    }

    @Test
    void compile_saveNotFound_throwsResourceNotFoundException() {
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.compile("cadet1", missionId));
    }

    @Test
    void compile_giteaRepoUrlNull_returnsErrorAndSetsCompileErrorStatus() {
        CadetCircuitSave noRepo = CadetCircuitSave.builder()
                .id(saveId).cadet(cadet).circuitDefinition(def)
                .giteaRepoUrl(null)
                .build();
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId))
                .thenReturn(Optional.of(noRepo));
        when(saveRepository.save(any())).thenReturn(noRepo);

        CompileCircuitResponse resp = service.compile("cadet1", missionId).join();

        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorOutput().contains("Gitea"));

        ArgumentCaptor<CadetCircuitSave> captor = ArgumentCaptor.forClass(CadetCircuitSave.class);
        verify(saveRepository).save(captor.capture());
        assertEquals(SimulationStatus.COMPILE_ERROR, captor.getValue().getSimulationStatus());
    }

    @Test
    void compile_unsupportedBoardType_throwsIllegalArgumentException() {
        CircuitDefinition rpiDef = CircuitDefinition.builder()
                .id(defId).mission(Mission.builder().id(missionId).build())
                .boardType(BoardType.RASPBERRY_PI_3)
                .status(CircuitDefinitionStatus.PUBLISHED)
                .build();
        stubCommonLookups(rpiDef);
        // toFqbn throws before reaching dispatch
        assertThrows(IllegalArgumentException.class,
                () -> service.compile("cadet1", missionId));
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void compile_happyPath_returnsSuccessResponseWithBase64Hex() throws IOException {
        stubCommonLookups();
        stubHappyPath();
        when(saveRepository.save(any())).thenReturn(save);

        CompileCircuitResponse resp = service.compile("cadet1", missionId).join();

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getHexBase64());
        assertFalse(resp.getHexBase64().isEmpty());
        assertNull(resp.getErrorOutput());
        assertNotNull(resp.getCompilationTimeMs());
        assertFalse(resp.isFromCache());
    }

    @Test
    void compile_happyPath_fqbnMatchesBoardType() throws IOException {
        stubCommonLookups();
        stubHappyPath();
        when(saveRepository.save(any())).thenReturn(save);

        CompileCircuitResponse resp = service.compile("cadet1", missionId).join();

        assertEquals(FAKE_FQBN, resp.getFqbn());
        assertEquals(BoardType.ARDUINO_UNO, resp.getBoardType());
    }

    @Test
    void compile_happyPath_dispatchedWithCorrectFqbn() throws IOException {
        stubCommonLookups();
        stubHappyPath();
        when(saveRepository.save(any())).thenReturn(save);

        service.compile("cadet1", missionId).join();

        verify(giteaActionsService).dispatchCompileWorkflow(eq(REPO_NAME), eq(FAKE_FQBN), eq(saveId));
    }

    @Test
    void compile_happyPath_setsSaveStatusToNeverRunAndStoresCompilationTime() throws IOException {
        stubCommonLookups();
        stubHappyPath();

        // Capture status snapshots at each save() call
        List<SimulationStatus> capturedStatuses = new ArrayList<>();
        doAnswer(invocation -> {
            CadetCircuitSave s = invocation.getArgument(0);
            capturedStatuses.add(s.getSimulationStatus());
            return s;
        }).when(saveRepository).save(any());

        service.compile("cadet1", missionId).join();

        assertEquals(2, capturedStatuses.size());
        assertEquals(SimulationStatus.COMPILING,  capturedStatuses.get(0));
        assertEquals(SimulationStatus.NEVER_RUN,  capturedStatuses.get(1));

        ArgumentCaptor<CadetCircuitSave> captor = ArgumentCaptor.forClass(CadetCircuitSave.class);
        verify(saveRepository, times(2)).save(captor.capture());
        assertNotNull(captor.getAllValues().get(1).getCompilationTimeMs());
    }

    @Test
    void compile_happyPath_storesCacheKeyAndHexBase64() throws IOException {
        stubCommonLookups();
        stubHappyPath();

        ArgumentCaptor<CadetCircuitSave> captor = ArgumentCaptor.forClass(CadetCircuitSave.class);
        doAnswer(inv -> inv.getArgument(0)).when(saveRepository).save(captor.capture());

        service.compile("cadet1", missionId).join();

        CadetCircuitSave finalSave = captor.getAllValues().get(1);
        assertNotNull(finalSave.getCompiledHexBase64());
        // cacheKey is null because getFileContent returns null (sketch not in Gitea) → buildCacheKey returns null
        assertNull(finalSave.getCompileCacheKey());
    }

    // -------------------------------------------------------------------------
    // Workflow failure and timeout
    // -------------------------------------------------------------------------

    @Test
    void compile_workflowFailed_returnsErrorResponseAndSetsCompileErrorStatus() throws IOException {
        stubCommonLookups();
        stubSketchFetch();
        when(saveRepository.save(any())).thenReturn(save);
        when(giteaActionsService.dispatchCompileWorkflow(any(), any(), any()))
                .thenReturn(Instant.now());
        when(giteaActionsService.pollUntilComplete(any(), any(), anyInt()))
                .thenReturn(FAILURE_RUN);

        CompileCircuitResponse resp = service.compile("cadet1", missionId).join();

        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorOutput().contains("failure"));

        ArgumentCaptor<CadetCircuitSave> captor = ArgumentCaptor.forClass(CadetCircuitSave.class);
        verify(saveRepository, times(2)).save(captor.capture());
        assertEquals(SimulationStatus.COMPILE_ERROR, captor.getAllValues().get(1).getSimulationStatus());
    }

    @Test
    void compile_workflowTimeout_returnsErrorResponseAndSetsCompileErrorStatus() throws IOException {
        stubCommonLookups();
        stubSketchFetch();
        when(saveRepository.save(any())).thenReturn(save);
        when(giteaActionsService.dispatchCompileWorkflow(any(), any(), any()))
                .thenReturn(Instant.now());
        when(giteaActionsService.pollUntilComplete(any(), any(), anyInt()))
                .thenThrow(new RuntimeException("Compile workflow timed out after 300s"));

        CompileCircuitResponse resp = service.compile("cadet1", missionId).join();

        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorOutput().contains("timed out"));
        verify(saveRepository, times(2)).save(any());
    }

    @Test
    void compile_artifactDownloadFails_returnsErrorResponse() throws IOException {
        stubCommonLookups();
        stubSketchFetch();
        when(saveRepository.save(any())).thenReturn(save);
        when(giteaActionsService.dispatchCompileWorkflow(any(), any(), any()))
                .thenReturn(Instant.now());
        when(giteaActionsService.pollUntilComplete(any(), any(), anyInt()))
                .thenReturn(SUCCESS_RUN);
        when(giteaActionsService.downloadHexArtifact(any(), anyLong(), any()))
                .thenThrow(new IOException("Artifact not found"));

        CompileCircuitResponse resp = service.compile("cadet1", missionId).join();

        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorOutput().contains("Artifact not found"));
    }

    // -------------------------------------------------------------------------
    // FQBN mapping — tested indirectly via compile() response
    // -------------------------------------------------------------------------

    @Test
    void compile_boardTypeMega2560_fqbnIsCorrect() throws IOException {
        CircuitDefinition megaDef = CircuitDefinition.builder()
                .id(defId).mission(Mission.builder().id(missionId).build())
                .boardType(BoardType.ARDUINO_MEGA_2560)
                .status(CircuitDefinitionStatus.PUBLISHED)
                .build();
        stubCommonLookups(megaDef);
        stubHappyPath("arduino:avr:mega:cpu=atmega2560");
        when(saveRepository.save(any())).thenReturn(save);

        CompileCircuitResponse resp = service.compile("cadet1", missionId).join();

        assertEquals("arduino:avr:mega:cpu=atmega2560", resp.getFqbn());
    }

    @Test
    void compile_boardTypeEsp8266_fqbnIsCorrect() throws IOException {
        CircuitDefinition espDef = CircuitDefinition.builder()
                .id(defId).mission(Mission.builder().id(missionId).build())
                .boardType(BoardType.ESP8266)
                .status(CircuitDefinitionStatus.PUBLISHED)
                .build();
        stubCommonLookups(espDef);
        stubHappyPath("esp8266:esp8266:nodemcuv2");
        when(saveRepository.save(any())).thenReturn(save);

        CompileCircuitResponse resp = service.compile("cadet1", missionId).join();

        assertEquals("esp8266:esp8266:nodemcuv2", resp.getFqbn());
    }

    @Test
    void compile_boardTypeEsp32_fqbnIsCorrect() throws IOException {
        CircuitDefinition espDef = CircuitDefinition.builder()
                .id(defId).mission(Mission.builder().id(missionId).build())
                .boardType(BoardType.ESP32)
                .status(CircuitDefinitionStatus.PUBLISHED)
                .build();
        stubCommonLookups(espDef);
        stubHappyPath("esp32:esp32:esp32");
        when(saveRepository.save(any())).thenReturn(save);

        CompileCircuitResponse resp = service.compile("cadet1", missionId).join();

        assertEquals("esp32:esp32:esp32", resp.getFqbn());
    }

    // -------------------------------------------------------------------------
    // Repo name extraction — verified via dispatchCompileWorkflow argument
    // -------------------------------------------------------------------------

    @Test
    void compile_extractsRepoNameFromUrlWithoutGitSuffix() throws IOException {
        stubCommonLookups();
        stubHappyPath();
        when(saveRepository.save(any())).thenReturn(save);

        service.compile("cadet1", missionId).join();

        verify(giteaActionsService).dispatchCompileWorkflow(eq(REPO_NAME), any(), any());
    }

    @Test
    void compile_extractsRepoNameFromUrlWithGitSuffix() throws IOException {
        CadetCircuitSave saveWithGit = CadetCircuitSave.builder()
                .id(saveId).cadet(cadet).circuitDefinition(def)
                .giteaRepoUrl(GITEA_REPO_URL + ".git")
                .build();
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId))
                .thenReturn(Optional.of(saveWithGit));
        stubHappyPath();
        when(saveRepository.save(any())).thenReturn(saveWithGit);

        service.compile("cadet1", missionId).join();

        // Must strip .git → same REPO_NAME
        verify(giteaActionsService).dispatchCompileWorkflow(eq(REPO_NAME), any(), any());
    }

    // -------------------------------------------------------------------------
    // Compile cache
    // -------------------------------------------------------------------------

    @Test
    void compile_cacheHit_ownSave_returnsCachedSuccessWithoutDispatching() {
        String sketchContent = "void setup(){} void loop(){}";
        String cacheKey = computeCacheKey(sketchContent, FAKE_FQBN);

        CadetCircuitSave cachedSave = CadetCircuitSave.builder()
                .id(saveId).cadet(cadet).circuitDefinition(def)
                .giteaRepoUrl(GITEA_REPO_URL)
                .compileCacheKey(cacheKey)
                .compiledHexBase64("cachedHexData")
                .build();
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId))
                .thenReturn(Optional.of(cachedSave));
        when(giteaService.getAdminUsername()).thenReturn("legymernok_admin");
        when(giteaService.getFileContent("legymernok_admin", REPO_NAME, "sketch.ino"))
                .thenReturn(sketchContent);

        CompileCircuitResponse resp = service.compile("cadet1", missionId).join();

        assertTrue(resp.isSuccess());
        assertTrue(resp.isFromCache());
        assertEquals("cachedHexData", resp.getHexBase64());
        verify(giteaActionsService, never()).dispatchCompileWorkflow(any(), any(), any());
    }

    @Test
    void compile_cacheHit_globalSave_reusesOtherCadetHexWithoutDispatching() {
        String sketchContent = "void setup(){} void loop(){}";
        String cacheKey = computeCacheKey(sketchContent, FAKE_FQBN);

        // Current cadet's save has no hex yet
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId))
                .thenReturn(Optional.of(save));
        when(giteaService.getAdminUsername()).thenReturn("legymernok_admin");
        when(giteaService.getFileContent("legymernok_admin", REPO_NAME, "sketch.ino"))
                .thenReturn(sketchContent);

        // Another cadet already compiled the same sketch
        CadetCircuitSave otherSave = CadetCircuitSave.builder()
                .id(UUID.randomUUID())
                .compileCacheKey(cacheKey)
                .compiledHexBase64("sharedHexData")
                .build();
        when(saveRepository.findFirstByCompileCacheKeyAndCompiledHexBase64IsNotNull(cacheKey))
                .thenReturn(Optional.of(otherSave));
        when(saveRepository.save(any())).thenReturn(save);

        CompileCircuitResponse resp = service.compile("cadet1", missionId).join();

        assertTrue(resp.isSuccess());
        assertTrue(resp.isFromCache());
        assertEquals("sharedHexData", resp.getHexBase64());
        verify(giteaActionsService, never()).dispatchCompileWorkflow(any(), any(), any());

        ArgumentCaptor<CadetCircuitSave> captor = ArgumentCaptor.forClass(CadetCircuitSave.class);
        verify(saveRepository).save(captor.capture());
        assertEquals("sharedHexData", captor.getValue().getCompiledHexBase64());
        assertEquals(SimulationStatus.NEVER_RUN, captor.getValue().getSimulationStatus());
    }

    @Test
    void compile_sketchNotFoundInGitea_bypassesCacheAndProceeds() throws IOException {
        // If sketch.ino is null (e.g. empty repo), cache key = null → cache bypassed → still compiles
        stubCommonLookups();
        // Cache is bypassed because stubHappyPath → stubSketchFetch returns null sketch
        stubHappyPath();
        when(saveRepository.save(any())).thenReturn(save);

        CompileCircuitResponse resp = service.compile("cadet1", missionId).join();

        assertTrue(resp.isSuccess());
        verify(giteaActionsService).dispatchCompileWorkflow(any(), any(), any());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void stubCommonLookups() {
        stubCommonLookups(def);
    }

    private void stubCommonLookups(CircuitDefinition definition) {
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(definition));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId))
                .thenReturn(Optional.of(save));
    }

    /**
     * Stubs the sketch fetch used for content-based cache key computation.
     * Returns null → cache key = null → cache bypassed → dispatch proceeds.
     * Call this in every test that reaches past toFqbn().
     */
    private void stubSketchFetch() {
        when(giteaService.getAdminUsername()).thenReturn("legymernok_admin");
        when(giteaService.getFileContent(eq("legymernok_admin"), any(), eq("sketch.ino")))
                .thenReturn(null);
    }

    private void stubHappyPath() throws IOException {
        stubHappyPath(FAKE_FQBN);
    }

    private void stubHappyPath(String fqbn) throws IOException {
        stubSketchFetch(); // cache check always fetches sketch.ino; null → cache bypassed
        when(giteaActionsService.dispatchCompileWorkflow(any(), eq(fqbn), any()))
                .thenReturn(Instant.now());
        when(giteaActionsService.pollUntilComplete(any(), any(), anyInt()))
                .thenReturn(SUCCESS_RUN);
        when(giteaActionsService.downloadHexArtifact(any(), eq(42L), any()))
                .thenReturn(FAKE_HEX);
    }

    /** Replicates the content-based SHA-256 cache key logic from ArduinoCompilerService. */
    private String computeCacheKey(String sketchContent, String fqbn) {
        try {
            String input = sketchContent + "|" + fqbn;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
