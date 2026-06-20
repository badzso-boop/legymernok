package com.legymernok.backend.repository.fillinblank;

import com.legymernok.backend.model.fillinblank.FillInBlankDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FillInBlankDefinitionRepository extends JpaRepository<FillInBlankDefinition, UUID> {

    Optional<FillInBlankDefinition> findByMissionId(UUID missionId);

    boolean existsByMissionId(UUID missionId);

    void deleteByMissionId(UUID missionId);
}
