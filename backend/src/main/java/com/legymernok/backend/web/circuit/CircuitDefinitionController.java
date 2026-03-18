package com.legymernok.backend.web.circuit;

import com.legymernok.backend.dto.circuit.*;
import com.legymernok.backend.service.circuit.CircuitDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/circuit/definitions")
@RequiredArgsConstructor
public class CircuitDefinitionController {

    private final CircuitDefinitionService circuitDefinitionService;

    @PostMapping
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<CircuitDefinitionResponse> create(@Valid @RequestBody CreateCircuitDefinitionRequest request) {
        return new ResponseEntity<>(circuitDefinitionService.createCircuitDefinition(request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<CircuitDefinitionResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(circuitDefinitionService.getCircuitDefinition(id));
    }

    @GetMapping("/by-mission/{missionId}")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<List<CircuitDefinitionResponse>> getAllByMission(@PathVariable UUID missionId) {
        return ResponseEntity.ok(circuitDefinitionService.getAllByMission(missionId));
    }

    @PutMapping("/{id}/canvas")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<CircuitDefinitionResponse> saveCanvas(@PathVariable UUID id,
                                                                 @Valid @RequestBody SaveCircuitDefinitionRequest request) {
        return ResponseEntity.ok(circuitDefinitionService.saveCanvas(id, request));
    }

    @PostMapping("/{id}/checks")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<CircuitVerificationCheckResponse> addCheck(@PathVariable UUID id,
                                                                      @Valid @RequestBody CreateCircuitVerificationCheckRequest request) {
        return new ResponseEntity<>(circuitDefinitionService.addVerificationCheck(id, request), HttpStatus.CREATED);
    }

    @DeleteMapping("/{id}/checks/{checkId}")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<Void> deleteCheck(@PathVariable UUID id, @PathVariable UUID checkId) {
        circuitDefinitionService.deleteVerificationCheck(checkId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<CircuitDefinitionResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(circuitDefinitionService.publishCircuitDefinition(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        circuitDefinitionService.deleteCircuitDefinition(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
