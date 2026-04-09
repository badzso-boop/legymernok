package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.BoardType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCircuitDefinitionRequest {
    @NotNull
    private UUID missionId;

    @NotNull
    private BoardType boardType;
}
