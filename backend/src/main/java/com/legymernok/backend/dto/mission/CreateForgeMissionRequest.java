package com.legymernok.backend.dto.mission;

import com.legymernok.backend.model.mission.Difficulty;
import com.legymernok.backend.model.mission.MissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CreateForgeMissionRequest {
    @NotNull
    private UUID starSystemId; // Melyik csillagrendszerbe kerül

    @NotBlank
    private String name;

    private String descriptionMarkdown;

    @NotNull
    private MissionType missionType;

    @NotNull
    private Difficulty difficulty;

    @NotNull
    private Integer orderInSystem; // Hova illessze be

    @NotBlank
    private String templateLanguage; // "javascript" vagy "python"

    @NotNull
    private Map<String, String> files; // Fájlnév -> tartalom (pl. solution.js -> "console.log('hi')")
}
