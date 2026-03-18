package com.legymernok.backend.service.circuit;

import com.legymernok.backend.dto.circuit.CadetVerificationResultResponse;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.circuit.*;
import com.legymernok.backend.repository.circuit.CircuitDefinitionRepository;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.circuit.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Verifies a cadet's circuit against the admin-defined checks.
 *
 * Phase 1 (instant, no simulation):
 *   - CIRCUIT_TOPOLOGY: component with the given label exists on the canvas
 *   - PATH_EXISTS: BFS over component-level adjacency graph
 *
 * Phase 2 (deferred, after avr8js simulation):
 *   - GPIO_BEHAVIOR, SERIAL_OUTPUT, PWM — skipped here, marked as passed=false pending simulation
 */
@Service
@RequiredArgsConstructor
public class CircuitVerificationService {

    private final CadetCircuitSaveRepository saveRepository;
    private final CadetRepository cadetRepository;
    private final CircuitDefinitionRepository circuitDefinitionRepository;
    private final CadetCircuitComponentRepository componentRepository;
    private final CadetCircuitConnectionRepository connectionRepository;
    private final CircuitVerificationCheckRepository checkRepository;
    private final CadetVerificationResultRepository resultRepository;

    @Transactional
    public List<CadetVerificationResultResponse> verifyTopology(String username, UUID missionId) {
        UUID cadetId = cadetRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "username", username))
                .getId();
        CircuitDefinition definition = circuitDefinitionRepository
                .findByMissionIdAndStatus(missionId, CircuitDefinitionStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException("CircuitDefinition", "missionId/status", missionId + "/PUBLISHED"));
        UUID cadetCircuitSaveId = saveRepository.findByCadetIdAndCircuitDefinitionId(cadetId, definition.getId())
                .orElseThrow(() -> new ResourceNotFoundException("CadetCircuitSave", "username/missionId", username + "/" + missionId))
                .getId();
        return verifyTopology(cadetCircuitSaveId);
    }

    @Transactional
    public List<CadetVerificationResultResponse> verifyTopology(UUID cadetCircuitSaveId) {
        CadetCircuitSave save = saveRepository.findById(cadetCircuitSaveId)
                .orElseThrow(() -> new ResourceNotFoundException("CadetCircuitSave", "id", cadetCircuitSaveId));

        UUID definitionId = save.getCircuitDefinition().getId();

        // Load everything into memory — single transaction, no lazy load surprises
        List<CadetCircuitComponent> components = componentRepository.findAllByCadetCircuitSaveId(cadetCircuitSaveId);
        List<CadetCircuitConnection> connections = connectionRepository.findAllByCadetCircuitSaveId(cadetCircuitSaveId);
        List<CircuitVerificationCheck> checks = checkRepository.findAllByCircuitDefinitionIdOrderByOrderIndex(definitionId);

        // label → component UUID (O(1) lookup)
        Map<String, UUID> labelToId = components.stream()
                .collect(Collectors.toMap(CadetCircuitComponent::getLabel, CadetCircuitComponent::getId));

        // Component-level adjacency list for BFS (undirected)
        Map<UUID, Set<UUID>> adjacency = buildAdjacency(connections);

        // Delete previous topology results (keep simulation-phase results if any)
        resultRepository.deleteAllByCadetCircuitSaveId(cadetCircuitSaveId);

        List<CadetVerificationResult> saved = new ArrayList<>();
        for (CircuitVerificationCheck check : checks) {
            boolean passed = switch (check.getCheckType()) {
                case CIRCUIT_TOPOLOGY -> checkComponentExists(check.getLabelFrom(), labelToId);
                case PATH_EXISTS -> checkPathExists(check.getLabelFrom(), check.getLabelTo(), labelToId, adjacency);
                // Simulation-phase checks: deferred — not passed until simulation runs
                case GPIO_BEHAVIOR, SERIAL_OUTPUT, PWM -> false;
            };

            saved.add(resultRepository.save(CadetVerificationResult.builder()
                    .cadetCircuitSave(save)
                    .check(check)
                    .passed(passed)
                    .build()));
        }

        Map<UUID, CircuitVerificationCheck> checksById = checks.stream()
                .collect(Collectors.toMap(CircuitVerificationCheck::getId, c -> c));

        return saved.stream().map(r -> toResponse(r, checksById)).toList();
    }

    // --- Check implementations ---

    private boolean checkComponentExists(String label, Map<String, UUID> labelToId) {
        return label != null && labelToId.containsKey(label);
    }

    private boolean checkPathExists(String fromLabel, String toLabel,
                                    Map<String, UUID> labelToId, Map<UUID, Set<UUID>> adjacency) {
        if (fromLabel == null || toLabel == null) return false;
        UUID fromId = labelToId.get(fromLabel);
        UUID toId = labelToId.get(toLabel);
        if (fromId == null || toId == null) return false;
        if (fromId.equals(toId)) return true;

        // BFS
        Set<UUID> visited = new HashSet<>();
        Queue<UUID> queue = new LinkedList<>();
        queue.add(fromId);
        visited.add(fromId);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (UUID neighbor : adjacency.getOrDefault(current, Set.of())) {
                if (neighbor.equals(toId)) return true;
                if (visited.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return false;
    }

    // --- Adjacency graph ---

    private Map<UUID, Set<UUID>> buildAdjacency(List<CadetCircuitConnection> connections) {
        Map<UUID, Set<UUID>> graph = new HashMap<>();
        for (CadetCircuitConnection conn : connections) {
            graph.computeIfAbsent(conn.getFromComponentId(), k -> new HashSet<>()).add(conn.getToComponentId());
            graph.computeIfAbsent(conn.getToComponentId(), k -> new HashSet<>()).add(conn.getFromComponentId());
        }
        return graph;
    }

    // --- Mapper ---

    private CadetVerificationResultResponse toResponse(CadetVerificationResult result,
                                                        Map<UUID, CircuitVerificationCheck> checksById) {
        CircuitVerificationCheck check = checksById.get(result.getCheck().getId());
        return CadetVerificationResultResponse.builder()
                .checkId(result.getCheck().getId())
                .i18nKey(check != null ? check.getI18nKey() : null)
                .orderIndex(check != null ? check.getOrderIndex() : null)
                .severity(check != null ? check.getSeverity() : null)
                .passed(result.isPassed())
                .message(result.getMessage())
                .checkedAt(result.getCheckedAt())
                .build();
    }
}
