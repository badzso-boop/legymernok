import React from "react";
import { Box, Typography, CircularProgress, Alert, IconButton, Tooltip } from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { StarfieldBackground } from "../../components/shared/StarfieldBackground";
import { NebulaLayer } from "../../components/shared/NebulaLayer";
import { GlowCard } from "../../components/shared/GlowCard";
import SectorMapGraph from "../../components/domain/sectormap/SectorMapGraph";
import { sectorApi, starSystemApi } from "../../api/client";

/**
 * Sector Map — a kétszintű Star Map (issue #38) felső szintje. A meglévő
 * Star Map oldal design-mintáját követi (StarfieldBackground/NebulaLayer/
 * GlowCard). Ld. plans/sector_map_2026.md.
 */
const SectorMapPage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const {
    data: sectors = [],
    isLoading: sectorsLoading,
    isError: sectorsError,
  } = useQuery({
    queryKey: ["sectors"],
    queryFn: sectorApi.getAll,
  });

  const { data: systems = [], isLoading: systemsLoading } = useQuery({
    queryKey: ["starSystems", "with-progress"],
    queryFn: starSystemApi.getWithProgress,
  });

  const loading = sectorsLoading || systemsLoading;
  const unassignedCount = systems.filter((s) => !s.sectorId).length;

  return (
    <Box sx={{ position: "relative", minHeight: "100%" }}>
      <StarfieldBackground intensity="ambient" />
      <NebulaLayer intensity="ambient" />

      <Box sx={{ position: "relative", zIndex: 1 }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 2 }}>
          <Tooltip title={t("sectorMap.backToDashboard")}>
            <IconButton
              onClick={() => navigate("/")}
              sx={{ color: "var(--color-accent-primary)" }}
              data-cy="sector-map-back"
            >
              <ArrowBackIcon />
            </IconButton>
          </Tooltip>
          <Typography variant="h4" sx={{ fontWeight: "bold" }}>
            {t("sectorMap.title")}
          </Typography>
        </Box>

        <GlowCard sx={{ height: { xs: "60vh", md: "70vh" }, position: "relative", p: 0, overflow: "hidden" }}>
          <StarfieldBackground intensity="ambient" layers={2} />
          <NebulaLayer intensity="ambient" />

          <Box sx={{ position: "relative", zIndex: 1, height: "100%" }}>
            {loading ? (
              <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", height: "100%" }}>
                <CircularProgress />
              </Box>
            ) : sectorsError ? (
              <Box sx={{ p: 2 }}>
                <Alert severity="error">{t("sectorMap.loadError")}</Alert>
              </Box>
            ) : sectors.length === 0 && unassignedCount === 0 ? (
              <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", height: "100%" }}>
                <Typography sx={{ color: "var(--color-text-secondary)" }}>
                  {t("sectorMap.empty")}
                </Typography>
              </Box>
            ) : (
              <SectorMapGraph
                sectors={sectors}
                unassignedCount={unassignedCount}
                interactive
                height="100%"
              />
            )}
          </Box>
        </GlowCard>
      </Box>
    </Box>
  );
};

export default SectorMapPage;
