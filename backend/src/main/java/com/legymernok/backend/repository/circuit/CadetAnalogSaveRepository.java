package com.legymernok.backend.repository.circuit;

import com.legymernok.backend.model.circuit.CadetAnalogSave;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CadetAnalogSaveRepository extends JpaRepository<CadetAnalogSave, UUID> {
    Optional<CadetAnalogSave> findByCadetIdAndAnalogCircuitDefinitionId(UUID cadetId, UUID analogCircuitDefinitionId);
    List<CadetAnalogSave> findAllByCadetId(UUID cadetId);
}
