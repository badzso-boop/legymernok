package com.legymernok.backend.service;

import com.legymernok.backend.dto.LoginRequest;
import com.legymernok.backend.dto.LoginResponse;
import com.legymernok.backend.model.Cadet;
import com.legymernok.backend.repository.CadetRepository;
import com.legymernok.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.legymernok.backend.exception.BadCredentialsException;
import com.legymernok.backend.exception.UserNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final CadetRepository cadetRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        Cadet cadet = cadetRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UserNotFoundException("User with username '" + request.getUsername() + "' not found"));

        if (!passwordEncoder.matches(request.getPassword(), cadet.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials: password does not match for user '" + request.getUsername() + "'");
        }

        String token = jwtService.generateToken(cadet);

        return new LoginResponse(token, cadet.getUsername());
    }
}