package com.legymernok.backend.model.mission;

import com.legymernok.backend.model.starsystem.StarSystem;
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
@Table(name = "missions")
public class Mission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY) // Sok küldetés tartozhat egy csillagrendszerhez
    @JoinColumn(name = "star_system_id", nullable = false)
    private StarSystem starSystem; // Foreign key a StarSystem-hez

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String descriptionMarkdown;

    @Column(nullable = false)
    private String templateRepositoryUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MissionType missionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(nullable = false)
    private Integer orderInSystem;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}