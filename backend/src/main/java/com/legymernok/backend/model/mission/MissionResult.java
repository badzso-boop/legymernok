package com.legymernok.backend.model.mission;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.legymernok.backend.model.cadet.Cadet;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mission_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cadet_id", nullable = false)
    @JsonIgnore
    private Cadet cadet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    @JsonIgnore
    private Mission mission;

    @Column(nullable = false)
    private Double score;

    @Column(nullable = false)
    private Double maxScore;

    @Column(nullable = false)
    private Double percentage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String detailedAnswers; // JSON: { "q1": ["o1"], "q2": ["o3", "o4"] }

    @Column(nullable = false)
    private boolean isLate = false;

    @CreationTimestamp
    private Instant completedAt;

    @Column(nullable = false)
    private String submissionHash;
}
