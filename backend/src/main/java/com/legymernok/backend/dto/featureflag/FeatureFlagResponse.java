package com.legymernok.backend.dto.featureflag;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class FeatureFlagResponse {
    private UUID id;
    private String key;
    private boolean enabled;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
}
