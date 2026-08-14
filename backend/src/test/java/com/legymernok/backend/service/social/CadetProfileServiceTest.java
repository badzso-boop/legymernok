package com.legymernok.backend.service.social;

import com.legymernok.backend.dto.social.CadetProfileResponse;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.MissionStatus;
import com.legymernok.backend.repository.ConnectTables.CadetMissionRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.mission.MissionGroupProgressRepository;
import com.legymernok.backend.repository.social.FollowRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CadetProfileServiceTest {

    @Mock private CadetRepository cadetRepository;
    @Mock private CadetMissionRepository cadetMissionRepository;
    @Mock private MissionGroupProgressRepository missionGroupProgressRepository;
    @Mock private FollowRepository followRepository;

    @InjectMocks
    private CadetProfileService cadetProfileService;

    @Test
    void getProfile_AggregatesAllCounts() {
        UUID id = UUID.randomUUID();
        Cadet cadet = Cadet.builder().id(id).username("kadét").currentStreak(3).longestStreak(7).build();
        when(cadetRepository.findById(id)).thenReturn(Optional.of(cadet));
        when(cadetMissionRepository.countByCadetIdAndStatus(id, MissionStatus.COMPLETED)).thenReturn(5L);
        when(missionGroupProgressRepository.countByCadetIdAndCompletedTrue(id)).thenReturn(2L);
        when(followRepository.countByFollowee_Id(id)).thenReturn(10L);
        when(followRepository.countByFollower_Id(id)).thenReturn(4L);

        CadetProfileResponse response = cadetProfileService.getProfile(id);

        assertEquals("kadét", response.getUsername());
        assertEquals(3, response.getCurrentStreak());
        assertEquals(5L, response.getTotalCompletedMissions());
        assertEquals(2L, response.getTotalCompletedGroups());
        assertEquals(10L, response.getFollowerCount());
        assertEquals(4L, response.getFollowingCount());
    }

    @Test
    void getProfile_UnknownCadet_ThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(cadetRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cadetProfileService.getProfile(id));
    }
}
