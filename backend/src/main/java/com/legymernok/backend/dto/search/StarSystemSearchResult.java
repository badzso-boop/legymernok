package com.legymernok.backend.dto.search;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class StarSystemSearchResult {
    private UUID id;
    private String name;
    private String description;
    private String iconUrl;
    private double similarity;
}
