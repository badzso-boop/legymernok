package com.legymernok.backend.service.circuit;

import com.legymernok.backend.dto.circuit.*;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.circuit.*;
import com.legymernok.backend.repository.circuit.ComponentElectricalSpecRepository;
import com.legymernok.backend.repository.circuit.ComponentPinDefinitionRepository;
import com.legymernok.backend.repository.circuit.UnitOfMeasureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ComponentCatalogService {

    private final ComponentPinDefinitionRepository pinDefinitionRepository;
    private final ComponentElectricalSpecRepository electricalSpecRepository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final UnitOfMeasureService unitOfMeasureService;

    // --- Pin Definitions ---

    public List<ComponentPinDefinitionResponse> getAllPinDefinitions() {
        return pinDefinitionRepository.findAll().stream()
                .map(this::toPinDefResponse)
                .toList();
    }

    public List<ComponentPinDefinitionResponse> getPinDefinitionsByComponentType(ComponentType type) {
        return pinDefinitionRepository.findAllByComponentType(type).stream()
                .map(this::toPinDefResponse)
                .toList();
    }

    public List<ComponentPinDefinitionResponse> getPinDefinitions(ComponentType type, BoardType boardType) {
        List<ComponentPinDefinition> generic = pinDefinitionRepository.findAllByComponentTypeAndBoardTypeIsNull(type);
        List<ComponentPinDefinition> boardSpecific = pinDefinitionRepository.findAllByComponentTypeAndBoardType(type, boardType);
        return java.util.stream.Stream.concat(generic.stream(), boardSpecific.stream())
                .map(this::toPinDefResponse)
                .toList();
    }

    @Transactional
    public ComponentPinDefinitionResponse createPinDefinition(CreateComponentPinDefinitionRequest request) {
        ComponentPinDefinition saved = pinDefinitionRepository.save(ComponentPinDefinition.builder()
                .componentType(request.getComponentType())
                .boardType(request.getBoardType())
                .pinName(request.getPinName())
                .pinIndex(request.getPinIndex())
                .pinType(request.getPinType())
                .allowMultipleConnections(request.isAllowMultipleConnections())
                .posXOffset(request.getPosXOffset())
                .posYOffset(request.getPosYOffset())
                .build());
        return toPinDefResponse(saved);
    }

    @Transactional
    public void deletePinDefinition(UUID id) {
        if (!pinDefinitionRepository.existsById(id)) {
            throw new ResourceNotFoundException("ComponentPinDefinition", "id", id);
        }
        pinDefinitionRepository.deleteById(id);
    }

    // --- Electrical Specs ---

    public List<ComponentElectricalSpecResponse> getAllElectricalSpecs() {
        return electricalSpecRepository.findAll().stream()
                .map(this::toSpecResponse)
                .toList();
    }

    public List<ComponentElectricalSpecResponse> getElectricalSpecsByComponentType(ComponentType type) {
        return electricalSpecRepository.findAllByComponentType(type).stream()
                .map(this::toSpecResponse)
                .toList();
    }

    @Transactional
    public ComponentElectricalSpecResponse createElectricalSpec(CreateComponentElectricalSpecRequest request) {
        UnitOfMeasure unit = resolveUnit(request.getUnitOfMeasureId());
        ComponentElectricalSpec saved = electricalSpecRepository.save(ComponentElectricalSpec.builder()
                .componentType(request.getComponentType())
                .validationType(request.getValidationType())
                .value(request.getValue())
                .unitOfMeasure(unit)
                .severity(request.getSeverity())
                .build());
        return toSpecResponse(saved);
    }

    @Transactional
    public void deleteElectricalSpec(UUID id) {
        if (!electricalSpecRepository.existsById(id)) {
            throw new ResourceNotFoundException("ComponentElectricalSpec", "id", id);
        }
        electricalSpecRepository.deleteById(id);
    }

    // --- Mappers ---

    private ComponentPinDefinitionResponse toPinDefResponse(ComponentPinDefinition pin) {
        return ComponentPinDefinitionResponse.builder()
                .id(pin.getId())
                .componentType(pin.getComponentType())
                .boardType(pin.getBoardType())
                .pinName(pin.getPinName())
                .pinIndex(pin.getPinIndex())
                .pinType(pin.getPinType())
                .allowMultipleConnections(pin.isAllowMultipleConnections())
                .posXOffset(pin.getPosXOffset())
                .posYOffset(pin.getPosYOffset())
                .build();
    }

    private ComponentElectricalSpecResponse toSpecResponse(ComponentElectricalSpec spec) {
        return ComponentElectricalSpecResponse.builder()
                .id(spec.getId())
                .componentType(spec.getComponentType())
                .validationType(spec.getValidationType())
                .value(spec.getValue())
                .unitOfMeasure(unitOfMeasureService.toResponse(spec.getUnitOfMeasure()))
                .severity(spec.getSeverity())
                .build();
    }

    private UnitOfMeasure resolveUnit(UUID id) {
        if (id == null) return null;
        return unitOfMeasureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UnitOfMeasure", "id", id));
    }
}
