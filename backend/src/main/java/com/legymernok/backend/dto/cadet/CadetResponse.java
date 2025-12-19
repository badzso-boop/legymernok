package com.legymernok.backend.dto.cadet;

import com.legymernok.backend.model.cadet.CadetRole;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class CadetResponse {
    private UUID id;
    private String username;
    private String email;
    private Set<String> roles;
    private Long giteaUserId;
    private Instant createdAt;
    private Instant updatedAt;
}
