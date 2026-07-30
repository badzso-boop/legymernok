package com.legymernok.backend.service.feedback;

import com.legymernok.backend.dto.feedback.CreateFeedbackRequest;
import com.legymernok.backend.dto.feedback.FeedbackIssueResponse;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.integration.GitHubService;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.repository.cadet.CadetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private GitHubService gitHubService;
    @Mock
    private CadetRepository cadetRepository;

    @InjectMocks
    private FeedbackService feedbackService;

    private Cadet testUser;

    @BeforeEach
    void setUp() {
        testUser = new Cadet();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("qa_cadet");
    }

    @Test
    void submitFeedback_shouldCreateIssueWithAttributionAndReturnMappedResponse() {
        when(cadetRepository.findByUsername("qa_cadet")).thenReturn(Optional.of(testUser));
        Map<String, Object> githubResponse = Map.of(
                "number", 42,
                "title", "Missing dark mode",
                "body", "Please add dark mode\n\n---\n_Beküldve..._",
                "html_url", "https://github.com/badzso-boop/legymernok/issues/42",
                "state", "open",
                "user", Map.of("login", "legymernok-bot"),
                "created_at", "2026-07-30T10:00:00Z"
        );
        when(gitHubService.createIssue(anyString(), anyString())).thenReturn(githubResponse);

        CreateFeedbackRequest request = new CreateFeedbackRequest();
        request.setTitle("Missing dark mode");
        request.setDescription("Please add dark mode");

        FeedbackIssueResponse result = feedbackService.submitFeedback(request, "qa_cadet");

        assertEquals(42, result.getNumber());
        assertEquals("Missing dark mode", result.getTitle());
        assertEquals("open", result.getState());
        assertEquals("https://github.com/badzso-boop/legymernok/issues/42", result.getUrl());

        verify(gitHubService).createIssue(eq("Missing dark mode"), argThat(body ->
                body.contains("Please add dark mode") && body.contains("qa_cadet kadét által")));
    }

    @Test
    void submitFeedback_whenUserNotFound_shouldThrow() {
        when(cadetRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        CreateFeedbackRequest request = new CreateFeedbackRequest();
        request.setTitle("t");
        request.setDescription("d");

        assertThrows(ResourceNotFoundException.class, () -> feedbackService.submitFeedback(request, "ghost"));
    }

    @Test
    void listFeedback_shouldMapAllIssuesAndTruncateLongBodies() {
        String longBody = "x".repeat(300);
        Map<String, Object> issue1 = Map.of(
                "number", 1,
                "title", "First",
                "body", longBody,
                "html_url", "https://github.com/badzso-boop/legymernok/issues/1",
                "state", "open",
                "user", Map.of("login", "cadet1"),
                "created_at", "2026-07-01T00:00:00Z"
        );
        Map<String, Object> issue2 = new java.util.HashMap<>();
        issue2.put("number", 2);
        issue2.put("title", "Second");
        issue2.put("body", null);
        issue2.put("html_url", "https://github.com/badzso-boop/legymernok/issues/2");
        issue2.put("state", "closed");
        issue2.put("user", null);
        issue2.put("created_at", "2026-07-02T00:00:00Z");

        when(gitHubService.listFeedbackIssues()).thenReturn(List.of(issue1, issue2));

        List<FeedbackIssueResponse> result = feedbackService.listFeedback();

        assertEquals(2, result.size());
        assertTrue(result.get(0).getBodyPreview().endsWith("…"));
        assertTrue(result.get(0).getBodyPreview().length() <= 241);
        assertNull(result.get(1).getAuthorUsername());
        assertEquals("", result.get(1).getBodyPreview());
    }
}
