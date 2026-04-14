package com.legymernok.backend.model.fillinblank;

import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.Mission;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "fill_in_blank_attempts")
public class FillInBlankAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cadet_id", nullable = false)
    private Cadet cadet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    private int score;

    private int maxScore;

    private int percentage;

    private boolean passed;

    @CreationTimestamp
    private Instant submittedAt;
}
