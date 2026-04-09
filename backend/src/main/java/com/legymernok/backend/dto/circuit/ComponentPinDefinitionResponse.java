package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.BoardType;
import com.legymernok.backend.model.circuit.ComponentType;
import com.legymernok.backend.model.circuit.PinType;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ComponentPinDefinitionResponse {
    private UUID id;
    private ComponentType componentType;
    private BoardType boardType;
    private String pinName;
    private Integer pinIndex;
    private PinType pinType;
    private boolean allowMultipleConnections;
    private Integer posXOffset;
    private Integer posYOffset;
}
