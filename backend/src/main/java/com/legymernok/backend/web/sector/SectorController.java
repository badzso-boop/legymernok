package com.legymernok.backend.web.sector;

import com.legymernok.backend.dto.group.ReorderResponse;
import com.legymernok.backend.dto.sector.CreateSectorRequest;
import com.legymernok.backend.dto.sector.SectorResponse;
import com.legymernok.backend.dto.starsystem.StarSystemResponse;
import com.legymernok.backend.service.sector.SectorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sectors")
@RequiredArgsConstructor
public class SectorController {

    private final SectorService sectorService;

    @PostMapping
    @PreAuthorize("hasAuthority('sector:write')")
    public ResponseEntity<SectorResponse> createSector(@Valid @RequestBody CreateSectorRequest request) {
        return new ResponseEntity<>(sectorService.createSector(request), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sector:read')")
    public ResponseEntity<List<SectorResponse>> getAllSectors() {
        return ResponseEntity.ok(sectorService.getAllSectors());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sector:read')")
    public ResponseEntity<SectorResponse> getSectorById(@PathVariable UUID id) {
        return ResponseEntity.ok(sectorService.getSectorById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sector:write')")
    public ResponseEntity<SectorResponse> updateSector(
            @PathVariable UUID id, @Valid @RequestBody CreateSectorRequest request) {
        return ResponseEntity.ok(sectorService.updateSector(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sector:write')")
    public ResponseEntity<Void> deleteSector(@PathVariable UUID id) {
        sectorService.deleteSector(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/{id}/star-systems")
    @PreAuthorize("hasAuthority('sector:read')")
    public ResponseEntity<List<StarSystemResponse>> getSectorStarSystems(@PathVariable UUID id) {
        return ResponseEntity.ok(sectorService.getSectorStarSystems(id));
    }

    @PostMapping("/{id}/reorder/{targetId}")
    @PreAuthorize("hasAuthority('sector:write')")
    public ResponseEntity<ReorderResponse> reorderSector(
            @PathVariable UUID id, @PathVariable UUID targetId) {
        return ResponseEntity.ok(sectorService.reorderSector(id, targetId));
    }
}
