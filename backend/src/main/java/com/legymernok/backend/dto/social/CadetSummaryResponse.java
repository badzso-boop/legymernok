package com.legymernok.backend.dto.social;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class CadetSummaryResponse {
    private UUID id;
    private String username;
    private String fullName;
    private String avatarUrl;
}
