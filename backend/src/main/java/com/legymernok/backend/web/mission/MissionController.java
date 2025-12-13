package com.legymernok.backend.web.mission;

import com.legymernok.backend.dto.mission.CreateMissionRequest;
import com.legymernok.backend.dto.mission.MissionResponse;
import com.legymernok.backend.service.mission.MissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missionService;

    @PostMapping
    public ResponseEntity<MissionResponse> createMission(@RequestBody CreateMissionRequest request) {
        return new ResponseEntity<>(missionService.createMission(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MissionResponse> getMissionById(@PathVariable UUID id) {
        return ResponseEntity.ok(missionService.getMissionById(id));
    }

    @GetMapping
    public ResponseEntity<List<MissionResponse>> getAllMissions(@RequestParam(required = false) UUID starSystemId) {
        if (starSystemId != null) {
            return ResponseEntity.ok(missionService.getMissionsByStarSystem(starSystemId));
        }
        return ResponseEntity.ok(missionService.getAllMissions());
    }
}