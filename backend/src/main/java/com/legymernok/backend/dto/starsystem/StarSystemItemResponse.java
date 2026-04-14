package com.legymernok.backend.dto.starsystem;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.legymernok.backend.dto.group.MissionGroupResponse;
import com.legymernok.backend.dto.mission.MissionResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StarSystemItemResponse {

    private String type;           // "GROUP" | "MISSION"
    private Integer orderIndex;
    private MissionResponse mission;
    private MissionGroupResponse group;
    private List<MissionResponse> groupMissions;
}
