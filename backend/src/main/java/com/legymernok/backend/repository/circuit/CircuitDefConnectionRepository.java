package com.legymernok.backend.repository.circuit;

import com.legymernok.backend.model.circuit.CircuitDefConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CircuitDefConnectionRepository extends JpaRepository<CircuitDefConnection, UUID> {
    List<CircuitDefConnection> findAllByCircuitDefinitionId(UUID circuitDefinitionId);
    void deleteAllByCircuitDefinitionId(UUID circuitDefinitionId);
}
