package com.legymernok.backend.repository.circuit;

import com.legymernok.backend.model.circuit.CircuitDefComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CircuitDefComponentRepository extends JpaRepository<CircuitDefComponent, UUID> {
    List<CircuitDefComponent> findAllByCircuitDefinitionId(UUID circuitDefinitionId);
    Optional<CircuitDefComponent> findByCircuitDefinitionIdAndLabel(UUID circuitDefinitionId, String label);
    void deleteAllByCircuitDefinitionId(UUID circuitDefinitionId);
}
