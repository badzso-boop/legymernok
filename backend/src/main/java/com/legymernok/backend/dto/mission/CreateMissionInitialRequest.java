package com.legymernok.backend.dto.mission;

import com.legymernok.backend.model.mission.Difficulty;
import com.legymernok.backend.model.mission.MissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateMissionInitialRequest {
    @NotNull
    private UUID starSystemId;
    @NotBlank
    private String name;
    private String descriptionMarkdown;
    @NotNull
    private MissionType missionType;
    @NotNull
    private Difficulty difficulty;
    @NotNull
    private Integer orderInSystem;
    @NotBlank
    private String templateLanguage;
}
