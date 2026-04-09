package com.legymernok.backend.repository.circuit;

import com.legymernok.backend.model.circuit.UnitOfMeasure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnitOfMeasureRepository extends JpaRepository<UnitOfMeasure, UUID> {
    Optional<UnitOfMeasure> findBySymbol(String symbol);
    boolean existsByNameOrSymbol(String name, String symbol);
}
