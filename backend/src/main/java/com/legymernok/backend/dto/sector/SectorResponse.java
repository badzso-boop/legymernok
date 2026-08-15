package com.legymernok.backend.dto.sector;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class SectorResponse {
    private UUID id;
    private String name;
    private String description;
    private String iconUrl;
    private Integer orderIndex;
    private long starSystemCount;
    private Instant createdAt;
    private Instant updatedAt;
}
