package com.legymernok.backend.repository.circuit;

import com.legymernok.backend.model.circuit.AnalogCircuitDefinition;
import com.legymernok.backend.model.circuit.CircuitDefinitionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalogCircuitDefinitionRepository extends JpaRepository<AnalogCircuitDefinition, UUID> {
    List<AnalogCircuitDefinition> findAllByMissionId(UUID missionId);
    Optional<AnalogCircuitDefinition> findByMissionIdAndStatus(UUID missionId, CircuitDefinitionStatus status);
}
