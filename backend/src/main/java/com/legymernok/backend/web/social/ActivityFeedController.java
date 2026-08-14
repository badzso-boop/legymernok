package com.legymernok.backend.web.social;

import com.legymernok.backend.dto.social.ActivityFeedItemResponse;
import com.legymernok.backend.service.social.ActivityFeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
public class ActivityFeedController {

    private final ActivityFeedService activityFeedService;

    @GetMapping("/activity-feed")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ActivityFeedItemResponse>> getActivityFeed() {
        return ResponseEntity.ok(activityFeedService.getActivityFeed());
    }
}
