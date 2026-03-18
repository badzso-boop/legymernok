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
@Table(name = "cadet_circuit_component_properties", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"cadet_component_id", "property_key"})
})
public class CadetCircuitComponentProperty {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cadet_component_id", nullable = false)
    private CadetCircuitComponent cadetComponent;

    @Column(name = "property_key", nullable = false)
    private String propertyKey;

    @Column(name = "property_value", nullable = false)
    private String propertyValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_of_measure_id")
    private UnitOfMeasure unitOfMeasure;
}
