package com.legymernok.backend.dto.user;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateThemePreferenceRequest {

    @Pattern(regexp = "SPACE|DARK|LIGHT", message = "theme must be one of SPACE, DARK, LIGHT")
    private String theme;
}
