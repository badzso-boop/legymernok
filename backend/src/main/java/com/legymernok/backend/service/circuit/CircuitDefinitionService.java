package com.legymernok.backend.service.circuit;

import com.legymernok.backend.dto.circuit.*;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.circuit.*;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.repository.circuit.*;
import com.legymernok.backend.repository.mission.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CircuitDefinitionService {

    private final CircuitDefinitionRepository circuitDefinitionRepository;
    private final CircuitDefComponentRepository componentRepository;
    private final CircuitDefComponentPropertyRepository propertyRepository;
    private final CircuitDefConnectionRepository connectionRepository;
    private final CircuitVerificationCheckRepository checkRepository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final MissionRepository missionRepository;
    private final UnitOfMeasureService unitOfMeasureService;

    // Cadet saves — needed for publish/unpublish
    private final CadetCircuitSaveRepository cadetCircuitSaveRepository;
    private final CadetCircuitComponentRepository cadetCircuitComponentRepository;
    private final CadetCircuitComponentPropertyRepository cadetCircuitComponentPropertyRepository;
    private final CadetCircuitConnectionRepository cadetCircuitConnectionRepository;
    private final CadetVerificationResultRepository cadetVerificationResultRepository;

    @Transactional
    public CircuitDefinitionResponse createCircuitDefinition(CreateCircuitDefinitionRequest request) {
        Mission mission = missionRepository.findById(request.getMissionId())
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", request.getMissionId()));
        CircuitDefinition saved = circuitDefinitionRepository.save(CircuitDefinition.builder()
                .mission(mission)
                .boardType(request.getBoardType())
                .build());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CircuitDefinitionResponse getCircuitDefinition(UUID id) {
        CircuitDefinition def = circuitDefinitionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CircuitDefinition", "id", id));
        return toResponse(def);
    }

    @Transactional(readOnly = true)
    public CircuitDefinitionResponse getPublishedByMission(UUID missionId) {
        CircuitDefinition def = circuitDefinitionRepository
                .findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("CircuitDefinition", "missionId/status", missionId + "/PUBLISHED"));
        return toResponse(def);
    }

    @Transactional(readOnly = true)
    public List<CircuitDefinitionResponse> getAllByMission(UUID missionId) {
        return circuitDefinitionRepository.findAllByMissionId(missionId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Replaces all components, properties and connections for a definition.
     * Connections use label-based references resolved after component creation.
     */
    @Transactional
    public CircuitDefinitionResponse saveCanvas(UUID definitionId, SaveCircuitDefinitionRequest request) {
        CircuitDefinition def = circuitDefinitionRepository.findById(definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("CircuitDefinition", "id", definitionId));

        // Delete in FK order: properties → components → connections
        propertyRepository.deleteAllByCircuitDefinitionId(definitionId);
        componentRepository.deleteAllByCircuitDefinitionId(definitionId);
        connectionRepository.deleteAllByCircuitDefinitionId(definitionId);

        // Create components and build label→UUID map
        Map<String, UUID> labelToId = createComponents(def, request.getComponents());

        // Create connections (normalize direction by UUID comparison)
        createConnections(def, request.getConnections(), labelToId);

        return toResponse(def);
    }

    @Transactional
    public CircuitVerificationCheckResponse addVerificationCheck(UUID definitionId, CreateCircuitVerificationCheckRequest request) {
        CircuitDefinition def = circuitDefinitionRepository.findById(definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("CircuitDefinition", "id", definitionId));
        UnitOfMeasure unit = resolveUnit(request.getUnitOfMeasureId());
        CircuitVerificationCheck saved = checkRepository.save(CircuitVerificationCheck.builder()
                .circuitDefinition(def)
                .checkType(request.getCheckType())
                .labelFrom(request.getLabelFrom())
                .labelTo(request.getLabelTo())
                .pinFrom(request.getPinFrom())
                .pinTo(request.getPinTo())
                .expectedValue(request.getExpectedValue())
                .unitOfMeasure(unit)
                .severity(request.getSeverity())
                .i18nKey(request.getI18nKey())
                .orderIndex(request.getOrderIndex())
                .build());
        return toCheckResponse(saved);
    }

    @Transactional
    public void deleteVerificationCheck(UUID checkId) {
        if (!checkRepository.existsById(checkId)) {
            throw new ResourceNotFoundException("CircuitVerificationCheck", "id", checkId);
        }
        checkRepository.deleteById(checkId);
    }

    /**
     * Publishes a definition: status → PUBLISHED.
     * Deletes all cadet saves for this definition so cadets start fresh with the new template.
     */
    @Transactional
    public CircuitDefinitionResponse publishCircuitDefinition(UUID definitionId) {
        CircuitDefinition def = circuitDefinitionRepository.findById(definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("CircuitDefinition", "id", definitionId));

        // Delete all cadet progress in FK order
        cadetVerificationResultRepository.deleteAllByCircuitDefinitionId(definitionId);
        cadetCircuitConnectionRepository.deleteAllByCircuitDefinitionId(definitionId);
        cadetCircuitComponentPropertyRepository.deleteAllByCircuitDefinitionId(definitionId);
        cadetCircuitComponentRepository.deleteAllByCircuitDefinitionId(definitionId);
        cadetCircuitSaveRepository.deleteAllByCircuitDefinitionId(definitionId);

        def.setStatus(CircuitDefinitionStatus.PUBLISHED);
        return toResponse(circuitDefinitionRepository.save(def));
    }

    @Transactional
    public void deleteCircuitDefinition(UUID definitionId) {
        CircuitDefinition def = circuitDefinitionRepository.findById(definitionId)
                .orElseThrow(() -> new ResourceNotFoundException("CircuitDefinition", "id", definitionId));

        // Delete cadet data first
        cadetVerificationResultRepository.deleteAllByCircuitDefinitionId(definitionId);
        cadetCircuitConnectionRepository.deleteAllByCircuitDefinitionId(definitionId);
        cadetCircuitComponentPropertyRepository.deleteAllByCircuitDefinitionId(definitionId);
        cadetCircuitComponentRepository.deleteAllByCircuitDefinitionId(definitionId);
        cadetCircuitSaveRepository.deleteAllByCircuitDefinitionId(definitionId);

        // Delete definition data
        checkRepository.deleteAllByCircuitDefinitionId(definitionId);
        propertyRepository.deleteAllByCircuitDefinitionId(definitionId);
        componentRepository.deleteAllByCircuitDefinitionId(definitionId);
        connectionRepository.deleteAllByCircuitDefinitionId(definitionId);

        circuitDefinitionRepository.delete(def);
    }

    // --- Helpers ---

    private Map<String, UUID> createComponents(CircuitDefinition def, List<UpsertCircuitDefComponentRequest> compRequests) {
        validateUniqueLabels(compRequests);
        return compRequests.stream().collect(Collectors.toMap(
                UpsertCircuitDefComponentRequest::getLabel,
                compReq -> {
                    CircuitDefComponent saved = componentRepository.save(CircuitDefComponent.builder()
                            .circuitDefinition(def)
                            .componentType(compReq.getComponentType())
                            .label(compReq.getLabel())
                            .posX(compReq.getPosX())
                            .posY(compReq.getPosY())
                            .build());
                    if (compReq.getProperties() != null) {
                        for (UpsertCircuitDefPropertyRequest propReq : compReq.getProperties()) {
                            propertyRepository.save(CircuitDefComponentProperty.builder()
                                    .component(saved)
                                    .propertyKey(propReq.getPropertyKey())
                                    .propertyValue(propReq.getPropertyValue())
                                    .unitOfMeasure(resolveUnit(propReq.getUnitOfMeasureId()))
                                    .build());
                        }
                    }
                    return saved.getId();
                }
        ));
    }

    private void createConnections(CircuitDefinition def, List<UpsertCircuitDefConnectionRequest> connRequests, Map<String, UUID> labelToId) {
        for (UpsertCircuitDefConnectionRequest connReq : connRequests) {
            UUID fromId = labelToId.get(connReq.getFromLabel());
            UUID toId = labelToId.get(connReq.getToLabel());
            if (fromId == null || toId == null) continue;

            // Normalize direction: smaller UUID is always "from"
            UUID normalFromId, normalToId;
            String normalFromPin, normalToPin;
            if (fromId.compareTo(toId) <= 0) {
                normalFromId = fromId; normalFromPin = connReq.getFromPinName();
                normalToId = toId;   normalToPin = connReq.getToPinName();
            } else {
                normalFromId = toId;   normalFromPin = connReq.getToPinName();
                normalToId = fromId; normalToPin = connReq.getFromPinName();
            }
            connectionRepository.save(CircuitDefConnection.builder()
                    .circuitDefinition(def)
                    .fromComponentId(normalFromId)
                    .fromPinName(normalFromPin)
                    .toComponentId(normalToId)
                    .toPinName(normalToPin)
                    .build());
        }
    }

    CircuitDefinitionResponse toResponse(CircuitDefinition def) {
        List<CircuitDefComponent> components = componentRepository.findAllByCircuitDefinitionId(def.getId());
        List<CircuitDefConnection> connections = connectionRepository.findAllByCircuitDefinitionId(def.getId());
        List<CircuitVerificationCheck> checks = checkRepository.findAllByCircuitDefinitionIdOrderByOrderIndex(def.getId());

        // Build properties per component individually
        List<CircuitDefComponentResponse> componentResponses = components.stream().map(comp -> {
            List<CircuitDefComponentProperty> props = propertyRepository.findAllByComponentId(comp.getId());
            return CircuitDefComponentResponse.builder()
                    .id(comp.getId())
                    .componentType(comp.getComponentType())
                    .label(comp.getLabel())
                    .posX(comp.getPosX())
                    .posY(comp.getPosY())
                    .properties(props.stream().map(this::toPropResponse).toList())
                    .build();
        }).toList();

        return CircuitDefinitionResponse.builder()
                .id(def.getId())
                .missionId(def.getMission().getId())
                .boardType(def.getBoardType())
                .status(def.getStatus())
                .components(componentResponses)
                .connections(connections.stream().map(this::toConnResponse).toList())
                .checks(checks.stream().map(this::toCheckResponse).toList())
                .createdAt(def.getCreatedAt())
                .updatedAt(def.getUpdatedAt())
                .build();
    }

    private CircuitDefComponentPropertyResponse toPropResponse(CircuitDefComponentProperty prop) {
        return CircuitDefComponentPropertyResponse.builder()
                .id(prop.getId())
                .propertyKey(prop.getPropertyKey())
                .propertyValue(prop.getPropertyValue())
                .unitOfMeasure(unitOfMeasureService.toResponse(prop.getUnitOfMeasure()))
                .build();
    }

    private CircuitDefConnectionResponse toConnResponse(CircuitDefConnection conn) {
        return CircuitDefConnectionResponse.builder()
                .id(conn.getId())
                .fromComponentId(conn.getFromComponentId())
                .fromPinName(conn.getFromPinName())
                .toComponentId(conn.getToComponentId())
                .toPinName(conn.getToPinName())
                .build();
    }

    CircuitVerificationCheckResponse toCheckResponse(CircuitVerificationCheck check) {
        return CircuitVerificationCheckResponse.builder()
                .id(check.getId())
                .checkType(check.getCheckType())
                .labelFrom(check.getLabelFrom())
                .labelTo(check.getLabelTo())
                .pinFrom(check.getPinFrom())
                .pinTo(check.getPinTo())
                .expectedValue(check.getExpectedValue())
                .unitOfMeasure(unitOfMeasureService.toResponse(check.getUnitOfMeasure()))
                .severity(check.getSeverity())
                .i18nKey(check.getI18nKey())
                .orderIndex(check.getOrderIndex())
                .build();
    }

    private void validateUniqueLabels(List<UpsertCircuitDefComponentRequest> compRequests) {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = compRequests.stream()
                .map(UpsertCircuitDefComponentRequest::getLabel)
                .filter(label -> !seen.add(label))
                .distinct()
                .toList();
        if (!duplicates.isEmpty()) {
            throw new ResourceConflictException("CircuitDefComponent", "label", String.join(", ", duplicates));
        }
    }

    private UnitOfMeasure resolveUnit(UUID id) {
        if (id == null) return null;
        return unitOfMeasureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UnitOfMeasure", "id", id));
    }
}
