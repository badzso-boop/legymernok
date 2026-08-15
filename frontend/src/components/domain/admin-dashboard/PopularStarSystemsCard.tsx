import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import LinearProgress from "@mui/material/LinearProgress";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { GlowCard } from "../../shared/GlowCard";
import type { PopularStarSystemSummary } from "../../../types/admin";

interface PopularStarSystemsCardProps {
  systems: PopularStarSystemSummary[];
}

export const PopularStarSystemsCard: React.FC<PopularStarSystemsCardProps> = ({ systems }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const maxCount = Math.max(1, ...systems.map((s) => s.startedByCount));

  return (
    <GlowCard sx={{ height: "100%" }}>
      <Typography variant="overline" sx={{ color: "var(--color-text-secondary)" }}>
        {t("adminOverview.popularStarSystems.label")}
      </Typography>

      {systems.length === 0 ? (
        <Typography variant="body2" sx={{ color: "var(--color-text-secondary)", mt: 2 }}>
          {t("adminOverview.popularStarSystems.empty")}
        </Typography>
      ) : (
        <Box sx={{ mt: 1.5, display: "flex", flexDirection: "column", gap: 1.5 }}>
          {systems.map((system) => (
            <Box
              key={system.id}
              onClick={() => navigate(`/admin/star-systems/${system.id}`)}
              sx={{ cursor: "pointer" }}
            >
              <Box sx={{ display: "flex", justifyContent: "space-between", mb: 0.5 }}>
                <Typography noWrap sx={{ fontWeight: 600, mr: 1 }}>
                  {system.name}
                </Typography>
                <Typography variant="caption" sx={{ color: "var(--color-accent-primary)", flexShrink: 0 }}>
                  {t("adminOverview.popularStarSystems.startedBy", { count: system.startedByCount })}
                </Typography>
              </Box>
              <LinearProgress
                variant="determinate"
                value={(system.startedByCount / maxCount) * 100}
                sx={{
                  height: 6,
                  borderRadius: "var(--radius-md)",
                  bgcolor: "var(--color-bg-elevated)",
                  "& .MuiLinearProgress-bar": { bgcolor: "var(--color-accent-primary)" },
                }}
              />
            </Box>
          ))}
        </Box>
      )}
    </GlowCard>
  );
};

export default PopularStarSystemsCard;
