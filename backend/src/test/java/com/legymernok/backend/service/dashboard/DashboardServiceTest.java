package com.legymernok.backend.service.dashboard;

import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.ConnectTable.CadetMission;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.ConnectTables.CadetMissionRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.mission.MissionGroupProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private CadetRepository cadetRepository;
    @Mock private CadetMissionRepository cadetMissionRepository;
    @Mock private MissionGroupProgressRepository missionGroupProgressRepository;
    @Mock private Authentication mockAuthentication;

    private DashboardService dashboardService;
    private Cadet currentUser;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(cadetRepository, cadetMissionRepository, missionGroupProgressRepository);
        currentUser = Cadet.builder().id(UUID.randomUUID()).username("kadét").build();

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(mockAuthentication);
        SecurityContextHolder.setContext(securityContext);
        when(mockAuthentication.getName()).thenReturn(currentUser.getUsername());
        when(cadetRepository.findByUsername(currentUser.getUsername())).thenReturn(Optional.of(currentUser));
    }

    @Test
    void getContinue_NoActivity_ThrowsNotFound() {
        when(cadetMissionRepository.findAllByCadetId(currentUser.getId())).thenReturn(List.of());
        when(missionGroupProgressRepository.findAllByCadetId(currentUser.getId())).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> dashboardService.getContinue());
    }

    @Test
    void getContinue_OnlyMissionActivity_ReturnsMission() {
        StarSystem system = StarSystem.builder().id(UUID.randomUUID()).build();
        Mission mission = Mission.builder().id(UUID.randomUUID()).name("Első lépések").starSystem(system).build();
        CadetMission cadetMission = CadetMission.builder().mission(mission).lastUpdatedAt(Instant.now()).build();

        when(cadetMissionRepository.findAllByCadetId(currentUser.getId())).thenReturn(List.of(cadetMission));
        when(missionGroupProgressRepository.findAllByCadetId(currentUser.getId())).thenReturn(List.of());

        var result = dashboardService.getContinue();

        assertEquals("MISSION", result.getType());
        assertEquals(mission.getId(), result.getMissionId());
        assertEquals(system.getId(), result.getStarSystemId());
    }
}
