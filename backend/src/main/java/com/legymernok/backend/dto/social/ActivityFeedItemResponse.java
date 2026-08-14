package com.legymernok.backend.dto.social;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ActivityFeedItemResponse {
    private UUID cadetId;
    private String cadetUsername;
    private String type; // "GROUP_STEP" | "FILL_IN_BLANK" | "QUIZ"
    private String label; // A misszió/csoport neve, megjelenítéshez
    private Instant occurredAt;
}
