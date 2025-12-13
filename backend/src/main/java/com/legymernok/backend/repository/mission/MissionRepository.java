package com.legymernok.backend.repository.mission;

import com.legymernok.backend.model.mission.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MissionRepository extends JpaRepository<Mission, UUID> {
    // Itt is definiálhatsz majd speciális lekérdezéseket
}