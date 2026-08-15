package com.legymernok.backend.repository.mission;

import com.legymernok.backend.model.mission.MissionGroupProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MissionGroupProgressRepository extends JpaRepository<MissionGroupProgress, UUID> {

    Optional<MissionGroupProgress> findByCadetIdAndGroupId(UUID cadetId, UUID groupId);

    void deleteAllByGroupId(UUID groupId);

    List<MissionGroupProgress> findAllByCadetId(UUID cadetId);

    long countByCadetIdAndCompletedTrue(UUID cadetId);

    /** Star System-enként hány egyedi kadét indított el legalább egy Mission Group-ot — admin dashboard. */
    @Query("SELECT p.group.starSystem.id, COUNT(DISTINCT p.cadet.id) FROM MissionGroupProgress p GROUP BY p.group.starSystem.id")
    List<Object[]> countDistinctCadetsByStarSystem();
}
