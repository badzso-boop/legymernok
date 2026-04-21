package com.legymernok.backend.dto.starsystem;

import lombok.Data;
import java.util.UUID;

@Data
public class ReorderItemsRequest {
    private UUID item1Id;
    private String item1Type;
    private UUID item2Id;
    private String item2Type;
}
