import React, { useState } from "react";
import { useParams } from "react-router-dom";
import { Link as RouterLink } from "react-router-dom";
import {
  Box,
  Typography,
  Avatar,
  Grid,
  TextField,
  InputAdornment,
  List,
  ListItem,
  ListItemAvatar,
  ListItemText,
  CircularProgress,
  Alert,
} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import { useTranslation } from "react-i18next";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { authApi, socialApi } from "../../api/client";
import type { CadetSummaryResponse } from "../../types/social";
import { GlowCard } from "../../components/shared/GlowCard";
import { NeonButton } from "../../components/shared/NeonButton";
import { StreakFlame } from "../../components/shared/StreakFlame";
import { useDebouncedValue } from "../../hooks/useDebouncedValue";

const StatTile: React.FC<{ label: string; value: number }> = ({
  label,
  value,
}) => (
  <GlowCard sx={{ textAlign: "center", py: 2 }}>
    <Typography sx={{ fontWeight: 700, fontSize: 28, lineHeight: 1 }}>
      {value}
    </Typography>
    <Typography sx={{ color: "text.secondary", fontSize: 13, mt: 0.5 }}>
      {label}
    </Typography>
  </GlowCard>
);

const UserSearchResultRow: React.FC<{
  cadet: CadetSummaryResponse;
  isFollowing: boolean;
  onToggleFollow: (cadet: CadetSummaryResponse) => void;
}> = ({ cadet, isFollowing, onToggleFollow }) => {
  const { t } = useTranslation();
  return (
    <ListItem
      component={RouterLink}
      to={`/profile/${cadet.id}`}
      sx={{ borderRadius: 2, mb: 1, textDecoration: "none", color: "inherit" }}
      secondaryAction={
        <NeonButton
          size="small"
          variant={isFollowing ? "outlined" : "contained"}
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            onToggleFollow(cadet);
          }}
        >
          {isFollowing ? t("profile.unfollow") : t("profile.follow")}
        </NeonButton>
      }
    >
      <ListItemAvatar>
        <Avatar src={cadet.avatarUrl ?? undefined}>
          {cadet.username[0]?.toUpperCase()}
        </Avatar>
      </ListItemAvatar>
      <ListItemText primary={cadet.username} secondary={cadet.fullName} />
    </ListItem>
  );
};

