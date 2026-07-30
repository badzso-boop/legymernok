package com.legymernok.backend.dto.feedback;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackIssueResponse {
    private int number;
    private String title;
    private String bodyPreview;
    private String url;
    private String state;
    private String authorUsername;
    private OffsetDateTime createdAt;
}
