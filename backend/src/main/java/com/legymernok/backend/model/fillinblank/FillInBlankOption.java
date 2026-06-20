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
@Table(name = "fill_in_blank_options")
public class FillInBlankOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blank_id", nullable = false)
    private FillInBlankBlank blank;

    @Column(nullable = false)
    private String optionText;

    @Column(nullable = false)
    private boolean correct;

    @Column(nullable = false)
    private Integer orderIndex;
}
