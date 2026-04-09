package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.AnalogCheckType;
import com.legymernok.backend.model.circuit.ValidationSeverity;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AnalogVerificationCheckResponse {
    private UUID id;
    private AnalogCheckType checkType;
    private String nodeOrLabel;
    private Double expectedValue;
    private Double tolerance;
    private UnitOfMeasureResponse unitOfMeasure;
    private ValidationSeverity severity;
    private String i18nKey;
    private Integer orderIndex;
}
