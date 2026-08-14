import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import CircularProgress from "@mui/material/CircularProgress";
import { useNavigate } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import PlayArrowIcon from "@mui/icons-material/PlayArrow";
import ExploreIcon from "@mui/icons-material/Explore";
import { GlowCard } from "../../shared/GlowCard";
import { NeonButton } from "../../shared/NeonButton";
import { dashboardApi } from "../../../api/client";

/**
 * "Folytasd onnan, ahol abbahagytad" kártya — terv 5.2, 2. pont.
 *
 * A backend `type: "MISSION" | "GROUP"`-ot ad vissza, de MISSION esetén nem
 * ismerjük a misszió tényleges lejátszási típusát (CODING/QUIZ/CONTENT) —
 * emiatt MISSION esetén a star system oldalra navigálunk (ahol a helyes,
 * típusfüggő route már ki van választva), GROUP esetén viszont közvetlenül
 * a Group Playerbe, mert az ID-alapú progress onnan pontosan folytatható.
 */
export const ContinueCard: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["dashboardContinue"],
    queryFn: dashboardApi.getContinue,
    retry: (failureCount, err: any) =>
      err?.response?.status !== 404 && failureCount < 2,
  });

  const isNotFound = isError && (error as any)?.response?.status === 404;

  const handleContinue = () => {
    if (!data) return;
    if (data.type === "GROUP" && data.groupId) {
      navigate(`/play/group/${data.groupId}`);
    } else {
      navigate(`/star-systems/${data.starSystemId}`);
    }
  };

  return (
    <GlowCard>
      <Typography variant="overline" sx={{ color: "var(--color-text-secondary)" }}>
        {t("homeDashboard.continue.label")}
      </Typography>

      {isLoading && (
        <Box sx={{ display: "flex", justifyContent: "center", py: 3 }}>
          <CircularProgress size={28} />
        </Box>
      )}

      {!isLoading && isNotFound && (
        <Box sx={{ textAlign: "center", py: 2 }}>
          <Typography sx={{ mb: 2, color: "var(--color-text-secondary)" }}>
            {t("homeDashboard.continue.emptyState")}
          </Typography>
          <NeonButton
            startIcon={<ExploreIcon />}
            onClick={() => navigate("/star-map")}
            data-cy="dashboard-continue-empty-cta"
          >
            {t("homeDashboard.continue.emptyCta")}
          </NeonButton>
        </Box>
      )}

      {!isLoading && data && (
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
            {data.name}
          </Typography>
          <NeonButton
            startIcon={<PlayArrowIcon />}
            onClick={handleContinue}
            data-cy="dashboard-continue-cta"
          >
            {t("homeDashboard.continue.cta")}
          </NeonButton>
        </Box>
      )}
    </GlowCard>
  );
};

export default ContinueCard;
