package com.legymernok.backend.dto.social;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CadetProfileResponse {
    private UUID id;
    private String username;
    private String fullName;
    private String avatarUrl;
    private int currentStreak;
    private int longestStreak;
    private long totalCompletedMissions;
    private long totalCompletedGroups;
    private long followerCount;
    private long followingCount;
    private Instant memberSince;
}
