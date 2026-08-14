package com.legymernok.backend.dto.dashboard;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ContinueResponse {
    private String type; // "MISSION" | "GROUP"
    private UUID missionId; // type == MISSION esetén
    private UUID groupId; // type == GROUP esetén
    private UUID starSystemId;
    private String name;
}
