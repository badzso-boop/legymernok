package com.legymernok.backend.dto;

import com.legymernok.backend.model.CadetRole;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CadetResponse {
    private UUID id;
    private String username;
    private String email;
    private CadetRole role;
    private Long giteaUserId;
    private Instant createdAt;
}
