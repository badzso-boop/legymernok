package com.legymernok.backend.service.social;

import com.legymernok.backend.dto.social.CadetSummaryResponse;
import com.legymernok.backend.exception.ResourceConflictException;
import com.legymernok.backend.exception.ResourceNotFoundException;
import com.legymernok.backend.model.cadet.Cadet;
import com.legymernok.backend.model.social.Follow;
import com.legymernok.backend.repository.cadet.CadetRepository;
import com.legymernok.backend.repository.social.FollowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** Egyirányú követés (Duolingo-mintázat) — plans/frontend_redesign_2026.md 7.2. */
@Service
@RequiredArgsConstructor
@Slf4j
public class FollowService {

    private final FollowRepository followRepository;
    private final CadetRepository cadetRepository;

    @Transactional
    public void follow(UUID followeeId) {
        Cadet current = getCurrentAuthenticatedUser();
        if (current.getId().equals(followeeId)) {
            throw new ResourceConflictException("Follow", "followeeId", followeeId,
                    "Nem követheted saját magad.");
        }
        Cadet followee = cadetRepository.findById(followeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "id", followeeId));

        if (followRepository.existsByFollower_IdAndFollowee_Id(current.getId(), followeeId)) {
            return; // Idempotens: már követi, nincs teendő.
        }

        followRepository.save(Follow.builder().follower(current).followee(followee).build());
        log.info("'{}' elkezdte követni '{}'-t.", current.getUsername(), followee.getUsername());
    }

    @Transactional
    public void unfollow(UUID followeeId) {
        Cadet current = getCurrentAuthenticatedUser();
        followRepository.deleteByFollower_IdAndFollowee_Id(current.getId(), followeeId);
    }

    @Transactional(readOnly = true)
    public List<CadetSummaryResponse> getFollowing(UUID cadetId) {
        return followRepository.findAllByFollower_Id(cadetId).stream()
                .map(f -> mapToSummary(f.getFollowee()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CadetSummaryResponse> getFollowers(UUID cadetId) {
        return followRepository.findAllByFollowee_Id(cadetId).stream()
                .map(f -> mapToSummary(f.getFollower()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CadetSummaryResponse> searchByUsername(String query) {
        return cadetRepository.findAllByUsernameContainingIgnoreCase(query).stream()
                .map(this::mapToSummary)
                .collect(Collectors.toList());
    }

    private CadetSummaryResponse mapToSummary(Cadet cadet) {
        return CadetSummaryResponse.builder()
                .id(cadet.getId())
                .username(cadet.getUsername())
                .fullName(cadet.getFullName())
                .avatarUrl(cadet.getAvatarUrl())
                .build();
    }

    private Cadet getCurrentAuthenticatedUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return cadetRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Cadet", "username", username));
    }
}
