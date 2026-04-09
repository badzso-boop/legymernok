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
@Table(name = "component_pin_definitions", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"component_type", "board_type", "pin_name"})
})
public class ComponentPinDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false)
    private ComponentType componentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "board_type")
    private BoardType boardType;

    @Column(name = "pin_name", nullable = false)
    private String pinName;

    @Column(name = "pin_index", nullable = false)
    private Integer pinIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "pin_type", nullable = false)
    private PinType pinType;

    @Builder.Default
    @Column(name = "allow_multiple_connections", nullable = false)
    private boolean allowMultipleConnections = false;

    @Column(name = "pos_x_offset")
    private Integer posXOffset;

    @Column(name = "pos_y_offset")
    private Integer posYOffset;
}
