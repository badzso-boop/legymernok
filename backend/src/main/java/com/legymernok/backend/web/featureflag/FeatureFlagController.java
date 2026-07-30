package com.legymernok.backend.web.featureflag;

import com.legymernok.backend.dto.featureflag.FeatureFlagResponse;
import com.legymernok.backend.dto.featureflag.UpdateFeatureFlagRequest;
import com.legymernok.backend.service.featureflag.FeatureFlagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/feature-flags")
@RequiredArgsConstructor
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    /** Admin: összes flag listázása kezeléshez. */
    @GetMapping
    @PreAuthorize("hasAuthority('feature_flag:read')")
    public ResponseEntity<List<FeatureFlagResponse>> getAllFlags() {
        return ResponseEntity.ok(featureFlagService.getAllFlags());
    }

    /**
     * Bármely bejelentkezett (nem csak admin) felhasználó lekérdezheti egy flag
     * aktuális értékét — erre van szükség pl. a chatbot widget frontend-oldali
     * megjelenítés-vezérléséhez. Nincs külön permission-check, a SecurityConfig
     * alapértelmezett .anyRequest().authenticated() szabálya védi (bejelentkezés
     * kötelező, de admin jog nem).
     */
    @GetMapping("/{key}")
    public ResponseEntity<FeatureFlagResponse> getFlagByKey(@PathVariable String key) {
        return ResponseEntity.ok(featureFlagService.getFlagByKey(key));
    }

    /** Admin: flag engedélyezés/tiltás + leírás módosítása. */
    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('feature_flag:write')")
    public ResponseEntity<FeatureFlagResponse> updateFlag(
            @PathVariable String key,
            @Valid @RequestBody UpdateFeatureFlagRequest request) {
        return ResponseEntity.ok(featureFlagService.updateFlag(key, request));
    }
}
