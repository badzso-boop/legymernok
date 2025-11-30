 package com.legymernok.backend.dto;

import com.legymernok.backend.model.Difficulty;
import com.legymernok.backend.model.MissionType;
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