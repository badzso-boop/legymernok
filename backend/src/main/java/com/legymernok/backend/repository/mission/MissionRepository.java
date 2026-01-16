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
    List<Mission> findAllByStarSystemIdOrderByOrderInSystemAsc(UUID starSystemId);

    @Query("SELECT COALESCE(MAX(m.orderInSystem), 0) FROM Mission m WHERE m.starSystem.id = :starSystemId")
    Integer findMaxOrderInSystem(@Param("starSystemId") UUID starSystemId);

    boolean existsByStarSystemIdAndName(UUID starSystemId, String name);

    boolean existsByStarSystemIdAndOrderInSystem(UUID starSystemId, Integer orderInSystem);

    @Modifying
    @Query("UPDATE Mission m SET m.orderInSystem = m.orderInSystem + 1 WHERE m.starSystem.id = :starSystemId AND m.orderInSystem >= :startOrder")
    void shiftOrdersUp(@Param("starSystemId") UUID starSystemId, @Param("startOrder") Integer startOrder);


}