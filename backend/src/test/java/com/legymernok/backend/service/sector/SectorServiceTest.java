package com.legymernok.backend.service.sector;

import com.legymernok.backend.dto.sector.CreateSectorRequest;
import com.legymernok.backend.dto.sector.SectorResponse;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.sector.Sector;
import com.legymernok.backend.repository.sector.SectorRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    void getAllSectors_ShouldReturnOrderedList() {
        when(sectorRepository.findAllByOrderByOrderIndexAsc()).thenReturn(List.of(physics));
        when(starSystemRepository.countBySectorId(physics.getId())).thenReturn(3L);

        List<SectorResponse> result = sectorService.getAllSectors();

        assertEquals(1, result.size());
        assertEquals(3L, result.get(0).getStarSystemCount());
    }
}
