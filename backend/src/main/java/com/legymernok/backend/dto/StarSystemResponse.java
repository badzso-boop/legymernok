package com.legymernok.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class StarSystemResponse {
    private UUID id;
    private String name;
    private String description;
    private String iconUrl;
    private Instant createdAt;
}