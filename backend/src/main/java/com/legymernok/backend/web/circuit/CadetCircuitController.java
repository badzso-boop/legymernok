package com.legymernok.backend.web.circuit;

import com.legymernok.backend.dto.circuit.CadetCircuitSaveResponse;
import com.legymernok.backend.dto.circuit.CadetVerificationResultResponse;
import com.legymernok.backend.dto.circuit.CompileCircuitResponse;
import com.legymernok.backend.dto.circuit.SaveCadetCircuitRequest;
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
     * Compiles the cadet's sketch.ino from the Gitea repo using Arduino CLI.
     * Returns Base64-encoded .hex on success, or the compiler error output on failure.
     * The response HTTP status is always 200 — the {@code success} field indicates
     * the compile result (compile errors are not HTTP errors).
     */
    @PostMapping("/{missionId}/compile")
    @PreAuthorize("hasAuthority('circuit:simulate')")
    public ResponseEntity<CompileCircuitResponse> compile(@PathVariable UUID missionId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(arduinoCompilerService.compile(username, missionId));
    }

    @PostMapping("/{missionId}/verify")
    @PreAuthorize("hasAuthority('circuit:simulate')")
    public ResponseEntity<List<CadetVerificationResultResponse>> verify(@PathVariable UUID missionId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(circuitVerificationService.verifyTopology(username, missionId));
    }
}
