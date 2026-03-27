package com.legymernok.backend.service.circuit;

import com.legymernok.backend.dto.circuit.*;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.circuit.*;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.mission.MissionType;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.circuit.*;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CadetCircuitServiceTest {

    @Mock private CadetCircuitSaveRepository saveRepository;
    @Mock private CadetCircuitComponentRepository componentRepository;
    @Mock private CadetCircuitComponentPropertyRepository propertyRepository;
    @Mock private CadetCircuitConnectionRepository connectionRepository;
    @Mock private CadetVerificationResultRepository verificationResultRepository;
    @Mock private CircuitDefinitionRepository circuitDefinitionRepository;
    @Mock private CircuitDefComponentRepository defComponentRepository;
    @Mock private CircuitDefComponentPropertyRepository defPropertyRepository;
    @Mock private UnitOfMeasureRepository unitOfMeasureRepository;
    @Mock private CadetRepository cadetRepository;
    @Mock private com.legymernok.backend.repository.ConnectTables.CadetMissionRepository cadetMissionRepository;
    @Mock private UnitOfMeasureService unitOfMeasureService;
    @Mock private CircuitVerificationCheckRepository checkRepository;
    @Mock private GiteaService giteaService;

    @InjectMocks private CadetCircuitService service;

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

    // --- Stub toResponse calls ---

    private void stubToResponse() {
        when(componentRepository.findAllByCadetCircuitSaveId(saveId)).thenReturn(List.of());
        when(connectionRepository.findAllByCadetCircuitSaveId(saveId)).thenReturn(List.of());
        when(verificationResultRepository.findAllByCadetCircuitSaveId(saveId)).thenReturn(List.of());
        when(checkRepository.findAllByCircuitDefinitionIdOrderByOrderIndex(defId)).thenReturn(List.of());
    }

    // --- startCircuitMission ---

    @Test
    void startCircuitMission_firstTime_createsSaveAndReturns() {
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId)).thenReturn(Optional.empty());
        when(saveRepository.save(any())).thenReturn(save);
        when(cadetMissionRepository.findByCadetIdAndMissionId(cadetId, missionId)).thenReturn(Optional.empty());
        when(cadetMissionRepository.save(any())).thenReturn(null);
        when(defComponentRepository.findAllByCircuitDefinitionId(defId)).thenReturn(List.of());
        stubToResponse();

        CadetCircuitSaveResponse result = service.startCircuitMission("cadet1", missionId);

        assertNotNull(result);
        assertEquals(saveId, result.getId());
        verify(saveRepository).save(any(CadetCircuitSave.class));
    }

    @Test
    void startCircuitMission_idempotent_returnsExistingSave() {
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId)).thenReturn(Optional.of(save));
        stubToResponse();

        CadetCircuitSaveResponse result = service.startCircuitMission("cadet1", missionId);

        assertEquals(saveId, result.getId());
        // save() must NOT be called again
        verify(saveRepository, never()).save(any());
    }

    @Test
    void startCircuitMission_cadetNotFound_throwsResourceNotFoundException() {
        when(cadetRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.startCircuitMission("ghost", missionId));
    }

    @Test
    void startCircuitMission_definitionNotPublished_throwsResourceNotFoundException() {
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.startCircuitMission("cadet1", missionId));
    }

    @Test
    void startCircuitMission_copiesTemplateComponentsAndProperties() {
        UUID templateCompId = UUID.randomUUID();
        UUID templatePropUnitId = UUID.randomUUID();
        UnitOfMeasure volt = UnitOfMeasure.builder().id(templatePropUnitId).name("Volt").symbol("V").build();

        CircuitDefComponent templateComp = CircuitDefComponent.builder()
                .id(templateCompId).circuitDefinition(def)
                .componentType(ComponentType.RESISTOR).label("R1").posX(5).posY(10).build();
        CircuitDefComponentProperty templateProp = CircuitDefComponentProperty.builder()
                .id(UUID.randomUUID()).component(templateComp)
                .propertyKey("resistance").propertyValue("330").unitOfMeasure(volt).build();

        UUID savedCadetCompId = UUID.randomUUID();
        CadetCircuitComponent savedCadetComp = CadetCircuitComponent.builder()
                .id(savedCadetCompId).cadetCircuitSave(save)
                .componentType(ComponentType.RESISTOR).label("R1").posX(5).posY(10).build();

        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId)).thenReturn(Optional.empty());
        when(saveRepository.save(any())).thenReturn(save);
        when(cadetMissionRepository.findByCadetIdAndMissionId(cadetId, missionId)).thenReturn(Optional.empty());
        when(cadetMissionRepository.save(any())).thenReturn(null);
        when(defComponentRepository.findAllByCircuitDefinitionId(defId)).thenReturn(List.of(templateComp));
        when(componentRepository.save(any())).thenReturn(savedCadetComp);
        when(defPropertyRepository.findAllByComponentId(templateCompId)).thenReturn(List.of(templateProp));
        // toResponse stubs
        when(componentRepository.findAllByCadetCircuitSaveId(saveId)).thenReturn(List.of());
        when(connectionRepository.findAllByCadetCircuitSaveId(saveId)).thenReturn(List.of());
        when(verificationResultRepository.findAllByCadetCircuitSaveId(saveId)).thenReturn(List.of());
        when(checkRepository.findAllByCircuitDefinitionIdOrderByOrderIndex(defId)).thenReturn(List.of());

        service.startCircuitMission("cadet1", missionId);

        // One CadetCircuitComponent saved
        verify(componentRepository).save(any(CadetCircuitComponent.class));
        // One CadetCircuitComponentProperty saved (with the unit copied over)
        ArgumentCaptor<CadetCircuitComponentProperty> propCaptor =
                ArgumentCaptor.forClass(CadetCircuitComponentProperty.class);
        verify(propertyRepository).save(propCaptor.capture());
        assertEquals("resistance", propCaptor.getValue().getPropertyKey());
        assertEquals("330", propCaptor.getValue().getPropertyValue());
        assertEquals(volt, propCaptor.getValue().getUnitOfMeasure());
    }

    // --- getCadetCircuitSave ---

    @Test
    void getCadetCircuitSave_happyPath_returnsResponse() {
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId)).thenReturn(Optional.of(save));
        stubToResponse();

        CadetCircuitSaveResponse result = service.getCadetCircuitSave("cadet1", missionId);

        assertEquals(saveId, result.getId());
        assertEquals(defId, result.getCircuitDefinitionId());
    }

    @Test
    void getCadetCircuitSave_saveNotFound_throwsResourceNotFoundException() {
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getCadetCircuitSave("cadet1", missionId));
    }

    // --- saveCanvas ---

    @Test
    void saveCanvas_happyPath_deletesOldAndCreatesNew() {
        UpsertCircuitDefComponentRequest compReq = makeCompRequest("LED1", ComponentType.LED);
        SaveCadetCircuitRequest request = new SaveCadetCircuitRequest();
        request.setComponents(List.of(compReq));
        request.setConnections(List.of());

        UUID cadetCompId = UUID.randomUUID();
        CadetCircuitComponent savedComp = CadetCircuitComponent.builder()
                .id(cadetCompId).cadetCircuitSave(save)
                .componentType(ComponentType.LED).label("LED1").posX(0).posY(0).build();

        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId)).thenReturn(Optional.of(save));
        when(componentRepository.save(any())).thenReturn(savedComp);
        stubToResponse();

        service.saveCanvas("cadet1", missionId, request);

        verify(propertyRepository).deleteAllByCadetCircuitSaveId(saveId);
        verify(componentRepository).deleteAllByCadetCircuitSaveId(saveId);
        verify(connectionRepository).deleteAllByCadetCircuitSaveId(saveId);
        verify(componentRepository).save(any(CadetCircuitComponent.class));
    }

    @Test
    void saveCanvas_duplicateLabels_throwsResourceConflictException() {
        UpsertCircuitDefComponentRequest c1 = makeCompRequest("LED1", ComponentType.LED);
        UpsertCircuitDefComponentRequest c2 = makeCompRequest("LED1", ComponentType.RESISTOR);

        SaveCadetCircuitRequest request = new SaveCadetCircuitRequest();
        request.setComponents(List.of(c1, c2));
        request.setConnections(List.of());

        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId)).thenReturn(Optional.of(save));

        assertThrows(ResourceConflictException.class,
                () -> service.saveCanvas("cadet1", missionId, request));
        verify(componentRepository, never()).save(any());
    }

    @Test
    void saveCanvas_saveNotStarted_throwsResourceNotFoundException() {
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId)).thenReturn(Optional.empty());

        SaveCadetCircuitRequest request = new SaveCadetCircuitRequest();
        request.setComponents(List.of());
        request.setConnections(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> service.saveCanvas("cadet1", missionId, request));
    }

    @Test
    void saveCanvas_definitionNotPublished_throwsResourceNotFoundException() {
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        SaveCadetCircuitRequest request = new SaveCadetCircuitRequest();
        request.setComponents(List.of());
        request.setConnections(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> service.saveCanvas("cadet1", missionId, request));
    }

    @Test
    void saveCanvas_unknownLabelInConnection_silentlySkipped() {
        UpsertCircuitDefComponentRequest compReq = makeCompRequest("LED1", ComponentType.LED);
        UpsertCircuitDefConnectionRequest connReq = new UpsertCircuitDefConnectionRequest();
        connReq.setFromLabel("LED1");
        connReq.setFromPinName("ANODE");
        connReq.setToLabel("GHOST");
        connReq.setToPinName("PIN");

        SaveCadetCircuitRequest request = new SaveCadetCircuitRequest();
        request.setComponents(List.of(compReq));
        request.setConnections(List.of(connReq));

        UUID compId = UUID.randomUUID();
        CadetCircuitComponent savedComp = CadetCircuitComponent.builder()
                .id(compId).cadetCircuitSave(save)
                .componentType(ComponentType.LED).label("LED1").posX(0).posY(0).build();

        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId)).thenReturn(Optional.of(save));
        when(componentRepository.save(any())).thenReturn(savedComp);
        stubToResponse();

        service.saveCanvas("cadet1", missionId, request);

        verify(connectionRepository, never()).save(any());
    }

    @Test
    void saveCanvas_connectionDirectionNormalized() {
        // 7fff... > 0000...0001 in Java signed long comparison (UUID.compareTo uses signed longs)
        UUID bigId  = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
        UUID smallId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        UpsertCircuitDefComponentRequest ledReq = makeCompRequest("LED1", ComponentType.LED);
        UpsertCircuitDefComponentRequest rReq   = makeCompRequest("R1", ComponentType.RESISTOR);

        UpsertCircuitDefConnectionRequest connReq = new UpsertCircuitDefConnectionRequest();
        connReq.setFromLabel("LED1");   // gets bigId
        connReq.setFromPinName("ANODE");
        connReq.setToLabel("R1");       // gets smallId
        connReq.setToPinName("PIN1");

        SaveCadetCircuitRequest request = new SaveCadetCircuitRequest();
        request.setComponents(List.of(ledReq, rReq));
        request.setConnections(List.of(connReq));

        CadetCircuitComponent ledComp = CadetCircuitComponent.builder()
                .id(bigId).cadetCircuitSave(save)
                .componentType(ComponentType.LED).label("LED1").posX(0).posY(0).build();
        CadetCircuitComponent rComp = CadetCircuitComponent.builder()
                .id(smallId).cadetCircuitSave(save)
                .componentType(ComponentType.RESISTOR).label("R1").posX(10).posY(0).build();

        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(circuitDefinitionRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, defId)).thenReturn(Optional.of(save));
        when(componentRepository.save(any()))
                .thenReturn(ledComp)
                .thenReturn(rComp);
        stubToResponse();

        service.saveCanvas("cadet1", missionId, request);

        ArgumentCaptor<CadetCircuitConnection> captor = ArgumentCaptor.forClass(CadetCircuitConnection.class);
        verify(connectionRepository).save(captor.capture());
        CadetCircuitConnection saved = captor.getValue();

        assertEquals(smallId, saved.getFromComponentId());
        assertEquals("PIN1", saved.getFromPinName());
        assertEquals(bigId, saved.getToComponentId());
        assertEquals("ANODE", saved.getToPinName());
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
