import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import PeopleIcon from "@mui/icons-material/People";
import PublicIcon from "@mui/icons-material/Public";
import RocketLaunchIcon from "@mui/icons-material/RocketLaunch";
import PersonAddIcon from "@mui/icons-material/PersonAdd";
import FeedbackIcon from "@mui/icons-material/Feedback";
import { useTranslation } from "react-i18next";
import { GlowCard } from "../../shared/GlowCard";
import type { AdminStatsResponse } from "../../../types/admin";

interface AdminStatTilesProps {
  stats: AdminStatsResponse;
}

const Tile: React.FC<{ icon: React.ReactNode; label: string; value: number }> = ({
  icon,
  label,
  value,
}) => (
  <GlowCard sx={{ display: "flex", alignItems: "center", gap: 1.5, flex: "1 1 160px" }}>
    <Box sx={{ color: "var(--color-accent-primary)", display: "flex" }}>{icon}</Box>
    <Box>
      <Typography variant="h5" sx={{ fontWeight: 700, lineHeight: 1.1 }}>
        {value}
      </Typography>
      <Typography variant="caption" sx={{ color: "var(--color-text-secondary)" }}>
        {label}
      </Typography>
    </Box>
  </GlowCard>
);

export const AdminStatTiles: React.FC<AdminStatTilesProps> = ({ stats }) => {
  const { t } = useTranslation();

  return (
    <Box sx={{ display: "flex", flexWrap: "wrap", gap: 2 }}>
      <Tile
        icon={<PeopleIcon />}
        label={t("adminOverview.stats.totalCadets")}
        value={stats.totalCadets}
      />
      <Tile
        icon={<PublicIcon />}
        label={t("adminOverview.stats.totalStarSystems")}
        value={stats.totalStarSystems}
      />
      <Tile
        icon={<RocketLaunchIcon />}
        label={t("adminOverview.stats.totalMissions")}
        value={stats.totalMissions}
      />
      <Tile
        icon={<PersonAddIcon />}
        label={t("adminOverview.stats.newCadetsThisWeek")}
        value={stats.newCadetsThisWeek}
      />
      <Tile
        icon={<FeedbackIcon />}
        label={t("adminOverview.stats.openFeedbackCount")}
        value={stats.openFeedbackCount}
      />
    </Box>
  );
};

export default AdminStatTiles;
