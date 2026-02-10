package com.legymernok.backend.dto.mission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class MissionForgeContentRequest {
    @NotNull
    private UUID missionId;
    @NotNull
    private Map<String, String> files;
}
