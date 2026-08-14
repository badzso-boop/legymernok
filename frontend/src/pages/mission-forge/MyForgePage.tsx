import React from "react";
import { Box, Typography, CircularProgress, Alert } from "@mui/material";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { NeonButton } from "../../components/shared/NeonButton";
import { GlowCard } from "../../components/shared/GlowCard";
import { forgeApi } from "../../api/client";
import StarSystemTable from "../../components/star-system/StarSystemTable";
import MissionTable from "../../components/mission/MissionTable";

const MyForgePage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  // Felhasználó saját csillagrendszerei
  const { data: systems, isLoading: loadingSystems } = useQuery({
    queryKey: ["myStarSystems"],
    queryFn: forgeApi.getMyStarSystems,
  });

  // Felhasználó saját missziói
  const {
    data: missions,
    isLoading: loadingMissions,
    error: missionsError,
  } = useQuery({
    queryKey: ["myMissions"],
    queryFn: forgeApi.getMyMissions,
  });

  if (loadingSystems || loadingMissions) {
    return (
      <Box
        sx={{
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          alignItems: "center",
          minHeight: "60vh",
          gap: 2,
        }}
      >
        <CircularProgress />
        <Typography sx={{ color: "var(--color-text-secondary)" }}>
          {t("controlPanel.loadingAssets")}
        </Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ maxWidth: 1200, mx: "auto", p: { xs: 2, md: 4 } }}>
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          mb: 4,
          flexWrap: "wrap",
          gap: 2,
        }}
      >
        <Box>
          <Typography variant="h4" sx={{ fontWeight: "bold" }}>
            {t("nav.myForge")}
          </Typography>
          <Typography variant="body2" sx={{ color: "var(--color-text-secondary)" }}>
            {t("forge.personalInventory")}
          </Typography>
        </Box>
        <NeonButton onClick={() => navigate("/forge")} data-cy="new-mission-btn">
          {t("forge.newMission")}
        </NeonButton>
      </Box>

      {missionsError && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {t("errorFetchMissions")}
        </Alert>
      )}

      <GlowCard sx={{ mb: 3 }}>
        <Typography variant="h6" sx={{ mb: 2 }}>
          {t("starSystems")}
        </Typography>
        <StarSystemTable
          systems={systems || []}
          loading={false}
          onEdit={(id) => navigate(`/admin/star-systems/${id}`)}
        />
      </GlowCard>

      <GlowCard>
        <Typography variant="h6" sx={{ mb: 2 }}>
          {t("missions")}
        </Typography>
        <MissionTable
          missions={missions || []}
          starSystems={systems || []}
          loading={false}
          onEdit={(id) => navigate(`/forge/${id}`)}
          onForge={(id) => navigate(`/forge/${id}`)}
        />
      </GlowCard>
    </Box>
  );
};

export default MyForgePage;
