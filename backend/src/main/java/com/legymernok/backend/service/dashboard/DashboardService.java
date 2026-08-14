package com.legymernok.backend.service.dashboard;

import com.legymernok.backend.dto.dashboard.ContinueResponse;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.ConnectTable.CadetMission;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.MissionGroupProgress;
import com.legymernok.backend.repository.ConnectTables.CadetMissionRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.mission.MissionGroupProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;

/** "Folytasd onnan, ahol abbahagytad" dashboard-kártya adatforrása — terv 5.2. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final CadetRepository cadetRepository;
    private final CadetMissionRepository cadetMissionRepository;
    private final MissionGroupProgressRepository missionGroupProgressRepository;

    @Transactional(readOnly = true)
    public ContinueResponse getContinue() {
        Cadet cadet = getCurrentAuthenticatedUser();

        Optional<CadetMission> lastMission = cadetMissionRepository.findAllByCadetId(cadet.getId()).stream()
                .max(Comparator.comparing(CadetMission::getLastUpdatedAt));

        Optional<MissionGroupProgress> lastGroup = missionGroupProgressRepository.findAllByCadetId(cadet.getId())
                .stream()
                .filter(p -> !p.isCompleted())
                .max(Comparator.comparing(MissionGroupProgress::getLastUpdatedAt));

        Instant missionTime = lastMission.map(CadetMission::getLastUpdatedAt).orElse(Instant.EPOCH);
        Instant groupTime = lastGroup.map(MissionGroupProgress::getLastUpdatedAt).orElse(Instant.EPOCH);

        if (lastMission.isEmpty() && lastGroup.isEmpty()) {
            throw new ResourceNotFoundException("Continue", "cadetId", cadet.getId());
        }

        if (groupTime.isAfter(missionTime)) {
            MissionGroupProgress progress = lastGroup.get();
            return ContinueResponse.builder()
                    .type("GROUP")
                    .groupId(progress.getGroup().getId())
                    .starSystemId(progress.getGroup().getStarSystem().getId())
                    .name(progress.getGroup().getName())
                    .build();
        }

        CadetMission cadetMission = lastMission.get();
        return ContinueResponse.builder()
                .type("MISSION")
                .missionId(cadetMission.getMission().getId())
                .starSystemId(cadetMission.getMission().getStarSystem().getId())
                .name(cadetMission.getMission().getName())
                .build();
    }

    private Cadet getCurrentAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return cadetRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "username", username));
    }
}
