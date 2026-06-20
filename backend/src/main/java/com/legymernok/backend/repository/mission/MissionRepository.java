package com.legymernok.backend.repository.mission;

import com.legymernok.backend.model.mission.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MissionRepository extends JpaRepository<Mission, UUID> {

    List<Mission> findAllByStarSystemIdAndGroupIsNullOrderByOrderIndexAsc(UUID starSystemId);

    List<Mission> findAllByGroupIdOrderByGroupOrderAsc(UUID groupId);

    @Query("SELECT COALESCE(MAX(m.orderIndex), -1) FROM Mission m " +
            "WHERE m.starSystem.id = :starSystemId AND m.group IS NULL")
    Integer findMaxOrderIndex(@Param("starSystemId") UUID starSystemId);

    @Query("SELECT COALESCE(MAX(m.groupOrder), -1) + 1 FROM Mission m WHERE m.group.id = :groupId")
    Integer findNextGroupOrder(@Param("groupId") UUID groupId);

    boolean existsByStarSystemIdAndName(UUID starSystemId, String name);

    boolean existsByStarSystemIdAndOrderIndex(UUID starSystemId, Integer orderIndex);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Mission m SET m.orderIndex = m.orderIndex + 1 " +
            "WHERE m.starSystem.id = :starSystemId AND m.orderIndex >= :startOrder AND m.group IS NULL")
    void shiftOrdersUp(@Param("starSystemId") UUID starSystemId, @Param("startOrder") Integer startOrder);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Mission m SET m.orderIndex = m.orderIndex - 1 " +
            "WHERE m.starSystem.id = :starSystemId AND m.orderIndex > :deletedOrder AND m.group IS NULL")
    void shiftOrdersDown(@Param("starSystemId") UUID starSystemId, @Param("deletedOrder") Integer deletedOrder);

    List<Mission> findAllByOwnerId(UUID ownerId);

    List<Mission> findAllByStarSystemId(UUID starSystemId);
}
