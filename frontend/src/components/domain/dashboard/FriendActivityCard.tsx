import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import CircularProgress from "@mui/material/CircularProgress";
import Stack from "@mui/material/Stack";
import { useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import PeopleIcon from "@mui/icons-material/People";
import { GlowCard } from "../../shared/GlowCard";
import { NeonButton } from "../../shared/NeonButton";
import { socialApi } from "../../../api/client";
import type { ActivityFeedItemResponse } from "../../../types/dashboard";

const activityLabelKey: Record<ActivityFeedItemResponse["type"], string> = {
  GROUP_STEP: "homeDashboard.friendActivity.completedStep",
  FILL_IN_BLANK: "homeDashboard.friendActivity.completedFillInBlank",
  QUIZ: "homeDashboard.friendActivity.completedQuiz",
};

/**
 * Barátok aktivitása kártya — terv 5.2, 4. pont. A route a Profil oldalra
 * (`/profile`) egy best-guess útvonal — a Profil oldal egy párhuzamos lépés
 * (terv 6.) munkája, ami ezt stabilizálja.
 */
export const FriendActivityCard: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const { data, isLoading } = useQuery({
    queryKey: ["activityFeed"],
    queryFn: socialApi.getActivityFeed,
  });

  const items = data ?? [];

  return (
    <GlowCard>
      <Typography variant="overline" sx={{ color: "var(--color-text-secondary)" }}>
        {t("homeDashboard.friendActivity.label")}
      </Typography>

      {isLoading && (
        <Box sx={{ display: "flex", justifyContent: "center", py: 3 }}>
          <CircularProgress size={28} />
        </Box>
      )}

      {!isLoading && items.length === 0 && (
        <Box sx={{ textAlign: "center", py: 2 }}>
          <Typography sx={{ mb: 2, color: "var(--color-text-secondary)" }}>
            {t("homeDashboard.friendActivity.emptyState")}
          </Typography>
          <NeonButton
            startIcon={<PeopleIcon />}
            onClick={() => navigate("/profile")}
            data-cy="dashboard-friends-empty-cta"
          >
            {t("homeDashboard.friendActivity.emptyCta")}
          </NeonButton>
        </Box>
      )}

      {!isLoading && items.length > 0 && (
        <Stack spacing={1.5} sx={{ mt: 1 }}>
          {items.slice(0, 8).map((item, idx) => (
            <Box
              key={`${item.cadetId}-${item.occurredAt}-${idx}`}
              sx={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "baseline",
                gap: 1,
              }}
            >
              <Typography sx={{ fontSize: "0.9rem" }}>
                <strong>{item.cadetUsername}</strong>{" "}
                {t(activityLabelKey[item.type], { name: item.label })}
              </Typography>
            </Box>
          ))}
        </Stack>
      )}
    </GlowCard>
  );
};

export default FriendActivityCard;
