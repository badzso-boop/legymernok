import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import ExploreIcon from "@mui/icons-material/Explore";
import { GlowCard } from "../../shared/GlowCard";
import { NeonButton } from "../../shared/NeonButton";

/**
 * Star Map előnézeti kártya — terv 5.2, 3. pont.
 *
 * SZÁNDÉKOSAN ideiglenes, NEM interaktív placeholder: a valódi, react-flow
 * alapú, progress-színezett előnézet egy KÉSŐBBI lépés (terv 5.3, 9. lépés)
 * — az akkor létrejövő komponens fogja kiváltani ezt a kártyát, ugyanabból a
 * komponensből paraméterezve, ahogy a terv 5.3/5. pontja előírja.
 */
export const StarMapPreviewCard: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  return (
    <GlowCard>
      <Typography variant="overline" sx={{ color: "var(--color-text-secondary)" }}>
        {t("homeDashboard.starMap.label")}
      </Typography>
      <Box
        sx={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          gap: 1.5,
          py: 3,
        }}
      >
        <ExploreIcon sx={{ fontSize: 48, color: "var(--color-accent-primary)" }} />
        <Typography sx={{ color: "var(--color-text-secondary)", textAlign: "center" }}>
          {t("homeDashboard.starMap.subtitle")}
        </Typography>
        <NeonButton
          onClick={() => navigate("/star-map")}
          data-cy="dashboard-starmap-cta"
        >
          {t("homeDashboard.starMap.cta")}
        </NeonButton>
      </Box>
    </GlowCard>
  );
};

export default StarMapPreviewCard;
