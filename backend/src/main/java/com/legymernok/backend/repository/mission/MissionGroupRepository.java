package com.legymernok.backend.repository.mission;

import com.legymernok.backend.model.mission.MissionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MissionGroupRepository extends JpaRepository<MissionGroup, UUID> {

    List<MissionGroup> findAllByStarSystemIdOrderByOrderIndexAsc(UUID starSystemId);

    boolean existsByStarSystemIdAndOrderIndex(UUID starSystemId, Integer orderIndex);

    @Query("SELECT COALESCE(MAX(g.orderIndex), -1) FROM MissionGroup g WHERE g.starSystem.id = :starSystemId")
    Integer findMaxOrderIndex(@Param("starSystemId") UUID starSystemId);

    @Modifying
    @Query("UPDATE MissionGroup g SET g.orderIndex = g.orderIndex + 1 " +
            "WHERE g.starSystem.id = :ssId AND g.orderIndex >= :from")
    void shiftOrdersUp(@Param("ssId") UUID ssId, @Param("from") Integer from);

    @Modifying
    @Query("UPDATE MissionGroup g SET g.orderIndex = g.orderIndex - 1 " +
            "WHERE g.starSystem.id = :ssId AND g.orderIndex > :deleted")
    void shiftOrdersDown(@Param("ssId") UUID ssId, @Param("deleted") Integer deleted);
}
