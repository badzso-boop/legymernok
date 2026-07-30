import React from "react";
import { Box, Typography } from "@mui/material";
import { useTranslation } from "react-i18next";
import { RetroPanel } from "../../components/forge/RetroPanel";
import "../../styles/RetroUI.css";

const AboutSection: React.FC = () => {
  const { t } = useTranslation();

  return (
    <Box component="section" sx={{ py: { xs: 3, md: 5 } }}>
      <RetroPanel
        title={t("landingPage.about.title")}
        sx={{
          maxWidth: 960,
          margin: "0 auto",
          padding: "clamp(20px, 4vw, 40px)",
          boxSizing: "border-box",
        }}
      >
        <Box sx={{ mt: 2, display: "flex", flexDirection: "column", gap: 2 }}>
          <Typography sx={{ color: "#e2e8f0", lineHeight: 1.7 }}>
            {t("landingPage.about.intro")}
          </Typography>
          <Typography sx={{ color: "#cbd5e1", lineHeight: 1.7 }}>
            {t("landingPage.about.narrative")}
          </Typography>
          <Typography sx={{ color: "#cbd5e1", lineHeight: 1.7 }}>
            {t("landingPage.about.missionsText")}
          </Typography>
          <Typography sx={{ color: "#cbd5e1", lineHeight: 1.7 }}>
            {t("landingPage.about.starSystemsText")}
          </Typography>
        </Box>
      </RetroPanel>
    </Box>
  );
};

export default AboutSection;
