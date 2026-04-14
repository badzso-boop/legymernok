package com.legymernok.backend.dto.group;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class GroupProgressResponse {

    private UUID groupId;
    private boolean completed;
    private UUID nextMissionId;
    private int completedCount;
    private int totalCount;
    private Instant startedAt;
    private Instant lastUpdatedAt;
    private Instant completedAt;
}
