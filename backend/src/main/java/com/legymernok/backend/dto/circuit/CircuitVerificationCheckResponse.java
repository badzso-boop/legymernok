package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.CheckType;
import com.legymernok.backend.model.circuit.ValidationSeverity;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CircuitVerificationCheckResponse {
    private UUID id;
    private CheckType checkType;
    private String labelFrom;
    private String labelTo;
    private String pinFrom;
    private String pinTo;
    private String expectedValue;
    private UnitOfMeasureResponse unitOfMeasure;
    private ValidationSeverity severity;
    private String i18nKey;
    private Integer orderIndex;
}
