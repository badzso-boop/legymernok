package com.legymernok.backend.service.admin;

import com.legymernok.backend.dto.admin.AdminStatsResponse;
import com.legymernok.backend.dto.feedback.FeedbackIssueResponse;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.ConnectTables.CadetMissionRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.mission.MissionGroupProgressRepository;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import com.legymernok.backend.service.feedback.FeedbackService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminStatsServiceTest {

    @Mock
    private CadetRepository cadetRepository;
    @Mock
    private StarSystemRepository starSystemRepository;
    @Mock
    private MissionRepository missionRepository;
    @Mock
    private CadetMissionRepository cadetMissionRepository;
    @Mock
    private MissionGroupProgressRepository missionGroupProgressRepository;
    @Mock
    private FeedbackService feedbackService;

    @InjectMocks
    private AdminStatsService adminStatsService;

    @Test
    void getStats_AggregatesCountsAndPopularStarSystems() {
        UUID s1 = UUID.randomUUID();
        UUID s2 = UUID.randomUUID();

        Cadet cadet = Cadet.builder()
                .id(UUID.randomUUID())
                .username("newbie")
                .fullName("New Cadet")
                .createdAt(Instant.now())
                .build();

        when(cadetRepository.findTop10ByOrderByCreatedAtDesc()).thenReturn(List.of(cadet));
        when(cadetRepository.count()).thenReturn(10L);
        when(cadetRepository.countByCreatedAtAfter(any())).thenReturn(2L);
        when(starSystemRepository.count()).thenReturn(4L);
        when(missionRepository.count()).thenReturn(20L);
        when(feedbackService.listFeedback()).thenReturn(List.of(
                FeedbackIssueResponse.builder().number(1).state("open").build(),
                FeedbackIssueResponse.builder().number(2).state("closed").build()
        ));

        // s1: 3 kadét standalone-ból + 1 group-ból = 4 összesen; s2: 1 group-ból
        when(cadetMissionRepository.countDistinctCadetsByStarSystem())
                .thenReturn(List.<Object[]>of(new Object[]{s1, 3L}));
        when(missionGroupProgressRepository.countDistinctCadetsByStarSystem())
                .thenReturn(List.<Object[]>of(new Object[]{s1, 1L}, new Object[]{s2, 1L}));

        StarSystem system1 = StarSystem.builder().id(s1).name("Python Alapok").build();
        StarSystem system2 = StarSystem.builder().id(s2).name("Java Galaxis").build();
        when(starSystemRepository.findAllById(any())).thenReturn(List.of(system1, system2));

        AdminStatsResponse result = adminStatsService.getStats();

        assertEquals(10, result.getTotalCadets());
        assertEquals(4, result.getTotalStarSystems());
        assertEquals(20, result.getTotalMissions());
        assertEquals(2, result.getNewCadetsThisWeek());
        assertEquals(1, result.getOpenFeedbackCount());
        assertEquals(1, result.getRecentCadets().size());
        assertEquals("newbie", result.getRecentCadets().get(0).getUsername());

        assertEquals(2, result.getMostStartedStarSystems().size());
        assertEquals("Python Alapok", result.getMostStartedStarSystems().get(0).getName());
        assertEquals(4L, result.getMostStartedStarSystems().get(0).getStartedByCount());
        assertEquals("Java Galaxis", result.getMostStartedStarSystems().get(1).getName());
        assertEquals(1L, result.getMostStartedStarSystems().get(1).getStartedByCount());
    }
}
