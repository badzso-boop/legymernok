package com.legymernok.backend.dto.quiz;

import lombok.Data;
import java.util.List;

@Data
public class QuizDefinition {
    private QuizConfigDTO config;
    private List<QuizQuestionDTO> questions;

    @Data
    public static class QuizConfigDTO {
        private Integer timeLimitSeconds;
        private boolean allowNavigation;
        private boolean showSolutions;
    }

    @Data
    public static class QuizQuestionDTO {
        private String id;
        private String text;
        private Integer points;
        private List<QuizOptionDTO> options;
    }

    @Data
    public static class QuizOptionDTO {
        private String id;
        private String text;
        private Boolean isCorrect; // Csak a szerver látja és a Forge
    }
}
