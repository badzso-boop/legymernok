package com.legymernok.backend.service.mission;

import com.legymernok.backend.dto.mission.CreateMissionRequest;
import com.legymernok.backend.dto.mission.MissionResponse;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.UnauthorizedAccessException;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.Difficulty;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionType;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import com.legymernok.backend.service.mission.MissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @Mock private MissionRepository missionRepository;
    @Mock private StarSystemRepository starSystemRepository;
    @Mock private GiteaService giteaService;
    @Mock private CadetRepository cadetRepository;
    @InjectMocks private MissionService missionService;

    private Cadet testUser;

    @BeforeEach
    void setUp() {
        testUser = new Cadet();
        testUser.setId(UUID.randomUUID());
        testUser.setUsername("test_user");

        Authentication auth = mock(Authentication.class);
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    private void mockSecurityContext(String username) {
        when(SecurityContextHolder.getContext().getAuthentication().getName()).thenReturn(username);
        when(cadetRepository.findByUsername(username)).thenReturn(Optional.of(testUser));
    }

    @Test
    void createMission_whenUserIsOwner_shouldSucceed() {
        mockSecurityContext("test_user");
        StarSystem userOwnedSystem = new StarSystem();
        userOwnedSystem.setOwner(testUser);
        userOwnedSystem.setId(UUID.randomUUID());

        CreateMissionRequest request = new CreateMissionRequest();
        request.setStarSystemId(userOwnedSystem.getId());
        request.setName("My First Mission");

        when(starSystemRepository.findById(userOwnedSystem.getId())).thenReturn(Optional.of(userOwnedSystem));
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArgument(0));
        when(giteaService.createRepository(anyString())).thenReturn("http://repo.url");

        assertDoesNotThrow(() -> missionService.createMission(request));
        verify(missionRepository).save(argThat(m -> m.getOwner().equals(testUser)));
    }

    @Test
    void createMission_inAnotherUsersSystem_shouldThrowUnauthorized() {
        mockSecurityContext("test_user");
        Cadet anotherUser = new Cadet();
        anotherUser.setId(UUID.randomUUID());
        StarSystem anotherSystem = new StarSystem();
        anotherSystem.setOwner(anotherUser);
        anotherSystem.setId(UUID.randomUUID());

        CreateMissionRequest request = new CreateMissionRequest();
        request.setStarSystemId(anotherSystem.getId());

        when(starSystemRepository.findById(anotherSystem.getId())).thenReturn(Optional.of(anotherSystem));

        assertThrows(UnauthorizedAccessException.class, () -> missionService.createMission(request));
    }

    @Test
    void deleteMission_byOwner_shouldSucceed() {
        mockSecurityContext("test_user");
        Mission mission = Mission.builder()
                .id(UUID.randomUUID())
                .owner(testUser)
                .starSystem(new StarSystem())
                .templateRepositoryUrl("http://gitea/user/repo.git")
                .build();

        when(missionRepository.findById(mission.getId())).thenReturn(Optional.of(mission));

        missionService.deleteMission(mission.getId());

        verify(missionRepository).delete(mission);
        verify(giteaService).deleteAdminRepository("repo");
    }

    @Test
    void createMission_withSmartInsert_shouldShiftOthers() {
        mockSecurityContext("test_user");
        StarSystem system = new StarSystem();
        system.setOwner(testUser);
        system.setId(UUID.randomUUID());

        CreateMissionRequest request = new CreateMissionRequest();
        request.setStarSystemId(system.getId());
        request.setOrderInSystem(2);
        request.setName("New Mission");

        when(starSystemRepository.findById(system.getId())).thenReturn(Optional.of(system));
        when(missionRepository.existsByStarSystemIdAndOrderInSystem(system.getId(), 2)).thenReturn(true);
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArgument(0));
        when(giteaService.createRepository(anyString())).thenReturn("http://repo.url");

        missionService.createMission(request);

        verify(missionRepository).shiftOrdersUp(system.getId(), 2);
        verify(missionRepository).save(any(Mission.class));
    }

    @Test
    void createMission_withDuplicateName_shouldThrowException() {
        mockSecurityContext("test_user");
        StarSystem system = new StarSystem();
        system.setOwner(testUser);
        system.setId(UUID.randomUUID());

        CreateMissionRequest request = new CreateMissionRequest();
        request.setStarSystemId(system.getId());
        request.setName("Existing Mission");

        when(starSystemRepository.findById(system.getId())).thenReturn(Optional.of(system));
        when(missionRepository.existsByStarSystemIdAndName(system.getId(), "Existing Mission")).thenReturn(true);

        assertThrows(ResourceConflictException.class, () -> missionService.createMission(request));
        verify(missionRepository, never()).save(any());
    }
}