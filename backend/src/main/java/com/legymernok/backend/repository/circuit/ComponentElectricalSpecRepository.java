package com.legymernok.backend.repository.circuit;

import com.legymernok.backend.model.circuit.ComponentElectricalSpec;
import com.legymernok.backend.model.circuit.ComponentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComponentElectricalSpecRepository extends JpaRepository<ComponentElectricalSpec, UUID> {
    List<ComponentElectricalSpec> findAllByComponentType(ComponentType componentType);
}
