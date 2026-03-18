package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.CircuitDefinitionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AnalogCircuitDefinitionResponse {
    private UUID id;
    private UUID missionId;
    private String falstadText;
    private CircuitDefinitionStatus status;
    private List<AnalogVerificationCheckResponse> checks;
    private Instant createdAt;
    private Instant updatedAt;
}
