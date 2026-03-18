package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.AnalogCheckType;
import com.legymernok.backend.model.circuit.ValidationSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateAnalogVerificationCheckRequest {
    @NotNull
    private AnalogCheckType checkType;

    @NotBlank
    private String nodeOrLabel;

    @NotNull
    private Double expectedValue;

    @NotNull
    private Double tolerance;

    private UUID unitOfMeasureId;

    @NotNull
    private ValidationSeverity severity;

    @NotBlank
    private String i18nKey;

    @NotNull
    private Integer orderIndex;
}
