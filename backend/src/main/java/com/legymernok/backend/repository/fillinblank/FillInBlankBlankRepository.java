package com.legymernok.backend.repository.fillinblank;

import com.legymernok.backend.model.fillinblank.FillInBlankBlank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FillInBlankBlankRepository extends JpaRepository<FillInBlankBlank, UUID> {

    List<FillInBlankBlank> findAllByDefinitionIdOrderByOrderIndexAsc(UUID definitionId);
}
