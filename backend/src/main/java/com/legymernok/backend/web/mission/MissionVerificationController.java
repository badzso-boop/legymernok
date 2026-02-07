package com.legymernok.backend.web.mission;

import com.legymernok.backend.model.mission.VerificationStatus;
import com.legymernok.backend.service.mission.MissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/mission-verification")
@RequiredArgsConstructor
@Slf4j
public class MissionVerificationController {

    private final MissionService missionService;

    // A titkos kulcs a gitea actionből jön majd
    @Value("${mission.verification.secret}")
    private String verificationSecret;

    @PostMapping("/{missionId}/callback")
    public ResponseEntity<Void> handleVerificationCallback(
            @PathVariable UUID missionId,
            @RequestParam String status,
            @RequestHeader("X-Verification-Secret") String secret) {

        if (!verificationSecret.equals(secret)) {
            log.warn("Unauthorized access to mission verification callback for mission ID: {}. Invalid secret.", missionId);
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        try {
            VerificationStatus verificationStatus = VerificationStatus.valueOf(status.toUpperCase());
            missionService.updateMissionVerificationStatus(missionId, verificationStatus);
            log.info("Mission {} verification status updated to: {}", missionId, verificationStatus);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid verification status received for mission {}: {}", missionId, status);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }
}
