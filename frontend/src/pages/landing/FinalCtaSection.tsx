import React from "react";
import { Box, Typography, Button } from "@mui/material";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

const FinalCtaSection: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  if (isAuthenticated) {
    return null;
  }

  return (
    <Box
      component="section"
      sx={{
        py: { xs: 4, md: 6 },
        textAlign: "center",
        maxWidth: 700,
        mx: "auto",
        px: 2,
      }}
    >
      <Typography
        variant="h4"
        component="h2"
        sx={{
          color: "#fff",
          fontWeight: 700,
          mb: 1.5,
          fontSize: { xs: "1.5rem", md: "2rem" },
        }}
      >
        {t("landingPage.finalCta.title")}
      </Typography>
      <Typography sx={{ color: "#94a3b8", mb: 3 }}>
        {t("landingPage.finalCta.subtitle")}
      </Typography>
      <Button
        variant="contained"
        color="primary"
        size="large"
        onClick={() => navigate("/register")}
        sx={{ px: 5, py: 1.25, fontWeight: "bold" }}
        data-cy="final-cta-register"
      >
        {t("register")}
      </Button>
    </Box>
  );
};

export default FinalCtaSection;
