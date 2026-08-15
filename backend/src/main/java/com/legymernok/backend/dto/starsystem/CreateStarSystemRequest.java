package com.legymernok.backend.dto.starsystem;

import lombok.Data;

import java.util.UUID;

@Data
public class CreateStarSystemRequest {
    private String name;
    private String description;
    private String iconUrl;
    private UUID sectorId;
}