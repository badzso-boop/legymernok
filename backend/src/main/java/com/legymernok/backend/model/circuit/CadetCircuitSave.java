package com.legymernok.backend.model.circuit;

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
@Table(name = "cadet_circuit_saves", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"cadet_id", "circuit_definition_id"})
})
public class CadetCircuitSave {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cadet_id", nullable = false)
    private Cadet cadet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "circuit_definition_id", nullable = false)
    private CircuitDefinition circuitDefinition;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "simulation_status", nullable = false)
    private SimulationStatus simulationStatus = SimulationStatus.NEVER_RUN;

    @Column(name = "simulation_started_at")
    private Instant simulationStartedAt;

    @Column(name = "compilation_time_ms")
    private Long compilationTimeMs;

    @Column(name = "total_time_spent_ms")
    private Long totalTimeSpentMs;

    /** Gitea repo URL where the cadet's Arduino sketch is stored. */
    @Column(name = "gitea_repo_url")
    private String giteaRepoUrl;

    /** Last compile error message (null if last compile was successful or never run). */
    @Column(name = "last_compile_error", columnDefinition = "TEXT")
    private String lastCompileError;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
