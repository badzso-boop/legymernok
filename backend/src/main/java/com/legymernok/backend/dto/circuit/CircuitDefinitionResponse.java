package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.BoardType;
import com.legymernok.backend.model.circuit.CircuitDefinitionStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CircuitDefinitionResponse {
    private UUID id;
    private UUID missionId;
    private BoardType boardType;
    private CircuitDefinitionStatus status;
    private List<CircuitDefComponentResponse> components;
    private List<CircuitDefConnectionResponse> connections;
    private List<CircuitVerificationCheckResponse> checks;
    private Instant createdAt;
    private Instant updatedAt;
}
