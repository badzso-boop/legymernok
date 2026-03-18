package com.legymernok.backend.dto.circuit;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateUnitOfMeasureRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String symbol;
}
