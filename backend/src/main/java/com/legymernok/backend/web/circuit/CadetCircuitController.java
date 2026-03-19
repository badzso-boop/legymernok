package com.legymernok.backend.web.circuit;

import com.legymernok.backend.dto.circuit.CadetCircuitSaveResponse;
import com.legymernok.backend.dto.circuit.CadetVerificationResultResponse;
import com.legymernok.backend.dto.circuit.SaveCadetCircuitRequest;
import com.legymernok.backend.dto.circuit.VerifyBehaviorRequest;
import com.legymernok.backend.service.circuit.ArduinoCompilerService;
import com.legymernok.backend.service.circuit.CadetCircuitService;
import com.legymernok.backend.service.circuit.CircuitVerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/circuit/missions")
@RequiredArgsConstructor
public class CadetCircuitController {

    private final CadetCircuitService cadetCircuitService;
    private final CircuitVerificationService circuitVerificationService;
    private final ArduinoCompilerService arduinoCompilerService;

    @PostMapping("/{missionId}/start")
    @PreAuthorize("hasAuthority('circuit:simulate')")
    public ResponseEntity<CadetCircuitSaveResponse> start(@PathVariable UUID missionId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return new ResponseEntity<>(cadetCircuitService.startCircuitMission(username, missionId), HttpStatus.OK);
    }

    @GetMapping("/{missionId}")
    @PreAuthorize("hasAuthority('circuit:simulate')")
    public ResponseEntity<CadetCircuitSaveResponse> get(@PathVariable UUID missionId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(cadetCircuitService.getCadetCircuitSave(username, missionId));
    }

    @PutMapping("/{missionId}/canvas")
    @PreAuthorize("hasAuthority('circuit:simulate')")
    public ResponseEntity<CadetCircuitSaveResponse> saveCanvas(@PathVariable UUID missionId,
                                                                @Valid @RequestBody SaveCadetCircuitRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(cadetCircuitService.saveCanvas(username, missionId, request));
    }

    /**
     * Triggers async compilation of the cadet's sketch.ino from the Gitea repo.
     * Returns 202 Accepted immediately. Poll GET /{missionId} and check
     * {@code simulationStatus}: COMPILING → in progress, NEVER_RUN → success,
     * COMPILE_ERROR → failure (see {@code lastCompileError}).
     */
    @PostMapping("/{missionId}/compile")
    @PreAuthorize("hasAuthority('circuit:simulate')")
    public ResponseEntity<Void> compile(@PathVariable UUID missionId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        arduinoCompilerService.compile(username, missionId); // fire-and-forget
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{missionId}/verify")
    @PreAuthorize("hasAuthority('circuit:simulate')")
    public ResponseEntity<List<CadetVerificationResultResponse>> verify(@PathVariable UUID missionId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(circuitVerificationService.verifyTopology(username, missionId));
    }

    /**
     * Phase 2 verification: checks GPIO pin states, serial output, and PWM duty cycles
     * against the expected values defined in the CircuitDefinition's verification checks.
     * Called after the cadet has run the avr8js simulation in the browser.
     */
    @PostMapping("/{missionId}/verify-behavior")
    @PreAuthorize("hasAuthority('circuit:simulate')")
    public ResponseEntity<List<CadetVerificationResultResponse>> verifyBehavior(
            @PathVariable UUID missionId,
            @RequestBody VerifyBehaviorRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(circuitVerificationService.verifyBehavior(username, missionId, request));
    }
}
