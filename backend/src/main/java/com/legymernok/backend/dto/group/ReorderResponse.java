package com.legymernok.backend.dto.group;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ReorderResponse {

    private List<ReorderItem> updated;

    @Data
    @Builder
    public static class ReorderItem {
        private UUID id;
        private Integer orderIndex;
    }
}
