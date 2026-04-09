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
@Table(name = "cadet_analog_saves", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"cadet_id", "analog_circuit_definition_id"})
})
public class CadetAnalogSave {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cadet_id", nullable = false)
    private Cadet cadet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analog_circuit_definition_id", nullable = false)
    private AnalogCircuitDefinition analogCircuitDefinition;

    // Cadet's modified Falstad text
    @Column(name = "falstad_text", columnDefinition = "TEXT", nullable = false)
    private String falstadText;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "simulation_status", nullable = false)
    private SimulationStatus simulationStatus = SimulationStatus.NEVER_RUN;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
