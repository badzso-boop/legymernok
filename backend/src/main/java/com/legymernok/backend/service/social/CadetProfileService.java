package com.legymernok.backend.service.social;

import com.legymernok.backend.dto.social.CadetProfileResponse;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.MissionStatus;
import com.legymernok.backend.repository.ConnectTables.CadetMissionRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.mission.MissionGroupProgressRepository;
import com.legymernok.backend.repository.social.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Publikus kadét-profil (bejelentkezve bárki más profilját megnézheti) — terv 7.3. */
@Service
@RequiredArgsConstructor
public class CadetProfileService {

    private final CadetRepository cadetRepository;
    private final CadetMissionRepository cadetMissionRepository;
    private final MissionGroupProgressRepository missionGroupProgressRepository;
    private final FollowRepository followRepository;

    @Transactional(readOnly = true)
    public CadetProfileResponse getProfile(UUID cadetId) {
        Cadet cadet = cadetRepository.findById(cadetId)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "id", cadetId));

        return CadetProfileResponse.builder()
                .id(cadet.getId())
                .username(cadet.getUsername())
                .fullName(cadet.getFullName())
                .avatarUrl(cadet.getAvatarUrl())
                .currentStreak(cadet.getCurrentStreak())
                .longestStreak(cadet.getLongestStreak())
                .totalCompletedMissions(cadetMissionRepository.countByCadetIdAndStatus(cadetId, MissionStatus.COMPLETED))
                .totalCompletedGroups(missionGroupProgressRepository.countByCadetIdAndCompletedTrue(cadetId))
                .followerCount(followRepository.countByFollowee_Id(cadetId))
                .followingCount(followRepository.countByFollower_Id(cadetId))
                .memberSince(cadet.getCreatedAt())
                .build();
    }
}
