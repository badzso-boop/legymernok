package com.legymernok.backend.dto.sector;

import com.legymernok.backend.model.audience.Audience;
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
    private Audience audience;
    private long starSystemCount;
    private Instant createdAt;
    private Instant updatedAt;
}
