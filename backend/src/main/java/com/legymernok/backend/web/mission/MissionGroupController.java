package com.legymernok.backend.web.mission;

import com.legymernok.backend.dto.group.*;
import com.legymernok.backend.service.mission.MissionGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/mission-groups")
@RequiredArgsConstructor
public class MissionGroupController {

    private final MissionGroupService missionGroupService;

    @PostMapping
    @PreAuthorize("hasAuthority('group:create')")
    public ResponseEntity<MissionGroupResponse> createGroup(@Valid @RequestBody CreateMissionGroupRequest request) {
        return new ResponseEntity<>(missionGroupService.createGroup(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('group:read')")
    public ResponseEntity<MissionGroupWithMissionsResponse> getGroupWithMissions(@PathVariable UUID id) {
        return ResponseEntity.ok(missionGroupService.getGroupWithMissions(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('group:edit')")
    public ResponseEntity<MissionGroupResponse> updateGroup(
            @PathVariable UUID id,
            @Valid @RequestBody CreateMissionGroupRequest request) {
        return ResponseEntity.ok(missionGroupService.updateGroup(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('group:delete')")
    public ResponseEntity<Void> deleteGroup(@PathVariable UUID id) {
        missionGroupService.deleteGroup(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/{id}/missions")
    @PreAuthorize("hasAuthority('group:edit')")
    public ResponseEntity<MissionGroupResponse> addMissionToGroupByBody(
            @PathVariable UUID id,
            @RequestBody java.util.Map<String, UUID> body) {
        UUID missionId = body.get("missionId");
        return ResponseEntity.ok(missionGroupService.addMissionToGroup(id, missionId));
    }

    @PostMapping("/{id}/missions/{missionId}")
    @PreAuthorize("hasAuthority('group:edit')")
    public ResponseEntity<MissionGroupResponse> addMissionToGroup(
            @PathVariable UUID id,
            @PathVariable UUID missionId) {
        return ResponseEntity.ok(missionGroupService.addMissionToGroup(id, missionId));
    }

    @DeleteMapping("/{id}/missions/{missionId}")
    @PreAuthorize("hasAuthority('group:edit')")
    public ResponseEntity<MissionGroupResponse> removeMissionFromGroup(
            @PathVariable UUID id,
            @PathVariable UUID missionId) {
        return ResponseEntity.ok(missionGroupService.removeMissionFromGroup(id, missionId));
    }

    @PutMapping("/{id}/reorder")
    @PreAuthorize("hasAuthority('group:edit')")
    public ResponseEntity<ReorderResponse> reorderGroupByBody(
            @PathVariable UUID id,
            @Valid @RequestBody ReorderItemRequest request) {
        return ResponseEntity.ok(missionGroupService.reorderGroup(id, request.getTargetId()));
    }

    @PostMapping("/{id}/reorder/{targetId}")
    @PreAuthorize("hasAuthority('group:edit')")
    public ResponseEntity<ReorderResponse> reorderGroup(
            @PathVariable UUID id,
            @PathVariable UUID targetId) {
        return ResponseEntity.ok(missionGroupService.reorderGroup(id, targetId));
    }

    @PutMapping("/{id}/missions/{missionId}/reorder")
    @PreAuthorize("hasAuthority('group:edit')")
    public ResponseEntity<ReorderResponse> reorderMissionInGroupByBody(
            @PathVariable UUID id,
            @PathVariable UUID missionId,
            @Valid @RequestBody ReorderItemRequest request) {
        return ResponseEntity.ok(missionGroupService.reorderMissionInGroup(id, missionId, request.getTargetId()));
    }

    @PostMapping("/{id}/missions/{missionId}/reorder/{targetMissionId}")
    @PreAuthorize("hasAuthority('group:edit')")
    public ResponseEntity<ReorderResponse> reorderMissionInGroup(
            @PathVariable UUID id,
            @PathVariable UUID missionId,
            @PathVariable UUID targetMissionId) {
        return ResponseEntity.ok(missionGroupService.reorderMissionInGroup(id, missionId, targetMissionId));
    }
}
