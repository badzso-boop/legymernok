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
@Table(name = "analog_verification_checks")
public class AnalogVerificationCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "analog_circuit_definition_id", nullable = false)
    private AnalogCircuitDefinition analogCircuitDefinition;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false)
    private AnalogCheckType checkType;

    @Column(name = "node_or_label", nullable = false)
    private String nodeOrLabel;

    @Column(name = "expected_value", nullable = false)
    private Double expectedValue;

    @Column(nullable = false)
    private Double tolerance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unit_of_measure_id")
    private UnitOfMeasure unitOfMeasure;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ValidationSeverity severity;

    @Column(name = "i18n_key", nullable = false)
    private String i18nKey;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;
}
