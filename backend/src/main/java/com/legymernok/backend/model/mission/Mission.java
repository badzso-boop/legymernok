package com.legymernok.backend.model.mission;

import com.legymernok.backend.model.cadet.Cadet;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "star_system_id", nullable = false)
    private StarSystem starSystem;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String descriptionMarkdown;

    @Column(nullable = true)
    private String templateRepositoryUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MissionType missionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(nullable = true)
    private Integer orderIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private Cadet owner;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private VerificationStatus verificationStatus = VerificationStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private MissionGroup group;

    @Column(nullable = true)
    private Integer groupOrder;

    @Column(columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
