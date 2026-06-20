package com.legymernok.backend.repository.fillinblank;

import com.legymernok.backend.model.fillinblank.FillInBlankAnswerDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FillInBlankAnswerDetailRepository extends JpaRepository<FillInBlankAnswerDetail, UUID> {

    @Modifying
    @Query("DELETE FROM FillInBlankAnswerDetail d WHERE d.attempt.mission.id = :missionId")
    void deleteAllByMissionId(@Param("missionId") UUID missionId);
}
