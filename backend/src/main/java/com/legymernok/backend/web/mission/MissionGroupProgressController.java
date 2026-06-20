package com.legymernok.backend.web.mission;

import com.legymernok.backend.dto.group.GroupProgressResponse;
import com.legymernok.backend.service.mission.MissionGroupProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/group-progress")
@RequiredArgsConstructor
public class MissionGroupProgressController {

    private final MissionGroupProgressService missionGroupProgressService;

    @GetMapping("/{groupId}")
    @PreAuthorize("hasAuthority('group:read')")
    public ResponseEntity<GroupProgressResponse> getProgress(@PathVariable UUID groupId) {
        return ResponseEntity.ok(missionGroupProgressService.getProgress(groupId));
    }

    @PostMapping("/{groupId}/start")
    @PreAuthorize("hasAuthority('group:read')")
    public ResponseEntity<GroupProgressResponse> startProgress(@PathVariable UUID groupId) {
        return ResponseEntity.ok(missionGroupProgressService.startProgress(groupId));
    }

    @PostMapping("/{groupId}/complete-step")
    @PreAuthorize("hasAuthority('group:read')")
    public ResponseEntity<GroupProgressResponse> completeStep(@PathVariable UUID groupId) {
        return ResponseEntity.ok(missionGroupProgressService.completeStep(groupId));
    }
}
