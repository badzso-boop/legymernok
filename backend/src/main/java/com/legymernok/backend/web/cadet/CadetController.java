package com.legymernok.backend.web.cadet;

import com.legymernok.backend.dto.cadet.CadetResponse;
import com.legymernok.backend.dto.cadet.CreateCadetRequest;
import com.legymernok.backend.service.cadet.CadetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class CadetController {

    private final CadetService cadetService;

    @PostMapping
    @PreAuthorize("hasAuthority('user:create')")
    public ResponseEntity<CadetResponse> createCadet(@RequestBody CreateCadetRequest request) {
        return new ResponseEntity<>(cadetService.createCadet(request), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('user:read')")
    public ResponseEntity<List<CadetResponse>> getAllCadets() {
        return ResponseEntity.ok(cadetService.getAllCadets());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:read')")
    public ResponseEntity<CadetResponse> getCadetById(@PathVariable UUID id) {
        return ResponseEntity.ok(cadetService.getCadetById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('user:edit')")
    public ResponseEntity<CadetResponse> updateCadet(@PathVariable UUID id, @RequestBody CreateCadetRequest request) {
        return ResponseEntity.ok(cadetService.updateCadet(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:delete')")
    public ResponseEntity<Void> deleteCadet(@PathVariable UUID id) {
        // TODO: A jövőben itt ellenőrizni kell, hogy a hívó user ADMIN jogokkal rendelkezik-e!
        cadetService.deleteCadet(id);
        return ResponseEntity.noContent().build();
    }
}
