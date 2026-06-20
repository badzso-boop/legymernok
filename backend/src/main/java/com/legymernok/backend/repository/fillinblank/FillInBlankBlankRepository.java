package com.legymernok.backend.repository.fillinblank;

import com.legymernok.backend.model.fillinblank.FillInBlankBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FillInBlankBlankRepository extends JpaRepository<FillInBlankBlank, UUID> {

    List<FillInBlankBlank> findAllByDefinitionIdOrderByOrderIndexAsc(UUID definitionId);

    @Modifying
    @Query("DELETE FROM FillInBlankBlank b WHERE b.definition.mission.id = :missionId")
    void deleteAllByMissionId(@Param("missionId") UUID missionId);
}
