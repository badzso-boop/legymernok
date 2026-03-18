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
@Table(name = "circuit_verification_checks")
public class CircuitVerificationCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "circuit_definition_id", nullable = false)
    private CircuitDefinition circuitDefinition;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false)
    private CheckType checkType;

    @Column(name = "label_from")
    private String labelFrom;

    @Column(name = "label_to")
    private String labelTo;

    @Column(name = "pin_from")
    private String pinFrom;

    @Column(name = "pin_to")
    private String pinTo;

    @Column(name = "expected_value")
    private String expectedValue;

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
