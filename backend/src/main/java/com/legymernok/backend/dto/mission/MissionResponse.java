package com.legymernok.backend.dto.mission;

import com.legymernok.backend.model.mission.Difficulty;
import com.legymernok.backend.model.mission.MissionType;
import com.legymernok.backend.model.mission.VerificationStatus;
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
    private UUID ownerId;
    private String ownerUsername;
    private VerificationStatus verificationStatus;
}