package com.legymernok.backend.dto.fillinblank;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class FillInBlankResultResponse {

    private int score;
    private int maxScore;
    private int percentage;
    private boolean passed;
    private Instant submittedAt;
    private List<BlankResultDetail> details;

    @Data
    @Builder
    public static class BlankResultDetail {
        private String blankKey;
        private UUID selectedOptionId;
        private boolean correct;
        private List<String> correctOptionTexts;
    }
}
