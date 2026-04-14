package com.legymernok.backend.dto.group;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class MissionGroupResponse {

    private UUID id;
    private String name;
    private String description;
    private UUID starSystemId;
    private Integer orderIndex;
    private Instant createdAt;
    private Instant updatedAt;
}
