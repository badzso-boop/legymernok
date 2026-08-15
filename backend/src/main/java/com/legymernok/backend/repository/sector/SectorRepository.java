package com.legymernok.backend.repository.sector;

import com.legymernok.backend.model.sector.Sector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SectorRepository extends JpaRepository<Sector, UUID> {
    Optional<Sector> findByName(String name);

    List<Sector> findAllByOrderByOrderIndexAsc();

    @Query("SELECT COALESCE(MAX(s.orderIndex), -1) FROM Sector s")
    int findMaxOrderIndex();
}
