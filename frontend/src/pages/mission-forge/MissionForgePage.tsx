import React from "react";
import { Box, CircularProgress } from "@mui/material";
import { useParams, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { forgeApi } from "../../api/client";
import ForgeConfigPanel from "../../components/forge/ForgeConfigPanel";
import ForgeEditor from "../../components/forge/ForgeEditor";
import QuizEditor from "../../components/forge/quiz/QuizEditor";
import type { MissionForgeResponse } from "../../types/mission-forge";

const MissionForgePage: React.FC = () => {
  const { missionId } = useParams<{ missionId: string }>();
  const navigate = useNavigate();

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
        justifyContent: "center",
        alignItems: "center",
        p: { xs: 1, md: 3 },
        bgcolor: "#121212",
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
  );
};

export default MissionForgePage;
