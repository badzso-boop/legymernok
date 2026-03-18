package com.legymernok.backend.service.circuit;

import com.legymernok.backend.dto.circuit.CompileCircuitResponse;
import com.legymernok.backend.exception.ResourceNotFoundException;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArduinoCompilerServiceTest {

    @Mock private CadetCircuitSaveRepository saveRepository;
    @Mock private CircuitDefinitionRepository circuitDefinitionRepository;
    @Mock private CadetRepository cadetRepository;
    @Mock private GiteaService giteaService;
    @Mock private ArduinoCliRunner cliRunner;

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
    private static final String SKETCH = "void setup() {} void loop() {}";

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

        CompileCircuitResponse resp = service.compile("cadet1", missionId);

        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorOutput().contains("Gitea"));

        ArgumentCaptor<CadetCircuitSave> captor = ArgumentCaptor.forClass(CadetCircuitSave.class);
        verify(saveRepository).save(captor.capture());
        assertEquals(SimulationStatus.COMPILE_ERROR, captor.getValue().getSimulationStatus());
    }

    @Test
    void compile_sketchNotFoundInGitea_setsCompilingThenCompileError() {
        stubCommonLookups();
        // Capture the SimulationStatus *at the moment of each save() call* —
        // ArgumentCaptor stores references, not snapshots, so we use doAnswer instead.
        List<SimulationStatus> capturedStatuses = new ArrayList<>();
        doAnswer(invocation -> {
            CadetCircuitSave s = invocation.getArgument(0);
            capturedStatuses.add(s.getSimulationStatus());
            return s;
        }).when(saveRepository).save(any());

        when(giteaService.getAdminUsername()).thenReturn("legymernok_admin");
        when(giteaService.getFileContent("legymernok_admin", REPO_NAME, "sketch.ino"))
                .thenReturn(null);

        CompileCircuitResponse resp = service.compile("cadet1", missionId);

        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorOutput().contains("sketch.ino"));
        assertEquals(2, capturedStatuses.size());
        assertEquals(SimulationStatus.COMPILING,     capturedStatuses.get(0));
        assertEquals(SimulationStatus.COMPILE_ERROR, capturedStatuses.get(1));
    }

    // -------------------------------------------------------------------------
    // Compile path — cliRunner is mocked, no spy needed
    // -------------------------------------------------------------------------

    @Test
    void compile_happyPath_returnsSuccessResponseWithBase64Hex() throws IOException, InterruptedException {
        stubCommonLookups();
        stubSketch();
        when(saveRepository.save(any())).thenReturn(save);

        // Runner writes a fake hex file into outputDir and reports success
        when(cliRunner.run(any(), any(), any())).thenAnswer(invocation -> {
            Path outputDir = invocation.getArgument(2);
            Files.write(outputDir.resolve("sketch.ino.hex"), ":00000001FF\n".getBytes());
            return new ArduinoCliRunner.Result(true, "");
        });

        CompileCircuitResponse resp = service.compile("cadet1", missionId);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getHexBase64());
        assertFalse(resp.getHexBase64().isEmpty());
        assertNull(resp.getErrorOutput());
        assertNotNull(resp.getCompilationTimeMs());
    }

    @Test
    void compile_happyPath_fqbnMatchesBoardType() throws IOException, InterruptedException {
        stubCommonLookups();
        stubSketch();
        when(saveRepository.save(any())).thenReturn(save);
        when(cliRunner.run(any(), any(), any())).thenAnswer(invocation -> {
            Path outputDir = invocation.getArgument(2);
            Files.write(outputDir.resolve("sketch.ino.hex"), ":00000001FF\n".getBytes());
            return new ArduinoCliRunner.Result(true, "");
        });

        CompileCircuitResponse resp = service.compile("cadet1", missionId);

        // BoardType.ARDUINO_UNO → fqbn must be "arduino:avr:uno"
        assertEquals("arduino:avr:uno", resp.getFqbn());
        assertEquals(BoardType.ARDUINO_UNO, resp.getBoardType());
    }

    @Test
    void compile_happyPath_cliRunnerCalledWithCorrectFqbn() throws IOException, InterruptedException {
        stubCommonLookups();
        stubSketch();
        when(saveRepository.save(any())).thenReturn(save);
        when(cliRunner.run(any(), any(), any())).thenAnswer(invocation -> {
            Path outputDir = invocation.getArgument(2);
            Files.write(outputDir.resolve("sketch.ino.hex"), ":00000001FF\n".getBytes());
            return new ArduinoCliRunner.Result(true, "");
        });

        service.compile("cadet1", missionId);

        ArgumentCaptor<String> fqbnCaptor = ArgumentCaptor.forClass(String.class);
        verify(cliRunner).run(fqbnCaptor.capture(), any(), any());
        assertEquals("arduino:avr:uno", fqbnCaptor.getValue());
    }

    @Test
    void compile_happyPath_setsSaveStatusToNeverRunAndStoresCompilationTime()
            throws IOException, InterruptedException {
        stubCommonLookups();
        stubSketch();
        when(saveRepository.save(any())).thenReturn(save);
        when(cliRunner.run(any(), any(), any())).thenAnswer(invocation -> {
            Path outputDir = invocation.getArgument(2);
            Files.write(outputDir.resolve("sketch.ino.hex"), ":00000001FF\n".getBytes());
            return new ArduinoCliRunner.Result(true, "");
        });

        service.compile("cadet1", missionId);

        ArgumentCaptor<CadetCircuitSave> captor = ArgumentCaptor.forClass(CadetCircuitSave.class);
        verify(saveRepository, times(2)).save(captor.capture()); // COMPILING + NEVER_RUN
        CadetCircuitSave finalSave = captor.getAllValues().get(1);
        assertEquals(SimulationStatus.NEVER_RUN, finalSave.getSimulationStatus());
        assertNotNull(finalSave.getCompilationTimeMs());
    }

    @Test
    void compile_compileError_returnsErrorResponseAndSetsCompileErrorStatus()
            throws IOException, InterruptedException {
        stubCommonLookups();
        stubSketch();
        when(saveRepository.save(any())).thenReturn(save);
        when(cliRunner.run(any(), any(), any()))
                .thenReturn(new ArduinoCliRunner.Result(false, "undefined reference to 'foo'"));

        CompileCircuitResponse resp = service.compile("cadet1", missionId);

        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorOutput().contains("undefined reference to 'foo'"));

        ArgumentCaptor<CadetCircuitSave> captor = ArgumentCaptor.forClass(CadetCircuitSave.class);
        verify(saveRepository, times(2)).save(captor.capture());
        CadetCircuitSave finalSave = captor.getAllValues().get(1);
        assertEquals(SimulationStatus.COMPILE_ERROR, finalSave.getSimulationStatus());
        assertEquals("undefined reference to 'foo'", finalSave.getLastCompileError());
    }

    @Test
    void compile_hexFileNotFoundAfterSuccess_returnsErrorResponse()
            throws IOException, InterruptedException {
        stubCommonLookups();
        stubSketch();
        when(saveRepository.save(any())).thenReturn(save);
        // Process reports success but writes no hex file
        when(cliRunner.run(any(), any(), any()))
                .thenReturn(new ArduinoCliRunner.Result(true, ""));

        CompileCircuitResponse resp = service.compile("cadet1", missionId);

        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorOutput().contains(".hex"));
    }

    @Test
    void compile_cliRunnerThrowsIOException_returnsErrorResponse()
            throws IOException, InterruptedException {
        stubCommonLookups();
        stubSketch();
        when(saveRepository.save(any())).thenReturn(save);
        when(cliRunner.run(any(), any(), any()))
                .thenThrow(new IOException("disk full"));

        CompileCircuitResponse resp = service.compile("cadet1", missionId);

        assertFalse(resp.isSuccess());
        assertTrue(resp.getErrorOutput().contains("disk full"));
    }

    // -------------------------------------------------------------------------
    // FQBN mapping — tested indirectly via compile() response
    // -------------------------------------------------------------------------

    @Test
    void compile_boardTypeMega2560_fqbnIsCorrect() throws IOException, InterruptedException {
        CircuitDefinition megaDef = CircuitDefinition.builder()
                .id(defId).mission(Mission.builder().id(missionId).build())
                .boardType(BoardType.ARDUINO_MEGA_2560)
                .status(CircuitDefinitionStatus.PUBLISHED)
                .build();
        stubCommonLookups(megaDef);
        stubSketch();
        when(saveRepository.save(any())).thenReturn(save);
        when(cliRunner.run(any(), any(), any())).thenAnswer(inv -> {
            Path outputDir = inv.getArgument(2);
            Files.write(outputDir.resolve("sketch.ino.hex"), ":00000001FF\n".getBytes());
            return new ArduinoCliRunner.Result(true, "");
        });

        CompileCircuitResponse resp = service.compile("cadet1", missionId);

        assertEquals("arduino:avr:mega:cpu=atmega2560", resp.getFqbn());
    }

    @Test
    void compile_boardTypeEsp8266_fqbnIsCorrect() throws IOException, InterruptedException {
        CircuitDefinition espDef = CircuitDefinition.builder()
                .id(defId).mission(Mission.builder().id(missionId).build())
                .boardType(BoardType.ESP8266)
                .status(CircuitDefinitionStatus.PUBLISHED)
                .build();
        stubCommonLookups(espDef);
        stubSketch();
        when(saveRepository.save(any())).thenReturn(save);
        when(cliRunner.run(any(), any(), any())).thenAnswer(inv -> {
            Path outputDir = inv.getArgument(2);
            Files.write(outputDir.resolve("sketch.ino.hex"), ":00000001FF\n".getBytes());
            return new ArduinoCliRunner.Result(true, "");
        });

        CompileCircuitResponse resp = service.compile("cadet1", missionId);

        assertEquals("esp8266:esp8266:nodemcuv2", resp.getFqbn());
    }

    @Test
    void compile_boardTypeEsp32_fqbnIsCorrect() throws IOException, InterruptedException {
        CircuitDefinition espDef = CircuitDefinition.builder()
                .id(defId).mission(Mission.builder().id(missionId).build())
                .boardType(BoardType.ESP32)
                .status(CircuitDefinitionStatus.PUBLISHED)
                .build();
        stubCommonLookups(espDef);
        stubSketch();
        when(saveRepository.save(any())).thenReturn(save);
        when(cliRunner.run(any(), any(), any())).thenAnswer(inv -> {
            Path outputDir = inv.getArgument(2);
            Files.write(outputDir.resolve("sketch.ino.hex"), ":00000001FF\n".getBytes());
            return new ArduinoCliRunner.Result(true, "");
        });

        CompileCircuitResponse resp = service.compile("cadet1", missionId);

        assertEquals("esp32:esp32:esp32", resp.getFqbn());
    }

    @Test
    void compile_unsupportedBoardType_returnsErrorResponse() {
        CircuitDefinition rpiDef = CircuitDefinition.builder()
                .id(defId).mission(Mission.builder().id(missionId).build())
                .boardType(BoardType.RASPBERRY_PI_3)
                .status(CircuitDefinitionStatus.PUBLISHED)
                .build();
        stubCommonLookups(rpiDef);
        stubSketch();
        when(saveRepository.save(any())).thenReturn(save);

        // toFqbn throws IllegalArgumentException — doCompile catches it as RuntimeException
        // which bubbles up (not caught by the IOException|InterruptedException handler)
        assertThrows(IllegalArgumentException.class,
                () -> service.compile("cadet1", missionId));
    }

    // -------------------------------------------------------------------------
    // Repo name extraction — tested indirectly via getFileContent argument
    // -------------------------------------------------------------------------

    @Test
    void compile_extractsRepoNameFromUrlWithoutGitSuffix() {
        stubCommonLookups();
        when(saveRepository.save(any())).thenReturn(save);
        when(giteaService.getAdminUsername()).thenReturn("legymernok_admin");
        when(giteaService.getFileContent("legymernok_admin", REPO_NAME, "sketch.ino"))
                .thenReturn(null); // sketch missing triggers early return — enough to verify arg

        service.compile("cadet1", missionId);

        verify(giteaService).getFileContent("legymernok_admin", "circuit-abc-cadet1", "sketch.ino");
    }

    @Test
    void compile_extractsRepoNameFromUrlWithGitSuffix() throws IOException, InterruptedException {
        CadetCircuitSave saveWithGit = CadetCircuitSave.builder()
                .id(saveId).cadet(cadet).circuitDefinition(def)
                .giteaRepoUrl(GITEA_REPO_URL + ".git")
                .build();
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId))
                .thenReturn(Optional.of(saveWithGit));
        when(saveRepository.save(any())).thenReturn(saveWithGit);
        when(giteaService.getAdminUsername()).thenReturn("legymernok_admin");
        when(giteaService.getFileContent("legymernok_admin", REPO_NAME, "sketch.ino"))
                .thenReturn(null);

        service.compile("cadet1", missionId);

        // Must strip .git → same REPO_NAME
        verify(giteaService).getFileContent("legymernok_admin", REPO_NAME, "sketch.ino");
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

    private void stubSketch() {
        when(giteaService.getAdminUsername()).thenReturn("legymernok_admin");
        when(giteaService.getFileContent("legymernok_admin", REPO_NAME, "sketch.ino"))
                .thenReturn(SKETCH);
    }
}
