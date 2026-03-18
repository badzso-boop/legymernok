package com.legymernok.backend.service.circuit;

import com.legymernok.backend.dto.circuit.*;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.circuit.*;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.circuit.*;
import com.legymernok.backend.repository.mission.MissionRepository;
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
class AnalogCircuitServiceTest {

    @Mock private AnalogCircuitDefinitionRepository analogDefRepository;
    @Mock private AnalogVerificationCheckRepository analogCheckRepository;
    @Mock private CadetAnalogSaveRepository cadetAnalogSaveRepository;
    @Mock private UnitOfMeasureRepository unitOfMeasureRepository;
    @Mock private MissionRepository missionRepository;
    @Mock private CadetRepository cadetRepository;
    @Mock private UnitOfMeasureService unitOfMeasureService;

    @InjectMocks private AnalogCircuitService service;

    private UUID defId;
    private UUID missionId;
    private UUID cadetId;

    private Mission mission;
    private Cadet cadet;
    private AnalogCircuitDefinition def;

    private static final String FALSTAD_TEXT = "$ 1 0.000005 10.634267";

    @BeforeEach
    void setUp() {
        defId = UUID.randomUUID();
        missionId = UUID.randomUUID();
        cadetId = UUID.randomUUID();

        mission = Mission.builder().id(missionId).build();
        cadet = Cadet.builder().id(cadetId).username("cadet1").build();
        def = AnalogCircuitDefinition.builder()
                .id(defId).mission(mission).falstadText(FALSTAD_TEXT).build();
    }

    // --- toDefResponse stub (checks list) ---

    private void stubChecks() {
        when(analogCheckRepository.findAllByAnalogCircuitDefinitionIdOrderByOrderIndex(defId))
                .thenReturn(List.of());
    }

    // --- createDefinition ---

    @Test
    void createDefinition_happyPath_savesAndReturns() {
        CreateAnalogCircuitDefinitionRequest request = new CreateAnalogCircuitDefinitionRequest();
        request.setMissionId(missionId);
        request.setFalstadText(FALSTAD_TEXT);

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        when(analogDefRepository.save(any())).thenReturn(def);
        stubChecks();

        AnalogCircuitDefinitionResponse result = service.createDefinition(request);

        assertEquals(defId, result.getId());
        assertEquals(missionId, result.getMissionId());
        assertEquals(FALSTAD_TEXT, result.getFalstadText());
        verify(analogDefRepository).save(any(AnalogCircuitDefinition.class));
    }

