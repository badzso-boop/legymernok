package com.legymernok.backend.dto.circuit;

import com.legymernok.backend.model.circuit.ValidationSeverity;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CadetVerificationResultResponse {
    private UUID checkId;
    private String i18nKey;
    private Integer orderIndex;
    private boolean passed;
    private String message;
    private ValidationSeverity severity;
    private Instant checkedAt;
}
