package com.legymernok.backend.web.user;

import com.legymernok.backend.dto.cadet.CadetResponse;
import com.legymernok.backend.dto.user.LoginRequest;
import com.legymernok.backend.dto.user.LoginResponse;
import com.legymernok.backend.dto.user.RegisterRequest;
import com.legymernok.backend.dto.user.RegisterResponse;
import com.legymernok.backend.dto.user.UpdateThemePreferenceRequest;
import com.legymernok.backend.service.user.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
        return new ResponseEntity<>(authService.register(request), HttpStatus.CREATED);
    }

    @GetMapping("/me")
    public ResponseEntity<CadetResponse> getCurrentUser() {
        return ResponseEntity.ok(authService.getCurrentUser());
    }

    @PutMapping("/me/theme")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> updateThemePreference(@Valid @RequestBody UpdateThemePreferenceRequest request) {
        authService.updateThemePreference(request);
        return ResponseEntity.noContent().build();
    }
}