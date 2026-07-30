package com.legymernok.backend.service.feedback;

import com.legymernok.backend.dto.feedback.CreateFeedbackRequest;
import com.legymernok.backend.dto.feedback.FeedbackIssueResponse;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.integration.GitHubService;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.repository.cadet.CadetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final int PREVIEW_LENGTH = 240;

    private final GitHubService gitHubService;
    private final CadetRepository cadetRepository;

    public FeedbackIssueResponse submitFeedback(CreateFeedbackRequest request, String username) {
        Cadet currentUser = cadetRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "username", username));

        String body = request.getDescription()
                + "\n\n---\n_Beküldve a LégyMérnök.hu visszajelzés-felületén, "
                + currentUser.getUsername() + " kadét által._";

        Map<String, Object> issue = gitHubService.createIssue(request.getTitle(), body);
        return toResponse(issue);
    }

    public List<FeedbackIssueResponse> listFeedback() {
        return gitHubService.listFeedbackIssues().stream()
                .map(this::toResponse)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private FeedbackIssueResponse toResponse(Map<String, Object> issue) {
        String rawBody = (String) issue.get("body");
        String preview = rawBody == null ? "" : rawBody.strip();
        if (preview.length() > PREVIEW_LENGTH) {
            preview = preview.substring(0, PREVIEW_LENGTH) + "…";
        }

        Map<String, Object> user = (Map<String, Object>) issue.get("user");
        String authorUsername = user != null ? (String) user.get("login") : null;

        OffsetDateTime createdAt = null;
        Object createdAtRaw = issue.get("created_at");
        if (createdAtRaw instanceof String s) {
            createdAt = OffsetDateTime.parse(s);
        }

        return FeedbackIssueResponse.builder()
                .number(((Number) issue.get("number")).intValue())
                .title((String) issue.get("title"))
                .bodyPreview(preview)
                .url((String) issue.get("html_url"))
                .state((String) issue.get("state"))
                .authorUsername(authorUsername)
                .createdAt(createdAt)
                .build();
    }
}
