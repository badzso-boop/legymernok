package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.ComponentType;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CircuitDefComponentResponse {
    private UUID id;
    private ComponentType componentType;
    private String label;
    private Integer posX;
    private Integer posY;
    private List<CircuitDefComponentPropertyResponse> properties;
}
