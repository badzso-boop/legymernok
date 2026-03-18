package com.legymernok.backend.web.circuit;

import com.legymernok.backend.dto.circuit.*;
import com.legymernok.backend.service.circuit.AnalogCircuitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/circuit/analog")
@RequiredArgsConstructor
public class AnalogCircuitController {

    private final AnalogCircuitService analogCircuitService;

    // --- Admin: Definition ---

    @PostMapping("/definitions")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<AnalogCircuitDefinitionResponse> create(
            @Valid @RequestBody CreateAnalogCircuitDefinitionRequest request) {
        return new ResponseEntity<>(analogCircuitService.createDefinition(request), HttpStatus.CREATED);
    }

    @GetMapping("/definitions/{id}")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<AnalogCircuitDefinitionResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(analogCircuitService.getDefinition(id));
    }

    @PutMapping("/definitions/{id}/falstad")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<AnalogCircuitDefinitionResponse> updateFalstad(@PathVariable UUID id,
                                                                          @RequestBody String falstadText) {
        return ResponseEntity.ok(analogCircuitService.updateFalstadText(id, falstadText));
    }

    @PostMapping("/definitions/{id}/publish")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<AnalogCircuitDefinitionResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(analogCircuitService.publishDefinition(id));
    }

    @PostMapping("/definitions/{id}/checks")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<AnalogVerificationCheckResponse> addCheck(@PathVariable UUID id,
                                                                     @Valid @RequestBody CreateAnalogVerificationCheckRequest request) {
        return new ResponseEntity<>(analogCircuitService.addVerificationCheck(id, request), HttpStatus.CREATED);
    }

    @DeleteMapping("/definitions/{id}/checks/{checkId}")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<Void> deleteCheck(@PathVariable UUID id, @PathVariable UUID checkId) {
        analogCircuitService.deleteVerificationCheck(checkId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    // --- Cadet ---

    @GetMapping("/missions/{missionId}")
    @PreAuthorize("hasAuthority('circuit:simulate')")
    public ResponseEntity<CadetAnalogSaveResponse> getCadetSave(@PathVariable UUID missionId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(analogCircuitService.getCadetSave(username, missionId));
    }

    @PutMapping("/missions/{missionId}")
    @PreAuthorize("hasAuthority('circuit:simulate')")
    public ResponseEntity<CadetAnalogSaveResponse> saveCadetAnalog(@PathVariable UUID missionId,
                                                                    @Valid @RequestBody SaveCadetAnalogRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(analogCircuitService.saveCadetAnalog(username, missionId, request));
    }
}
