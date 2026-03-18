package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.SimulationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CadetAnalogSaveResponse {
    private UUID id;
    private UUID analogCircuitDefinitionId;
    private String falstadText;
    private SimulationStatus simulationStatus;
    private Instant createdAt;
    private Instant updatedAt;
}
