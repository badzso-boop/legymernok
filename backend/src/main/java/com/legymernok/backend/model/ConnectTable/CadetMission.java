package com.legymernok.backend.model.ConnectTable;

import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.mission.Mission;
import com.legymernok.backend.model.mission.MissionStatus;
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
@Table(name = "cadet_missions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"cadet_id", "mission_id"})
})
public class CadetMission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cadet_id", nullable = false)
    private Cadet cadet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MissionStatus status;

    private String repositoryUrl; // A diák saját repója

    private Instant startedAt;
    private Instant completedAt;

    @CreationTimestamp
    private Instant lastUpdatedAt;
}
