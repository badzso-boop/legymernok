import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { useTranslation } from "react-i18next";
import { GlowCard } from "../../shared/GlowCard";
import { StreakFlame } from "../../shared/StreakFlame";

export interface StreakBarProps {
  currentStreak: number;
  longestStreak: number;
}

/** A dashboard legfelső, legszembetűnőbb eleme — ld. terv 5.2, 1. pont. */
export const StreakBar: React.FC<StreakBarProps> = ({
  currentStreak,
  longestStreak,
}) => {
  const { t } = useTranslation();

  return (
    <GlowCard
      active={currentStreak > 0}
      sx={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        flexWrap: "wrap",
        gap: 2,
      }}
    >
      <Box>
        <Typography variant="overline" sx={{ color: "var(--color-text-secondary)" }}>
          {t("homeDashboard.streak.label")}
        </Typography>
        <Typography sx={{ color: "var(--color-text-secondary)", fontSize: "0.85rem" }}>
          {currentStreak > 0
            ? t("homeDashboard.streak.activeSubtitle")
            : t("homeDashboard.streak.inactiveSubtitle")}
        </Typography>
      </Box>
      <Box sx={{ textAlign: "right" }}>
        <StreakFlame currentStreak={currentStreak} size="large" />
        {longestStreak > 0 && (
          <Typography
            sx={{ color: "var(--color-text-secondary)", fontSize: "0.75rem", mt: 0.5 }}
          >
            {t("homeDashboard.streak.longest", { count: longestStreak })}
          </Typography>
        )}
      </Box>
    </GlowCard>
  );
};

export default StreakBar;
