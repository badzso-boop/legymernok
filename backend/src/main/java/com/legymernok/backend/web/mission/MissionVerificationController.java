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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    private static final int MAX_LOG_LINES = 200;
    private static final int MAX_LINE_LENGTH = 500;
    private static final String RESERVED_LOG_PREFIX = "[STATUS_UPDATED]";

    private boolean isValidSecret(String secret) {
        return MessageDigest.isEqual(
                verificationSecret.getBytes(StandardCharsets.UTF_8),
                secret.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/{missionId}/callback")
    public ResponseEntity<Void> handleVerificationCallback(
            @PathVariable UUID missionId,
            @RequestParam String status,
            @RequestHeader("X-Verification-Secret") String secret) {

        if (!isValidSecret(secret)) {
            log.warn("Unauthorized access to mission verification callback for mission ID: {}. Invalid secret.", missionId);
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        VerificationStatus verificationStatus;
        try {
            verificationStatus = VerificationStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid verification status '{}' received for mission {}", status, missionId);
            return ResponseEntity.badRequest().build();
        }

        log.info("Received verification callback for mission {}: {}", missionId, verificationStatus);

        missionService.updateMissionVerificationStatus(missionId, verificationStatus);

        missionLogService.sendMissionLog(missionId, "[STATUS_UPDATED]:" + verificationStatus);

        return ResponseEntity.ok().build();
    }

    @PostMapping(value = "/{missionId}/logs", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<Void> streamLogs(
            @PathVariable UUID missionId,
            @RequestHeader("X-Verification-Secret") String secret,
            HttpServletRequest request) {

        if (!isValidSecret(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream()))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                if (++lineCount > MAX_LOG_LINES) {
                    log.warn("Log stream exceeded max line limit ({}) for mission {}", MAX_LOG_LINES, missionId);
                    break;
                }
                if (line.startsWith(RESERVED_LOG_PREFIX)) {
                    log.warn("Filtered reserved log prefix '{}' in stream for mission {}", RESERVED_LOG_PREFIX, missionId);
                    continue;
                }
                if (line.length() > MAX_LINE_LENGTH) {
                    line = line.substring(0, MAX_LINE_LENGTH) + "...[TRUNCATED]";
                }
                missionLogService.sendMissionLog(missionId, line);
            }
        } catch (IOException e) {
            log.error("Error reading log stream for mission {}", missionId, e);
        }

        return ResponseEntity.ok().build();
    }
}
