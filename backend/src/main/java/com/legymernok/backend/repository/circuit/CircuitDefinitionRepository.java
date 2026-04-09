package com.legymernok.backend.repository.circuit;

import com.legymernok.backend.model.circuit.CircuitDefinition;
import com.legymernok.backend.model.circuit.CircuitDefinitionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CircuitDefinitionRepository extends JpaRepository<CircuitDefinition, UUID> {
    List<CircuitDefinition> findAllByMissionId(UUID missionId);
    Optional<CircuitDefinition> findByMissionIdAndStatus(UUID missionId, CircuitDefinitionStatus status);
}
