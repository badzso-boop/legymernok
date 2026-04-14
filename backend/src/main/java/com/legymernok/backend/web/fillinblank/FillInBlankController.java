package com.legymernok.backend.web.fillinblank;

import com.legymernok.backend.dto.fillinblank.*;
import com.legymernok.backend.service.fillinblank.FillInBlankService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
public class FillInBlankController {

    private final FillInBlankService fillInBlankService;

    @PostMapping("/{missionId}/fill-in-blank")
    @PreAuthorize("hasAuthority('mission:edit')")
    public ResponseEntity<FillInBlankUserResponse> createDefinition(
            @PathVariable UUID missionId,
            @Valid @RequestBody SaveFillInBlankRequest request) {
        return new ResponseEntity<>(fillInBlankService.saveDefinition(missionId, request), HttpStatus.CREATED);
    }

    @PutMapping("/{missionId}/fill-in-blank")
    @PreAuthorize("hasAuthority('mission:edit')")
    public ResponseEntity<FillInBlankUserResponse> updateDefinition(
            @PathVariable UUID missionId,
            @Valid @RequestBody SaveFillInBlankRequest request) {
        return ResponseEntity.ok(fillInBlankService.saveDefinition(missionId, request));
    }

    @GetMapping("/{missionId}/fill-in-blank/admin")
    @PreAuthorize("hasAuthority('mission:edit')")
    public ResponseEntity<SaveFillInBlankRequest> getDefinitionForAdmin(@PathVariable UUID missionId) {
        return ResponseEntity.ok(fillInBlankService.getDefinitionForAdmin(missionId));
    }

    @GetMapping("/{missionId}/fill-in-blank")
    @PreAuthorize("hasAuthority('mission:read')")
    public ResponseEntity<FillInBlankUserResponse> getDefinitionForUser(@PathVariable UUID missionId) {
        return ResponseEntity.ok(fillInBlankService.getDefinitionForUser(missionId));
    }

    @PostMapping("/{missionId}/fill-in-blank/submit")
    @PreAuthorize("hasAuthority('mission:start')")
    public ResponseEntity<FillInBlankResultResponse> submit(
            @PathVariable UUID missionId,
            @RequestBody SubmitFillInBlankRequest request) {
        return ResponseEntity.ok(fillInBlankService.submit(missionId, request));
    }

    @GetMapping("/{missionId}/fill-in-blank/last-attempt")
    @PreAuthorize("hasAuthority('mission:start')")
    public ResponseEntity<LastAttemptResponse> getLastAttempt(@PathVariable UUID missionId) {
        return fillInBlankService.getLastAttempt(missionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
