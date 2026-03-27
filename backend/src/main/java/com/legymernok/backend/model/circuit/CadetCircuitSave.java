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

    /**
     * True ha ez a save egy visszavont (PUBLISHED → IN_WORK) definícióhoz tartozik.
     * Stale save-en a kadét nem dolgozhat tovább, de az adat megmarad archívként.
     */
    @Builder.Default
    @Column(name = "stale", nullable = false)
    private boolean stale = false;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
