package com.legymernok.backend.dto.circuit;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class UpsertCircuitDefPropertyRequest {
    @NotBlank
    private String propertyKey;

    @NotBlank
    private String propertyValue;

    private UUID unitOfMeasureId;
}
