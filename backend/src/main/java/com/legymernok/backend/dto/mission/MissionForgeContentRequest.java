package com.legymernok.backend.dto.mission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class MissionForgeContentRequest {
    // Szándékosan NINCS @NotNull: a kliens sosem küldi ezt a mezőt a body-ban —
    // a controller (saveForgeMissionContent) a path variable-ből tölti ki
    // @Valid UTÁN futó request.setMissionId(missionId) hívással. Ha ide
    // @NotNull kerülne, minden hívás 400-at dobna, mielőtt a controller
    // beállíthatná az értéket.
    private UUID missionId;
    @NotNull
    private Map<String, String> files;
}
