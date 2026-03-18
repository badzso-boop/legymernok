package com.legymernok.backend.model.circuit;

import com.legymernok.backend.model.mission.Mission;
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
@Table(name = "analog_circuit_definitions")
public class AnalogCircuitDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mission_id", nullable = false)
    private Mission mission;

    // Falstad/CircuitJS1 text format
    @Column(name = "falstad_text", columnDefinition = "TEXT", nullable = false)
    private String falstadText;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CircuitDefinitionStatus status = CircuitDefinitionStatus.IN_WORK;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
