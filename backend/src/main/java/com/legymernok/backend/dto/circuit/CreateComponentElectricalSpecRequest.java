package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.ComponentType;
import com.legymernok.backend.model.circuit.ElectricalValidationType;
import com.legymernok.backend.model.circuit.ValidationSeverity;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateComponentElectricalSpecRequest {
    @NotNull
    private ComponentType componentType;

    @NotNull
    private ElectricalValidationType validationType;

    @NotNull
    private Double value;

    private UUID unitOfMeasureId;

    @NotNull
    private ValidationSeverity severity;
}
