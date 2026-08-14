package com.legymernok.backend.service.streak;

import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.repository.cadet.CadetRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    @Mock
    private CadetRepository cadetRepository;

    @InjectMocks
    private StreakService streakService;

    private Cadet cadetWithLastActivity(LocalDate lastActivity) {
        return Cadet.builder()
                .id(UUID.randomUUID())
                .currentStreak(3)
                .longestStreak(5)
                .lastActivityDate(lastActivity)
                .build();
    }

    @Test
    void recordActivity_SameDay_NoChange() {
        Cadet cadet = cadetWithLastActivity(LocalDate.now());
        when(cadetRepository.findById(cadet.getId())).thenReturn(Optional.of(cadet));

        streakService.recordActivity(cadet.getId());

        assertEquals(3, cadet.getCurrentStreak());
        verify(cadetRepository, never()).save(any());
    }

    @Test
    void recordActivity_ConsecutiveDay_IncrementsStreak() {
        Cadet cadet = cadetWithLastActivity(LocalDate.now().minusDays(1));
        when(cadetRepository.findById(cadet.getId())).thenReturn(Optional.of(cadet));

        streakService.recordActivity(cadet.getId());

        assertEquals(4, cadet.getCurrentStreak());
        assertEquals(5, cadet.getLongestStreak());
        assertEquals(LocalDate.now(), cadet.getLastActivityDate());
        verify(cadetRepository).save(cadet);
    }

    @Test
    void recordActivity_GapInActivity_ResetsToOne() {
        Cadet cadet = cadetWithLastActivity(LocalDate.now().minusDays(5));
        when(cadetRepository.findById(cadet.getId())).thenReturn(Optional.of(cadet));

        streakService.recordActivity(cadet.getId());

        assertEquals(1, cadet.getCurrentStreak());
        assertEquals(5, cadet.getLongestStreak()); // A rekord nem csökken
    }

    @Test
    void recordActivity_NewLongestStreak_UpdatesLongest() {
        Cadet cadet = Cadet.builder()
                .id(UUID.randomUUID())
                .currentStreak(5)
                .longestStreak(5)
                .lastActivityDate(LocalDate.now().minusDays(1))
                .build();
        when(cadetRepository.findById(cadet.getId())).thenReturn(Optional.of(cadet));

        streakService.recordActivity(cadet.getId());

        assertEquals(6, cadet.getCurrentStreak());
        assertEquals(6, cadet.getLongestStreak());
    }

    @Test
    void recordActivity_UnknownCadet_ThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(cadetRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> streakService.recordActivity(id));
    }
}
