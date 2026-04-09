package com.legymernok.backend.dto.circuit;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveCadetAnalogRequest {
    @NotBlank
    private String falstadText;
}
