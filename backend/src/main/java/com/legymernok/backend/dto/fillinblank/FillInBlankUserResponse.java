package com.legymernok.backend.dto.fillinblank;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class FillInBlankUserResponse {

    private UUID missionId;
    private String templateText;
    private Integer passThreshold;
    private List<BlankResponse> blanks;

    @Data
    @Builder
    public static class BlankResponse {
        private UUID id;
        private String key;
        private int orderIndex;
        private List<OptionResponse> options;
    }

    @Data
    @Builder
    public static class OptionResponse {
        private UUID id;
        private String optionText;
        private int orderIndex;
        // correct mező NINCS — szándékosan
    }
}
