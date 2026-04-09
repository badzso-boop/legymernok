package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.BoardType;
import com.legymernok.backend.model.circuit.ComponentType;
import com.legymernok.backend.model.circuit.PinType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateComponentPinDefinitionRequest {
    @NotNull
    private ComponentType componentType;

    private BoardType boardType;

    @NotBlank
    private String pinName;

    @NotNull
    private Integer pinIndex;

    @NotNull
    private PinType pinType;

    private boolean allowMultipleConnections = false;

    private Integer posXOffset;
    private Integer posYOffset;
}
