package com.legymernok.backend.service.circuit;

import com.legymernok.backend.dto.circuit.*;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.circuit.*;
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
class ComponentCatalogServiceTest {

    @Mock private ComponentPinDefinitionRepository pinRepo;
    @Mock private ComponentElectricalSpecRepository specRepo;
    @Mock private UnitOfMeasureRepository unitRepo;
    @Mock private UnitOfMeasureService unitOfMeasureService;
    @InjectMocks private ComponentCatalogService service;

    private UUID pinId;
    private UUID specId;
    private ComponentPinDefinition ledAnode;
    private ComponentElectricalSpec ledForwardVoltage;
    private UnitOfMeasure volt;

    @BeforeEach
    void setUp() {
        pinId = UUID.randomUUID();
        specId = UUID.randomUUID();
        volt = UnitOfMeasure.builder().id(UUID.randomUUID()).name("Volt").symbol("V").build();

        ledAnode = ComponentPinDefinition.builder()
                .id(pinId)
                .componentType(ComponentType.LED)
                .pinName("ANODE")
                .pinIndex(0)
                .pinType(PinType.COMPONENT_ANODE)
                .allowMultipleConnections(false)
                .build();

        ledForwardVoltage = ComponentElectricalSpec.builder()
                .id(specId)
                .componentType(ComponentType.LED)
                .validationType(ElectricalValidationType.FORWARD_VOLTAGE)
                .value(2.0)
                .unitOfMeasure(volt)
                .severity(ValidationSeverity.ERROR)
                .build();
    }

    // --- Pin Definitions ---

    @Test
    void getAllPinDefinitions_returnsMappedList() {
        when(pinRepo.findAll()).thenReturn(List.of(ledAnode));
        List<ComponentPinDefinitionResponse> result = service.getAllPinDefinitions();
        assertEquals(1, result.size());
        assertEquals(pinId, result.get(0).getId());
        assertEquals(ComponentType.LED, result.get(0).getComponentType());
        assertEquals("ANODE", result.get(0).getPinName());
    }

    @Test
    void getPinDefinitionsByComponentType_filtersCorrectly() {
        when(pinRepo.findAllByComponentType(ComponentType.LED)).thenReturn(List.of(ledAnode));
        List<ComponentPinDefinitionResponse> result = service.getPinDefinitionsByComponentType(ComponentType.LED);
        assertEquals(1, result.size());
        assertEquals(ComponentType.LED, result.get(0).getComponentType());
    }

    @Test
    void getPinDefinitions_mergesGenericAndBoardSpecific() {
        ComponentPinDefinition boardSpecific = ComponentPinDefinition.builder()
                .id(UUID.randomUUID()).componentType(ComponentType.BOARD)
                .boardType(BoardType.ARDUINO_UNO).pinName("D13").pinIndex(13)
                .pinType(PinType.DIGITAL_IO).build();

        when(pinRepo.findAllByComponentTypeAndBoardTypeIsNull(ComponentType.BOARD)).thenReturn(List.of());
        when(pinRepo.findAllByComponentTypeAndBoardType(ComponentType.BOARD, BoardType.ARDUINO_UNO))
                .thenReturn(List.of(boardSpecific));

        List<ComponentPinDefinitionResponse> result = service.getPinDefinitions(ComponentType.BOARD, BoardType.ARDUINO_UNO);
        assertEquals(1, result.size());
        assertEquals("D13", result.get(0).getPinName());
    }

    @Test
    void getPinDefinitions_genericAndBoardSpecificCombined() {
        ComponentPinDefinition generic = ComponentPinDefinition.builder()
                .id(UUID.randomUUID()).componentType(ComponentType.BOARD)
                .pinName("GND").pinIndex(99).pinType(PinType.POWER_GND).build();
        ComponentPinDefinition boardSpecific = ComponentPinDefinition.builder()
                .id(UUID.randomUUID()).componentType(ComponentType.BOARD)
                .boardType(BoardType.ARDUINO_UNO).pinName("D0").pinIndex(0)
                .pinType(PinType.DIGITAL_IO).build();

        when(pinRepo.findAllByComponentTypeAndBoardTypeIsNull(ComponentType.BOARD)).thenReturn(List.of(generic));
        when(pinRepo.findAllByComponentTypeAndBoardType(ComponentType.BOARD, BoardType.ARDUINO_UNO))
                .thenReturn(List.of(boardSpecific));

        List<ComponentPinDefinitionResponse> result = service.getPinDefinitions(ComponentType.BOARD, BoardType.ARDUINO_UNO);
        assertEquals(2, result.size());
    }

    @Test
    void createPinDefinition_savesAndReturns() {
        CreateComponentPinDefinitionRequest request = new CreateComponentPinDefinitionRequest();
        request.setComponentType(ComponentType.LED);
        request.setPinName("ANODE");
        request.setPinIndex(0);
        request.setPinType(PinType.COMPONENT_ANODE);

        when(pinRepo.save(any())).thenReturn(ledAnode);
        ComponentPinDefinitionResponse result = service.createPinDefinition(request);

        assertEquals("ANODE", result.getPinName());
        verify(pinRepo).save(any(ComponentPinDefinition.class));
    }

