package com.legymernok.backend.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class RecentCadetSummary {
    private UUID id;
    private String username;
    private String fullName;
    private Instant createdAt;
}
