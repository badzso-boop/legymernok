package com.legymernok.backend.web.feedback;

import com.legymernok.backend.dto.feedback.CreateFeedbackRequest;
import com.legymernok.backend.dto.feedback.FeedbackIssueResponse;
import com.legymernok.backend.service.feedback.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Nincs külön permission ehhez — bármely bejelentkezett kadét beküldhet
// visszajelzést és láthatja a listát, ugyanúgy, ahogy bárki nyithatna egy
// GitHub issue-t a repóban. A SecurityConfig alap .anyRequest().authenticated()
// szabálya védi (bejelentkezés kötelező, admin jog nem).
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @GetMapping
    public ResponseEntity<List<FeedbackIssueResponse>> listFeedback() {
        return ResponseEntity.ok(feedbackService.listFeedback());
    }

    @PostMapping
    public ResponseEntity<FeedbackIssueResponse> submitFeedback(@Valid @RequestBody CreateFeedbackRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        FeedbackIssueResponse created = feedbackService.submitFeedback(request, username);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }
}
