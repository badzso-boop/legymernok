package com.legymernok.backend.repository;

import com.legymernok.backend.model.Mission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MissionRepository extends JpaRepository<Mission, UUID> {
    // Itt is definiálhatsz majd speciális lekérdezéseket
}