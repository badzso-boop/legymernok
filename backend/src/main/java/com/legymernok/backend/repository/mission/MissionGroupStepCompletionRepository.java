package com.legymernok.backend.repository.mission;

import com.legymernok.backend.model.mission.MissionGroupStepCompletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MissionGroupStepCompletionRepository extends JpaRepository<MissionGroupStepCompletion, UUID> {

    List<MissionGroupStepCompletion> findAllByProgressId(UUID progressId);

    boolean existsByProgressIdAndMissionId(UUID progressId, UUID missionId);
}
