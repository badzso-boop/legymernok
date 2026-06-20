package com.legymernok.backend.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateMissionGroupRequest {

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private UUID starSystemId;

    private Integer orderIndex;
}
