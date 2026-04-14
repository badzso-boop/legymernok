package com.legymernok.backend.repository.fillinblank;

import com.legymernok.backend.model.fillinblank.FillInBlankAnswerDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FillInBlankAnswerDetailRepository extends JpaRepository<FillInBlankAnswerDetail, UUID> {
}
