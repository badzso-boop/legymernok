package com.legymernok.backend.dto.sector;

import com.legymernok.backend.model.audience.Audience;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateSectorRequest {
    @NotBlank
    private String name;
    private String description;
    private String iconUrl;
    /** Létrehozáskor hiányzó érték esetén ENGINEER; frissítéskor hiányzó érték esetén a meglévő marad. */
    private Audience audience;
}
