package com.legymernok.backend.dto.sector;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSectorRequest {
    @NotBlank
    private String name;
    private String description;
    private String iconUrl;
}
