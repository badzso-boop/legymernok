package com.legymernok.backend.web.cadet;

import com.legymernok.backend.dto.cadet.CadetResponse;
import com.legymernok.backend.dto.cadet.CreateCadetRequest;
import com.legymernok.backend.service.cadet.CadetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class CadetController {

    private final CadetService cadetService;

    @PostMapping
    public ResponseEntity<CadetResponse> createCadet(@RequestBody CreateCadetRequest request) {
        return new ResponseEntity<>(cadetService.createCadet(request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<CadetResponse>> getAllCadets() {
        return ResponseEntity.ok(cadetService.getAllCadets());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CadetResponse> getCadetById(@PathVariable UUID id) {
        return ResponseEntity.ok(cadetService.getCadetById(id));
    }
}
