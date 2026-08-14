package com.legymernok.backend.repository.ConnectTables;

import com.legymernok.backend.model.ConnectTable.CadetMission;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.MissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CadetMissionRepository extends JpaRepository<CadetMission, UUID> {
    void deleteAllByCadetId(UUID cadetId);
    void deleteAllByMissionId(UUID missionId);
    Optional<CadetMission> findByCadetIdAndMissionId(UUID cadetId, UUID missionId);
    List<CadetMission> findAllByCadetId(UUID cadetId);
    long countByCadetIdAndStatus(UUID cadetId, MissionStatus status);
}
