import React from "react";
import { Box, Typography, CircularProgress, Alert } from "@mui/material";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import i18n from "../../i18n/config";
import RetroButton from "../../components/RetroButton";
import { forgeApi } from "../../api/client";
import { RetroPanel } from "../../components/forge/RetroPanel";
import StarSystemTable from "../../components/star-system/StarSystemTable";
import MissionTable from "../../components/mission/MissionTable";

const MyForgePage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const toggleLanguage = () => {
    i18n.changeLanguage(i18n.language === "hu" ? "en" : "hu");
  };

  // --- 1. Adatok lekérése (React Query) ---

  // Felhasználó saját csillagrendszerei
  const { data: systems, isLoading: loadingSystems } = useQuery({
    queryKey: ["myStarSystems"],
    queryFn: forgeApi.getMyStarSystems,
  });

  // Felhasználó saját missziói
  const {
    data: missions,
    isLoading: loadingMissions,
    error: missionsError,
  } = useQuery({
    queryKey: ["myMissions"],
    queryFn: forgeApi.getMyMissions,
  });

  // --- 2. Betöltési és Hiba állapotok ---

  if (loadingSystems || loadingMissions) {
    return (
      <Box
        sx={{
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          alignItems: "center",
          minHeight: "80vh",
          gap: 2,
        }}
      >
        <CircularProgress color="inherit" />
        <Typography sx={{ color: "#fff", fontFamily: "monospace" }}>
          {t("controlPanel.loadingAssets").toUpperCase()}
        </Typography>
      </Box>
    );
  }

  return (
    <Box
      sx={{
        bgcolor: "#121212",
        minHeight: "100vh",
        display: "flex",
        flexDirection: "column",
      }}
    >
      {/* Retro fejléc sáv */}
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          px: { xs: 2, md: 3 },
          py: 1,
          borderBottom: "1px solid #2a2a2a",
          bgcolor: "#0d0d0d",
          flexShrink: 0,
        }}
      >
        <Typography
          sx={{
            color: "#00ff88",
            fontFamily: "monospace",
            fontSize: "0.75rem",
            letterSpacing: 1,
          }}
        >
          {t("forge.personalInventory")} // {t("forge.online")}
        </Typography>
        <Box sx={{ display: "flex", gap: 2 }}>
          <RetroButton
            color="yellow"
            labelKey="nav.language"
            size="small"
            onClick={toggleLanguage}
          />
          <RetroButton
            color="red"
            labelKey="nav.back"
            size="small"
            onClick={() => navigate("/")}
          />
        </Box>
      </Box>

      {/* Fő tartalom */}
      <Box
        sx={{
          p: { xs: 1, md: 4 },
          flex: 1,
          display: "flex",
          justifyContent: "center",
        }}
      >
        <RetroPanel
          title={`CADET_FORGE // PERSONAL_INVENTORY // ${missions?.length || 0}_MISSIONS_DETECTED`}
          sx={{ width: "95vw", maxWidth: "1400px", p: 4 }}
        >
          {/* FEJLÉC ÉS ÚJ MISSZIÓ GOMB */}
          <Box
            sx={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              mb: 6,
              borderBottom: "2px solid #333",
              pb: 2,
            }}
          >
            <Box>
              <Typography
                variant="h4"
                sx={{
                  color: "#fff",
                  fontFamily: "monospace",
                  fontWeight: "bold",
                }}
              >
                {t("dashboard").toUpperCase()}
              </Typography>
              <Typography
                variant="caption"
                sx={{ color: "#666", fontFamily: "monospace" }}
              >
                ACCESS_GRANTED // AUTH_LEVEL: CADET_ENGINEER
              </Typography>
            </Box>

            <RetroButton
              color="green"
              labelKey="forge.newMission"
              onClick={() => navigate("/forge")}
              data-cy="new-mission-btn"
            />
          </Box>

          {missionsError && (
            <Alert
              severity="error"
              sx={{
                mb: 4,
                bgcolor: "#200",
                color: "#f88",
                fontFamily: "monospace",
              }}
            >
              {t("errorFetchMissions").toUpperCase()}
            </Alert>
          )}

          {/* 1. SZAKASZ: SAJÁT CSILLAGRENDSZEREK */}
          <Box sx={{ mb: 8 }}>
            <Typography
              variant="h6"
              sx={{
                color: "#fff",
                mb: 2,
                fontFamily: "monospace",
                display: "flex",
                alignItems: "center",
                gap: 1,
              }}
            >
              <span style={{ color: "#888" }}>{">"}</span>{" "}
              {t("starSystems").toUpperCase()}
            </Typography>
            <StarSystemTable
              systems={systems || []}
              loading={false}
              variant="retro"
              onEdit={(id) => navigate(`/admin/star-systems/${id}`)} // Itt navigálhatunk a szerkesztőre
            />
          </Box>

          {/* 2. SZAKASZ: SAJÁT MISSZIÓK */}
          <Box>
            <Typography
              variant="h6"
              sx={{
                color: "#fff",
                mb: 2,
                fontFamily: "monospace",
                display: "flex",
                alignItems: "center",
                gap: 1,
              }}
            >
              <span style={{ color: "#888" }}>{">"}</span>{" "}
              {t("missions").toUpperCase()}
            </Typography>
            <MissionTable
              missions={missions || []}
              starSystems={systems || []}
              loading={false}
              variant="retro"
              onEdit={(id) => navigate(`/forge/config/${id}`)} // Alapadatok szerkesztése (ha lesz ilyen route)
              onForge={(id) => navigate(`/forge/${id}`)} // Monaco Editor megnyitása
            />
          </Box>

          {/* LÁBJEGYZET */}
          <Box
            sx={{
              mt: 6,
              pt: 2,
              borderTop: "1px solid #222",
              textAlign: "center",
            }}
          >
            <Typography
              variant="caption"
              sx={{ color: "#444", fontFamily: "monospace" }}
            >
              TERMINAL_V2.1 // LEGYMERNOK_CODE_STORAGE_SYSTEM
            </Typography>
          </Box>
        </RetroPanel>
      </Box>
    </Box>
  );
};

export default MyForgePage;
