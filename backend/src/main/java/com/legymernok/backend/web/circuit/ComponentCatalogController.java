package com.legymernok.backend.web.circuit;

import com.legymernok.backend.dto.circuit.*;
import com.legymernok.backend.model.circuit.BoardType;
import com.legymernok.backend.model.circuit.ComponentType;
import com.legymernok.backend.service.circuit.ComponentCatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/circuit/catalog")
@RequiredArgsConstructor
public class ComponentCatalogController {

    private final ComponentCatalogService componentCatalogService;

    // --- Pin Definitions (frontend loads at startup — circuit:read) ---

    @GetMapping("/pins")
    @PreAuthorize("hasAuthority('circuit:read')")
    public ResponseEntity<List<ComponentPinDefinitionResponse>> getPinDefinitions(
            @RequestParam(required = false) ComponentType componentType,
            @RequestParam(required = false) BoardType boardType) {
        if (componentType != null && boardType != null) {
            return ResponseEntity.ok(componentCatalogService.getPinDefinitions(componentType, boardType));
        }
        if (componentType != null) {
            return ResponseEntity.ok(componentCatalogService.getPinDefinitionsByComponentType(componentType));
        }
        return ResponseEntity.ok(componentCatalogService.getAllPinDefinitions());
    }

    @PostMapping("/pins")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<ComponentPinDefinitionResponse> createPinDefinition(
            @Valid @RequestBody CreateComponentPinDefinitionRequest request) {
        return new ResponseEntity<>(componentCatalogService.createPinDefinition(request), HttpStatus.CREATED);
    }

    @DeleteMapping("/pins/{id}")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<Void> deletePinDefinition(@PathVariable UUID id) {
        componentCatalogService.deletePinDefinition(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    // --- Electrical Specs ---

    @GetMapping("/specs")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<List<ComponentElectricalSpecResponse>> getElectricalSpecs(
            @RequestParam(required = false) ComponentType componentType) {
        if (componentType != null) {
            return ResponseEntity.ok(componentCatalogService.getElectricalSpecsByComponentType(componentType));
        }
        return ResponseEntity.ok(componentCatalogService.getAllElectricalSpecs());
    }

    @PostMapping("/specs")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<ComponentElectricalSpecResponse> createElectricalSpec(
            @Valid @RequestBody CreateComponentElectricalSpecRequest request) {
        return new ResponseEntity<>(componentCatalogService.createElectricalSpec(request), HttpStatus.CREATED);
    }

    @DeleteMapping("/specs/{id}")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<Void> deleteElectricalSpec(@PathVariable UUID id) {
        componentCatalogService.deleteElectricalSpec(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
