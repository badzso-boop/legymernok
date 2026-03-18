package com.legymernok.backend.web.circuit;

import com.legymernok.backend.dto.circuit.CreateUnitOfMeasureRequest;
import com.legymernok.backend.dto.circuit.UnitOfMeasureResponse;
import com.legymernok.backend.service.circuit.UnitOfMeasureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/circuit/units")
@RequiredArgsConstructor
public class UnitOfMeasureController {

    private final UnitOfMeasureService unitOfMeasureService;

    @GetMapping
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<List<UnitOfMeasureResponse>> getAll() {
        return ResponseEntity.ok(unitOfMeasureService.getAll());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<UnitOfMeasureResponse> create(@Valid @RequestBody CreateUnitOfMeasureRequest request) {
        return new ResponseEntity<>(unitOfMeasureService.create(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<UnitOfMeasureResponse> update(@PathVariable UUID id,
                                                         @Valid @RequestBody CreateUnitOfMeasureRequest request) {
        return ResponseEntity.ok(unitOfMeasureService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('circuit:manage')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        unitOfMeasureService.delete(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
