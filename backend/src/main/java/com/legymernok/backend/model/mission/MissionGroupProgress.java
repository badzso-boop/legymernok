package com.legymernok.backend.model.mission;

import com.legymernok.backend.model.cadet.Cadet;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mission_group_progress",
        uniqueConstraints = @UniqueConstraint(columnNames = {"cadet_id", "group_id"}))
public class MissionGroupProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cadet_id", nullable = false)
    private Cadet cadet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private MissionGroup group;

    private UUID nextMissionId;

    private boolean completed;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant startedAt;

    @UpdateTimestamp
    private Instant lastUpdatedAt;

    private Instant completedAt;
}
