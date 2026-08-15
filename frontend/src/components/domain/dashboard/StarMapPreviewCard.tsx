import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import CircularProgress from "@mui/material/CircularProgress";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { GlowCard } from "../../shared/GlowCard";
import { NeonButton } from "../../shared/NeonButton";
import { StarfieldBackground } from "../../shared/StarfieldBackground";
import SectorMapGraph from "../sectormap/SectorMapGraph";
import { sectorApi, starSystemApi } from "../../../api/client";

/**
 * Star Map előnézeti kártya a Dashboard-on — terv 5.2, 3. pont / 5.3, 5.
 * pont, a Sector Map (#38) bevezetése óta a FELSŐ szintet (Szektorokat)
 * mutatja, nem a lapos Star Map-et — konzisztensen azzal, hogy a fő
 * navigáció is a Sector Map-re visz be elsőként.
 */
export const StarMapPreviewCard: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const { data: sectors, isLoading: sectorsLoading } = useQuery({
    queryKey: ["sectors"],
    queryFn: sectorApi.getAll,
  });

  const { data: systems, isLoading: systemsLoading } = useQuery({
    queryKey: ["starSystems", "with-progress"],
    queryFn: starSystemApi.getWithProgress,
  });

  const isLoading = sectorsLoading || systemsLoading;
  const unassignedCount = systems?.filter((s) => !s.sectorId).length ?? 0;
  const hasAnything = (sectors && sectors.length > 0) || unassignedCount > 0;

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
          position: "relative",
        }}
      >
        <StarfieldBackground intensity="ambient" layers={1} />

        {isLoading ? (
          <Box
            sx={{
              position: "relative",
              zIndex: 1,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              height: "100%",
            }}
          >
            <CircularProgress size={24} />
          </Box>
        ) : hasAnything ? (
          <Box sx={{ position: "relative", zIndex: 1, height: "100%" }}>
            <SectorMapGraph
              sectors={sectors ?? []}
              unassignedCount={unassignedCount}
              interactive={false}
              height="100%"
            />
          </Box>
        ) : (
          <Box
            sx={{
              position: "relative",
              zIndex: 1,
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              height: "100%",
            }}
          >
            <Typography variant="body2" sx={{ color: "var(--color-text-secondary)" }}>
              {t("homeDashboard.starMap.subtitle")}
            </Typography>
          </Box>
        )}
      </Box>

      <NeonButton
        onClick={() => navigate("/sector-map")}
        data-cy="dashboard-starmap-cta"
      >
        {t("homeDashboard.starMap.cta")}
      </NeonButton>
    </GlowCard>
  );
};

export default StarMapPreviewCard;
