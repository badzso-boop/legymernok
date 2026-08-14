import React from "react";
import { Box, CircularProgress, Typography } from "@mui/material";
import { useParams, useNavigate, useLocation } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { missionApi } from "../../api/client";
import CodingMissionPlayer from "../../components/play/CodingMissionPlayer";
import { MissionPlayerShell } from "../../components/shared/MissionPlayerShell";

const CodingMissionPage: React.FC = () => {
  const { t } = useTranslation();
  const { missionId } = useParams<{ missionId: string }>();
  const navigate = useNavigate();
  const location = useLocation();
  const starSystemId = (location.state as { starSystemId?: string } | null)?.starSystemId;

  const handleBack = () => {
    if (starSystemId) navigate(`/star-systems/${starSystemId}`);
    else navigate(-1);
  };

  // Idempotens: ha a kadét már elindította ezt a missziót, csak
  // visszaadja a meglévő repót — biztonságos itt is meghívni.
  const { isLoading, isError } = useQuery({
    queryKey: ["missionStart", missionId],
    queryFn: () => missionApi.start(missionId!),
    enabled: !!missionId,
    retry: false,
  });

  return (
    <MissionPlayerShell onBack={handleBack} fullBleed>
      {isLoading && (
        <Box sx={{ display: "flex", justifyContent: "center", py: 8 }}>
          <CircularProgress color="inherit" />
        </Box>
      )}
      {isError && (
        <Typography sx={{ color: "var(--color-error)" }}>
          {t("forge.codingMissionStartError")}
        </Typography>
      )}
      {!isLoading && !isError && missionId && (
        <CodingMissionPlayer missionId={missionId} />
      )}
    </MissionPlayerShell>
  );
};

export default CodingMissionPage;
