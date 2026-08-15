package com.legymernok.backend.dto.starsystem;

import com.legymernok.backend.dto.mission.MissionResponse;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class StarSystemWithMissionResponse {
    private UUID id;
    private String name;
    private String description;
    private String iconUrl;
    private Instant createdAt;
    private Instant updatedAt;
    private List<MissionResponse> missions;
}
