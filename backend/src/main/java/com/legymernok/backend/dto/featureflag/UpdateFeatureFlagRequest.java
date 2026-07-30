package com.legymernok.backend.dto.featureflag;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateFeatureFlagRequest {
    @NotNull
    private Boolean enabled;

    @Size(max = 500)
    private String description;
}
