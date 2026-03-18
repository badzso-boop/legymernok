package com.legymernok.backend.repository.circuit;

import com.legymernok.backend.model.circuit.CircuitDefComponentProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

@Repository
public interface CircuitDefComponentPropertyRepository extends JpaRepository<CircuitDefComponentProperty, UUID> {
    List<CircuitDefComponentProperty> findAllByComponentId(UUID componentId);
    void deleteAllByComponentId(UUID componentId);

    @Modifying
    @Query("DELETE FROM CircuitDefComponentProperty p WHERE p.component.circuitDefinition.id = :definitionId")
    void deleteAllByCircuitDefinitionId(@Param("definitionId") UUID definitionId);
}
