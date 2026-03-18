package com.legymernok.backend.service.circuit;

import com.legymernok.backend.dto.circuit.*;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.circuit.*;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.repository.circuit.*;
import com.legymernok.backend.repository.mission.MissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class CircuitDefinitionServiceTest {

    @Mock private CircuitDefinitionRepository circuitDefinitionRepository;
    @Mock private CircuitDefComponentRepository componentRepository;
    @Mock private CircuitDefComponentPropertyRepository propertyRepository;
    @Mock private CircuitDefConnectionRepository connectionRepository;
    @Mock private CircuitVerificationCheckRepository checkRepository;
    @Mock private UnitOfMeasureRepository unitOfMeasureRepository;
    @Mock private MissionRepository missionRepository;
    @Mock private UnitOfMeasureService unitOfMeasureService;
    @Mock private CadetCircuitSaveRepository cadetCircuitSaveRepository;
    @Mock private CadetCircuitComponentRepository cadetCircuitComponentRepository;
    @Mock private CadetCircuitComponentPropertyRepository cadetCircuitComponentPropertyRepository;
    @Mock private CadetCircuitConnectionRepository cadetCircuitConnectionRepository;
    @Mock private CadetVerificationResultRepository cadetVerificationResultRepository;

    @InjectMocks private CircuitDefinitionService service;

    private UUID defId;
    private UUID missionId;
    private Mission mission;
    private CircuitDefinition def;

    @BeforeEach
    void setUp() {
        defId = UUID.randomUUID();
        missionId = UUID.randomUUID();
        mission = Mission.builder().id(missionId).build();
        def = CircuitDefinition.builder()
                .id(defId).mission(mission).boardType(BoardType.ARDUINO_UNO).build();
    }

    // --- Stub toResponse calls with empty lists ---

    private void stubToResponse(UUID circuitDefId) {
        when(componentRepository.findAllByCircuitDefinitionId(circuitDefId)).thenReturn(List.of());
        when(connectionRepository.findAllByCircuitDefinitionId(circuitDefId)).thenReturn(List.of());
        when(checkRepository.findAllByCircuitDefinitionIdOrderByOrderIndex(circuitDefId)).thenReturn(List.of());
    }

    // --- createCircuitDefinition ---

    @Test
    void createCircuitDefinition_happyPath_savesAndReturns() {
        CreateCircuitDefinitionRequest request = new CreateCircuitDefinitionRequest();
        request.setMissionId(missionId);
        request.setBoardType(BoardType.ARDUINO_UNO);

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(circuitDefinitionRepository.save(any())).thenReturn(def);
        stubToResponse(defId);

        CircuitDefinitionResponse result = service.createCircuitDefinition(request);

        assertEquals(defId, result.getId());
        assertEquals(missionId, result.getMissionId());
        verify(circuitDefinitionRepository).save(any(CircuitDefinition.class));
    }

    @Test
    void createCircuitDefinition_missionNotFound_throwsResourceNotFoundException() {
        CreateCircuitDefinitionRequest request = new CreateCircuitDefinitionRequest();
        request.setMissionId(missionId);
        request.setBoardType(BoardType.ARDUINO_UNO);

        when(missionRepository.findById(missionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createCircuitDefinition(request));
        verify(circuitDefinitionRepository, never()).save(any());
    }

    // --- getCircuitDefinition ---

    @Test
    void getCircuitDefinition_happyPath_returnsResponse() {
        when(circuitDefinitionRepository.findById(defId)).thenReturn(Optional.of(def));
        stubToResponse(defId);

        CircuitDefinitionResponse result = service.getCircuitDefinition(defId);

        assertEquals(defId, result.getId());
        assertEquals(BoardType.ARDUINO_UNO, result.getBoardType());
    }

    @Test
    void getCircuitDefinition_notFound_throwsResourceNotFoundException() {
        when(circuitDefinitionRepository.findById(defId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getCircuitDefinition(defId));
    }

    // --- getPublishedByMission ---

    @Test
    void getPublishedByMission_happyPath_returnsPublished() {
        CircuitDefinition published = CircuitDefinition.builder()
                .id(defId).mission(mission).boardType(BoardType.ARDUINO_UNO)
                .status(CircuitDefinitionStatus.PUBLISHED).build();

        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(published));
        stubToResponse(defId);

        CircuitDefinitionResponse result = service.getPublishedByMission(missionId);

        assertEquals(CircuitDefinitionStatus.PUBLISHED, result.getStatus());
    }

    @Test
    void getPublishedByMission_notFound_throwsResourceNotFoundException() {
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getPublishedByMission(missionId));
    }

    // --- getAllByMission ---

    @Test
    void getAllByMission_returnsMappedList() {
        when(circuitDefinitionRepository.findAllByMissionId(missionId)).thenReturn(List.of(def));
        stubToResponse(defId);

        List<CircuitDefinitionResponse> result = service.getAllByMission(missionId);

        assertEquals(1, result.size());
        assertEquals(defId, result.get(0).getId());
    }

    @Test
    void getAllByMission_empty_returnsEmptyList() {
        when(circuitDefinitionRepository.findAllByMissionId(missionId)).thenReturn(List.of());

        assertTrue(service.getAllByMission(missionId).isEmpty());
    }

    // --- saveCanvas ---

    @Test
    void saveCanvas_happyPath_deletesOldAndCreatesNew() {
        UpsertCircuitDefComponentRequest compReq = makeCompRequest("LED1", ComponentType.LED);
        SaveCircuitDefinitionRequest request = new SaveCircuitDefinitionRequest();
        request.setComponents(List.of(compReq));
        request.setConnections(List.of());

        UUID compId = UUID.randomUUID();
        CircuitDefComponent savedComp = CircuitDefComponent.builder()
                .id(compId).circuitDefinition(def).componentType(ComponentType.LED)
                .label("LED1").posX(0).posY(0).build();

        when(circuitDefinitionRepository.findById(defId)).thenReturn(Optional.of(def));
        when(componentRepository.save(any())).thenReturn(savedComp);
        stubToResponse(defId);

        service.saveCanvas(defId, request);

        verify(propertyRepository).deleteAllByCircuitDefinitionId(defId);
        verify(componentRepository).deleteAllByCircuitDefinitionId(defId);
        verify(connectionRepository).deleteAllByCircuitDefinitionId(defId);
        verify(componentRepository).save(any(CircuitDefComponent.class));
    }

    @Test
    void saveCanvas_definitionNotFound_throwsResourceNotFoundException() {
        when(circuitDefinitionRepository.findById(defId)).thenReturn(Optional.empty());

        SaveCircuitDefinitionRequest request = new SaveCircuitDefinitionRequest();
        request.setComponents(List.of());
        request.setConnections(List.of());

        assertThrows(ResourceNotFoundException.class, () -> service.saveCanvas(defId, request));
        verify(componentRepository, never()).save(any());
    }

    @Test
    void saveCanvas_duplicateLabels_throwsResourceConflictException() {
        UpsertCircuitDefComponentRequest comp1 = makeCompRequest("LED1", ComponentType.LED);
        UpsertCircuitDefComponentRequest comp2 = makeCompRequest("LED1", ComponentType.RESISTOR);

        SaveCircuitDefinitionRequest request = new SaveCircuitDefinitionRequest();
        request.setComponents(List.of(comp1, comp2));
        request.setConnections(List.of());

        when(circuitDefinitionRepository.findById(defId)).thenReturn(Optional.of(def));

        assertThrows(ResourceConflictException.class, () -> service.saveCanvas(defId, request));
        verify(componentRepository, never()).save(any());
    }

    @Test
    void saveCanvas_unknownLabelInConnection_silentlySkipped() {
        UpsertCircuitDefComponentRequest compReq = makeCompRequest("LED1", ComponentType.LED);
        UpsertCircuitDefConnectionRequest connReq = new UpsertCircuitDefConnectionRequest();
        connReq.setFromLabel("LED1");
        connReq.setFromPinName("ANODE");
        connReq.setToLabel("GHOST"); // does not exist
        connReq.setToPinName("PIN1");

        SaveCircuitDefinitionRequest request = new SaveCircuitDefinitionRequest();
        request.setComponents(List.of(compReq));
        request.setConnections(List.of(connReq));

        UUID compId = UUID.randomUUID();
        CircuitDefComponent savedComp = CircuitDefComponent.builder()
                .id(compId).circuitDefinition(def).componentType(ComponentType.LED)
                .label("LED1").posX(0).posY(0).build();

        when(circuitDefinitionRepository.findById(defId)).thenReturn(Optional.of(def));
        when(componentRepository.save(any())).thenReturn(savedComp);
        stubToResponse(defId);

        service.saveCanvas(defId, request);

        // Connection with unknown label must be silently dropped
        verify(connectionRepository, never()).save(any());
    }

    @Test
    void saveCanvas_connectionDirectionNormalized() {
        // 7fff... > 0000...0001 in Java signed long comparison (UUID.compareTo uses signed longs)
        UUID bigId  = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
        UUID smallId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        UpsertCircuitDefComponentRequest ledReq = makeCompRequest("LED1", ComponentType.LED);
        UpsertCircuitDefComponentRequest rReq   = makeCompRequest("R1", ComponentType.RESISTOR);

        // Connection: from LED1 (will get bigId) → R1 (will get smallId)
        UpsertCircuitDefConnectionRequest connReq = new UpsertCircuitDefConnectionRequest();
        connReq.setFromLabel("LED1");
        connReq.setFromPinName("ANODE");
        connReq.setToLabel("R1");
        connReq.setToPinName("PIN1");

        SaveCircuitDefinitionRequest request = new SaveCircuitDefinitionRequest();
        request.setComponents(List.of(ledReq, rReq));
        request.setConnections(List.of(connReq));

        CircuitDefComponent ledComp = CircuitDefComponent.builder()
                .id(bigId).circuitDefinition(def).componentType(ComponentType.LED)
                .label("LED1").posX(0).posY(0).build();
        CircuitDefComponent rComp = CircuitDefComponent.builder()
                .id(smallId).circuitDefinition(def).componentType(ComponentType.RESISTOR)
                .label("R1").posX(10).posY(0).build();

        when(circuitDefinitionRepository.findById(defId)).thenReturn(Optional.of(def));
        when(componentRepository.save(any()))
                .thenReturn(ledComp)
                .thenReturn(rComp);
        stubToResponse(defId);

        service.saveCanvas(defId, request);

        ArgumentCaptor<CircuitDefConnection> captor = ArgumentCaptor.forClass(CircuitDefConnection.class);
        verify(connectionRepository).save(captor.capture());
        CircuitDefConnection saved = captor.getValue();

        // smallId must be "from" (normalized)
        assertEquals(smallId, saved.getFromComponentId());
        assertEquals("PIN1", saved.getFromPinName());
        assertEquals(bigId, saved.getToComponentId());
        assertEquals("ANODE", saved.getToPinName());
    }

    // --- addVerificationCheck ---

    @Test
    void addVerificationCheck_happyPath_savesAndReturns() {
        CreateCircuitVerificationCheckRequest request = new CreateCircuitVerificationCheckRequest();
        request.setCheckType(CheckType.CIRCUIT_TOPOLOGY);
        request.setLabelFrom("LED1");
        request.setSeverity(ValidationSeverity.ERROR);
        request.setI18nKey("check.led1.exists");
        request.setOrderIndex(0);

        CircuitVerificationCheck saved = CircuitVerificationCheck.builder()
                .id(UUID.randomUUID()).circuitDefinition(def)
                .checkType(CheckType.CIRCUIT_TOPOLOGY).labelFrom("LED1")
                .severity(ValidationSeverity.ERROR).i18nKey("check.led1.exists").orderIndex(0)
                .build();

        when(circuitDefinitionRepository.findById(defId)).thenReturn(Optional.of(def));
        when(checkRepository.save(any())).thenReturn(saved);

        CircuitVerificationCheckResponse result = service.addVerificationCheck(defId, request);

        assertEquals(CheckType.CIRCUIT_TOPOLOGY, result.getCheckType());
        assertEquals("LED1", result.getLabelFrom());
        verify(checkRepository).save(any(CircuitVerificationCheck.class));
    }

    @Test
    void addVerificationCheck_definitionNotFound_throwsResourceNotFoundException() {
        when(circuitDefinitionRepository.findById(defId)).thenReturn(Optional.empty());

        CreateCircuitVerificationCheckRequest request = new CreateCircuitVerificationCheckRequest();
        request.setCheckType(CheckType.PATH_EXISTS);
        request.setSeverity(ValidationSeverity.ERROR);
        request.setI18nKey("key");
        request.setOrderIndex(0);

        assertThrows(ResourceNotFoundException.class, () -> service.addVerificationCheck(defId, request));
        verify(checkRepository, never()).save(any());
    }

    // --- deleteVerificationCheck ---

    @Test
    void deleteVerificationCheck_happyPath_deletes() {
        UUID checkId = UUID.randomUUID();
        when(checkRepository.existsById(checkId)).thenReturn(true);

        service.deleteVerificationCheck(checkId);

        verify(checkRepository).deleteById(checkId);
    }

    @Test
    void deleteVerificationCheck_notFound_throwsResourceNotFoundException() {
        UUID checkId = UUID.randomUUID();
        when(checkRepository.existsById(checkId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.deleteVerificationCheck(checkId));
        verify(checkRepository, never()).deleteById(any());
    }

    // --- publishCircuitDefinition ---

    @Test
    void publishCircuitDefinition_cascadesCadetDeletesAndSetsPublished() {
        when(circuitDefinitionRepository.findById(defId)).thenReturn(Optional.of(def));
        when(circuitDefinitionRepository.save(def)).thenReturn(def);
        stubToResponse(defId);

        service.publishCircuitDefinition(defId);

        verify(cadetVerificationResultRepository).deleteAllByCircuitDefinitionId(defId);
        verify(cadetCircuitConnectionRepository).deleteAllByCircuitDefinitionId(defId);
        verify(cadetCircuitComponentPropertyRepository).deleteAllByCircuitDefinitionId(defId);
        verify(cadetCircuitComponentRepository).deleteAllByCircuitDefinitionId(defId);
        verify(cadetCircuitSaveRepository).deleteAllByCircuitDefinitionId(defId);
        assertEquals(CircuitDefinitionStatus.PUBLISHED, def.getStatus());
    }

    @Test
    void publishCircuitDefinition_notFound_throwsResourceNotFoundException() {
        when(circuitDefinitionRepository.findById(defId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.publishCircuitDefinition(defId));
        verify(circuitDefinitionRepository, never()).save(any());
    }

    // --- deleteCircuitDefinition ---

    @Test
    void deleteCircuitDefinition_cascadesAllThenDeletesDefinition() {
        when(circuitDefinitionRepository.findById(defId)).thenReturn(Optional.of(def));

        service.deleteCircuitDefinition(defId);

        // Cadet data deleted first
        verify(cadetVerificationResultRepository).deleteAllByCircuitDefinitionId(defId);
        verify(cadetCircuitConnectionRepository).deleteAllByCircuitDefinitionId(defId);
        verify(cadetCircuitComponentPropertyRepository).deleteAllByCircuitDefinitionId(defId);
        verify(cadetCircuitComponentRepository).deleteAllByCircuitDefinitionId(defId);
        verify(cadetCircuitSaveRepository).deleteAllByCircuitDefinitionId(defId);
        // Definition data deleted
        verify(checkRepository).deleteAllByCircuitDefinitionId(defId);
        verify(propertyRepository).deleteAllByCircuitDefinitionId(defId);
        verify(componentRepository).deleteAllByCircuitDefinitionId(defId);
        verify(connectionRepository).deleteAllByCircuitDefinitionId(defId);
        verify(circuitDefinitionRepository).delete(def);
    }

    @Test
    void deleteCircuitDefinition_notFound_throwsResourceNotFoundException() {
        when(circuitDefinitionRepository.findById(defId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteCircuitDefinition(defId));
        verify(circuitDefinitionRepository, never()).delete(any());
    }

    // --- Helpers ---

    private UpsertCircuitDefComponentRequest makeCompRequest(String label, ComponentType type) {
        UpsertCircuitDefComponentRequest req = new UpsertCircuitDefComponentRequest();
        req.setLabel(label);
        req.setComponentType(type);
        req.setPosX(0);
        req.setPosY(0);
        return req;
    }
}
