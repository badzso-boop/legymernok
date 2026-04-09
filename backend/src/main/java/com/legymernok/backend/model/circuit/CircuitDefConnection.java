package com.legymernok.backend.model.circuit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "circuit_def_connections", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"circuit_definition_id", "from_component_id", "from_pin_name", "to_component_id", "to_pin_name"})
})
public class CircuitDefConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "circuit_definition_id", nullable = false)
    private CircuitDefinition circuitDefinition;

    // Direction normalized: from_component_id < to_component_id (UUID string comparison)
    @Column(name = "from_component_id", nullable = false)
    private UUID fromComponentId;

    @Column(name = "from_pin_name", nullable = false)
    private String fromPinName;

    @Column(name = "to_component_id", nullable = false)
    private UUID toComponentId;

    @Column(name = "to_pin_name", nullable = false)
    private String toPinName;
}
