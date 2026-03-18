package com.legymernok.backend.repository.circuit;

import com.legymernok.backend.model.circuit.AnalogVerificationCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnalogVerificationCheckRepository extends JpaRepository<AnalogVerificationCheck, UUID> {
    List<AnalogVerificationCheck> findAllByAnalogCircuitDefinitionIdOrderByOrderIndex(UUID analogCircuitDefinitionId);
    void deleteAllByAnalogCircuitDefinitionId(UUID analogCircuitDefinitionId);
}
