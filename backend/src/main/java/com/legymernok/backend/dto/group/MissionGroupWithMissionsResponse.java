package com.legymernok.backend.dto.group;

import com.legymernok.backend.dto.mission.MissionResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class MissionGroupWithMissionsResponse {

    private UUID id;
    private String name;
    private String description;
    private UUID starSystemId;
    private String starSystemName;
    private Integer orderIndex;
    private List<MissionResponse> missions;
}
