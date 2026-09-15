package com.legymernok.backend.service.sector;

import com.legymernok.backend.dto.sector.CreateSectorRequest;
import com.legymernok.backend.dto.sector.SectorResponse;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.audience.Audience;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.sector.Sector;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.sector.SectorRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SectorServiceTest {

    @Mock private SectorRepository sectorRepository;
    @Mock private StarSystemRepository starSystemRepository;
    @Mock private CadetRepository cadetRepository;
    @InjectMocks private SectorService sectorService;

    private Sector physics;

    @BeforeEach
    void setUp() {
        physics = Sector.builder().id(UUID.randomUUID()).name("Fizika").orderIndex(0).build();
    }

    @Test
    void createSector_ShouldSucceed_WhenNameIsUnique() {
        CreateSectorRequest request = new CreateSectorRequest();
        request.setName("Fizika");

        when(sectorRepository.findByName("Fizika")).thenReturn(Optional.empty());
        when(sectorRepository.findMaxOrderIndex()).thenReturn(-1);
        when(sectorRepository.save(any(Sector.class))).thenAnswer(inv -> inv.getArgument(0));
        when(starSystemRepository.countBySectorId(any())).thenReturn(0L);

        SectorResponse response = sectorService.createSector(request);

        assertEquals("Fizika", response.getName());
        assertEquals(0, response.getOrderIndex());
    }

    @Test
    void createSector_ShouldThrow_WhenNameAlreadyExists() {
        CreateSectorRequest request = new CreateSectorRequest();
        request.setName("Fizika");

        when(sectorRepository.findByName("Fizika")).thenReturn(Optional.of(physics));

        assertThrows(ResourceConflictException.class, () -> sectorService.createSector(request));
        verify(sectorRepository, never()).save(any());
    }

    @Test
    void deleteSector_ShouldThrow_WhenNotFound() {
        UUID missingId = UUID.randomUUID();
        when(sectorRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sectorService.deleteSector(missingId));
    }

    @Test
    void deleteSector_ShouldDeleteSector_NotStarSystems() {
        when(sectorRepository.findById(physics.getId())).thenReturn(Optional.of(physics));

        sectorService.deleteSector(physics.getId());

        verify(sectorRepository).delete(physics);
        // A DB szintű ON DELETE SET NULL kezeli a star_systems.sector_id-t —
        // a service réteg nem törli/nem is éri el a StarSystem rekordokat.
        verifyNoInteractions(starSystemRepository);
    }

    @Test
    void reorderSector_ShouldSwapOrderIndexes() {
        Sector informatics = Sector.builder().id(UUID.randomUUID()).name("Informatika").orderIndex(1).build();
        when(sectorRepository.findById(physics.getId())).thenReturn(Optional.of(physics));
        when(sectorRepository.findById(informatics.getId())).thenReturn(Optional.of(informatics));

        var response = sectorService.reorderSector(physics.getId(), informatics.getId());

        assertEquals(1, physics.getOrderIndex());
        assertEquals(0, informatics.getOrderIndex());
        assertEquals(2, response.getUpdated().size());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAllSectors_ShouldReturnOrderedList() {
        authenticateAs("admin", "sector:read", "sector:write");
        when(sectorRepository.findAllByOrderByOrderIndexAsc()).thenReturn(List.of(physics));
        when(starSystemRepository.countBySectorId(physics.getId())).thenReturn(3L);

        List<SectorResponse> result = sectorService.getAllSectors();

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).getStarSystemCount());
    }

    @Test
    void getAllSectors_AsSectorManager_ShouldReturnAllAudiences() {
        Sector academy = Sector.builder().id(UUID.randomUUID()).name("Navigátor Akadémia")
                .orderIndex(1).audience(Audience.ACADEMY).build();
        authenticateAs("admin", "sector:read", "sector:write");
        when(sectorRepository.findAllByOrderByOrderIndexAsc()).thenReturn(List.of(physics, academy));

        List<SectorResponse> result = sectorService.getAllSectors();

        assertEquals(2, result.size());
        verify(sectorRepository, never()).findAllByAudienceOrderByOrderIndexAsc(any());
        verifyNoInteractions(cadetRepository);
    }

    @Test
    void getAllSectors_AsAcademyCadet_ShouldFilterByOwnAudience() {
        Sector academy = Sector.builder().id(UUID.randomUUID()).name("Navigátor Akadémia")
                .orderIndex(1).audience(Audience.ACADEMY).build();
        authenticateAs("kid", "sector:read");
        when(cadetRepository.findByUsername("kid"))
                .thenReturn(Optional.of(Cadet.builder().username("kid").audience(Audience.ACADEMY).build()));
        when(sectorRepository.findAllByAudienceOrderByOrderIndexAsc(Audience.ACADEMY)).thenReturn(List.of(academy));

        List<SectorResponse> result = sectorService.getAllSectors();

        assertEquals(1, result.size());
        assertEquals(Audience.ACADEMY, result.get(0).getAudience());
        verify(sectorRepository, never()).findAllByOrderByOrderIndexAsc();
    }

    @Test
    void getAllSectors_AsEngineerCadet_ShouldNotSeeAcademySectors() {
        authenticateAs("student", "sector:read");
        when(cadetRepository.findByUsername("student"))
                .thenReturn(Optional.of(Cadet.builder().username("student").build()));
        when(sectorRepository.findAllByAudienceOrderByOrderIndexAsc(Audience.ENGINEER)).thenReturn(List.of(physics));

        List<SectorResponse> result = sectorService.getAllSectors();

        assertEquals(1, result.size());
        assertEquals(Audience.ENGINEER, result.get(0).getAudience());
    }

    @Test
    void getAllSectors_AsContentCreator_ShouldReturnAllAudiences() {
        authenticateAs("author", "sector:read", "starsystem:create", "starsystem:edit");
        when(sectorRepository.findAllByOrderByOrderIndexAsc()).thenReturn(List.of(physics));

        sectorService.getAllSectors();

        verify(sectorRepository).findAllByOrderByOrderIndexAsc();
        verifyNoInteractions(cadetRepository);
    }

    @Test
    void getSectorById_AsEngineerCadet_ForAcademySector_ShouldReturn404() {
        Sector academy = Sector.builder().id(UUID.randomUUID()).name("Navigátor Akadémia")
                .orderIndex(1).audience(Audience.ACADEMY).build();
        authenticateAs("student", "sector:read");
        when(sectorRepository.findById(academy.getId())).thenReturn(Optional.of(academy));
        when(cadetRepository.findByUsername("student"))
                .thenReturn(Optional.of(Cadet.builder().username("student").build()));

        assertThrows(ResourceNotFoundException.class, () -> sectorService.getSectorById(academy.getId()));
    }

    @Test
    void getSectorStarSystems_AsEngineerCadet_ForAcademySector_ShouldReturn404() {
        Sector academy = Sector.builder().id(UUID.randomUUID()).name("Navigátor Akadémia")
                .orderIndex(1).audience(Audience.ACADEMY).build();
        authenticateAs("student", "sector:read");
        when(sectorRepository.findById(academy.getId())).thenReturn(Optional.of(academy));
        when(cadetRepository.findByUsername("student"))
                .thenReturn(Optional.of(Cadet.builder().username("student").build()));

        assertThrows(ResourceNotFoundException.class, () -> sectorService.getSectorStarSystems(academy.getId()));
        verify(starSystemRepository, never()).findAllBySectorId(any());
    }

    @Test
    void getSectorById_AsAcademyCadet_ForAcademySector_ShouldSucceed() {
        Sector academy = Sector.builder().id(UUID.randomUUID()).name("Navigátor Akadémia")
                .orderIndex(1).audience(Audience.ACADEMY).build();
        authenticateAs("kid", "sector:read");
        when(sectorRepository.findById(academy.getId())).thenReturn(Optional.of(academy));
        when(cadetRepository.findByUsername("kid"))
                .thenReturn(Optional.of(Cadet.builder().username("kid").audience(Audience.ACADEMY).build()));

        assertEquals(academy.getId(), sectorService.getSectorById(academy.getId()).getId());
    }

    @Test
    void createSector_WithoutAudience_ShouldDefaultToEngineer() {
        CreateSectorRequest request = new CreateSectorRequest();
        request.setName("Új szektor");
        when(sectorRepository.findByName("Új szektor")).thenReturn(Optional.empty());
        when(sectorRepository.findMaxOrderIndex()).thenReturn(0);
        when(sectorRepository.save(any(Sector.class))).thenAnswer(i -> i.getArgument(0));

        SectorResponse response = sectorService.createSector(request);

        assertEquals(Audience.ENGINEER, response.getAudience());
    }

    @Test
    void updateSector_WithoutAudience_ShouldKeepExisting() {
        Sector academy = Sector.builder().id(UUID.randomUUID()).name("Navigátor Akadémia")
                .orderIndex(1).audience(Audience.ACADEMY).build();
        CreateSectorRequest request = new CreateSectorRequest();
        request.setName("Navigátor Akadémia");
        when(sectorRepository.findById(academy.getId())).thenReturn(Optional.of(academy));
        when(sectorRepository.findByName("Navigátor Akadémia")).thenReturn(Optional.of(academy));
        when(sectorRepository.save(any(Sector.class))).thenAnswer(i -> i.getArgument(0));

        SectorResponse response = sectorService.updateSector(academy.getId(), request);

        assertEquals(Audience.ACADEMY, response.getAudience());
    }

    // Új context kell, nem a getContext() módosítása: más tesztosztályok mockolt SecurityContext-et
    // hagyhatnak a szálon, amin a setAuthentication() hatástalan.
    private void authenticateAs(String username, String... authorities) {
        SecurityContextHolder.setContext(new SecurityContextImpl(new UsernamePasswordAuthenticationToken(
                username, null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList())));
    }
}
