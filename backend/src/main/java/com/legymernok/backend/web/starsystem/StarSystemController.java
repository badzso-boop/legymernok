package com.legymernok.backend.web.starsystem;

import com.legymernok.backend.dto.starsystem.CreateStarSystemRequest;
import com.legymernok.backend.dto.starsystem.StarSystemResponse;
import com.legymernok.backend.dto.starsystem.StarSystemWithMissionResponse;
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
    public ResponseEntity<StarSystemWithMissionResponse> getStarSystemWithMissions(@PathVariable UUID id) {
        return ResponseEntity.ok(starSystemService.getStarSystemWithMissions(id));
    }

    @GetMapping("/my-systems")
    @PreAuthorize("isAuthenticated()") // Bármely bejelentkezett felhasználó lekérheti a sajátjait
    public ResponseEntity<List<StarSystemResponse>> getMyStarSystems() {
        return ResponseEntity.ok(starSystemService.getSystemsByCurrentUser());
    }
}