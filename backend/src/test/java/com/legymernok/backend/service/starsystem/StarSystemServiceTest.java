package com.legymernok.backend.service.starsystem;

import com.legymernok.backend.dto.mission.MissionResponse;
import com.legymernok.backend.dto.starsystem.CreateStarSystemRequest;
import com.legymernok.backend.dto.starsystem.StarSystemResponse;
import com.legymernok.backend.dto.starsystem.StarSystemWithMissionResponse;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import com.legymernok.backend.service.mission.MissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StarSystemServiceTest {
    @Mock
    private StarSystemRepository starSystemRepository;

    @Mock
    private MissionService missionService;

    @InjectMocks
    private StarSystemService starSystemService;

    private StarSystem testStarSystem;
    private UUID starSystemId;

    @BeforeEach
    void setUp() {
        starSystemId = UUID.randomUUID();
        testStarSystem = StarSystem.builder()
                .id(starSystemId)
                .name("Tatooine")
                .description("A desert planet.")
                .iconUrl("tatooine.png")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void createStarSystem_Success() {
        CreateStarSystemRequest request = new CreateStarSystemRequest();
        request.setName("Java Basics");
        request.setDescription("Intro");
        request.setIconUrl("icon.png");

        when(starSystemRepository.findByName("Java Basics")).thenReturn(Optional.empty());
        when(starSystemRepository.save(any(StarSystem.class))).thenAnswer(i -> {
            StarSystem s = i.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        StarSystemResponse response = starSystemService.createStarSystem(request);

        assertNotNull(response);
        assertEquals("Java Basics", response.getName());
        verify(starSystemRepository).save(any(StarSystem.class));
    }

    @Test
    void updateStarSystem_Success() {
        // Arrange
        CreateStarSystemRequest request = new CreateStarSystemRequest();
        request.setName("Tatooine II");
        request.setDescription("Still a desert planet.");
        request.setIconUrl("tatooine_v2.png");

        when(starSystemRepository.findById(starSystemId)).thenReturn(Optional.of(testStarSystem));
        when(starSystemRepository.findByName("Tatooine II")).thenReturn(Optional.empty());
        when(starSystemRepository.save(any(StarSystem.class))).thenReturn(testStarSystem);

        // Act
        StarSystemResponse response = starSystemService.updateStarSystem(starSystemId, request);

        // Assert
        assertNotNull(response);
        assertEquals("Tatooine II", response.getName());
        assertEquals("Still a desert planet.", response.getDescription());
        assertEquals("tatooine_v2.png", response.getIconUrl());
        verify(starSystemRepository, times(1)).save(testStarSystem);
    }

    @Test
    void updateStarSystem_NotFound_ThrowsException() {
        CreateStarSystemRequest request = new CreateStarSystemRequest();
        UUID nonExistentId = UUID.randomUUID();
        when(starSystemRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> starSystemService.updateStarSystem(nonExistentId, request));
    }

    @Test
    void deleteStarSystem_Success() {
        // Arrange
        when(starSystemRepository.existsById(starSystemId)).thenReturn(true);
        doNothing().when(starSystemRepository).deleteById(starSystemId);

        // Act
        starSystemService.deleteStarSystem(starSystemId);

        // Assert
        verify(starSystemRepository, times(1)).deleteById(starSystemId);
    }

    @Test
    void deleteStarSystem_NotFound_ThrowsException() {
        UUID nonExistentId = UUID.randomUUID();
        when(starSystemRepository.existsById(nonExistentId)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> starSystemService.deleteStarSystem(nonExistentId));
    }

    @Test
    void getStarSystemWithMissions_Success() {
        // Arrange
        MissionResponse mission1 = MissionResponse.builder().id(UUID.randomUUID()).name("Mission 1").build();
        List<MissionResponse> missions = List.of(mission1);

        when(starSystemRepository.findById(starSystemId)).thenReturn(Optional.of(testStarSystem));
        when(missionService.getMissionsByStarSystem(starSystemId)).thenReturn(missions);

        // Act
        StarSystemWithMissionResponse response = starSystemService.getStarSystemWithMissions(starSystemId);

        // Assert
        assertNotNull(response);
        assertEquals(testStarSystem.getName(), response.getName());
        assertEquals(1, response.getMissions().size());
        assertEquals("Mission 1", response.getMissions().get(0).getName());
        verify(missionService, times(1)).getMissionsByStarSystem(starSystemId);
    }

    @Test
    void getStarSystemWithMissions_NotFound_ThrowsException() {
        UUID nonExistentId = UUID.randomUUID();
        when(starSystemRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> starSystemService.getStarSystemWithMissions(nonExistentId));
        verify(missionService, never()).getMissionsByStarSystem(any());
    }

    @Test
    void createStarSystem_AlreadyExists_ThrowsException() {
        CreateStarSystemRequest request = new CreateStarSystemRequest();
        request.setName("Existing");

        when(starSystemRepository.findByName("Existing")).thenReturn(Optional.of(new StarSystem()));

        assertThrows(RuntimeException.class, () -> starSystemService.createStarSystem(request));
        verify(starSystemRepository, never()).save(any());
    }

    @Test
    void getAllStarSystems() {
        when(starSystemRepository.findAll()).thenReturn(List.of(
                StarSystem.builder().name("S1").build(),
                StarSystem.builder().name("S2").build()
        ));

        List<StarSystemResponse> responses = starSystemService.getAllStarSystems();

        assertEquals(2, responses.size());
    }

    @Test
    void updateStarSystem_NameConflict_ThrowsException() {
        // Arrange
        CreateStarSystemRequest request = new CreateStarSystemRequest();
        request.setName("ExistingName");

        // Egy másik létező rendszer szimulálása ugyanazzal a névvel
        StarSystem existingOther = StarSystem.builder().id(UUID.randomUUID()).name("ExistingName"
        ).build();

        when(starSystemRepository.findById(starSystemId)).thenReturn(Optional.of(testStarSystem));
        when(starSystemRepository.findByName("ExistingName"
        )).thenReturn(Optional.of(existingOther));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> starSystemService.updateStarSystem(starSystemId, request));
        verify(starSystemRepository, never()).save(any());
    }
}
