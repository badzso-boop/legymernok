package com.legymernok.backend.repository.circuit;

import com.legymernok.backend.model.circuit.CircuitVerificationCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CircuitVerificationCheckRepository extends JpaRepository<CircuitVerificationCheck, UUID> {
    List<CircuitVerificationCheck> findAllByCircuitDefinitionIdOrderByOrderIndex(UUID circuitDefinitionId);
    void deleteAllByCircuitDefinitionId(UUID circuitDefinitionId);
}
