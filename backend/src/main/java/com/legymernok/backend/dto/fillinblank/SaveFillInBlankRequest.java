package com.legymernok.backend.dto.fillinblank;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SaveFillInBlankRequest {

    @NotBlank
    private String templateText;

    private Integer passThreshold;

    @NotEmpty
    private List<BlankRequest> blanks;

    @Data
    public static class BlankRequest {
        @NotBlank
        private String key;
        private int orderIndex;
        @NotEmpty
        private List<OptionRequest> options;
    }

    @Data
    public static class OptionRequest {
        @NotBlank
        private String optionText;
        private boolean correct;
        private int orderIndex;
    }
}
