 package com.legymernok.backend.dto.mission;

import com.legymernok.backend.model.mission.Difficulty;
import com.legymernok.backend.model.mission.MissionType;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateMissionRequest {
    private UUID starSystemId;
    private String name;
    private String descriptionMarkdown;
    private String templateRepositoryUrl;
    private MissionType missionType;
    private Difficulty difficulty;
    private Integer orderInSystem;
}