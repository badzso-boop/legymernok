package com.legymernok.backend.dto.circuit;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CircuitDefComponentPropertyResponse {
    private UUID id;
    private String propertyKey;
    private String propertyValue;
    private UnitOfMeasureResponse unitOfMeasure;
}
