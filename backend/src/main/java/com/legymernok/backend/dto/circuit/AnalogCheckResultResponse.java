package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.AnalogCheckType;
import com.legymernok.backend.model.circuit.ValidationSeverity;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class AnalogCheckResultResponse {
    private UUID checkId;
    private String i18nKey;
    private Integer orderIndex;
    private AnalogCheckType checkType;
    private ValidationSeverity severity;
    private boolean passed;
    private String message;
}
