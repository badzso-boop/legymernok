package com.legymernok.backend.repository.circuit;

import com.legymernok.backend.model.circuit.BoardType;
import com.legymernok.backend.model.circuit.ComponentPinDefinition;
import com.legymernok.backend.model.circuit.ComponentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComponentPinDefinitionRepository extends JpaRepository<ComponentPinDefinition, UUID> {
    List<ComponentPinDefinition> findAllByComponentType(ComponentType componentType);
    List<ComponentPinDefinition> findAllByComponentTypeAndBoardTypeIsNull(ComponentType componentType);
    List<ComponentPinDefinition> findAllByComponentTypeAndBoardType(ComponentType componentType, BoardType boardType);
    boolean existsByComponentTypeAndBoardTypeAndPinName(ComponentType componentType, BoardType boardType, String pinName);
    boolean existsByComponentTypeAndBoardTypeIsNullAndPinName(ComponentType componentType, String pinName);
}