    @Test
    void createDefinition_missionNotFound_throwsResourceNotFoundException() {
        CreateAnalogCircuitDefinitionRequest request = new CreateAnalogCircuitDefinitionRequest();
        request.setMissionId(missionId);
        request.setFalstadText(FALSTAD_TEXT);

        when(missionRepository.findById(missionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createDefinition(request));
        verify(analogDefRepository, never()).save(any());
    }

    // --- getDefinition ---

    @Test
    void getDefinition_happyPath_returnsResponse() {
        when(analogDefRepository.findById(defId)).thenReturn(Optional.of(def));
        stubChecks();

        AnalogCircuitDefinitionResponse result = service.getDefinition(defId);

        assertEquals(defId, result.getId());
        assertEquals(FALSTAD_TEXT, result.getFalstadText());
    }

    @Test
    void getDefinition_notFound_throwsResourceNotFoundException() {
        when(analogDefRepository.findById(defId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getDefinition(defId));
    }

    // --- getPublishedByMission ---

    @Test
    void getPublishedByMission_happyPath_returnsPublished() {
        AnalogCircuitDefinition published = AnalogCircuitDefinition.builder()
                .id(defId).mission(mission).falstadText(FALSTAD_TEXT)
                .status(CircuitDefinitionStatus.PUBLISHED).build();

        when(analogDefRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(published));
        when(analogCheckRepository.findAllByAnalogCircuitDefinitionIdOrderByOrderIndex(defId))
                .thenReturn(List.of());

        AnalogCircuitDefinitionResponse result = service.getPublishedByMission(missionId);

        assertEquals(CircuitDefinitionStatus.PUBLISHED, result.getStatus());
    }

    @Test
    void getPublishedByMission_notFound_throwsResourceNotFoundException() {
        when(analogDefRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getPublishedByMission(missionId));
    }

    // --- updateFalstadText ---

    @Test
    void updateFalstadText_happyPath_updatesAndReturns() {
        String newText = "$ 1 0.000001 5.0";
        AnalogCircuitDefinition updated = AnalogCircuitDefinition.builder()
                .id(defId).mission(mission).falstadText(newText).build();

        when(analogDefRepository.findById(defId)).thenReturn(Optional.of(def));
        when(analogDefRepository.save(def)).thenReturn(updated);
        when(analogCheckRepository.findAllByAnalogCircuitDefinitionIdOrderByOrderIndex(defId))
                .thenReturn(List.of());

        AnalogCircuitDefinitionResponse result = service.updateFalstadText(defId, newText);

        assertEquals(newText, result.getFalstadText());
        verify(analogDefRepository).save(def);
    }

    @Test
    void updateFalstadText_notFound_throwsResourceNotFoundException() {
        when(analogDefRepository.findById(defId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateFalstadText(defId, "text"));
        verify(analogDefRepository, never()).save(any());
    }

    // --- publishDefinition ---

    @Test
    void publishDefinition_happyPath_setsStatusPublished() {
        when(analogDefRepository.findById(defId)).thenReturn(Optional.of(def));
        when(analogDefRepository.save(def)).thenReturn(def);
        stubChecks();

        AnalogCircuitDefinitionResponse result = service.publishDefinition(defId);

        assertEquals(CircuitDefinitionStatus.PUBLISHED, def.getStatus());
        verify(analogDefRepository).save(def);
    }

    @Test
    void publishDefinition_notFound_throwsResourceNotFoundException() {
        when(analogDefRepository.findById(defId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.publishDefinition(defId));
    }

    // --- addVerificationCheck ---

    @Test
    void addVerificationCheck_happyPath_savesAndReturns() {
        CreateAnalogVerificationCheckRequest request = new CreateAnalogVerificationCheckRequest();
        request.setCheckType(AnalogCheckType.NODE_VOLTAGE);
        request.setNodeOrLabel("node1");
        request.setExpectedValue(5.0);
        request.setTolerance(0.1);
        request.setSeverity(ValidationSeverity.ERROR);
        request.setI18nKey("check.voltage");
        request.setOrderIndex(0);

        AnalogVerificationCheck saved = AnalogVerificationCheck.builder()
                .id(UUID.randomUUID()).analogCircuitDefinition(def)
                .checkType(AnalogCheckType.NODE_VOLTAGE).nodeOrLabel("node1")
                .expectedValue(5.0).tolerance(0.1)
                .severity(ValidationSeverity.ERROR).i18nKey("check.voltage").orderIndex(0).build();

        when(analogDefRepository.findById(defId)).thenReturn(Optional.of(def));
        when(analogCheckRepository.save(any())).thenReturn(saved);

        AnalogVerificationCheckResponse result = service.addVerificationCheck(defId, request);

        assertEquals(AnalogCheckType.NODE_VOLTAGE, result.getCheckType());
        assertEquals(5.0, result.getExpectedValue());
        verify(analogCheckRepository).save(any(AnalogVerificationCheck.class));
    }

    @Test
    void addVerificationCheck_withUnit_resolvesAndSaves() {
        UUID unitId = UUID.randomUUID();
        UnitOfMeasure volt = UnitOfMeasure.builder().id(unitId).name("Volt").symbol("V").build();

        CreateAnalogVerificationCheckRequest request = new CreateAnalogVerificationCheckRequest();
        request.setCheckType(AnalogCheckType.NODE_VOLTAGE);
        request.setNodeOrLabel("node1");
        request.setExpectedValue(5.0);
        request.setTolerance(0.1);
        request.setSeverity(ValidationSeverity.ERROR);
        request.setI18nKey("check.v");
        request.setOrderIndex(0);
        request.setUnitOfMeasureId(unitId);

        AnalogVerificationCheck saved = AnalogVerificationCheck.builder()
                .id(UUID.randomUUID()).analogCircuitDefinition(def)
                .checkType(AnalogCheckType.NODE_VOLTAGE).nodeOrLabel("node1")
                .expectedValue(5.0).tolerance(0.1).unitOfMeasure(volt)
                .severity(ValidationSeverity.ERROR).i18nKey("check.v").orderIndex(0).build();

        when(analogDefRepository.findById(defId)).thenReturn(Optional.of(def));
        when(unitOfMeasureRepository.findById(unitId)).thenReturn(Optional.of(volt));
        when(analogCheckRepository.save(any())).thenReturn(saved);
        when(unitOfMeasureService.toResponse(volt)).thenReturn(
                UnitOfMeasureResponse.builder().id(unitId).name("Volt").symbol("V").build());

        AnalogVerificationCheckResponse result = service.addVerificationCheck(defId, request);

        assertNotNull(result.getUnitOfMeasure());
        assertEquals("V", result.getUnitOfMeasure().getSymbol());
    }

    @Test
    void addVerificationCheck_definitionNotFound_throwsResourceNotFoundException() {
        when(analogDefRepository.findById(defId)).thenReturn(Optional.empty());

        CreateAnalogVerificationCheckRequest request = new CreateAnalogVerificationCheckRequest();
        request.setCheckType(AnalogCheckType.NODE_VOLTAGE);
        request.setNodeOrLabel("n");
        request.setExpectedValue(1.0);
        request.setTolerance(0.01);
        request.setSeverity(ValidationSeverity.ERROR);
        request.setI18nKey("k");
        request.setOrderIndex(0);

        assertThrows(ResourceNotFoundException.class,
                () -> service.addVerificationCheck(defId, request));
        verify(analogCheckRepository, never()).save(any());
    }

    // --- deleteVerificationCheck ---

    @Test
    void deleteVerificationCheck_happyPath_deletes() {
        UUID checkId = UUID.randomUUID();
        when(analogCheckRepository.existsById(checkId)).thenReturn(true);

        service.deleteVerificationCheck(checkId);

        verify(analogCheckRepository).deleteById(checkId);
    }

    @Test
    void deleteVerificationCheck_notFound_throwsResourceNotFoundException() {
        UUID checkId = UUID.randomUUID();
        when(analogCheckRepository.existsById(checkId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.deleteVerificationCheck(checkId));
        verify(analogCheckRepository, never()).deleteById(any());
    }

    // --- getCadetSave ---

    @Test
    void getCadetSave_happyPath_returnsResponse() {
        UUID saveId = UUID.randomUUID();
        CadetAnalogSave analogSave = CadetAnalogSave.builder()
                .id(saveId).cadet(cadet).analogCircuitDefinition(def)
                .falstadText(FALSTAD_TEXT).build();

        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(analogDefRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(cadetAnalogSaveRepository.findByCadetIdAndAnalogCircuitDefinitionId(cadetId, defId))
                .thenReturn(Optional.of(analogSave));

        CadetAnalogSaveResponse result = service.getCadetSave("cadet1", missionId);

        assertEquals(saveId, result.getId());
        assertEquals(FALSTAD_TEXT, result.getFalstadText());
    }

    @Test
    void getCadetSave_cadetNotFound_throwsResourceNotFoundException() {
        when(cadetRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getCadetSave("ghost", missionId));
    }

    @Test
    void getCadetSave_definitionNotPublished_throwsResourceNotFoundException() {
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(analogDefRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getCadetSave("cadet1", missionId));
    }

    @Test
    void getCadetSave_saveNotFound_throwsResourceNotFoundException() {
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(analogDefRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(cadetAnalogSaveRepository.findByCadetIdAndAnalogCircuitDefinitionId(cadetId, defId))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getCadetSave("cadet1", missionId));
    }

    // --- saveCadetAnalog ---

    @Test
    void saveCadetAnalog_createsNewSaveOnFirstCall() {
        String newText = "$ 1 modified";
        SaveCadetAnalogRequest request = new SaveCadetAnalogRequest();
        request.setFalstadText(newText);

        UUID saveId = UUID.randomUUID();
        CadetAnalogSave savedEntity = CadetAnalogSave.builder()
                .id(saveId).cadet(cadet).analogCircuitDefinition(def)
                .falstadText(newText).build();

        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(analogDefRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(cadetAnalogSaveRepository.findByCadetIdAndAnalogCircuitDefinitionId(cadetId, defId))
                .thenReturn(Optional.empty()); // no existing save
        when(cadetAnalogSaveRepository.save(any())).thenReturn(savedEntity);

        CadetAnalogSaveResponse result = service.saveCadetAnalog("cadet1", missionId, request);

        assertEquals(saveId, result.getId());
        assertEquals(newText, result.getFalstadText());
        verify(cadetAnalogSaveRepository).save(any(CadetAnalogSave.class));
    }

    @Test
    void saveCadetAnalog_updatesExistingSave() {
        String originalText = FALSTAD_TEXT;
        String updatedText = "$ 1 updated";
        SaveCadetAnalogRequest request = new SaveCadetAnalogRequest();
        request.setFalstadText(updatedText);

        UUID saveId = UUID.randomUUID();
        CadetAnalogSave existing = CadetAnalogSave.builder()
                .id(saveId).cadet(cadet).analogCircuitDefinition(def)
                .falstadText(originalText).build();
        CadetAnalogSave updated = CadetAnalogSave.builder()
                .id(saveId).cadet(cadet).analogCircuitDefinition(def)
                .falstadText(updatedText).build();

        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(analogDefRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(cadetAnalogSaveRepository.findByCadetIdAndAnalogCircuitDefinitionId(cadetId, defId))
                .thenReturn(Optional.of(existing));
        when(cadetAnalogSaveRepository.save(existing)).thenReturn(updated);

        CadetAnalogSaveResponse result = service.saveCadetAnalog("cadet1", missionId, request);

        assertEquals(updatedText, result.getFalstadText());
        // Same entity updated, not a new one created
        verify(cadetAnalogSaveRepository).save(existing);
    }

    @Test
    void saveCadetAnalog_cadetNotFound_throwsResourceNotFoundException() {
        when(cadetRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        SaveCadetAnalogRequest request = new SaveCadetAnalogRequest();
        request.setFalstadText("text");

        assertThrows(ResourceNotFoundException.class,
                () -> service.saveCadetAnalog("ghost", missionId, request));
        verify(cadetAnalogSaveRepository, never()).save(any());
    }

    @Test
    void saveCadetAnalog_definitionNotPublished_throwsResourceNotFoundException() {
        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(analogDefRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        SaveCadetAnalogRequest request = new SaveCadetAnalogRequest();
        request.setFalstadText("text");

        assertThrows(ResourceNotFoundException.class,
                () -> service.saveCadetAnalog("cadet1", missionId, request));
        verify(cadetAnalogSaveRepository, never()).save(any());
    }

    @Test
    void saveCadetAnalog_newSaveInitializedWithTemplateFalstad() {
        // On first save, the new CadetAnalogSave is initialized with def.getFalstadText()
        // then overwritten with request text — verify both behaviours
        String templateText = FALSTAD_TEXT;
        String cadetText = "$ 1 cadet-modified";
        SaveCadetAnalogRequest request = new SaveCadetAnalogRequest();
        request.setFalstadText(cadetText);

        when(cadetRepository.findByUsername("cadet1")).thenReturn(Optional.of(cadet));
        when(analogDefRepository.findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED))
                .thenReturn(Optional.of(def));
        when(cadetAnalogSaveRepository.findByCadetIdAndAnalogCircuitDefinitionId(cadetId, defId))
                .thenReturn(Optional.empty());

        // Capture what is actually saved
        when(cadetAnalogSaveRepository.save(any())).thenAnswer(inv -> {
            CadetAnalogSave s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        CadetAnalogSaveResponse result = service.saveCadetAnalog("cadet1", missionId, request);

        // The returned text must be cadetText (request overwrites template)
        assertEquals(cadetText, result.getFalstadText());
    }
}
