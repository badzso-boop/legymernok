package com.legymernok.backend.service.social;

import com.legymernok.backend.dto.social.ActivityFeedItemResponse;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.social.Follow;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.fillinblank.FillInBlankAttemptRepository;
import com.legymernok.backend.repository.mission.MissionGroupStepCompletionRepository;
import com.legymernok.backend.repository.mission.MissionResultRepository;
import com.legymernok.backend.repository.social.FollowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityFeedServiceTest {

    @Mock private FollowRepository followRepository;
    @Mock private CadetRepository cadetRepository;
    @Mock private MissionGroupStepCompletionRepository stepCompletionRepository;
    @Mock private FillInBlankAttemptRepository fillInBlankAttemptRepository;
    @Mock private MissionResultRepository missionResultRepository;
    @Mock private Authentication mockAuthentication;

    private ActivityFeedService activityFeedService;
    private Cadet currentUser;

    @BeforeEach
    void setUp() {
        activityFeedService = new ActivityFeedService(
                followRepository, cadetRepository, stepCompletionRepository,
                fillInBlankAttemptRepository, missionResultRepository);
        currentUser = Cadet.builder().id(UUID.randomUUID()).username("kadét").build();

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(mockAuthentication);
        SecurityContextHolder.setContext(securityContext);
        when(mockAuthentication.getName()).thenReturn(currentUser.getUsername());
        when(cadetRepository.findByUsername(currentUser.getUsername())).thenReturn(Optional.of(currentUser));
    }

    @Test
    void getActivityFeed_NoFollowees_ReturnsEmpty() {
        when(followRepository.findAllByFollower_Id(currentUser.getId())).thenReturn(List.of());

        List<ActivityFeedItemResponse> result = activityFeedService.getActivityFeed();

        assertTrue(result.isEmpty());
        verifyNoInteractions(stepCompletionRepository, fillInBlankAttemptRepository, missionResultRepository);
    }

    @Test
    void getActivityFeed_WithFollowees_QueriesAllThreeSources() {
        Cadet followee = Cadet.builder().id(UUID.randomUUID()).username("kovetett").build();
        Follow follow = Follow.builder().follower(currentUser).followee(followee).build();
        when(followRepository.findAllByFollower_Id(currentUser.getId())).thenReturn(List.of(follow));
        when(stepCompletionRepository.findTop20ByProgress_Cadet_IdInOrderByCompletedAtDesc(anyList()))
                .thenReturn(List.of());
        when(fillInBlankAttemptRepository.findTop20ByCadet_IdInAndPassedTrueOrderBySubmittedAtDesc(anyList()))
                .thenReturn(List.of());
        when(missionResultRepository.findTop20ByCadet_IdInOrderByCompletedAtDesc(anyList()))
                .thenReturn(List.of());

        List<ActivityFeedItemResponse> result = activityFeedService.getActivityFeed();

        assertTrue(result.isEmpty());
        verify(stepCompletionRepository).findTop20ByProgress_Cadet_IdInOrderByCompletedAtDesc(List.of(followee.getId()));
    }
}
