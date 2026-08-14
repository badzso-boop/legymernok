import React from "react";
import { Box, Typography, Stack, Button } from "@mui/material";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { RobotMascot } from "../../components/domain/landing/RobotMascot";
import "../../styles/RetroUI.css";

const HeroSection: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  return (
    <Box
      component="section"
      sx={{
        textAlign: "center",
        py: { xs: 4, md: 8 },
        px: { xs: 1, sm: 2 },
      }}
    >
      <Box sx={{ mb: 2 }}>
        <RobotMascot size={88} />
      </Box>
      <Typography
        className="retro-font-header"
        sx={{
          color: "#33ff00",
          textShadow: "0 0 8px rgba(51,255,0,0.6)",
          letterSpacing: { xs: 1, md: 3 },
          mb: 2,
          fontSize: { xs: "0.7rem", sm: "0.85rem", md: "1rem" },
          wordBreak: "break-word",
        }}
      >
        {t("landingPage.hero.kicker")}
      </Typography>
      <Typography
        variant="h2"
        component="h1"
        sx={{
          fontWeight: 800,
          color: "#fff",
          fontSize: { xs: "1.9rem", sm: "2.5rem", md: "3.4rem" },
          lineHeight: 1.15,
          mb: 3,
          maxWidth: 900,
          mx: "auto",
        }}
      >
        {t("landingPage.hero.title")}
      </Typography>
      <Typography
        sx={{
          color: "#94a3b8",
          maxWidth: 720,
          mx: "auto",
          mb: 4,
          fontSize: { xs: "0.95rem", md: "1.15rem" },
          lineHeight: 1.6,
        }}
      >
        {t("landingPage.hero.subtitle")}
      </Typography>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={2}
        justifyContent="center"
        alignItems="center"
        sx={{ width: "100%" }}
      >
        {isAuthenticated ? (
          <Button
            variant="contained"
            size="large"
            onClick={() => navigate("/star-map")}
            sx={{ px: 4, py: 1.25, fontWeight: "bold" }}
          >
            {t("landingPage.hero.ctaContinue")}
          </Button>
        ) : (
          <>
            <Button
              variant="contained"
              color="primary"
              size="large"
              onClick={() => navigate("/register")}
              sx={{ px: 4, py: 1.25, fontWeight: "bold" }}
              data-cy="hero-cta-register"
            >
              {t("register")}
            </Button>
            <Button
              variant="outlined"
              color="inherit"
              size="large"
              onClick={() => navigate("/login")}
              sx={{ px: 4, py: 1.25 }}
              data-cy="hero-cta-login"
            >
              {t("login")}
            </Button>
          </>
        )}
      </Stack>
    </Box>
  );
};

export default HeroSection;
