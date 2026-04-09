package com.legymernok.backend.repository.circuit;

import com.legymernok.backend.model.circuit.CadetCircuitComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CadetCircuitComponentRepository extends JpaRepository<CadetCircuitComponent, UUID> {
    List<CadetCircuitComponent> findAllByCadetCircuitSaveId(UUID cadetCircuitSaveId);
    Optional<CadetCircuitComponent> findByCadetCircuitSaveIdAndLabel(UUID cadetCircuitSaveId, String label);
    void deleteAllByCadetCircuitSaveId(UUID cadetCircuitSaveId);

    @Modifying
    @Query("DELETE FROM CadetCircuitComponent c WHERE c.cadetCircuitSave.circuitDefinition.id = :definitionId")
    void deleteAllByCircuitDefinitionId(@Param("definitionId") UUID definitionId);
}
