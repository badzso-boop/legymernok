package com.legymernok.backend.repository.circuit;

import com.legymernok.backend.model.circuit.CadetCircuitSave;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CadetCircuitSaveRepository extends JpaRepository<CadetCircuitSave, UUID> {
    Optional<CadetCircuitSave> findByCadetIdAndCircuitDefinitionId(UUID cadetId, UUID circuitDefinitionId);
    List<CadetCircuitSave> findAllByCadetId(UUID cadetId);
    List<CadetCircuitSave> findAllByCircuitDefinitionId(UUID circuitDefinitionId);
    void deleteAllByCircuitDefinitionId(UUID circuitDefinitionId);

    // Used by verification service: loads save + definition in one query
    @EntityGraph(attributePaths = {"circuitDefinition", "cadet"})
    Optional<CadetCircuitSave> findById(UUID id);

}
