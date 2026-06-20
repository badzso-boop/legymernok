package com.legymernok.backend.dto.group;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ReorderItemRequest {

    @NotNull
    private UUID targetId;
}
