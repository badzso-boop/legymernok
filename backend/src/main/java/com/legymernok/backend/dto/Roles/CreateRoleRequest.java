package com.legymernok.backend.dto.Roles;

import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class CreateRoleRequest {
    private String name;
    private String description;
    private Set<UUID> permissionIds;
}
