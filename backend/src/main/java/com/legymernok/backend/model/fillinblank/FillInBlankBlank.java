package com.legymernok.backend.model.fillinblank;

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
@Table(name = "fill_in_blank_blanks")
public class FillInBlankBlank {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "definition_id", nullable = false)
    private FillInBlankDefinition definition;

    @Column(nullable = false)
    private String blanksKey;

    @Column(nullable = false)
    private Integer orderIndex;
}
