package com.legymernok.backend.service.circuit;

import com.legymernok.backend.dto.circuit.*;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.ConnectTable.CadetMission;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.circuit.*;
import com.legymernok.backend.model.mission.MissionStatus;
import com.legymernok.backend.model.mission.MissionType;
import com.legymernok.backend.repository.ConnectTables.CadetMissionRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.circuit.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CadetCircuitService {

    private final CadetCircuitSaveRepository saveRepository;
    private final CadetCircuitComponentRepository componentRepository;
    private final CadetCircuitComponentPropertyRepository propertyRepository;
    private final CadetCircuitConnectionRepository connectionRepository;
    private final CadetVerificationResultRepository verificationResultRepository;
    private final CircuitDefinitionRepository circuitDefinitionRepository;
    private final CircuitDefComponentRepository defComponentRepository;
    private final CircuitDefComponentPropertyRepository defPropertyRepository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final CadetRepository cadetRepository;
    private final CadetMissionRepository cadetMissionRepository;
    private final UnitOfMeasureService unitOfMeasureService;
    private final CircuitVerificationCheckRepository checkRepository;
    private final GiteaService giteaService;

    /**
     * Creates a CadetCircuitSave if not yet started, copies template components + properties.
     * Idempotent: returns existing save if already started.
     */
    @Transactional
    public CadetCircuitSaveResponse startCircuitMission(String username, UUID missionId) {
        Cadet cadet = resolveCadet(username);
        CircuitDefinition definition = circuitDefinitionRepository
                .findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("CircuitDefinition", "missionId/status", missionId + "/PUBLISHED"));

        return saveRepository.findByCadetIdAndCircuitDefinitionId(cadet.getId(), definition.getId())
                .map(this::toResponse)
                .orElseGet(() -> createSaveFromTemplate(cadet, definition));
    }

    @Transactional(readOnly = true)
    public CadetCircuitSaveResponse getCadetCircuitSave(String username, UUID missionId) {
        Cadet cadet = resolveCadet(username);
        CircuitDefinition definition = circuitDefinitionRepository
                .findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("CircuitDefinition", "missionId/status", missionId + "/PUBLISHED"));

        CadetCircuitSave save = saveRepository.findByCadetIdAndCircuitDefinitionId(cadet.getId(), definition.getId())
                .orElseThrow(() -> new ResourceNotFoundException("CadetCircuitSave", "username/missionId", username + "/" + missionId));
        return toResponse(save);
    }

    /**
     * Replaces the cadet's canvas (components + connections) with the submitted state.
     * Verification results are kept until the next verification run.
     */
    @Transactional
    public CadetCircuitSaveResponse saveCanvas(String username, UUID missionId, SaveCadetCircuitRequest request) {
        Cadet cadet = resolveCadet(username);
        CircuitDefinition definition = circuitDefinitionRepository
                .findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("CircuitDefinition", "missionId/status", missionId + "/PUBLISHED"));

        CadetCircuitSave save = saveRepository.findByCadetIdAndCircuitDefinitionId(cadet.getId(), definition.getId())
                .orElseThrow(() -> new ResourceNotFoundException("CadetCircuitSave", "username/missionId", username + "/" + missionId));

        // Delete in FK order: properties → components → connections
        propertyRepository.deleteAllByCadetCircuitSaveId(save.getId());
        componentRepository.deleteAllByCadetCircuitSaveId(save.getId());
        connectionRepository.deleteAllByCadetCircuitSaveId(save.getId());

        // Create new components and build label→UUID map
        Map<String, UUID> labelToId = createComponents(save, request.getComponents());

        // Create new connections (normalize direction)
        createConnections(save, request.getConnections(), labelToId);

        return toResponse(save);
    }

    // --- Template copy ---

    private CadetCircuitSaveResponse createSaveFromTemplate(Cadet cadet, CircuitDefinition definition) {
        // Create the Gitea repo before touching the DB so that on Gitea failure
        // we don't leave a broken DB record. On DB failure the Gitea repo becomes
        // an orphan — known architectural limitation documented in CLAUDE.md.
        String missionId = definition.getMission().getId().toString();
        String giteaRepoUrl = giteaService.createMissionRepository(missionId, "", cadet, MissionType.CIRCUIT_SIMULATION);
        log.info("Gitea circuit repo created for cadet '{}', mission {}: {}", cadet.getUsername(), missionId, giteaRepoUrl);

        CadetCircuitSave save = saveRepository.save(CadetCircuitSave.builder()
                .cadet(cadet)
                .circuitDefinition(definition)
                .giteaRepoUrl(giteaRepoUrl)
                .build());

        // Register the cadet's participation in the mission (idempotent)
        cadetMissionRepository.findByCadetIdAndMissionId(cadet.getId(), definition.getMission().getId())
                .orElseGet(() -> cadetMissionRepository.save(CadetMission.builder()
                        .cadet(cadet)
                        .mission(definition.getMission())
                        .status(MissionStatus.IN_PROGRESS)
                        .repositoryUrl(giteaRepoUrl)
                        .startedAt(java.time.Instant.now())
                        .build()));

        List<CircuitDefComponent> templateComponents = defComponentRepository.findAllByCircuitDefinitionId(definition.getId());
        for (CircuitDefComponent template : templateComponents) {
            CadetCircuitComponent cadetComp = componentRepository.save(CadetCircuitComponent.builder()
                    .cadetCircuitSave(save)
                    .componentType(template.getComponentType())
                    .label(template.getLabel())
                    .posX(template.getPosX())
                    .posY(template.getPosY())
                    .build());

            List<CircuitDefComponentProperty> templateProps = defPropertyRepository.findAllByComponentId(template.getId());
            for (CircuitDefComponentProperty prop : templateProps) {
                propertyRepository.save(CadetCircuitComponentProperty.builder()
                        .cadetComponent(cadetComp)
                        .propertyKey(prop.getPropertyKey())
                        .propertyValue(prop.getPropertyValue())
                        .unitOfMeasure(prop.getUnitOfMeasure())
                        .build());
            }
        }

        return toResponse(save);
    }

    // --- Canvas helpers ---

    private Map<String, UUID> createComponents(CadetCircuitSave save, List<UpsertCircuitDefComponentRequest> compRequests) {
        validateUniqueLabels(compRequests);
        return compRequests.stream().collect(Collectors.toMap(
                UpsertCircuitDefComponentRequest::getLabel,
                compReq -> {
                    CadetCircuitComponent saved = componentRepository.save(CadetCircuitComponent.builder()
                            .cadetCircuitSave(save)
                            .componentType(compReq.getComponentType())
                            .label(compReq.getLabel())
                            .posX(compReq.getPosX())
                            .posY(compReq.getPosY())
                            .build());
                    if (compReq.getProperties() != null) {
                        for (UpsertCircuitDefPropertyRequest propReq : compReq.getProperties()) {
                            UnitOfMeasure unit = resolveUnit(propReq.getUnitOfMeasureId());
                            propertyRepository.save(CadetCircuitComponentProperty.builder()
                                    .cadetComponent(saved)
                                    .propertyKey(propReq.getPropertyKey())
                                    .propertyValue(propReq.getPropertyValue())
                                    .unitOfMeasure(unit)
                                    .build());
                        }
                    }
                    return saved.getId();
                }
        ));
    }

    private void createConnections(CadetCircuitSave save, List<UpsertCircuitDefConnectionRequest> connRequests,
                                   Map<String, UUID> labelToId) {
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
            connectionRepository.save(CadetCircuitConnection.builder()
                    .cadetCircuitSave(save)
                    .fromComponentId(normalFromId)
                    .fromPinName(normalFromPin)
                    .toComponentId(normalToId)
                    .toPinName(normalToPin)
                    .build());
        }
    }

    // --- Mapper ---

    CadetCircuitSaveResponse toResponse(CadetCircuitSave save) {
        List<CadetCircuitComponent> components = componentRepository.findAllByCadetCircuitSaveId(save.getId());
        List<CadetCircuitConnection> connections = connectionRepository.findAllByCadetCircuitSaveId(save.getId());
        List<CadetVerificationResult> results = verificationResultRepository.findAllByCadetCircuitSaveId(save.getId());
        List<CircuitVerificationCheck> checks = checkRepository
                .findAllByCircuitDefinitionIdOrderByOrderIndex(save.getCircuitDefinition().getId());
        Map<UUID, CircuitVerificationCheck> checksById = checks.stream()
                .collect(Collectors.toMap(CircuitVerificationCheck::getId, c -> c));

        List<CadetCircuitComponentResponse> componentResponses = components.stream().map(comp -> {
            List<CadetCircuitComponentProperty> props = propertyRepository.findAllByCadetComponentId(comp.getId());
            return CadetCircuitComponentResponse.builder()
                    .id(comp.getId())
                    .componentType(comp.getComponentType())
                    .label(comp.getLabel())
                    .posX(comp.getPosX())
                    .posY(comp.getPosY())
                    .properties(props.stream().map(this::toPropResponse).toList())
                    .build();
        }).toList();

        List<CadetCircuitConnectionResponse> connectionResponses = connections.stream()
                .map(conn -> CadetCircuitConnectionResponse.builder()
                        .id(conn.getId())
                        .fromComponentId(conn.getFromComponentId())
                        .fromPinName(conn.getFromPinName())
                        .toComponentId(conn.getToComponentId())
                        .toPinName(conn.getToPinName())
                        .build())
                .toList();

        List<CadetVerificationResultResponse> resultResponses = results.stream().map(r -> {
            CircuitVerificationCheck check = checksById.get(r.getCheck().getId());
            return CadetVerificationResultResponse.builder()
                    .checkId(r.getCheck().getId())
                    .i18nKey(check != null ? check.getI18nKey() : null)
                    .orderIndex(check != null ? check.getOrderIndex() : null)
                    .severity(check != null ? check.getSeverity() : null)
                    .passed(r.isPassed())
                    .message(r.getMessage())
                    .checkedAt(r.getCheckedAt())
                    .build();
        }).toList();

        return CadetCircuitSaveResponse.builder()
                .id(save.getId())
                .circuitDefinitionId(save.getCircuitDefinition().getId())
                .giteaRepoUrl(save.getGiteaRepoUrl())
                .lastCompileError(save.getLastCompileError())
                .stale(save.isStale())
                .simulationStatus(save.getSimulationStatus())
                .simulationStartedAt(save.getSimulationStartedAt())
                .compilationTimeMs(save.getCompilationTimeMs())
                .totalTimeSpentMs(save.getTotalTimeSpentMs())
                .components(componentResponses)
                .connections(connectionResponses)
                .verificationResults(resultResponses)
                .createdAt(save.getCreatedAt())
                .updatedAt(save.getUpdatedAt())
                .build();
    }

    private CadetCircuitComponentPropertyResponse toPropResponse(CadetCircuitComponentProperty prop) {
        return CadetCircuitComponentPropertyResponse.builder()
                .id(prop.getId())
                .propertyKey(prop.getPropertyKey())
                .propertyValue(prop.getPropertyValue())
                .unitOfMeasure(unitOfMeasureService.toResponse(prop.getUnitOfMeasure()))
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
            throw new ResourceConflictException("CadetCircuitComponent", "label", String.join(", ", duplicates));
        }
    }

    private Cadet resolveCadet(String username) {
        return cadetRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "username", username));
    }

    private UnitOfMeasure resolveUnit(UUID id) {
        if (id == null) return null;
        return unitOfMeasureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UnitOfMeasure", "id", id));
    }
}
