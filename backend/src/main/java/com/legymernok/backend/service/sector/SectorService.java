package com.legymernok.backend.service.sector;

import com.legymernok.backend.dto.group.ReorderResponse;
import com.legymernok.backend.dto.sector.CreateSectorRequest;
import com.legymernok.backend.dto.sector.SectorResponse;
import com.legymernok.backend.dto.starsystem.StarSystemResponse;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.sector.Sector;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.sector.SectorRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Sector Map (issue #38) — a Star System-ek fölötti, admin-kurátori
 * témakör-csoportosítás. Egyszerűbb jogosultsági/tulajdonlási modell, mint a
 * StarSystem-é: nincs owner-koncepció, csak globális admin CRUD (ld.
 * plans/sector_map_2026.md).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SectorService {

    private final SectorRepository sectorRepository;
    private final StarSystemRepository starSystemRepository;

    @Transactional
    public SectorResponse createSector(CreateSectorRequest request) {
        if (sectorRepository.findByName(request.getName()).isPresent()) {
            throw new ResourceConflictException("Sector", "name", request.getName());
        }

        int orderIndex = sectorRepository.findMaxOrderIndex() + 1;

        Sector sector = Sector.builder()
                .name(request.getName())
                .description(request.getDescription())
                .iconUrl(request.getIconUrl())
                .orderIndex(orderIndex)
                .build();

        Sector saved = sectorRepository.save(sector);
        log.info("Sector created: {}", saved.getName());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SectorResponse> getAllSectors() {
        return sectorRepository.findAllByOrderByOrderIndexAsc().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SectorResponse getSectorById(UUID id) {
        return mapToResponse(getSectorOrThrow(id));
    }

    @Transactional
    public SectorResponse updateSector(UUID id, CreateSectorRequest request) {
        Sector sector = getSectorOrThrow(id);

        sectorRepository.findByName(request.getName()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new ResourceConflictException("Sector", "name", request.getName());
            }
        });

        sector.setName(request.getName());
        sector.setDescription(request.getDescription());
        sector.setIconUrl(request.getIconUrl());

        return mapToResponse(sectorRepository.save(sector));
    }

    @Transactional
    public void deleteSector(UUID id) {
        Sector sector = getSectorOrThrow(id);
        // A hozzá tartozó Star System-ek NEM törlődnek, a DB FK ON DELETE SET
        // NULL gondoskodik a "Besorolatlan" állapotba kerülésükről.
        sectorRepository.delete(sector);
    }

    @Transactional(readOnly = true)
    public List<StarSystemResponse> getSectorStarSystems(UUID id) {
        getSectorOrThrow(id);
        return starSystemRepository.findAllBySectorId(id).stream()
                .map(this::mapStarSystemToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ReorderResponse reorderSector(UUID sectorId, UUID targetId) {
        Sector sector = getSectorOrThrow(sectorId);
        Sector target = getSectorOrThrow(targetId);

        int tmp = sector.getOrderIndex();
        sector.setOrderIndex(target.getOrderIndex());
        target.setOrderIndex(tmp);
        sectorRepository.save(sector);
        sectorRepository.save(target);

        return ReorderResponse.builder()
                .updated(List.of(
                        ReorderResponse.ReorderItem.builder().id(sectorId).orderIndex(sector.getOrderIndex()).build(),
                        ReorderResponse.ReorderItem.builder().id(targetId).orderIndex(target.getOrderIndex()).build()
                ))
                .build();
    }

    private Sector getSectorOrThrow(UUID id) {
        return sectorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sector", "id", id));
    }

    private SectorResponse mapToResponse(Sector sector) {
        return SectorResponse.builder()
                .id(sector.getId())
                .name(sector.getName())
                .description(sector.getDescription())
                .iconUrl(sector.getIconUrl())
                .orderIndex(sector.getOrderIndex())
                .starSystemCount(starSystemRepository.countBySectorId(sector.getId()))
                .createdAt(sector.getCreatedAt())
                .updatedAt(sector.getUpdatedAt())
                .build();
    }

    private StarSystemResponse mapStarSystemToResponse(StarSystem system) {
        return StarSystemResponse.builder()
                .id(system.getId())
                .name(system.getName())
                .description(system.getDescription())
                .iconUrl(system.getIconUrl())
                .sectorId(system.getSector() != null ? system.getSector().getId() : null)
                .sectorName(system.getSector() != null ? system.getSector().getName() : null)
                .createdAt(system.getCreatedAt())
                .updatedAt(system.getUpdatedAt())
                .build();
    }
}
