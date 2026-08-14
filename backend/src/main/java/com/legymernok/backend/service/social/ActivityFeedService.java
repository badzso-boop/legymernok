package com.legymernok.backend.service.social;

import com.legymernok.backend.dto.social.ActivityFeedItemResponse;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.fillinblank.FillInBlankAttempt;
import com.legymernok.backend.model.mission.MissionGroupStepCompletion;
import com.legymernok.backend.model.mission.MissionResult;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.fillinblank.FillInBlankAttemptRepository;
import com.legymernok.backend.repository.mission.MissionGroupStepCompletionRepository;
import com.legymernok.backend.repository.mission.MissionResultRepository;
import com.legymernok.backend.repository.social.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A követett kadétok legutóbbi teljesítéseinek összefésült listája — terv 5.2/7.2.
 * Három forrás-tábla (group step, fill-in-blank, quiz) egyszerű Java-oldali
 * összefésülésével, nem natív SQL UNION-nal.
 */
@Service
@RequiredArgsConstructor
public class ActivityFeedService {

    private static final int FEED_SIZE = 20;

    private final FollowRepository followRepository;
    private final CadetRepository cadetRepository;
    private final MissionGroupStepCompletionRepository stepCompletionRepository;
    private final FillInBlankAttemptRepository fillInBlankAttemptRepository;
    private final MissionResultRepository missionResultRepository;

    @Transactional(readOnly = true)
    public List<ActivityFeedItemResponse> getActivityFeed() {
        Cadet current = getCurrentAuthenticatedUser();
        List<UUID> followeeIds = followRepository.findAllByFollower_Id(current.getId()).stream()
                .map(f -> f.getFollowee().getId())
                .collect(Collectors.toList());

        if (followeeIds.isEmpty()) {
            return List.of();
        }

        Stream<ActivityFeedItemResponse> groupSteps = stepCompletionRepository
                .findTop20ByProgress_Cadet_IdInOrderByCompletedAtDesc(followeeIds).stream()
                .map(this::mapGroupStep);

        Stream<ActivityFeedItemResponse> fillInBlanks = fillInBlankAttemptRepository
                .findTop20ByCadet_IdInAndPassedTrueOrderBySubmittedAtDesc(followeeIds).stream()
                .map(this::mapFillInBlank);

        Stream<ActivityFeedItemResponse> quizzes = missionResultRepository
                .findTop20ByCadet_IdInOrderByCompletedAtDesc(followeeIds).stream()
                .map(this::mapQuiz);

        return Stream.of(groupSteps, fillInBlanks, quizzes)
                .flatMap(s -> s)
                .sorted(Comparator.comparing(ActivityFeedItemResponse::getOccurredAt).reversed())
                .limit(FEED_SIZE)
                .collect(Collectors.toList());
    }

    private ActivityFeedItemResponse mapGroupStep(MissionGroupStepCompletion step) {
        Cadet cadet = step.getProgress().getCadet();
        return ActivityFeedItemResponse.builder()
                .cadetId(cadet.getId())
                .cadetUsername(cadet.getUsername())
                .type("GROUP_STEP")
                .label(step.getMission().getName())
                .occurredAt(step.getCompletedAt())
                .build();
    }

    private ActivityFeedItemResponse mapFillInBlank(FillInBlankAttempt attempt) {
        return ActivityFeedItemResponse.builder()
                .cadetId(attempt.getCadet().getId())
                .cadetUsername(attempt.getCadet().getUsername())
                .type("FILL_IN_BLANK")
                .label(attempt.getMission().getName())
                .occurredAt(attempt.getSubmittedAt())
                .build();
    }

    private ActivityFeedItemResponse mapQuiz(MissionResult result) {
        return ActivityFeedItemResponse.builder()
                .cadetId(result.getCadet().getId())
                .cadetUsername(result.getCadet().getUsername())
                .type("QUIZ")
                .label(result.getMission().getName())
                .occurredAt(result.getCompletedAt())
                .build();
    }

    private Cadet getCurrentAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return cadetRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "username", username));
    }
}
