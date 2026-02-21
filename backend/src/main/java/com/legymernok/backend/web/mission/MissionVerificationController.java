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

        VerificationStatus verificationStatus = "SUCCESS".equalsIgnoreCase(status)
                ? VerificationStatus.SUCCESS
                : VerificationStatus.FAILED;

        log.info("Received verification callback for mission {}: {}", missionId, verificationStatus);

        missionService.updateMissionVerificationStatus(missionId, verificationStatus);

        return ResponseEntity.ok().build();
    }
}
