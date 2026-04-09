package com.legymernok.backend.dto.circuit;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateAnalogCircuitDefinitionRequest {
    @NotNull
    private UUID missionId;

    @NotBlank
    private String falstadText;
}
