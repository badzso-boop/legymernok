package com.legymernok.backend.dto.circuit;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SaveCadetCircuitRequest {
    @NotNull
    private List<UpsertCircuitDefComponentRequest> components;

    @NotNull
    private List<UpsertCircuitDefConnectionRequest> connections;
}
