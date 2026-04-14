package com.legymernok.backend.dto.fillinblank;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class LastAttemptResponse {

    private boolean passed;
    private int percentage;
    private Instant submittedAt;
}
