package com.legymernok.backend.repository.mission;

import com.legymernok.backend.model.mission.MissionResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MissionResultRepository extends JpaRepository<MissionResult, UUID> {

    List<MissionResult> findAllByCadetId(UUID cadetId);

    Optional<MissionResult> findByMissionIdAndCadetIdAndSubmissionHash(UUID missionId, UUID cadetId, String submissionHash);

    List<MissionResult> findTop20ByCadet_IdInOrderByCompletedAtDesc(List<UUID> cadetIds);

    void deleteAllByMissionId(UUID missionId);
}
