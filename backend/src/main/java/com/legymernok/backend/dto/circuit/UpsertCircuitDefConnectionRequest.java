package com.legymernok.backend.dto.circuit;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpsertCircuitDefConnectionRequest {
    @NotBlank
    private String fromLabel;

    @NotBlank
    private String fromPinName;

    @NotBlank
    private String toLabel;

    @NotBlank
    private String toPinName;
}
