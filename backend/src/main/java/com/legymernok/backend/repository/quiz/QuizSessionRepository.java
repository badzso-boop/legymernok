package com.legymernok.backend.repository.quiz;

import com.legymernok.backend.model.mission.QuizSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuizSessionRepository extends JpaRepository<QuizSession, UUID> {
    Optional<QuizSession> findByMissionIdAndCadetId(UUID missionId, UUID cadetId);

    void deleteAllByMissionId(UUID missionId);
}
