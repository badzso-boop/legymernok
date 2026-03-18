package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.ComponentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpsertCircuitDefComponentRequest {
    @NotNull
    private ComponentType componentType;

    @NotBlank
    private String label;

    @NotNull
    private Integer posX;

    @NotNull
    private Integer posY;

    private List<UpsertCircuitDefPropertyRequest> properties;
}
