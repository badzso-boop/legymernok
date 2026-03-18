package com.legymernok.backend.service.circuit;

import com.legymernok.backend.dto.circuit.CreateUnitOfMeasureRequest;
import com.legymernok.backend.dto.circuit.UnitOfMeasureResponse;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.circuit.UnitOfMeasure;
import com.legymernok.backend.repository.circuit.UnitOfMeasureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UnitOfMeasureService {

    private final UnitOfMeasureRepository unitOfMeasureRepository;

    public List<UnitOfMeasureResponse> getAll() {
        return unitOfMeasureRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public UnitOfMeasureResponse create(CreateUnitOfMeasureRequest request) {
        if (unitOfMeasureRepository.existsByNameOrSymbol(request.getName(), request.getSymbol())) {
            throw new ResourceConflictException("UnitOfMeasure", "name/symbol", request.getName() + "/" + request.getSymbol());
        }
        UnitOfMeasure saved = unitOfMeasureRepository.save(UnitOfMeasure.builder()
                .name(request.getName())
                .symbol(request.getSymbol())
                .build());
        return toResponse(saved);
    }

    @Transactional
    public UnitOfMeasureResponse update(UUID id, CreateUnitOfMeasureRequest request) {
        UnitOfMeasure unit = unitOfMeasureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UnitOfMeasure", "id", id));
        unit.setName(request.getName());
        unit.setSymbol(request.getSymbol());
        return toResponse(unitOfMeasureRepository.save(unit));
    }

    @Transactional
    public void delete(UUID id) {
        if (!unitOfMeasureRepository.existsById(id)) {
            throw new ResourceNotFoundException("UnitOfMeasure", "id", id);
        }
        unitOfMeasureRepository.deleteById(id);
    }

    public UnitOfMeasureResponse toResponse(UnitOfMeasure unit) {
        if (unit == null) return null;
        return UnitOfMeasureResponse.builder()
                .id(unit.getId())
                .name(unit.getName())
                .symbol(unit.getSymbol())
                .build();
    }
}
