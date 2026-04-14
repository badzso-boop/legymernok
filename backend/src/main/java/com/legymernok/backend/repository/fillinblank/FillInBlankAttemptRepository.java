package com.legymernok.backend.repository.fillinblank;

import com.legymernok.backend.model.fillinblank.FillInBlankAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FillInBlankAttemptRepository extends JpaRepository<FillInBlankAttempt, UUID> {

    Optional<FillInBlankAttempt> findTopByCadetIdAndMissionIdOrderBySubmittedAtDesc(UUID cadetId, UUID missionId);
}
