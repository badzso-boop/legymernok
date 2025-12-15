package com.legymernok.backend.service.mission;

import com.legymernok.backend.dto.mission.CreateMissionRequest;
import com.legymernok.backend.dto.mission.MissionResponse;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.mission.Difficulty;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionType;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @Mock
    private MissionRepository missionRepository;
    @Mock
    private StarSystemRepository starSystemRepository;
    @Mock
    private GiteaService giteaService;

    @InjectMocks
    private MissionService missionService;

    @Test
    void createMission_WithFiles_Success() {
        // Arrange
        UUID starSystemId = UUID.randomUUID();
        CreateMissionRequest request = new CreateMissionRequest();
        request.setStarSystemId(starSystemId);
        request.setName("File Mission");
        request.setMissionType(MissionType.CODING);
        request.setDifficulty(Difficulty.EASY);
        request.setOrderInSystem(1);

        Map<String, String> files = new HashMap<>();
        files.put("Main.java", "code");
        request.setTemplateFiles(files);

        StarSystem starSystem = new StarSystem();
        starSystem.setId(starSystemId);

        when(starSystemRepository.findById(starSystemId)).thenReturn(Optional.of(starSystem));
        when(giteaService.createRepository(anyString())).thenReturn("http://gitea/repo.git");
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> {
            Mission m = i.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        // Act
        MissionResponse response = missionService.createMission(request);

        // Assert
        assertNotNull(response);
        // Ellenőrizzük, hogy meghívta-e a repo létrehozást
        verify(giteaService).createRepository(contains("mission-template-file-mission"));
        // Ellenőrizzük, hogy feltöltötte-e a fájlt
        verify(giteaService).createFile(anyString(), eq("Main.java"), eq("code"));
        // Ellenőrizzük, hogy mentette-e az adatbázisba
        verify(missionRepository).save(any(Mission.class));
    }

    @Test
    void getMissionsByStarSystem() {
        UUID starSystemId = UUID.randomUUID();
        StarSystem ss = new StarSystem();
        ss.setId(starSystemId);

        Mission m1 = Mission.builder().id(UUID.randomUUID()).starSystem(ss).build();
        Mission m2 = Mission.builder().id(UUID.randomUUID()).starSystem(ss).build();

        when(missionRepository.findAllByStarSystemIdOrderByOrderInSystemAsc(starSystemId))
                .thenReturn(List.of(m1, m2));

        // Mock Security Context for isAdmin check (default false/null)
        SecurityContext securityContext = mock(SecurityContext.class);
        SecurityContextHolder.setContext(securityContext);

        List<MissionResponse> responses = missionService.getMissionsByStarSystem(starSystemId);

        assertEquals(2, responses.size());
    }
}
