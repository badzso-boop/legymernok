package com.legymernok.backend.service.circuit;

import com.legymernok.backend.dto.circuit.CreateUnitOfMeasureRequest;
import com.legymernok.backend.dto.circuit.UnitOfMeasureResponse;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.circuit.UnitOfMeasure;
import com.legymernok.backend.repository.circuit.UnitOfMeasureRepository;
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
class UnitOfMeasureServiceTest {

    @Mock private UnitOfMeasureRepository repository;
    @InjectMocks private UnitOfMeasureService service;

    private UUID unitId;
    private UnitOfMeasure ohm;

    @BeforeEach
    void setUp() {
        unitId = UUID.randomUUID();
        ohm = UnitOfMeasure.builder().id(unitId).name("Ohm").symbol("Ω").build();
    }

    // --- getAll ---

    @Test
    void getAll_returnsAllMapped() {
        UnitOfMeasure volt = UnitOfMeasure.builder().id(UUID.randomUUID()).name("Volt").symbol("V").build();
        when(repository.findAll()).thenReturn(List.of(ohm, volt));

        List<UnitOfMeasureResponse> result = service.getAll();

        assertEquals(2, result.size());
        assertEquals("Ohm", result.get(0).getName());
        assertEquals("Ω", result.get(0).getSymbol());
        assertEquals("Volt", result.get(1).getName());
    }

    @Test
    void getAll_emptyList_returnsEmpty() {
        when(repository.findAll()).thenReturn(List.of());
        assertTrue(service.getAll().isEmpty());
    }

    // --- create ---

    @Test
    void create_happyPath_savesAndReturns() {
        CreateUnitOfMeasureRequest request = new CreateUnitOfMeasureRequest();
        request.setName("Ohm");
        request.setSymbol("Ω");

        when(repository.existsByNameOrSymbol("Ohm", "Ω")).thenReturn(false);
        when(repository.save(any())).thenReturn(ohm);

        UnitOfMeasureResponse result = service.create(request);

        assertEquals("Ohm", result.getName());
        assertEquals("Ω", result.getSymbol());
        verify(repository).save(any(UnitOfMeasure.class));
    }

    @Test
    void create_conflictOnName_throwsResourceConflictException() {
        CreateUnitOfMeasureRequest request = new CreateUnitOfMeasureRequest();
        request.setName("Ohm");
        request.setSymbol("Ω");

        when(repository.existsByNameOrSymbol("Ohm", "Ω")).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> service.create(request));
        verify(repository, never()).save(any());
    }

    // --- update ---

    @Test
    void update_happyPath_updatesAndReturns() {
        CreateUnitOfMeasureRequest request = new CreateUnitOfMeasureRequest();
        request.setName("Ohm updated");
        request.setSymbol("Ω2");

        UnitOfMeasure updated = UnitOfMeasure.builder().id(unitId).name("Ohm updated").symbol("Ω2").build();
        when(repository.findById(unitId)).thenReturn(Optional.of(ohm));
        when(repository.save(any())).thenReturn(updated);

        UnitOfMeasureResponse result = service.update(unitId, request);

        assertEquals("Ohm updated", result.getName());
        assertEquals("Ω2", result.getSymbol());
    }

    @Test
    void update_notFound_throwsResourceNotFoundException() {
        when(repository.findById(unitId)).thenReturn(Optional.empty());

        CreateUnitOfMeasureRequest request = new CreateUnitOfMeasureRequest();
        request.setName("X");
        request.setSymbol("x");

        assertThrows(ResourceNotFoundException.class, () -> service.update(unitId, request));
        verify(repository, never()).save(any());
    }

    // --- delete ---

    @Test
    void delete_happyPath_deletesById() {
        when(repository.existsById(unitId)).thenReturn(true);

        service.delete(unitId);

        verify(repository).deleteById(unitId);
    }

    @Test
    void delete_notFound_throwsResourceNotFoundException() {
        when(repository.existsById(unitId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> service.delete(unitId));
        verify(repository, never()).deleteById(any());
    }

    // --- toResponse ---

    @Test
    void toResponse_nullInput_returnsNull() {
        assertNull(service.toResponse(null));
    }

    @Test
    void toResponse_validInput_mapsAllFields() {
        UnitOfMeasureResponse response = service.toResponse(ohm);

        assertEquals(unitId, response.getId());
        assertEquals("Ohm", response.getName());
        assertEquals("Ω", response.getSymbol());
    }
}
