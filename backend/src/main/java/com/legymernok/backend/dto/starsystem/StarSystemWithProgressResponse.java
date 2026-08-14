package com.legymernok.backend.dto.starsystem;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class StarSystemWithProgressResponse {
    private UUID id;
    private String name;
    private String description;
    private String iconUrl;
    private Instant createdAt;
    private Instant updatedAt;
    private String status; // "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED"
}
