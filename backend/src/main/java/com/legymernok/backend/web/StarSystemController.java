package com.legymernok.backend.web;

import com.legymernok.backend.dto.CreateStarSystemRequest;
import com.legymernok.backend.dto.StarSystemResponse;
import com.legymernok.backend.service.StarSystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/star-systems")
@RequiredArgsConstructor
public class StarSystemController {

    private final StarSystemService starSystemService;

    @PostMapping
    public ResponseEntity<StarSystemResponse> createStarSystem(@RequestBody CreateStarSystemRequest request) {
        return new ResponseEntity<>(starSystemService.createStarSystem(request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<StarSystemResponse>> getAllStarSystems() {
        return ResponseEntity.ok(starSystemService.getAllStarSystems());
    }

    @GetMapping("/{id}")
    public ResponseEntity<StarSystemResponse> getStarSystemById(@PathVariable UUID id) {
        return ResponseEntity.ok(starSystemService.getStarSystemById(id));
    }
}