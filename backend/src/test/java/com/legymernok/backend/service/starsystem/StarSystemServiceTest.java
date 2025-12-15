package com.legymernok.backend.service.starsystem;

import com.legymernok.backend.dto.starsystem.CreateStarSystemRequest;
import com.legymernok.backend.dto.starsystem.StarSystemResponse;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StarSystemServiceTest {
    @Mock
    private StarSystemRepository starSystemRepository;

    @InjectMocks
    private StarSystemService starSystemService;

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
}
