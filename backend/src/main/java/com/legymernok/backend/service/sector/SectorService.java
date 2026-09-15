package com.legymernok.backend.service.sector;

import com.legymernok.backend.dto.group.ReorderResponse;
import com.legymernok.backend.dto.sector.CreateSectorRequest;
import com.legymernok.backend.dto.sector.SectorResponse;
import com.legymernok.backend.dto.starsystem.StarSystemResponse;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.audience.Audience;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.sector.Sector;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.sector.SectorRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    /** Szektort kezel, vagy csillagrendszert hoz létre / szerkeszt (és ehhez minden szektort ki kell tudnia választani). */
    private static final Set<String> FULL_VISIBILITY_AUTHORITIES =
            Set.of("sector:write", "starsystem:create", "starsystem:edit");

    private final StarSystemRepository starSystemRepository;
    private final CadetRepository cadetRepository;

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
                .audience(request.getAudience() != null ? request.getAudience() : Audience.ENGINEER)
                .build();

        Sector saved = sectorRepository.save(sector);
        log.info("Sector created: {}", saved.getName());
        return mapToResponse(saved);
    }

    /**
     * Szektorkezelő és tartalomkészítő (ld. {@link #FULL_VISIBILITY_AUTHORITIES}) minden szektort lát;
     * mindenki más csak a saját audience-éhez tartozókat (a mérnöki felületen nem jelennek meg az
     * akadémiás szektorok és fordítva). A részlet-végpontok ugyanezt a szabályt követik.
     */
    @Transactional(readOnly = true)
    public List<SectorResponse> getAllSectors() {
        Optional<Audience> audience = currentUserAudienceRestriction();
        List<Sector> sectors = audience
                .map(sectorRepository::findAllByAudienceOrderByOrderIndexAsc)
                .orElseGet(sectorRepository::findAllByOrderByOrderIndexAsc);

        return sectors.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public SectorResponse getSectorById(UUID id) {
        return mapToResponse(getVisibleSectorOrThrow(id));
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
        if (request.getAudience() != null) {
            sector.setAudience(request.getAudience());
        }

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
        getVisibleSectorOrThrow(id);
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

    /** Nem látható szektornál 404 — ugyanaz a válasz, mint nem létezőnél, hogy a létezése se derüljön ki. */
    private Sector getVisibleSectorOrThrow(UUID id) {
        Sector sector = getSectorOrThrow(id);
        Optional<Audience> audience = currentUserAudienceRestriction();
        if (audience.isPresent() && audience.get() != sector.getAudience()) {
            throw new ResourceNotFoundException("Sector", "id", id);
        }
        return sector;
    }

    /** Üres, ha a felhasználó minden szektort láthat; különben a saját audience-e. */
    private Optional<Audience> currentUserAudienceRestriction() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean fullVisibility = auth.getAuthorities().stream()
                .anyMatch(a -> FULL_VISIBILITY_AUTHORITIES.contains(a.getAuthority()));
        if (fullVisibility) {
            return Optional.empty();
        }
        return Optional.of(cadetRepository.findByUsername(auth.getName())
                .map(Cadet::getAudience)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "username", auth.getName())));
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
                .audience(sector.getAudience())
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
