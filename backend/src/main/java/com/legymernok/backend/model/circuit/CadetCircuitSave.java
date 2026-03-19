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

    /**
     * True ha ez a save egy visszavont (PUBLISHED → IN_WORK) definícióhoz tartozik.
     * Stale save-en a kadét nem dolgozhat tovább, de az adat megmarad archívként.
     */
    @Builder.Default
    @Column(name = "stale", nullable = false)
    private boolean stale = false;

    /**
     * Cache kulcs az utolsó fordításhoz: SHA-256(giteaRepoUrl + latestCommitHash + fqbn).
     * Ha a következő fordításkor ugyanez a kulcs számítódik ki, a compiledHexBase64
     * közvetlenül visszaadható — nem kell újra fordítani.
     */
    @Column(name = "compile_cache_key", length = 64)
    private String compileCacheKey;

    /**
     * Az utolsó sikeres fordítás eredménye Base64 kódolva.
     * Cache hit esetén ebből kerül visszaadásra a hex, arduino-cli futtatás nélkül.
     * Null ha még nem volt sikeres fordítás, vagy a cache érvénytelen.
     */
    @Column(name = "compiled_hex_base64", columnDefinition = "TEXT")
    private String compiledHexBase64;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
