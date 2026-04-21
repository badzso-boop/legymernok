package com.legymernok.backend.repository.mission;

import com.legymernok.backend.model.mission.MissionGroupProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MissionGroupProgressRepository extends JpaRepository<MissionGroupProgress, UUID> {

    Optional<MissionGroupProgress> findByCadetIdAndGroupId(UUID cadetId, UUID groupId);

    void deleteAllByGroupId(UUID groupId);
}