const ProfilePage: React.FC = () => {
  const { t } = useTranslation();
  const { id: paramId } = useParams<{ id?: string }>();
  const queryClient = useQueryClient();
  const [searchTerm, setSearchTerm] = useState("");
  const debouncedSearch = useDebouncedValue(searchTerm, 300);

  const { data: me } = useQuery({
    queryKey: ["me"],
    queryFn: authApi.getMe,
  });

  const profileId = paramId ?? me?.id;
  const isOwnProfile = Boolean(me && profileId === me.id);

  const {
    data: profile,
    isLoading: isProfileLoading,
    isError: isProfileError,
  } = useQuery({
    queryKey: ["cadetProfile", profileId],
    queryFn: () => socialApi.getProfile(profileId as string),
    enabled: Boolean(profileId),
  });

  const followingQueryKey = ["following", me?.id];
  const { data: myFollowing } = useQuery({
    queryKey: followingQueryKey,
    queryFn: () => socialApi.getFollowing(me!.id),
    enabled: Boolean(me?.id) && !isOwnProfile,
  });

  const isFollowingProfile = Boolean(
    myFollowing?.some((c) => c.id === profileId),
  );

  const followMutation = useMutation({
    mutationFn: (targetId: string) => socialApi.follow(targetId),
    onMutate: async (targetId) => {
      await queryClient.cancelQueries({ queryKey: followingQueryKey });
      const previous =
        queryClient.getQueryData<CadetSummaryResponse[]>(followingQueryKey);
      if (profile && profile.id === targetId) {
        queryClient.setQueryData<CadetSummaryResponse[]>(
          followingQueryKey,
          (old) => [...(old ?? []), profile],
        );
      }
      return { previous };
    },
    onError: (_err, _targetId, context) => {
      if (context?.previous) {
        queryClient.setQueryData(followingQueryKey, context.previous);
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: followingQueryKey });
      queryClient.invalidateQueries({ queryKey: ["cadetProfile"] });
    },
  });

  const unfollowMutation = useMutation({
    mutationFn: (targetId: string) => socialApi.unfollow(targetId),
    onMutate: async (targetId) => {
      await queryClient.cancelQueries({ queryKey: followingQueryKey });
      const previous =
        queryClient.getQueryData<CadetSummaryResponse[]>(followingQueryKey);
      queryClient.setQueryData<CadetSummaryResponse[]>(
        followingQueryKey,
        (old) => (old ?? []).filter((c) => c.id !== targetId),
      );
      return { previous };
    },
    onError: (_err, _targetId, context) => {
      if (context?.previous) {
        queryClient.setQueryData(followingQueryKey, context.previous);
      }
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: followingQueryKey });
      queryClient.invalidateQueries({ queryKey: ["cadetProfile"] });
    },
  });

  const handleToggleFollow = (targetId: string, currentlyFollowing: boolean) => {
    if (currentlyFollowing) {
      unfollowMutation.mutate(targetId);
    } else {
      followMutation.mutate(targetId);
    }
  };

  const { data: searchResults, isFetching: isSearching } = useQuery({
    queryKey: ["cadetSearch", debouncedSearch],
    queryFn: () => socialApi.search(debouncedSearch),
    enabled: debouncedSearch.trim().length >= 2,
  });

  return (
    <Box sx={{ maxWidth: 720, mx: "auto", px: 2 }}>
      <Typography variant="h4" sx={{ fontWeight: "bold", mb: 3 }}>
        {isOwnProfile ? t("profile.myProfileTitle") : t("profile.title")}
      </Typography>

      <TextField
        fullWidth
        placeholder={t("profile.searchPlaceholder")}
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
        sx={{ mb: 3 }}
        slotProps={{
          input: {
            startAdornment: (
              <InputAdornment position="start">
                <SearchIcon />
              </InputAdornment>
            ),
          },
        }}
      />

      {debouncedSearch.trim().length >= 2 && (
        <GlowCard sx={{ mb: 4 }}>
          {isSearching ? (
            <Box sx={{ display: "flex", justifyContent: "center", py: 2 }}>
              <CircularProgress size={24} />
            </Box>
          ) : searchResults && searchResults.length > 0 ? (
            <List disablePadding>
              {searchResults.map((cadet) => (
                <UserSearchResultRow
                  key={cadet.id}
                  cadet={cadet}
                  isFollowing={Boolean(
                    myFollowing?.some((c) => c.id === cadet.id),
                  )}
                  onToggleFollow={(c) =>
                    handleToggleFollow(
                      c.id,
                      Boolean(myFollowing?.some((f) => f.id === c.id)),
                    )
                  }
                />
              ))}
            </List>
          ) : (
            <Typography sx={{ color: "text.secondary", textAlign: "center", py: 2 }}>
              {t("profile.noSearchResults")}
            </Typography>
          )}
        </GlowCard>
      )}

      {isProfileLoading && (
        <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
          <CircularProgress />
        </Box>
      )}

      {isProfileError && (
        <Alert severity="error">{t("profile.loadError")}</Alert>
      )}

      {profile && (
        <GlowCard active sx={{ mb: 3 }}>
          <Box sx={{ display: "flex", alignItems: "center", gap: 2, mb: 2 }}>
            <Avatar
              src={profile.avatarUrl ?? undefined}
              sx={{ width: 72, height: 72, fontSize: 28 }}
            >
              {profile.username[0]?.toUpperCase()}
            </Avatar>
            <Box sx={{ flex: 1 }}>
              <Typography sx={{ fontWeight: 700, fontSize: 22 }}>
                {profile.username}
              </Typography>
              {profile.fullName && (
                <Typography sx={{ color: "text.secondary" }}>
                  {profile.fullName}
                </Typography>
              )}
              <Typography sx={{ color: "text.secondary", fontSize: 13 }}>
                {t("profile.memberSince", {
                  date: new Date(profile.memberSince).toLocaleDateString(),
                })}
              </Typography>
            </Box>
            <StreakFlame currentStreak={profile.currentStreak} size="large" />
          </Box>

          {isOwnProfile ? (
            <Typography sx={{ color: "text.secondary", fontSize: 13 }}>
              {t("profile.ownProfileNote")}
            </Typography>
          ) : (
            <NeonButton
              fullWidth
              variant={isFollowingProfile ? "outlined" : "contained"}
              disabled={followMutation.isPending || unfollowMutation.isPending}
              onClick={() => handleToggleFollow(profile.id, isFollowingProfile)}
            >
              {isFollowingProfile ? t("profile.unfollow") : t("profile.follow")}
            </NeonButton>
          )}
        </GlowCard>
      )}

      {profile && (
        <Grid container spacing={2}>
          <Grid size={{ xs: 6, sm: 3 }}>
            <StatTile
              label={t("profile.stats.completedMissions")}
              value={profile.totalCompletedMissions}
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 3 }}>
            <StatTile
              label={t("profile.stats.completedGroups")}
              value={profile.totalCompletedGroups}
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 3 }}>
            <StatTile
              label={t("profile.stats.followers")}
              value={profile.followerCount}
            />
          </Grid>
          <Grid size={{ xs: 6, sm: 3 }}>
            <StatTile
              label={t("profile.stats.following")}
              value={profile.followingCount}
            />
          </Grid>
        </Grid>
      )}
    </Box>
  );
};

export default ProfilePage;
