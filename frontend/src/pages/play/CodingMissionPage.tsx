import React from "react";
import { Box, CircularProgress, Typography } from "@mui/material";
import { useParams, useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { missionApi } from "../../api/client";
import CodingMissionPlayer from "../../components/play/CodingMissionPlayer";
import "../../styles/RetroUI.css";

const CodingMissionPage: React.FC = () => {
  const { t } = useTranslation();
  const { missionId } = useParams<{ missionId: string }>();
  const navigate = useNavigate();

  // Idempotens: ha a kadét már elindította ezt a missziót, csak
  // visszaadja a meglévő repót — biztonságos itt is meghívni.
  const { isLoading, isError } = useQuery({
    queryKey: ["missionStart", missionId],
    queryFn: () => missionApi.start(missionId!),
    enabled: !!missionId,
    retry: false,
  });

  return (
    <Box
      sx={{
        width: "100vw",
        minHeight: "100vh",
        bgcolor: "#1a1a1a",
        p: 2,
        display: "flex",
        justifyContent: "center",
        alignItems: isLoading || isError ? "center" : undefined,
      }}
    >
      {isLoading && <CircularProgress color="inherit" />}
      {isError && (
        <Typography sx={{ color: "#fff" }}>
          {t("forge.codingMissionStartError")}
        </Typography>
      )}
      {!isLoading && !isError && missionId && (
        <CodingMissionPlayer
          missionId={missionId}
          onBack={() => navigate(-1)}
        />
      )}
    </Box>
  );
};

export default CodingMissionPage;
