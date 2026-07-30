import React from "react";
import { Box, Typography, Paper, Chip } from "@mui/material";
import {
  Code as CodeIcon,
  QuizOutlined as QuizIcon,
  ElectricBolt as CircuitIcon,
  Map as MapIcon,
  Build as ForgeIcon,
  Language as LanguageIcon,
} from "@mui/icons-material";
import { useTranslation } from "react-i18next";

interface FeatureCardProps {
  icon: React.ReactNode;
  titleKey: string;
  descKey: string;
  badgeKey?: string;
}

const FeatureCard: React.FC<FeatureCardProps> = ({
  icon,
  titleKey,
  descKey,
  badgeKey,
}) => {
  const { t } = useTranslation();
  return (
    <Paper
      sx={{
        p: 3,
        height: "100%",
        boxSizing: "border-box",
        bgcolor: "#111",
        border: "1px solid #333",
        color: "#ccc",
        display: "flex",
        flexDirection: "column",
        gap: 1.5,
        transition: "border-color 0.2s, box-shadow 0.2s",
        "&:hover": {
          borderColor: "#33ff00",
          boxShadow: "0 0 12px rgba(51,255,0,0.15)",
        },
      }}
    >
      <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
        <Box sx={{ color: "#33ff00", display: "flex" }}>{icon}</Box>
        <Typography
          sx={{
            color: "#fff",
            fontWeight: 700,
            fontSize: "1.05rem",
          }}
        >
          {t(titleKey)}
        </Typography>
        {badgeKey && (
          <Chip
            label={t(badgeKey)}
            size="small"
            sx={{
              ml: "auto",
              bgcolor: "#3d2d0a",
              color: "#ffb000",
              fontFamily: "monospace",
              fontSize: "0.65rem",
            }}
          />
        )}
      </Box>
      <Typography sx={{ color: "#94a3b8", fontSize: "0.92rem", lineHeight: 1.6 }}>
        {t(descKey)}
      </Typography>
    </Paper>
  );
};

const FeaturesSection: React.FC = () => {
  const { t } = useTranslation();

  const features: FeatureCardProps[] = [
    {
      icon: <CodeIcon />,
      titleKey: "landingPage.features.coding.title",
      descKey: "landingPage.features.coding.desc",
    },
    {
      icon: <QuizIcon />,
      titleKey: "landingPage.features.quiz.title",
      descKey: "landingPage.features.quiz.desc",
    },
    {
      icon: <MapIcon />,
      titleKey: "landingPage.features.starSystems.title",
      descKey: "landingPage.features.starSystems.desc",
    },
    {
      icon: <ForgeIcon />,
      titleKey: "landingPage.features.forge.title",
      descKey: "landingPage.features.forge.desc",
    },
    {
      icon: <CircuitIcon />,
      titleKey: "landingPage.features.circuit.title",
      descKey: "landingPage.features.circuit.desc",
      badgeKey: "landingPage.features.circuit.badge",
    },
    {
      icon: <LanguageIcon />,
      titleKey: "landingPage.features.ui.title",
      descKey: "landingPage.features.ui.desc",
    },
  ];

  return (
    <Box component="section" sx={{ py: { xs: 3, md: 5 } }}>
      <Box sx={{ textAlign: "center", mb: 4 }}>
        <Typography
          className="retro-font-header"
          sx={{ color: "#ccc", fontSize: { xs: "1.2rem", md: "1.5rem" }, mb: 1 }}
        >
          {t("landingPage.features.title")}
        </Typography>
        <Typography sx={{ color: "#94a3b8" }}>
          {t("landingPage.features.subtitle")}
        </Typography>
      </Box>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: {
            xs: "1fr",
            sm: "repeat(2, 1fr)",
            md: "repeat(3, 1fr)",
          },
          gap: 2.5,
          maxWidth: 1100,
          mx: "auto",
        }}
      >
        {features.map((f) => (
          <FeatureCard key={f.titleKey} {...f} />
        ))}
      </Box>
    </Box>
  );
};

export default FeaturesSection;
