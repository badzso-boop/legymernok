package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.CheckType;
import com.legymernok.backend.model.circuit.ValidationSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCircuitVerificationCheckRequest {
    @NotNull
    private CheckType checkType;

    private String labelFrom;
    private String labelTo;
    private String pinFrom;
    private String pinTo;
    private String expectedValue;
    private UUID unitOfMeasureId;

    @NotNull
    private ValidationSeverity severity;

    @NotBlank
    private String i18nKey;

    @NotNull
    private Integer orderIndex;
}
