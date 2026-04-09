package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.ComponentType;
import com.legymernok.backend.model.circuit.ElectricalValidationType;
import com.legymernok.backend.model.circuit.ValidationSeverity;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ComponentElectricalSpecResponse {
    private UUID id;
    private ComponentType componentType;
    private ElectricalValidationType validationType;
    private Double value;
    private UnitOfMeasureResponse unitOfMeasure;
    private ValidationSeverity severity;
}
