package com.legymernok.backend.service.quiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.exception.UnauthorizedAccessException;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.auth.Permission;
import com.legymernok.backend.model.auth.Role;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.repository.mission.MissionResultRepository;
import com.legymernok.backend.repository.quiz.QuizSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock private GiteaService giteaService;
    @Mock private QuizSessionRepository quizSessionRepository;
    @Mock private MissionRepository missionRepository;
    @Mock private MissionResultRepository missionResultRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private QuizService quizService;

    private Cadet testUser;
    private StarSystem testStarSystem;

    @BeforeEach
    void setUp() {
        testUser = new Cadet();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("test_user");
        testUser.setRoles(new HashSet<>());

        testStarSystem = new StarSystem();
        testStarSystem.setId(UUID.randomUUID());
        testStarSystem.setOwner(testUser);
        testStarSystem.setName("TestSystem");
    }

    private void mockUserAuthorities(String... authorities) {
        Set<Permission> permissions = new HashSet<>();
        for (String authName : authorities) {
            Permission p = new Permission();
            p.setName(authName);
            permissions.add(p);
        }
        Role mockRole = new Role();
        mockRole.setName("MOCK_ROLE");
        mockRole.setPermissions(permissions);
        testUser.setRoles(Set.of(mockRole));
    }

    // =========================================================================
    // clearAllSessions tesztek
    // =========================================================================

    @Test
    void clearAllSessions_byOwner_shouldDeleteAllSessions() {
        UUID missionId = UUID.randomUUID();
        Mission mission = Mission.builder()
                .id(missionId)
                .owner(testUser)
                .starSystem(testStarSystem)
                .build();

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        doNothing().when(quizSessionRepository).deleteAllByMissionId(missionId);

        quizService.clearAllSessions(missionId, testUser);

        verify(quizSessionRepository).deleteAllByMissionId(missionId);
    }

    @Test
    void clearAllSessions_byNonOwnerWithoutPermission_shouldThrowUnauthorized() {
        UUID missionId = UUID.randomUUID();
        Cadet anotherUser = new Cadet();
        anotherUser.setId(UUID.randomUUID());

        Mission mission = Mission.builder()
                .id(missionId)
                .owner(anotherUser)
                .starSystem(testStarSystem)
                .build();

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));

        assertThrows(UnauthorizedAccessException.class,
                () -> quizService.clearAllSessions(missionId, testUser));

        verify(quizSessionRepository, never()).deleteAllByMissionId(any());
    }

    @Test
    void clearAllSessions_byAdminWithEditAnyPermission_shouldDeleteAllSessions() {
        UUID missionId = UUID.randomUUID();
        Cadet anotherUser = new Cadet();
        anotherUser.setId(UUID.randomUUID());

        Mission mission = Mission.builder()
                .id(missionId)
                .owner(anotherUser)
                .starSystem(testStarSystem)
                .build();

        mockUserAuthorities("mission:edit_any");

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        doNothing().when(quizSessionRepository).deleteAllByMissionId(missionId);

        quizService.clearAllSessions(missionId, testUser);

        verify(quizSessionRepository).deleteAllByMissionId(missionId);
    }

    @Test
    void clearAllSessions_whenMissionNotFound_shouldThrowNotFoundException() {
        UUID missionId = UUID.randomUUID();
        when(missionRepository.findById(missionId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> quizService.clearAllSessions(missionId, testUser));

        verify(quizSessionRepository, never()).deleteAllByMissionId(any());
    }

    @Test
    void clearAllSessions_whenNoActiveSessions_shouldCompleteWithoutError() {
        UUID missionId = UUID.randomUUID();
        Mission mission = Mission.builder()
                .id(missionId)
                .owner(testUser)
                .starSystem(testStarSystem)
                .build();

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        // deleteAllByMissionId 0 sessionre is meghívódik, nem dob kivételt
        doNothing().when(quizSessionRepository).deleteAllByMissionId(missionId);

        quizService.clearAllSessions(missionId, testUser);

        verify(quizSessionRepository).deleteAllByMissionId(missionId);
    }
}
