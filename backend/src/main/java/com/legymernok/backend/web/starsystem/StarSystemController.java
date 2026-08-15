package com.legymernok.backend.web.starsystem;

import com.legymernok.backend.dto.group.ReorderResponse;
import com.legymernok.backend.dto.starsystem.CreateStarSystemRequest;
import com.legymernok.backend.dto.starsystem.ReorderItemsRequest;
import com.legymernok.backend.dto.starsystem.StarSystemResponse;
import com.legymernok.backend.dto.starsystem.StarSystemWithItemsResponse;
import com.legymernok.backend.dto.starsystem.StarSystemWithProgressResponse;
import com.legymernok.backend.service.starsystem.StarSystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/star-systems")
@RequiredArgsConstructor
public class StarSystemController {

    private final StarSystemService starSystemService;

    @PostMapping
    @PreAuthorize("hasAuthority('starsystem:create')")
    public ResponseEntity<StarSystemResponse> createStarSystem(@RequestBody CreateStarSystemRequest request) {
        return new ResponseEntity<>(starSystemService.createStarSystem(request), HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('starsystem:read')")
    public ResponseEntity<List<StarSystemResponse>> getAllStarSystems() {
        return ResponseEntity.ok(starSystemService.getAllStarSystems());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('starsystem:read')")
    public ResponseEntity<StarSystemResponse> getStarSystemById(@PathVariable UUID id) {
        return ResponseEntity.ok(starSystemService.getStarSystemById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('starsystem:delete')")
    public ResponseEntity<Void> deleteStarSystem(@PathVariable UUID id) {
        starSystemService.deleteStarSystem(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('starsystem:edit')")
    public ResponseEntity<StarSystemResponse> updateStarSystem(@PathVariable UUID id, @RequestBody
    CreateStarSystemRequest request) {
        StarSystemResponse updatedStarSystem = starSystemService.updateStarSystem(id, request);
        return ResponseEntity.ok(updatedStarSystem);
    }

    @GetMapping("/{id}/with-missions")
    @PreAuthorize("hasAuthority('starsystem:read')")
    public ResponseEntity<StarSystemWithItemsResponse> getStarSystemWithItems(@PathVariable UUID id) {
        return ResponseEntity.ok(starSystemService.getStarSystemWithItems(id));
    }

    @PostMapping("/{id}/reorder-items")
    @PreAuthorize("hasAuthority('starsystem:edit')")
    public ResponseEntity<ReorderResponse> reorderItems(@PathVariable UUID id, @RequestBody ReorderItemsRequest request) {
        return ResponseEntity.ok(starSystemService.reorderItems(id, request));
    }

    @GetMapping("/with-progress")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<StarSystemWithProgressResponse>> getAllStarSystemsWithProgress() {
        return ResponseEntity.ok(starSystemService.getAllStarSystemsWithProgress());
    }

    @GetMapping("/my-systems")
    @PreAuthorize("isAuthenticated()") // Bármely bejelentkezett felhasználó lekérheti a sajátjait
    public ResponseEntity<List<StarSystemResponse>> getMyStarSystems() {
        return ResponseEntity.ok(starSystemService.getSystemsByCurrentUser());
    }

    @GetMapping("/{id}/embedding-status")
    @PreAuthorize("hasAuthority('starsystem:read')")
    public ResponseEntity<EmbeddingStatusResponse> getEmbeddingStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(new EmbeddingStatusResponse(starSystemService.hasEmbedding(id)));
    }

    @PostMapping("/{id}/embed")
    @PreAuthorize("hasAuthority('starsystem:edit_any')")
    public ResponseEntity<EmbeddingStatusResponse> generateEmbedding(@PathVariable UUID id) {
        starSystemService.generateAndSaveEmbedding(id);
        return ResponseEntity.ok(new EmbeddingStatusResponse(true));
    }

    @DeleteMapping("/{id}/embed")
    @PreAuthorize("hasAuthority('starsystem:edit_any')")
    public ResponseEntity<EmbeddingStatusResponse> deleteEmbedding(@PathVariable UUID id) {
        starSystemService.deleteEmbedding(id);
        return ResponseEntity.ok(new EmbeddingStatusResponse(false));
    }

    record EmbeddingStatusResponse(boolean hasEmbedding) {}
}