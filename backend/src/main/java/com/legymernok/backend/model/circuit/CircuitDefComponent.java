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
@Table(name = "circuit_def_components", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"circuit_definition_id", "label"})
})
public class CircuitDefComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "circuit_definition_id", nullable = false)
    private CircuitDefinition circuitDefinition;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false)
    private ComponentType componentType;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private Integer posX;

    @Column(nullable = false)
    private Integer posY;
}
