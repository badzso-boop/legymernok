package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.SimulationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CadetCircuitSaveResponse {
    private UUID id;
    private UUID circuitDefinitionId;
    private SimulationStatus simulationStatus;
    private boolean stale;
    private List<CadetCircuitComponentResponse> components;
    private List<CadetCircuitConnectionResponse> connections;
    private List<CadetVerificationResultResponse> verificationResults;
    private Instant createdAt;
    private Instant updatedAt;
}
