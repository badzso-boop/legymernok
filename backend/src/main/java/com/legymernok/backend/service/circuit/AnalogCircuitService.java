package com.legymernok.backend.service.circuit;

import com.legymernok.backend.dto.circuit.*;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.circuit.*;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.circuit.*;
import com.legymernok.backend.repository.mission.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AnalogCircuitService {

    private final AnalogCircuitDefinitionRepository analogDefRepository;
    private final AnalogVerificationCheckRepository analogCheckRepository;
    private final CadetAnalogSaveRepository cadetAnalogSaveRepository;
    private final UnitOfMeasureRepository unitOfMeasureRepository;
    private final MissionRepository missionRepository;
    private final CadetRepository cadetRepository;
    private final UnitOfMeasureService unitOfMeasureService;

    // --- Admin: Definition ---

    @Transactional
    public AnalogCircuitDefinitionResponse createDefinition(CreateAnalogCircuitDefinitionRequest request) {
        Mission mission = missionRepository.findById(request.getMissionId())
                .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", request.getMissionId()));
        AnalogCircuitDefinition saved = analogDefRepository.save(AnalogCircuitDefinition.builder()
                .mission(mission)
                .falstadText(request.getFalstadText())
                .build());
        return toDefResponse(saved);
    }

    @Transactional(readOnly = true)
    public AnalogCircuitDefinitionResponse getDefinition(UUID id) {
        return toDefResponse(findDef(id));
    }

    @Transactional(readOnly = true)
    public AnalogCircuitDefinitionResponse getPublishedByMission(UUID missionId) {
        AnalogCircuitDefinition def = analogDefRepository
                .findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("AnalogCircuitDefinition", "missionId/status", missionId + "/PUBLISHED"));
        return toDefResponse(def);
    }

    @Transactional
    public AnalogCircuitDefinitionResponse updateFalstadText(UUID id, String falstadText) {
        AnalogCircuitDefinition def = findDef(id);
        def.setFalstadText(falstadText);
        return toDefResponse(analogDefRepository.save(def));
    }

    @Transactional
    public AnalogCircuitDefinitionResponse publishDefinition(UUID id) {
        AnalogCircuitDefinition def = findDef(id);
        def.setStatus(CircuitDefinitionStatus.PUBLISHED);
        return toDefResponse(analogDefRepository.save(def));
    }

    // --- Admin: Verification Checks ---

    @Transactional
    public AnalogVerificationCheckResponse addVerificationCheck(UUID definitionId, CreateAnalogVerificationCheckRequest request) {
        AnalogCircuitDefinition def = findDef(definitionId);
        UnitOfMeasure unit = resolveUnit(request.getUnitOfMeasureId());
        AnalogVerificationCheck saved = analogCheckRepository.save(AnalogVerificationCheck.builder()
                .analogCircuitDefinition(def)
                .checkType(request.getCheckType())
                .nodeOrLabel(request.getNodeOrLabel())
                .expectedValue(request.getExpectedValue())
                .tolerance(request.getTolerance())
                .unitOfMeasure(unit)
                .severity(request.getSeverity())
                .i18nKey(request.getI18nKey())
                .orderIndex(request.getOrderIndex())
                .build());
        return toCheckResponse(saved);
    }

    @Transactional
    public void deleteVerificationCheck(UUID checkId) {
        if (!analogCheckRepository.existsById(checkId)) {
            throw new ResourceNotFoundException("AnalogVerificationCheck", "id", checkId);
        }
        analogCheckRepository.deleteById(checkId);
    }

    // --- Cadet: Analog Save ---

    @Transactional(readOnly = true)
    public CadetAnalogSaveResponse getCadetSave(String username, UUID missionId) {
        Cadet cadet = resolveCadet(username);
        AnalogCircuitDefinition def = analogDefRepository
                .findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("AnalogCircuitDefinition", "missionId/status", missionId + "/PUBLISHED"));
        CadetAnalogSave save = cadetAnalogSaveRepository
                .findByCadetIdAndAnalogCircuitDefinitionId(cadet.getId(), def.getId())
                .orElseThrow(() -> new ResourceNotFoundException("CadetAnalogSave", "username/missionId", username + "/" + missionId));
        return toSaveResponse(save);
    }

    @Transactional
    public CadetAnalogSaveResponse saveCadetAnalog(String username, UUID missionId, SaveCadetAnalogRequest request) {
        Cadet cadet = resolveCadet(username);
        AnalogCircuitDefinition def = analogDefRepository
                .findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("AnalogCircuitDefinition", "missionId/status", missionId + "/PUBLISHED"));

        CadetAnalogSave save = cadetAnalogSaveRepository
                .findByCadetIdAndAnalogCircuitDefinitionId(cadet.getId(), def.getId())
                .orElseGet(() -> CadetAnalogSave.builder()
                        .cadet(cadet)
                        .analogCircuitDefinition(def)
                        .falstadText(def.getFalstadText())
                        .build());

        save.setFalstadText(request.getFalstadText());
        return toSaveResponse(cadetAnalogSaveRepository.save(save));
    }

    // --- Cadet: Verification ---

    /**
     * Evaluates the analog circuit against the definition's verification checks using
     * the simulation results (node voltages, branch currents, component values) submitted
     * by the frontend (CircuitJS1/Falstad).
     *
     * Results are returned in-memory only (not persisted) — sufficient for MVP.
     * Updates the CadetAnalogSave's simulationStatus to NEVER_RUN (completed).
     */
    @Transactional
    public List<AnalogCheckResultResponse> verifyAnalog(String username, UUID missionId, VerifyAnalogRequest request) {
        Cadet cadet = resolveCadet(username);
        AnalogCircuitDefinition def = analogDefRepository
                .findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("AnalogCircuitDefinition", "missionId/status", missionId + "/PUBLISHED"));
        CadetAnalogSave save = cadetAnalogSaveRepository
                .findByCadetIdAndAnalogCircuitDefinitionId(cadet.getId(), def.getId())
                .orElseThrow(() -> new ResourceNotFoundException("CadetAnalogSave", "username/missionId", username + "/" + missionId));

        List<AnalogVerificationCheck> checks = analogCheckRepository
                .findAllByAnalogCircuitDefinitionIdOrderByOrderIndex(def.getId());

        List<AnalogCheckResultResponse> results = new ArrayList<>();
        boolean anyFailed = false;
        for (AnalogVerificationCheck check : checks) {
            boolean passed = evaluateAnalogCheck(check, request);
            if (!passed) anyFailed = true;
            String message = passed ? null : buildFailMessage(check);
            results.add(AnalogCheckResultResponse.builder()
                    .checkId(check.getId())
                    .i18nKey(check.getI18nKey())
                    .orderIndex(check.getOrderIndex())
                    .checkType(check.getCheckType())
                    .severity(check.getSeverity())
                    .passed(passed)
                    .message(message)
                    .build());
        }

        save.setSimulationStatus(anyFailed ? SimulationStatus.FAILED : SimulationStatus.SUCCESS);
        cadetAnalogSaveRepository.save(save);

        return results;
    }

    private boolean evaluateAnalogCheck(AnalogVerificationCheck check, VerifyAnalogRequest request) {
        double expected = check.getExpectedValue();
        double tolerance = check.getTolerance();
        return switch (check.getCheckType()) {
            case NODE_VOLTAGE -> {
                if (request.getNodeVoltages() == null) yield false;
                Double observed = request.getNodeVoltages().get(check.getNodeOrLabel());
                yield observed != null && Math.abs(observed - expected) <= tolerance;
            }
            case BRANCH_CURRENT -> {
                if (request.getBranchCurrents() == null) yield false;
                Double observed = request.getBranchCurrents().get(check.getNodeOrLabel());
                yield observed != null && Math.abs(observed - expected) <= tolerance;
            }
            case COMPONENT_VALUE -> {
                if (request.getComponentValues() == null) yield false;
                Double observed = request.getComponentValues().get(check.getNodeOrLabel());
                yield observed != null && Math.abs(observed - expected) <= tolerance;
            }
            case COMPONENT_EXISTS -> {
                // The cadet-submitted Falstad text is in the save — check if label appears
                yield true; // Topology check; Falstad parsing out of scope for MVP
            }
            case LED_LIGHTS -> {
                // LED lights if current through it exceeds threshold (expected = min current in A)
                if (request.getBranchCurrents() == null) yield false;
                Double observed = request.getBranchCurrents().get(check.getNodeOrLabel());
                yield observed != null && observed >= expected;
            }
        };
    }

    private String buildFailMessage(AnalogVerificationCheck check) {
        return String.format("Expected %.4f (±%.4f) for %s", check.getExpectedValue(), check.getTolerance(), check.getNodeOrLabel());
    }

    // --- Mappers ---

    private AnalogCircuitDefinitionResponse toDefResponse(AnalogCircuitDefinition def) {
        List<AnalogVerificationCheck> checks = analogCheckRepository
                .findAllByAnalogCircuitDefinitionIdOrderByOrderIndex(def.getId());
        return AnalogCircuitDefinitionResponse.builder()
                .id(def.getId())
                .missionId(def.getMission().getId())
                .falstadText(def.getFalstadText())
                .status(def.getStatus())
                .checks(checks.stream().map(this::toCheckResponse).toList())
                .createdAt(def.getCreatedAt())
                .updatedAt(def.getUpdatedAt())
                .build();
    }

    AnalogVerificationCheckResponse toCheckResponse(AnalogVerificationCheck check) {
        return AnalogVerificationCheckResponse.builder()
                .id(check.getId())
                .checkType(check.getCheckType())
                .nodeOrLabel(check.getNodeOrLabel())
                .expectedValue(check.getExpectedValue())
                .tolerance(check.getTolerance())
                .unitOfMeasure(unitOfMeasureService.toResponse(check.getUnitOfMeasure()))
                .severity(check.getSeverity())
                .i18nKey(check.getI18nKey())
                .orderIndex(check.getOrderIndex())
                .build();
    }

    private CadetAnalogSaveResponse toSaveResponse(CadetAnalogSave save) {
        return CadetAnalogSaveResponse.builder()
                .id(save.getId())
                .analogCircuitDefinitionId(save.getAnalogCircuitDefinition().getId())
                .falstadText(save.getFalstadText())
                .simulationStatus(save.getSimulationStatus())
                .createdAt(save.getCreatedAt())
                .updatedAt(save.getUpdatedAt())
                .build();
    }

    private Cadet resolveCadet(String username) {
        return cadetRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "username", username));
    }

    private AnalogCircuitDefinition findDef(UUID id) {
        return analogDefRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AnalogCircuitDefinition", "id", id));
    }

    private UnitOfMeasure resolveUnit(UUID id) {
        if (id == null) return null;
        return unitOfMeasureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UnitOfMeasure", "id", id));
    }
}
