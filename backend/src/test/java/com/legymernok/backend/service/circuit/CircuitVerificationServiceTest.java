package com.legymernok.backend.service.circuit;

import com.legymernok.backend.dto.circuit.CadetVerificationResultResponse;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.circuit.*;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.circuit.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CircuitVerificationServiceTest {

    @Mock private CadetCircuitSaveRepository saveRepository;
    @Mock private CadetRepository cadetRepository;
    @Mock private CircuitDefinitionRepository circuitDefinitionRepository;
    @Mock private CadetCircuitComponentRepository componentRepository;
    @Mock private CadetCircuitConnectionRepository connectionRepository;
    @Mock private CircuitVerificationCheckRepository checkRepository;
    @Mock private CadetVerificationResultRepository resultRepository;

    @InjectMocks private CircuitVerificationService service;

    private UUID saveId;
    private UUID defId;
    private UUID missionId;
    private UUID cadetId;

    private Cadet cadet;
    private CircuitDefinition def;
    private CadetCircuitSave save;

    @BeforeEach
    void setUp() {
        saveId = UUID.randomUUID();
        defId = UUID.randomUUID();
        missionId = UUID.randomUUID();
        cadetId = UUID.randomUUID();

        Mission mission = Mission.builder().id(missionId).build();
        cadet = Cadet.builder().id(cadetId).username("cadet1").build();
        def = CircuitDefinition.builder()
                .id(defId).mission(mission).boardType(BoardType.ARDUINO_UNO)
                .status(CircuitDefinitionStatus.PUBLISHED).build();
        save = CadetCircuitSave.builder()
                .id(saveId).cadet(cadet).circuitDefinition(def).build();
    }

    // --- verifyTopology(username, missionId) entry point ---

    @Test
    void verifyTopology_byUsername_cadetNotFound_throwsResourceNotFoundException() {
        when(cadetRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.verifyTopology("ghost", missionId));
    }

    @Test
    void verifyTopology_byUsername_definitionNotPublished_throwsResourceNotFoundException() {
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.verifyTopology("cadet1", missionId));
    }

    @Test
    void verifyTopology_byUsername_saveNotFound_throwsResourceNotFoundException() {
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.verifyTopology("cadet1", missionId));
    }

    // --- verifyTopology(UUID saveId) internal ---

    @Test
    void verifyTopology_bySaveId_saveNotFound_throwsResourceNotFoundException() {
        when(saveRepository.findById(saveId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.verifyTopology(saveId));
    }

    // --- CIRCUIT_TOPOLOGY checks ---

    @Test
    void verifyTopology_circuitTopology_labelPresent_passes() {
        UUID compId = UUID.randomUUID();
        CadetCircuitComponent led = CadetCircuitComponent.builder()
                .id(compId).label("LED1").componentType(ComponentType.LED)
                .cadetCircuitSave(save).posX(0).posY(0).build();

        CircuitVerificationCheck check = CircuitVerificationCheck.builder()
                .id(UUID.randomUUID()).circuitDefinition(def)
                .checkType(CheckType.CIRCUIT_TOPOLOGY).labelFrom("LED1")
                .severity(ValidationSeverity.ERROR).i18nKey("check.led1").orderIndex(0).build();

        stubVerify(List.of(led), List.of(), List.of(check));

        List<CadetVerificationResultResponse> results = service.verifyTopology(saveId);

        assertEquals(1, results.size());
        assertTrue(results.get(0).isPassed());
    }

    @Test
    void verifyTopology_circuitTopology_labelMissing_fails() {
        CircuitVerificationCheck check = CircuitVerificationCheck.builder()
                .id(UUID.randomUUID()).circuitDefinition(def)
                .checkType(CheckType.CIRCUIT_TOPOLOGY).labelFrom("MISSING")
                .severity(ValidationSeverity.ERROR).i18nKey("check.missing").orderIndex(0).build();

        stubVerify(List.of(), List.of(), List.of(check));

        List<CadetVerificationResultResponse> results = service.verifyTopology(saveId);

        assertFalse(results.get(0).isPassed());
    }

    @Test
    void verifyTopology_circuitTopology_nullLabel_fails() {
        CircuitVerificationCheck check = CircuitVerificationCheck.builder()
                .id(UUID.randomUUID()).circuitDefinition(def)
                .checkType(CheckType.CIRCUIT_TOPOLOGY).labelFrom(null)
                .severity(ValidationSeverity.WARNING).i18nKey("check.null").orderIndex(0).build();

        stubVerify(List.of(), List.of(), List.of(check));

        assertFalse(service.verifyTopology(saveId).get(0).isPassed());
    }

    // --- PATH_EXISTS checks ---

    @Test
    void verifyTopology_pathExists_directConnection_passes() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        CadetCircuitComponent a = component(aId, "A");
        CadetCircuitComponent b = component(bId, "B");
        CadetCircuitConnection conn = connection(aId, bId);

        CircuitVerificationCheck check = pathCheck("A", "B");

        stubVerify(List.of(a, b), List.of(conn), List.of(check));

        assertTrue(service.verifyTopology(saveId).get(0).isPassed());
    }

    @Test
    void verifyTopology_pathExists_multiHop_passes() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID cId = UUID.randomUUID();
        CadetCircuitComponent a = component(aId, "A");
        CadetCircuitComponent b = component(bId, "B");
        CadetCircuitComponent c = component(cId, "C");
        // A-B and B-C but no direct A-C
        CadetCircuitConnection ab = connection(aId, bId);
        CadetCircuitConnection bc = connection(bId, cId);

        CircuitVerificationCheck check = pathCheck("A", "C");

        stubVerify(List.of(a, b, c), List.of(ab, bc), List.of(check));

        assertTrue(service.verifyTopology(saveId).get(0).isPassed());
    }

    @Test
    void verifyTopology_pathExists_noPath_fails() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID cId = UUID.randomUUID();
        CadetCircuitComponent a = component(aId, "A");
        CadetCircuitComponent b = component(bId, "B");
        CadetCircuitComponent c = component(cId, "C");
        // Only A-B, no path to C
        CadetCircuitConnection ab = connection(aId, bId);

        CircuitVerificationCheck check = pathCheck("A", "C");

        stubVerify(List.of(a, b, c), List.of(ab), List.of(check));

        assertFalse(service.verifyTopology(saveId).get(0).isPassed());
    }

    @Test
    void verifyTopology_pathExists_sameLabel_triviallyPasses() {
        UUID aId = UUID.randomUUID();
        CadetCircuitComponent a = component(aId, "A");

        CircuitVerificationCheck check = pathCheck("A", "A");

        stubVerify(List.of(a), List.of(), List.of(check));

        assertTrue(service.verifyTopology(saveId).get(0).isPassed());
    }

    @Test
    void verifyTopology_pathExists_fromLabelNotOnCanvas_fails() {
        UUID bId = UUID.randomUUID();
        CadetCircuitComponent b = component(bId, "B");

        CircuitVerificationCheck check = pathCheck("GHOST", "B");

        stubVerify(List.of(b), List.of(), List.of(check));

        assertFalse(service.verifyTopology(saveId).get(0).isPassed());
    }

    @Test
    void verifyTopology_pathExists_toLabelNotOnCanvas_fails() {
        UUID aId = UUID.randomUUID();
        CadetCircuitComponent a = component(aId, "A");

        CircuitVerificationCheck check = pathCheck("A", "GHOST");

        stubVerify(List.of(a), List.of(), List.of(check));

        assertFalse(service.verifyTopology(saveId).get(0).isPassed());
    }

    @Test
    void verifyTopology_pathExists_nullLabels_fails() {
        CircuitVerificationCheck check = CircuitVerificationCheck.builder()
                .id(UUID.randomUUID()).circuitDefinition(def)
                .checkType(CheckType.PATH_EXISTS).labelFrom(null).labelTo(null)
                .severity(ValidationSeverity.ERROR).i18nKey("check.path").orderIndex(0).build();

        stubVerify(List.of(), List.of(), List.of(check));

        assertFalse(service.verifyTopology(saveId).get(0).isPassed());
    }

    @Test
    void verifyTopology_pathExists_cycleInGraph_doesNotLoop() {
        // A-B, B-C, C-A but asking for path A→D (not connected)
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        UUID cId = UUID.randomUUID();
        UUID dId = UUID.randomUUID();
        CadetCircuitComponent a = component(aId, "A");
        CadetCircuitComponent b = component(bId, "B");
        CadetCircuitComponent c = component(cId, "C");
        CadetCircuitComponent d = component(dId, "D");

        stubVerify(
                List.of(a, b, c, d),
                List.of(connection(aId, bId), connection(bId, cId), connection(cId, aId)),
                List.of(pathCheck("A", "D"))
        );

        // Must terminate without infinite loop and return false
        assertFalse(service.verifyTopology(saveId).get(0).isPassed());
    }

    // --- Simulation-phase checks (always false) ---

    @Test
    void verifyTopology_gpioBehavior_alwaysFalse() {
        CircuitVerificationCheck check = CircuitVerificationCheck.builder()
                .id(UUID.randomUUID()).circuitDefinition(def)
                .checkType(CheckType.GPIO_BEHAVIOR)
                .severity(ValidationSeverity.ERROR).i18nKey("check.gpio").orderIndex(0).build();

        stubVerify(List.of(), List.of(), List.of(check));

        assertFalse(service.verifyTopology(saveId).get(0).isPassed());
    }

    @Test
    void verifyTopology_serialOutput_alwaysFalse() {
        CircuitVerificationCheck check = CircuitVerificationCheck.builder()
                .id(UUID.randomUUID()).circuitDefinition(def)
                .checkType(CheckType.SERIAL_OUTPUT)
                .severity(ValidationSeverity.ERROR).i18nKey("check.serial").orderIndex(0).build();

        stubVerify(List.of(), List.of(), List.of(check));

        assertFalse(service.verifyTopology(saveId).get(0).isPassed());
    }

    @Test
    void verifyTopology_pwm_alwaysFalse() {
        CircuitVerificationCheck check = CircuitVerificationCheck.builder()
                .id(UUID.randomUUID()).circuitDefinition(def)
                .checkType(CheckType.PWM)
                .severity(ValidationSeverity.ERROR).i18nKey("check.pwm").orderIndex(0).build();

        stubVerify(List.of(), List.of(), List.of(check));

        assertFalse(service.verifyTopology(saveId).get(0).isPassed());
    }

    // --- Result lifecycle ---

    @Test
    void verifyTopology_deletesOldResultsBeforeSavingNew() {
        CircuitVerificationCheck check = CircuitVerificationCheck.builder()
                .id(UUID.randomUUID()).circuitDefinition(def)
                .checkType(CheckType.CIRCUIT_TOPOLOGY).labelFrom("LED1")
                .severity(ValidationSeverity.ERROR).i18nKey("key").orderIndex(0).build();

        UUID compId = UUID.randomUUID();
        CadetCircuitComponent led = component(compId, "LED1");

        stubVerify(List.of(led), List.of(), List.of(check));

        service.verifyTopology(saveId);

        // Old results must be deleted before new ones are saved
        verify(resultRepository).deleteAllByCadetCircuitSaveId(saveId);
        verify(resultRepository).save(any(CadetVerificationResult.class));
    }

    @Test
    void verifyTopology_mixedChecks_returnsAllResults() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        CadetCircuitComponent a = component(aId, "A");
        CadetCircuitComponent b = component(bId, "B");
        CadetCircuitConnection ab = connection(aId, bId);

        CircuitVerificationCheck topologyCheck = CircuitVerificationCheck.builder()
                .id(UUID.randomUUID()).circuitDefinition(def)
                .checkType(CheckType.CIRCUIT_TOPOLOGY).labelFrom("A")
                .severity(ValidationSeverity.ERROR).i18nKey("check.a").orderIndex(0).build();
        CircuitVerificationCheck pathCheck = pathCheck("A", "B");
        pathCheck.setOrderIndex(1);
        CircuitVerificationCheck gpioCheck = CircuitVerificationCheck.builder()
                .id(UUID.randomUUID()).circuitDefinition(def)
                .checkType(CheckType.GPIO_BEHAVIOR)
                .severity(ValidationSeverity.ERROR).i18nKey("check.gpio").orderIndex(2).build();

        stubVerify(List.of(a, b), List.of(ab), List.of(topologyCheck, pathCheck, gpioCheck));

        List<CadetVerificationResultResponse> results = service.verifyTopology(saveId);

        assertEquals(3, results.size());
        assertTrue(results.get(0).isPassed());  // CIRCUIT_TOPOLOGY "A" — found
        assertTrue(results.get(1).isPassed());  // PATH_EXISTS A→B — connected
        assertFalse(results.get(2).isPassed()); // GPIO_BEHAVIOR — always false
    }

    // --- Helpers ---

    /**
     * Sets up all repository stubs needed for verifyTopology(saveId) to run.
     * The resultRepository.save call returns a minimal CadetVerificationResult
     * reflecting the passed value that was computed.
     */
    private void stubVerify(List<CadetCircuitComponent> components,
                            List<CadetCircuitConnection> connections,
                            List<CircuitVerificationCheck> checks) {
        when(saveRepository.findById(saveId)).thenReturn(Optional.of(save));
        when(componentRepository.findAllByCadetCircuitSaveId(saveId)).thenReturn(components);
        when(connectionRepository.findAllByCadetCircuitSaveId(saveId)).thenReturn(connections);
        when(checkRepository.findAllByCircuitDefinitionIdOrderByOrderIndex(defId)).thenReturn(checks);
        when(resultRepository.save(any())).thenAnswer(inv -> {
            CadetVerificationResult r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
    }

    private CadetCircuitComponent component(UUID id, String label) {
        return CadetCircuitComponent.builder()
                .id(id).label(label).componentType(ComponentType.LED)
                .cadetCircuitSave(save).posX(0).posY(0).build();
    }

    private CadetCircuitConnection connection(UUID fromId, UUID toId) {
        return CadetCircuitConnection.builder()
                .id(UUID.randomUUID()).cadetCircuitSave(save)
                .fromComponentId(fromId).fromPinName("A")
                .toComponentId(toId).toPinName("B").build();
    }

    private CircuitVerificationCheck pathCheck(String from, String to) {
        return CircuitVerificationCheck.builder()
                .id(UUID.randomUUID()).circuitDefinition(def)
                .checkType(CheckType.PATH_EXISTS).labelFrom(from).labelTo(to)
                .severity(ValidationSeverity.ERROR).i18nKey("check.path").orderIndex(0).build();
    }
}
