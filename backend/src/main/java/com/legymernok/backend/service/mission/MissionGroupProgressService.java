package com.legymernok.backend.service.mission;

import com.legymernok.backend.dto.group.GroupProgressResponse;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionGroup;
import com.legymernok.backend.model.mission.MissionGroupProgress;
import com.legymernok.backend.model.mission.MissionGroupStepCompletion;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.mission.MissionGroupProgressRepository;
import com.legymernok.backend.repository.mission.MissionGroupRepository;
import com.legymernok.backend.repository.mission.MissionGroupStepCompletionRepository;
import com.legymernok.backend.repository.mission.MissionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionGroupProgressService {

    private final MissionGroupProgressRepository progressRepository;
    private final MissionGroupStepCompletionRepository stepCompletionRepository;
    private final MissionGroupRepository groupRepository;
    private final MissionRepository missionRepository;
    private final CadetRepository cadetRepository;

    @Transactional(readOnly = true)
    public GroupProgressResponse getProgress(UUID groupId) {
        Cadet cadet = getCurrentAuthenticatedUser();
        MissionGroupProgress progress = progressRepository.findByCadetIdAndGroupId(cadet.getId(), groupId)
                .orElseThrow(() -> new ResourceNotFoundException("MissionGroupProgress", "groupId", groupId));

        List<Mission> groupMissions = missionRepository.findAllByGroupIdOrderByGroupOrderAsc(groupId);
        int completedCount = stepCompletionRepository.findAllByProgressId(progress.getId()).size();

        return mapToResponse(progress, completedCount, groupMissions.size());
    }

    @Transactional
    public GroupProgressResponse startProgress(UUID groupId) {
        Cadet cadet = getCurrentAuthenticatedUser();
        MissionGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("MissionGroup", "id", groupId));

        // Ha már létezik → 409
        if (progressRepository.findByCadetIdAndGroupId(cadet.getId(), groupId).isPresent()) {
            throw new ResourceConflictException("MissionGroupProgress", "groupId",
                    "A progress már létezik ehhez a csoporthoz.");
        }

        List<Mission> groupMissions = missionRepository.findAllByGroupIdOrderByGroupOrderAsc(groupId);
        UUID firstMissionId = groupMissions.isEmpty() ? null : groupMissions.get(0).getId();
        boolean completed = groupMissions.isEmpty();

        MissionGroupProgress progress = MissionGroupProgress.builder()
                .cadet(cadet)
                .group(group)
                .nextMissionId(firstMissionId)
                .completed(completed)
                .completedAt(completed ? Instant.now() : null)
                .build();

        progress = progressRepository.save(progress);
        return mapToResponse(progress, 0, groupMissions.size());
    }

    @Transactional
    public GroupProgressResponse completeStep(UUID groupId) {
        Cadet cadet = getCurrentAuthenticatedUser();
        MissionGroupProgress progress = progressRepository.findByCadetIdAndGroupId(cadet.getId(), groupId)
                .orElseThrow(() -> new ResourceNotFoundException("MissionGroupProgress", "groupId", groupId));

        List<Mission> groupMissions = missionRepository.findAllByGroupIdOrderByGroupOrderAsc(groupId);

        // Az aktuális lépés (nextMissionId) teljesítése
        UUID currentMissionId = progress.getNextMissionId();
        if (currentMissionId != null) {
            Mission currentMission = missionRepository.findById(currentMissionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Mission", "id", currentMissionId));
            try {
                MissionGroupStepCompletion step = MissionGroupStepCompletion.builder()
                        .progress(progress)
                        .mission(currentMission)
                        .build();
                stepCompletionRepository.saveAndFlush(step);
            } catch (DataIntegrityViolationException e) {
                // Idempotens: ha már létezik, nem duplikálódik
                log.debug("Step completion already exists for mission {}, ignoring.", currentMissionId);
            }
        }

        // Elvégzett lépések
        Set<UUID> completedIds = stepCompletionRepository.findAllByProgressId(progress.getId())
                .stream()
                .map(s -> s.getMission().getId())
                .collect(Collectors.toSet());

        // Következő misszió meghatározása
        UUID nextMissionId = groupMissions.stream()
                .filter(m -> !completedIds.contains(m.getId()))
                .map(Mission::getId)
                .findFirst()
                .orElse(null);

        boolean completed = nextMissionId == null;
        progress.setNextMissionId(nextMissionId);
        progress.setCompleted(completed);
        if (completed && progress.getCompletedAt() == null) {
            progress.setCompletedAt(Instant.now());
        }
        progress = progressRepository.save(progress);

        return mapToResponse(progress, completedIds.size(), groupMissions.size());
    }

    private GroupProgressResponse mapToResponse(MissionGroupProgress progress, int completedCount, int totalCount) {
        return GroupProgressResponse.builder()
                .groupId(progress.getGroup().getId())
                .completed(progress.isCompleted())
                .nextMissionId(progress.getNextMissionId())
                .completedCount(completedCount)
                .totalCount(totalCount)
                .startedAt(progress.getStartedAt())
                .lastUpdatedAt(progress.getLastUpdatedAt())
                .completedAt(progress.getCompletedAt())
                .build();
    }

    private Cadet getCurrentAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return cadetRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
    }
}
