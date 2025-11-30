package com.legymernok.backend.dto;

import com.legymernok.backend.model.Difficulty;
import com.legymernok.backend.model.MissionType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class MissionResponse {
    private UUID id;
    private UUID starSystemId;
    private String name;
    private String descriptionMarkdown;
    private String templateRepositoryUrl;
    private MissionType missionType;
    private Difficulty difficulty;
    private Integer orderInSystem;
    private Instant createdAt;
}