    @Test
    void deletePinDefinition_happyPath_deletes() {
        when(pinRepo.existsById(pinId)).thenReturn(true);
        service.deletePinDefinition(pinId);
        verify(pinRepo).deleteById(pinId);
    }

    @Test
    void deletePinDefinition_notFound_throwsResourceNotFoundException() {
        when(pinRepo.existsById(pinId)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.deletePinDefinition(pinId));
        verify(pinRepo, never()).deleteById(any());
    }

    // --- Electrical Specs ---

    @Test
    void getAllElectricalSpecs_returnsMappedList() {
        when(specRepo.findAll()).thenReturn(List.of(ledForwardVoltage));
        when(unitOfMeasureService.toResponse(volt)).thenReturn(
                UnitOfMeasureResponse.builder().id(volt.getId()).name("Volt").symbol("V").build());

        List<ComponentElectricalSpecResponse> result = service.getAllElectricalSpecs();

        assertEquals(1, result.size());
        assertEquals(ComponentType.LED, result.get(0).getComponentType());
        assertEquals(ElectricalValidationType.FORWARD_VOLTAGE, result.get(0).getValidationType());
        assertEquals(2.0, result.get(0).getValue());
    }

    @Test
    void getElectricalSpecsByComponentType_filtersCorrectly() {
        when(specRepo.findAllByComponentType(ComponentType.LED)).thenReturn(List.of(ledForwardVoltage));
        when(unitOfMeasureService.toResponse(any())).thenReturn(null);

        List<ComponentElectricalSpecResponse> result = service.getElectricalSpecsByComponentType(ComponentType.LED);
        assertEquals(1, result.size());
    }

    @Test
    void createElectricalSpec_withoutUnit_savesAndReturns() {
        CreateComponentElectricalSpecRequest request = new CreateComponentElectricalSpecRequest();
        request.setComponentType(ComponentType.LED);
        request.setValidationType(ElectricalValidationType.FORWARD_VOLTAGE);
        request.setValue(2.0);
        request.setSeverity(ValidationSeverity.ERROR);

        ComponentElectricalSpec noUnit = ComponentElectricalSpec.builder()
                .id(specId).componentType(ComponentType.LED)
                .validationType(ElectricalValidationType.FORWARD_VOLTAGE)
                .value(2.0).severity(ValidationSeverity.ERROR).build();

        when(specRepo.save(any())).thenReturn(noUnit);
        when(unitOfMeasureService.toResponse(null)).thenReturn(null);

        ComponentElectricalSpecResponse result = service.createElectricalSpec(request);

        assertEquals(ComponentType.LED, result.getComponentType());
        assertNull(result.getUnitOfMeasure());
    }

    @Test
    void createElectricalSpec_withUnit_resolvesAndSaves() {
        UUID unitId = volt.getId();
        CreateComponentElectricalSpecRequest request = new CreateComponentElectricalSpecRequest();
        request.setComponentType(ComponentType.LED);
        request.setValidationType(ElectricalValidationType.FORWARD_VOLTAGE);
        request.setValue(2.0);
        request.setSeverity(ValidationSeverity.ERROR);
        request.setUnitOfMeasureId(unitId);

        when(unitRepo.findById(unitId)).thenReturn(Optional.of(volt));
        when(specRepo.save(any())).thenReturn(ledForwardVoltage);
        when(unitOfMeasureService.toResponse(volt)).thenReturn(
                UnitOfMeasureResponse.builder().id(unitId).name("Volt").symbol("V").build());

        ComponentElectricalSpecResponse result = service.createElectricalSpec(request);

        assertNotNull(result.getUnitOfMeasure());
        assertEquals("V", result.getUnitOfMeasure().getSymbol());
    }

    @Test
    void createElectricalSpec_unitNotFound_throwsResourceNotFoundException() {
        UUID missingId = UUID.randomUUID();
        CreateComponentElectricalSpecRequest request = new CreateComponentElectricalSpecRequest();
        request.setComponentType(ComponentType.LED);
        request.setValidationType(ElectricalValidationType.FORWARD_VOLTAGE);
        request.setValue(2.0);
        request.setSeverity(ValidationSeverity.ERROR);
        request.setUnitOfMeasureId(missingId);

        when(unitRepo.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.createElectricalSpec(request));
        verify(specRepo, never()).save(any());
    }

    @Test
    void deleteElectricalSpec_happyPath_deletes() {
        when(specRepo.existsById(specId)).thenReturn(true);
        service.deleteElectricalSpec(specId);
        verify(specRepo).deleteById(specId);
    }

    @Test
    void deleteElectricalSpec_notFound_throwsResourceNotFoundException() {
        when(specRepo.existsById(specId)).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.deleteElectricalSpec(specId));
        verify(specRepo, never()).deleteById(any());
    }
}
