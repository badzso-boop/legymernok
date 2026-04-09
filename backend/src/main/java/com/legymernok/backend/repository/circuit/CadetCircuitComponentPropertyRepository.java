package com.legymernok.backend.repository.circuit;

import com.legymernok.backend.model.circuit.CadetCircuitComponentProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CadetCircuitComponentPropertyRepository extends JpaRepository<CadetCircuitComponentProperty, UUID> {
    List<CadetCircuitComponentProperty> findAllByCadetComponentId(UUID cadetComponentId);
    List<CadetCircuitComponentProperty> findAllByCadetComponentIdIn(Collection<UUID> cadetComponentIds);
    void deleteAllByCadetComponentId(UUID cadetComponentId);

    @Modifying
    @Query("DELETE FROM CadetCircuitComponentProperty p WHERE p.cadetComponent.cadetCircuitSave.id = :saveId")
    void deleteAllByCadetCircuitSaveId(@Param("saveId") UUID saveId);

    @Modifying
    @Query("DELETE FROM CadetCircuitComponentProperty p WHERE p.cadetComponent.cadetCircuitSave.circuitDefinition.id = :definitionId")
    void deleteAllByCircuitDefinitionId(@Param("definitionId") UUID definitionId);
}
