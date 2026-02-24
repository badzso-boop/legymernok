package com.legymernok.backend.web.mission;

import com.legymernok.backend.model.mission.VerificationStatus;
import com.legymernok.backend.service.mission.MissionLogService;
import com.legymernok.backend.service.mission.MissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.UUID;

@RestController
@RequestMapping("/api/mission-verification")
@RequiredArgsConstructor
@Slf4j
public class MissionVerificationController {

    private final MissionService missionService;
    private final MissionLogService missionLogService;

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

    @PostMapping(value = "/{missionId}/logs", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Void> streamLogs(
            @PathVariable UUID missionId,
            @RequestHeader("X-Verification-Secret") String secret,
            HttpServletRequest request) {

        if (!verificationSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                missionLogService.sendMissionLog(missionId, line);
            }
        } catch (IOException e) {
            log.error("Error reading log stream for mission {}", missionId, e);
        }

        return ResponseEntity.ok().build();
    }
}
