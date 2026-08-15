package com.legymernok.backend.dto.Roles;

import com.legymernok.backend.dto.Permission.PermissionResponse;
import lombok.Builder;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class RoleResponse {
    private UUID id;
    private String name;
    private String description;
    private Set<PermissionResponse> permissions;
}
