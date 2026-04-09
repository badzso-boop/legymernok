package com.legymernok.backend.repository.circuit;

import com.legymernok.backend.model.circuit.CadetVerificationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

@Repository
public interface CadetVerificationResultRepository extends JpaRepository<CadetVerificationResult, UUID> {
    List<CadetVerificationResult> findAllByCadetCircuitSaveId(UUID cadetCircuitSaveId);
    void deleteAllByCadetCircuitSaveId(UUID cadetCircuitSaveId);

    @Modifying
    @Query("DELETE FROM CadetVerificationResult r WHERE r.cadetCircuitSave.circuitDefinition.id = :definitionId")
    void deleteAllByCircuitDefinitionId(@Param("definitionId") UUID definitionId);
}
