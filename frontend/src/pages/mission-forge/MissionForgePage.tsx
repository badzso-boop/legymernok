import React from "react";
import { Box, CircularProgress, Typography } from "@mui/material";
import { useParams, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { forgeApi } from "../../api/client";
import ForgeConfigPanel from "../../components/forge/ForgeConfigPanel";
import ForgeEditor from "../../components/forge/ForgeEditor";
import QuizEditor from "../../components/forge/quiz/QuizEditor";
import RetroButton from "../../components/RetroButton";
import type { MissionForgeResponse } from "../../types/mission-forge";

const MissionForgePage: React.FC = () => {
  const { missionId } = useParams<{ missionId: string }>();
  const navigate = useNavigate();
  const { t, i18n } = useTranslation();

  const toggleLanguage = () => {
    i18n.changeLanguage(i18n.language === "hu" ? "en" : "hu");
  };

  const { data: mission, isLoading } = useQuery({
    queryKey: ["mission", missionId],
    queryFn: () => (missionId ? forgeApi.getMissionById(missionId) : null),
    enabled: !!missionId,
  });

  const handleMissionInitialized = (
    initializedMission: MissionForgeResponse,
  ) => {
    navigate(`/forge/${initializedMission.id}`);
  };

  if (missionId && isLoading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", mt: 10 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box
      sx={{
        width: "100%",
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
        bgcolor: "#121212",
      }}
    >
      {/* Retro fejléc sáv */}
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          px: { xs: 2, md: 3 },
          py: 1,
          borderBottom: "1px solid #2a2a2a",
          bgcolor: "#0d0d0d",
          flexShrink: 0,
        }}
      >
        <Typography
          sx={{
            color: "#00ff88",
            fontFamily: "monospace",
            fontSize: "0.75rem",
            letterSpacing: 1,
          }}
        >
          {t("forge.engineeringStation")} // {t("forge.online")}
        </Typography>
        <Box sx={{ display: "flex", gap: 2 }}>
          <RetroButton
            color="yellow"
            labelKey="nav.language"
            size="small"
            onClick={toggleLanguage}
          />
          <RetroButton
            color="red"
            labelKey="nav.back"
            size="small"
            onClick={() => navigate("/my-forge")}
          />
        </Box>
      </Box>

      {/* Fő tartalom */}
      <Box
        sx={{
          flex: 1,
          display: "flex",
          justifyContent: "center",
          alignItems: "center",
          p: { xs: 1, md: 3 },
        }}
      >
        {!missionId ? (
          <ForgeConfigPanel onMissionInitialized={handleMissionInitialized} />
        ) : mission?.missionType === "QUIZ" ? (
          <QuizEditor missionId={missionId} />
        ) : (
          <ForgeEditor missionId={missionId} />
        )}
      </Box>
    </Box>
  );
};

export default MissionForgePage;
