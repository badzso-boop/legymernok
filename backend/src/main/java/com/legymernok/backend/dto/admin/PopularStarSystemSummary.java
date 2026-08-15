package com.legymernok.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class PopularStarSystemSummary {
    private UUID id;
    private String name;
    /** Hány egyedi kadét indított el legalább egy tartalmat (standalone missziót vagy group-ot) ebben a rendszerben. */
    private long startedByCount;
}
