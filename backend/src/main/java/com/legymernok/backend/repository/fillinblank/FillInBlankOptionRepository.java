package com.legymernok.backend.repository.fillinblank;

import com.legymernok.backend.model.fillinblank.FillInBlankOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FillInBlankOptionRepository extends JpaRepository<FillInBlankOption, UUID> {

    List<FillInBlankOption> findAllByBlankIdOrderByOrderIndexAsc(UUID blankId);

    @Query("SELECT o FROM FillInBlankOption o WHERE o.blank.definition.id = :definitionId " +
            "ORDER BY o.blank.orderIndex, o.orderIndex")
    List<FillInBlankOption> findAllByDefinitionId(@Param("definitionId") UUID definitionId);

    @Modifying
    @Query("DELETE FROM FillInBlankOption o WHERE o.blank.definition.mission.id = :missionId")
    void deleteAllByMissionId(@Param("missionId") UUID missionId);
}
