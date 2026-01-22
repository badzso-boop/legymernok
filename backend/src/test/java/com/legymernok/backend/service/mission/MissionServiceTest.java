package com.legymernok.backend.service.mission;

import com.legymernok.backend.dto.mission.CreateMissionRequest;
import com.legymernok.backend.dto.mission.MissionResponse;
import com.legymernok.backend.integration.GiteaService;
import com.legymernok.backend.model.mission.Difficulty;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionType;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.ConnectTables.CadetMissionRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MissionServiceTest {

    @Mock
    private MissionRepository missionRepository;
    @Mock
    private StarSystemRepository starSystemRepository;
    @Mock
    private GiteaService giteaService;
    @Mock
    private CadetRepository cadetRepository;
    @Mock
    private CadetMissionRepository cadetMissionRepository;

    @InjectMocks
    private MissionService missionService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

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

    @Test
    void createMission_WithSmartInsert_Success() {
        // Arrange
        UUID starSystemId = UUID.randomUUID();
        CreateMissionRequest request = new CreateMissionRequest();
        request.setStarSystemId(starSystemId);
        request.setName("New Mission");
        request.setOrderInSystem(2); // A 2. helyre akarjuk
        request.setMissionType(MissionType.CODING);
        request.setDifficulty(Difficulty.EASY);

        StarSystem starSystem = new StarSystem();
        starSystem.setId(starSystemId);

        when(starSystemRepository.findById(starSystemId)).thenReturn(Optional.of(starSystem));

        // Szimuláljuk, hogy a 2. hely már FOGLALT
        when(missionRepository.existsByStarSystemIdAndOrderInSystem(starSystemId, 2)).thenReturn(true);
        // Szimuláljuk, hogy a név még NEM létezik
        when(missionRepository.existsByStarSystemIdAndName(starSystemId, "New Mission")).thenReturn(false);

        when(giteaService.createRepository(anyString())).thenReturn("http://repo.git");
        when(missionRepository.save(any(Mission.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        missionService.createMission(request);

        // Assert
        // 1. Ellenőrizzük, hogy megtörtént-e az eltolás (Smart Insert)
        verify(missionRepository).shiftOrdersUp(starSystemId, 2);
        // 2. Ellenőrizzük, hogy volt-e flush
        verify(missionRepository).flush();
        // 3. Mentés
        verify(missionRepository).save(any(Mission.class));
    }

    @Test
    void createMission_DuplicateName_ThrowsException() {
        // Arrange
        UUID starSystemId = UUID.randomUUID();
        CreateMissionRequest request = new CreateMissionRequest();
        request.setStarSystemId(starSystemId);
        request.setName("Duplicate Mission");

        StarSystem starSystem = new StarSystem();
        starSystem.setId(starSystemId);

        when(starSystemRepository.findById(starSystemId)).thenReturn(Optional.of(starSystem));
        // Szimuláljuk, hogy a név MÁR létezik
        when(missionRepository.existsByStarSystemIdAndName(starSystemId, "Duplicate Mission")).thenReturn(true);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> missionService.createMission(request));

        // Biztosítjuk, hogy NEM történt mentés
        verify(missionRepository, never()).save(any());
        verify(giteaService, never()).createRepository(anyString());
    }

    @Test
    void deleteMission_WithSmartDelete_Success() {
        // Arrange
        UUID missionId = UUID.randomUUID();
        UUID starSystemId = UUID.randomUUID();

        StarSystem starSystem = new StarSystem();
        starSystem.setId(starSystemId);

        Mission mission = Mission.builder()
                .id(missionId)
                .starSystem(starSystem)
                .orderInSystem(5) // Az 5. elemet töröljük
                .templateRepositoryUrl("http://gitea/admin/repo.git")
                .build();

        when(missionRepository.findById(missionId)).thenReturn(Optional.of(mission));
        // Szimuláljuk az admin username lekérését (ha publikus lenne, de most feltételezzük, hogy a deleteAdminRepository-t hívja)
        // when(giteaService.getAdminUsername()).thenReturn("admin");

        // Act
        missionService.deleteMission(missionId);

        // Assert
        // 1. Gitea törlés
        verify(giteaService).deleteAdminRepository("repo");
        // 2. DB törlés
        verify(missionRepository).delete(mission);
        // 3. Smart Delete (visszahúzás)
        verify(missionRepository).shiftOrdersDown(starSystemId, 5);
        verify(missionRepository).flush();
    }

    @Test
    void getNextOrder_Success() {
        UUID starSystemId = UUID.randomUUID();
        // A repository azt mondja, a max sorszám jelenleg 10
        when(missionRepository.findMaxOrderInSystem(starSystemId)).thenReturn(10);

        Integer nextOrder = missionService.getNextOrderForStarSystem(starSystemId);

        // A következőknek 11-nek kell lennie
        assertEquals(11, nextOrder);
    }
}
