package com.legymernok.backend.web.social;

import com.legymernok.backend.dto.social.CadetProfileResponse;
import com.legymernok.backend.dto.social.CadetSummaryResponse;
import com.legymernok.backend.service.social.CadetProfileService;
import com.legymernok.backend.service.social.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cadets")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class FollowController {

    private final FollowService followService;
    private final CadetProfileService cadetProfileService;

    @GetMapping("/{id}/profile")
    public ResponseEntity<CadetProfileResponse> getProfile(@PathVariable("id") UUID cadetId) {
        return ResponseEntity.ok(cadetProfileService.getProfile(cadetId));
    }

    @PostMapping("/{id}/follow")
    public ResponseEntity<Void> follow(@PathVariable("id") UUID followeeId) {
        followService.follow(followeeId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/follow")
    public ResponseEntity<Void> unfollow(@PathVariable("id") UUID followeeId) {
        followService.unfollow(followeeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/following")
    public ResponseEntity<List<CadetSummaryResponse>> getFollowing(@PathVariable("id") UUID cadetId) {
        return ResponseEntity.ok(followService.getFollowing(cadetId));
    }

    @GetMapping("/{id}/followers")
    public ResponseEntity<List<CadetSummaryResponse>> getFollowers(@PathVariable("id") UUID cadetId) {
        return ResponseEntity.ok(followService.getFollowers(cadetId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<CadetSummaryResponse>> search(@RequestParam String username) {
        return ResponseEntity.ok(followService.searchByUsername(username));
    }
}
