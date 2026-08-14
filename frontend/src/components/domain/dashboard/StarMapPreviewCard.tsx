import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import CircularProgress from "@mui/material/CircularProgress";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { GlowCard } from "../../shared/GlowCard";
import { NeonButton } from "../../shared/NeonButton";
import StarMapGraph from "../starmap/StarMapGraph";
import { starSystemApi } from "../../../api/client";

/**
 * Star Map előnézeti kártya — terv 5.2, 3. pont / 5.3, 5. pont.
 *
 * Ugyanazt a `StarMapGraph` komponenst használja, mint a teljes `/star-map`
 * oldal, csak kicsinyítve és `interactive={false}` móddal — nem külön
 * implementáció.
 */
export const StarMapPreviewCard: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const { data: systems, isLoading } = useQuery({
    queryKey: ["starSystems", "with-progress"],
    queryFn: starSystemApi.getWithProgress,
  });

  return (
    <GlowCard sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}>
      <Typography variant="overline" sx={{ color: "var(--color-text-secondary)" }}>
        {t("homeDashboard.starMap.label")}
      </Typography>

      <Box
        sx={{
          height: 160,
          borderRadius: "var(--radius-md)",
          overflow: "hidden",
          border: "1px solid var(--color-border)",
        }}
      >
        {isLoading ? (
          <Box sx={{ display: "flex", alignItems: "center", justifyContent: "center", height: "100%" }}>
            <CircularProgress size={24} />
          </Box>
        ) : systems && systems.length > 0 ? (
          <StarMapGraph systems={systems} interactive={false} compact height="100%" />
        ) : (
          <Box sx={{ display: "flex", alignItems: "center", justifyContent: "center", height: "100%" }}>
            <Typography variant="body2" sx={{ color: "var(--color-text-secondary)" }}>
              {t("homeDashboard.starMap.subtitle")}
            </Typography>
          </Box>
        )}
      </Box>

      <NeonButton
        onClick={() => navigate("/star-map")}
        data-cy="dashboard-starmap-cta"
      >
        {t("homeDashboard.starMap.cta")}
      </NeonButton>
    </GlowCard>
  );
};

export default StarMapPreviewCard;
