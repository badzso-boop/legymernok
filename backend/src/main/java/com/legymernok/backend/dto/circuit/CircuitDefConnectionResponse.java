package com.legymernok.backend.dto.circuit;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CircuitDefConnectionResponse {
    private UUID id;
    private UUID fromComponentId;
    private String fromPinName;
    private UUID toComponentId;
    private String toPinName;
}
