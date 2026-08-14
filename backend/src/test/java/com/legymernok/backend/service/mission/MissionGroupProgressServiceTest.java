package com.legymernok.backend.service.mission;

import com.legymernok.backend.dto.group.GroupProgressResponse;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionGroup;
import com.legymernok.backend.model.mission.MissionGroupProgress;
import com.legymernok.backend.model.mission.MissionGroupStepCompletion;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.mission.MissionGroupProgressRepository;
import com.legymernok.backend.repository.mission.MissionGroupRepository;
import com.legymernok.backend.repository.mission.MissionGroupStepCompletionRepository;
import com.legymernok.backend.repository.mission.MissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionGroupProgressServiceTest {

    @Mock private MissionGroupProgressRepository progressRepository;
    @Mock private MissionGroupStepCompletionRepository stepCompletionRepository;
    @Mock private MissionGroupRepository groupRepository;
    @Mock private MissionRepository missionRepository;
    @Mock private CadetRepository cadetRepository;
    @Mock private com.legymernok.backend.service.streak.StreakService streakService;
    @InjectMocks private MissionGroupProgressService missionGroupProgressService;

    private Cadet testUser;
    private MissionGroup testGroup;
    private UUID groupId;

    @BeforeEach
    void setUp() {
        testUser = new Cadet();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("test_user");

        groupId = UUID.randomUUID();
        testGroup = MissionGroup.builder()
                .id(groupId)
                .name("Test Group")
                .orderIndex(1)
                .build();

        Authentication auth = mock(Authentication.class);
        SecurityContext ctx = mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
        when(auth.getName()).thenReturn("test_user");
        when(cadetRepository.findByUsername("test_user")).thenReturn(Optional.of(testUser));
    }

    // =========================================================================
    // startProgress tesztek
    // =========================================================================

    @Test
    void startProgress_whenAlreadyExists_shouldThrow409() {
        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));
        when(progressRepository.findByCadetIdAndGroupId(testUser.getId(), groupId))
                .thenReturn(Optional.of(mock(MissionGroupProgress.class)));

        assertThrows(ResourceConflictException.class,
                () -> missionGroupProgressService.startProgress(groupId));
    }

    @Test
    void startProgress_whenGroupHasMissions_shouldSetFirstMissionAsNext() {
        Mission m1 = buildMission(UUID.randomUUID());
        Mission m2 = buildMission(UUID.randomUUID());

        when(groupRepository.findById(groupId)).thenReturn(Optional.of(testGroup));
        when(progressRepository.findByCadetIdAndGroupId(testUser.getId(), groupId))
                .thenReturn(Optional.empty());
        when(missionRepository.findAllByGroupIdOrderByGroupOrderAsc(groupId))
                .thenReturn(List.of(m1, m2));
        when(progressRepository.save(any(MissionGroupProgress.class))).thenAnswer(i -> {
            MissionGroupProgress p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        GroupProgressResponse response = missionGroupProgressService.startProgress(groupId);

        assertEquals(m1.getId(), response.getNextMissionId());
        assertFalse(response.isCompleted());
    }

    // =========================================================================
    // completeStep tesztek
    // =========================================================================

    @Test
    void completeStep_whenLastMission_shouldMarkCompleted() {
        Mission m1 = buildMission(UUID.randomUUID());

        MissionGroupProgress progress = MissionGroupProgress.builder()
                .id(UUID.randomUUID())
                .cadet(testUser)
                .group(testGroup)
                .nextMissionId(m1.getId())
                .completed(false)
                .build();

        MissionGroupStepCompletion stepCompletion = MissionGroupStepCompletion.builder()
                .id(UUID.randomUUID())
                .progress(progress)
                .mission(m1)
                .build();

        when(progressRepository.findByCadetIdAndGroupId(testUser.getId(), groupId))
                .thenReturn(Optional.of(progress));
        when(missionRepository.findAllByGroupIdOrderByGroupOrderAsc(groupId))
                .thenReturn(List.of(m1));
        when(missionRepository.findById(m1.getId())).thenReturn(Optional.of(m1));
        when(stepCompletionRepository.saveAndFlush(any())).thenReturn(stepCompletion);
        when(stepCompletionRepository.findAllByProgressId(progress.getId()))
                .thenReturn(List.of(stepCompletion));
        when(progressRepository.save(any(MissionGroupProgress.class))).thenAnswer(i -> i.getArgument(0));

        GroupProgressResponse response = missionGroupProgressService.completeStep(groupId);

        assertTrue(response.isCompleted());
        assertNull(response.getNextMissionId());
        assertNotNull(progress.getCompletedAt());
    }

    @Test
    void completeStep_whenMiddleMission_shouldAdvanceToNextMission() {
        Mission m1 = buildMission(UUID.randomUUID());
        Mission m2 = buildMission(UUID.randomUUID());

        MissionGroupProgress progress = MissionGroupProgress.builder()
                .id(UUID.randomUUID())
                .cadet(testUser)
                .group(testGroup)
                .nextMissionId(m1.getId())
                .completed(false)
                .build();

        MissionGroupStepCompletion stepCompletion = MissionGroupStepCompletion.builder()
                .id(UUID.randomUUID())
                .progress(progress)
                .mission(m1)
                .build();

        when(progressRepository.findByCadetIdAndGroupId(testUser.getId(), groupId))
                .thenReturn(Optional.of(progress));
        when(missionRepository.findAllByGroupIdOrderByGroupOrderAsc(groupId))
                .thenReturn(List.of(m1, m2));
        when(missionRepository.findById(m1.getId())).thenReturn(Optional.of(m1));
        when(stepCompletionRepository.saveAndFlush(any())).thenReturn(stepCompletion);
        when(stepCompletionRepository.findAllByProgressId(progress.getId()))
                .thenReturn(List.of(stepCompletion));
        when(progressRepository.save(any(MissionGroupProgress.class))).thenAnswer(i -> i.getArgument(0));

        GroupProgressResponse response = missionGroupProgressService.completeStep(groupId);

        assertFalse(response.isCompleted());
        assertEquals(m2.getId(), response.getNextMissionId());
    }

    @Test
    void completeStep_whenStepAlreadyCompleted_shouldBeIdempotent() {
        Mission m1 = buildMission(UUID.randomUUID());

        MissionGroupProgress progress = MissionGroupProgress.builder()
                .id(UUID.randomUUID())
                .cadet(testUser)
                .group(testGroup)
                .nextMissionId(m1.getId())
                .completed(false)
                .build();

        MissionGroupStepCompletion stepCompletion = MissionGroupStepCompletion.builder()
                .id(UUID.randomUUID()).progress(progress).mission(m1).build();

        when(progressRepository.findByCadetIdAndGroupId(testUser.getId(), groupId))
                .thenReturn(Optional.of(progress));
        when(missionRepository.findAllByGroupIdOrderByGroupOrderAsc(groupId))
                .thenReturn(List.of(m1));
        when(missionRepository.findById(m1.getId())).thenReturn(Optional.of(m1));
        // Unique constraint → DataIntegrityViolationException: idempotens viselkedés
        when(stepCompletionRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("unique constraint"));
        // A progress már tartalmazza az elvégzett lépést
        when(stepCompletionRepository.findAllByProgressId(progress.getId()))
                .thenReturn(List.of(stepCompletion));
        when(progressRepository.save(any(MissionGroupProgress.class))).thenAnswer(i -> i.getArgument(0));

        // Nem dob kivételt — idempotens
        assertDoesNotThrow(() -> missionGroupProgressService.completeStep(groupId));

        // Progresst frissíti (completed=true, mert m1 már teljesítve van)
        ArgumentCaptor<MissionGroupProgress> captor = ArgumentCaptor.forClass(MissionGroupProgress.class);
        verify(progressRepository).save(captor.capture());
        assertTrue(captor.getValue().isCompleted());
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private Mission buildMission(UUID id) {
        Mission m = new Mission();
        m.setId(id);
        return m;
    }
}
