package com.legymernok.backend.service.admin;

import com.legymernok.backend.dto.admin.AdminStatsResponse;
import com.legymernok.backend.dto.admin.PopularStarSystemSummary;
import com.legymernok.backend.dto.admin.RecentCadetSummary;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.starsystem.StarSystem;
import com.legymernok.backend.repository.ConnectTables.CadetMissionRepository;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.mission.MissionGroupProgressRepository;
import com.legymernok.backend.repository.mission.MissionRepository;
import com.legymernok.backend.repository.starsystem.StarSystemRepository;
import com.legymernok.backend.service.feedback.FeedbackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin dashboard áttekintő statisztikák — terv: az admin főoldal ne üres
 * "Work in Progress" legyen. Egyszerű aggregáló query-k a meglévő
 * táblákon, nem igényel új domain-modellt (a `follows`/streak mintához
 * hasonlóan, ld. [[project_legymernok]]).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminStatsService {

    private static final int RECENT_CADETS_LIMIT = 10;
    private static final int POPULAR_STAR_SYSTEMS_LIMIT = 5;

    private final CadetRepository cadetRepository;
    private final StarSystemRepository starSystemRepository;
    private final MissionRepository missionRepository;
    private final CadetMissionRepository cadetMissionRepository;
    private final MissionGroupProgressRepository missionGroupProgressRepository;
    private final FeedbackService feedbackService;

    public AdminStatsResponse getStats() {
        AdminStatsResponse.AdminStatsResponseBuilder builder = getDbStats();
        return builder.openFeedbackCount(getOpenFeedbackCount()).build();
    }

    /**
     * Nincs kulon @Transactional itt: a Spring Data repository metodusok
     * mindegyike sajat tranzakcioban fut (SimpleJpaRepository default), es
     * mivel ezt a metodust a getStats() ugyanazon a peldanyon (self-invocation)
     * hivja, egy ide tett @Transactional annotaciot a CGLIB proxy amugy sem
     * venne figyelembe.
     */
    private AdminStatsResponse.AdminStatsResponseBuilder getDbStats() {
        Instant oneWeekAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        List<RecentCadetSummary> recentCadets = cadetRepository.findTop10ByOrderByCreatedAtDesc().stream()
                .limit(RECENT_CADETS_LIMIT)
                .map(this::toRecentCadetSummary)
                .toList();

        return AdminStatsResponse.builder()
                .totalCadets(cadetRepository.count())
                .totalStarSystems(starSystemRepository.count())
                .totalMissions(missionRepository.count())
                .newCadetsThisWeek(cadetRepository.countByCreatedAtAfter(oneWeekAgo))
                .recentCadets(recentCadets)
                .mostStartedStarSystems(getMostStartedStarSystems());
    }

    /**
     * A GitHub-alapú feedback-szám egy külső, blokkoló hívás — ha a GitHub
     * elérhetetlen vagy nincs konfigurálva (GITHUB_TOKEN hiányzik), ez ne
     * dobja el a teljes dashboardot, csak ezt az egy számot hagyja ki.
     */
    private long getOpenFeedbackCount() {
        try {
            return feedbackService.listFeedback().stream()
                    .filter(issue -> "open".equalsIgnoreCase(issue.getState()))
                    .count();
        } catch (Exception e) {
            log.warn("Nem sikerult lekerni a nyitott feedback-ek szamat: {}", e.getMessage());
            return 0;
        }
    }

    private List<PopularStarSystemSummary> getMostStartedStarSystems() {
        Map<UUID, Long> counts = new HashMap<>();
        mergeCounts(counts, cadetMissionRepository.countDistinctCadetsByStarSystem());
        mergeCounts(counts, missionGroupProgressRepository.countDistinctCadetsByStarSystem());

        List<Map.Entry<UUID, Long>> top = counts.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(POPULAR_STAR_SYSTEMS_LIMIT)
                .toList();

        if (top.isEmpty()) {
            return List.of();
        }

        Map<UUID, StarSystem> starSystemsById = starSystemRepository
                .findAllById(top.stream().map(Map.Entry::getKey).toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(StarSystem::getId, s -> s));

        return top.stream()
                .filter(entry -> starSystemsById.containsKey(entry.getKey()))
                .map(entry -> PopularStarSystemSummary.builder()
                        .id(entry.getKey())
                        .name(starSystemsById.get(entry.getKey()).getName())
                        .startedByCount(entry.getValue())
                        .build())
                .sorted(Comparator.comparingLong(PopularStarSystemSummary::getStartedByCount).reversed())
                .toList();
    }

    /**
     * A standalone-misszió és a group-indítás számlálása KÜLÖN query — ha
     * ugyanaz a kadét mindkét úton indított már valamit ugyanabban a
     * rendszerben, a két számot ÖSSZEADJUK (nem deduplikáljuk kadét-szinten
     * a két forrás között) — ez egy tudatos, egyszerűsített MVP-metrika:
     * "hány (kadét, indítási esemény) pár volt ebben a rendszerben",
     * nem "hány egyedi kadét összesen".
     */
    private void mergeCounts(Map<UUID, Long> target, List<Object[]> rows) {
        for (Object[] row : rows) {
            UUID starSystemId = (UUID) row[0];
            Long count = (Long) row[1];
            target.merge(starSystemId, count, Long::sum);
        }
    }

    private RecentCadetSummary toRecentCadetSummary(Cadet cadet) {
        return RecentCadetSummary.builder()
                .id(cadet.getId())
                .username(cadet.getUsername())
                .fullName(cadet.getFullName())
                .createdAt(cadet.getCreatedAt())
                .build();
    }
}
