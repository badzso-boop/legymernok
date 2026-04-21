package com.legymernok.backend.web.quiz;

import com.legymernok.backend.dto.quiz.QuizDefinition;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.MissionResult;
import com.legymernok.backend.service.quiz.QuizService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {
    private final QuizService quizService;

    @PostMapping("/{missionId}/start")
    @PreAuthorize("hasAuthority('mission:start')")
    public ResponseEntity<QuizDefinition> startQuiz(
            @PathVariable UUID missionId,
            @AuthenticationPrincipal Cadet cadet) throws Exception {

        return ResponseEntity.ok(quizService.startQuiz(missionId, cadet));
    }

    @PutMapping("/{missionId}/sync")
    @PreAuthorize("hasAuthority('mission:start')")
    public ResponseEntity<Void> syncProgress(
            @PathVariable UUID missionId,
            @RequestBody Map<String, Object> answers,
            @AuthenticationPrincipal Cadet cadet) throws Exception {

        quizService.syncProgress(missionId, cadet, answers);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{missionId}/submit")
    @PreAuthorize("hasAuthority('mission:start')")
    public ResponseEntity<MissionResult> submitQuiz(
            @PathVariable UUID missionId,
            @RequestBody Map<String, List<String>> answers,
            @AuthenticationPrincipal Cadet cadet) throws Exception {
        return ResponseEntity.ok(quizService.submitQuiz(missionId, cadet, answers));
    }

    @DeleteMapping("/{missionId}/sessions")
    @PreAuthorize("hasAuthority('mission:edit')")
    public ResponseEntity<Void> clearAllSessions(
            @PathVariable UUID missionId,
            @AuthenticationPrincipal Cadet cadet) {
        quizService.clearAllSessions(missionId, cadet);
        return ResponseEntity.noContent().build();
    }
}
