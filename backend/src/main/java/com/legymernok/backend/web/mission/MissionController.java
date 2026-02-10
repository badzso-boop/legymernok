package com.legymernok.backend.web.mission;

import com.legymernok.backend.dto.mission.*;
import com.legymernok.backend.service.mission.MissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
public class MissionController {

    private final MissionService missionService;

    /**
     * Inicializál egy új missziót a Mission Forge-on keresztül (létrehozza az adatbázis rekordot és a Gitea repót).
     * @param request A misszió alapadatai és a választott nyelv.
     * @return A létrehozott misszió.
     */
    @PostMapping("/forge/initialize")
    @PreAuthorize("hasAuthority('mission:create')")
    public ResponseEntity<MissionResponse> initializeForgeMission(@RequestBody CreateMissionInitialRequest request) {
        MissionResponse newMission = missionService.initializeForgeMission(request);
        return new ResponseEntity<>(newMission, HttpStatus.CREATED);
    }

    /**
     * Menti egy Forge misszió fájljainak tartalmát a Gitea repóba.
     * @param missionId A misszió ID-je.
     * @param request A DTO, ami tartalmazza a fájlok tartalmát.
     * @return A frissített misszió.
     */
    @PostMapping("/{missionId}/forge/save")
    @PreAuthorize("hasAuthority('mission:edit')")
    public ResponseEntity<MissionResponse> saveForgeMissionContent(
            @PathVariable UUID missionId,
            @RequestBody MissionForgeContentRequest request) {
        request.setMissionId(missionId);
        MissionResponse updatedMission = missionService.saveForgeMissionContent(request);
        return ResponseEntity.ok(updatedMission);
    }

    /**
     * Lekéri egy Forge misszió fájljainak tartalmát a Gitea repóból.
     * @param missionId A misszió ID-je.
     * @return Map<String, String>, ahol a kulcs a fájlnév, az érték a fájl tartalma.
     */
    @GetMapping("/{missionId}/forge/files")
    @PreAuthorize("hasAuthority('mission:read')")
    public ResponseEntity<Map<String, String>> getMissionFiles(@PathVariable UUID missionId) {
        Map<String, String> files = missionService.getMissionFiles(missionId);
        return ResponseEntity.ok(files);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('mission:read')")
    public ResponseEntity<MissionResponse> getMissionById(@PathVariable UUID id) {
        return ResponseEntity.ok(missionService.getMissionById(id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('mission:read')")
    public ResponseEntity<List<MissionResponse>> getAllMissions(@RequestParam(required = false) UUID starSystemId) {
        if (starSystemId != null) {
            return ResponseEntity.ok(missionService.getMissionsByStarSystem(starSystemId));
        }
        return ResponseEntity.ok(missionService.getAllMissions());
    }

    @GetMapping("/next-order")
    @PreAuthorize("hasAuthority('mission:create')")
    public ResponseEntity<Integer> getNextOrder(@RequestParam UUID starSystemId) {
        return ResponseEntity.ok(missionService.getNextOrderForStarSystem(starSystemId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('mission:edit')")
    public ResponseEntity<MissionResponse> updateMission(@PathVariable UUID id, @RequestBody
    CreateMissionRequest request) {
        MissionResponse updatedMission = missionService.updateMission(id, request);
        return ResponseEntity.ok(updatedMission);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('mission:delete')")
    public ResponseEntity<Void> deleteMission(@PathVariable UUID id) {
        missionService.deleteMission(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('mission:start')")
    public ResponseEntity<String> startMission(@PathVariable UUID id) {
        // A bejelentkezett user nevét a SecurityContext-ből szedjük ki
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        String repoUrl = missionService.startMission(id, username);
        return ResponseEntity.ok(repoUrl);
    }
}