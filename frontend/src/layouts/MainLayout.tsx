import React from "react";
import { Outlet, useNavigate } from "react-router-dom";
import {
  AppBar,
  Toolbar,
  Typography,
  Button,
  Box,
  Container,
} from "@mui/material";
import { useAuth } from "../context/AuthContext";
import { useTranslation } from "react-i18next";
import "../App.css";

const MainLayout: React.FC = () => {
  const { isAuthenticated, logout, hasRole } = useAuth();
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();

  const toggleLanguage = () => {
    i18n.changeLanguage(i18n.language === "hu" ? "en" : "hu");
  };

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        minHeight: "100vh",
        position: "relative",
      }}
    >
      <div className="star-background" />
      <AppBar
        position="sticky"
        sx={{
          bgcolor: "rgba(2, 6, 23, 0.6)",
          backdropFilter: "blur(8px)",
          borderBottom: "1px solid rgba(255,255,255,0.1)",
        }}
      >
        <Toolbar>
          <Typography
            variant="h6"
            component="div"
            sx={{
              flexGrow: 1,
              cursor: "pointer",
              fontWeight: "bold",
              letterSpacing: 1,
            }}
            onClick={() => navigate("/")}
          >
            🚀 LÉGYMÉRNÖK.HU
          </Typography>

          <Box sx={{ display: "flex", gap: 1 }}>
            <Button color="inherit" onClick={toggleLanguage}>
              {i18n.language.toUpperCase()}
            </Button>

            {!isAuthenticated ? (
              <>
                <Button color="inherit" onClick={() => navigate("/login")}>
                  {t("login")}
                </Button>
                <Button
                  variant="contained"
                  color="primary"
                  onClick={() => navigate("/register")}
                >
                  {t("register")}
                </Button>
              </>
            ) : (
              <>
                {hasRole("ROLE_ADMIN") && (
                  <Button
                    color="secondary"
                    variant="outlined"
                    onClick={() => navigate("/admin")}
                  >
                    {t("adminDashboard")}
                  </Button>
                )}
                <Button color="inherit" onClick={logout}>
                  Logout
                </Button>
              </>
            )}
          </Box>
        </Toolbar>
      </AppBar>

      <Container
        component="main"
        maxWidth="xl"
        sx={{ flexGrow: 1, py: 4, position: "relative", zIndex: 1 }}
      >
        <Outlet />
      </Container>
    </Box>
  );
};

export default MainLayout;
