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
@Table(name = "cadet_circuit_components", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"cadet_circuit_save_id", "label"})
})
public class CadetCircuitComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cadet_circuit_save_id", nullable = false)
    private CadetCircuitSave cadetCircuitSave;

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
