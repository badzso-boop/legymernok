package com.legymernok.backend.repository.ConnectTables;

import com.legymernok.backend.model.ConnectTable.CadetMission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CadetMissionRepository extends JpaRepository<CadetMission, UUID> {
    void deleteAllByCadetId(UUID cadetId); // Ez kell a törléshez!
}
