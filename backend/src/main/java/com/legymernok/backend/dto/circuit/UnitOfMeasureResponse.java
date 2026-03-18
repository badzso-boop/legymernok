package com.legymernok.backend.dto.circuit;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class UnitOfMeasureResponse {
    private UUID id;
    private String name;
    private String symbol;
}
