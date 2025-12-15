package com.legymernok.backend.service.cadet;

import com.legymernok.backend.dto.cadet.CadetResponse;
import com.legymernok.backend.dto.cadet.CreateCadetRequest;
import com.legymernok.backend.exception.UserNotFoundException;
import com.legymernok.backend.model.ConnectTable.CadetMission;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.cadet.CadetRole;
import com.legymernok.backend.repository.ConnectTables.CadetMissionRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.exception.UserAlreadyExistsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.legymernok.backend.integration.GiteaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CadetService {

    private final CadetRepository cadetRepository;
    private final CadetMissionRepository cadetMissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final GiteaService giteaService;

    @Transactional
    public CadetResponse createCadet(CreateCadetRequest request) {
        if (cadetRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username '" + request.getUsername() + "' already exists");
        }
        if (cadetRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email '" + request.getEmail() + "' already exists");
        }

        Long giteaId = giteaService.createGiteaUser(
            request.getUsername(),
            request.getEmail(),
            request.getPassword()
        );

        Cadet cadet = Cadet.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .giteaUserId(giteaId)
                .role(CadetRole.CADET)
                .build();

        Cadet savedCadet = cadetRepository.save(cadet);
        return mapToResponse(savedCadet);
    }

    @Transactional(readOnly = true)
    public List<CadetResponse> getAllCadets() {
        return cadetRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CadetResponse getCadetById(UUID id) {
        Cadet cadet = cadetRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
        return mapToResponse(cadet);
    }

    @Transactional
    public void deleteCadet(UUID id) {
        Cadet cadet = cadetRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));

        try {
            giteaService.deleteGiteaUser(cadet.getUsername());
        } catch (Exception e) {
            System.err.println("Gitea user deletion failed for " + cadet.getUsername() + ": " + e.getMessage());
        }

        cadetMissionRepository.deleteAllByCadetId(id);

        cadetRepository.delete(cadet);
    }

    private CadetResponse mapToResponse(Cadet cadet) {
        return CadetResponse.builder()
                .id(cadet.getId())
                .username(cadet.getUsername())
                .email(cadet.getEmail())
                .role(cadet.getRole())
                .giteaUserId(cadet.getGiteaUserId())
                .createdAt(cadet.getCreatedAt())
                .build();
    }
}
