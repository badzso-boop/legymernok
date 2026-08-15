import React, { useState } from "react";
import { Box, Typography, CircularProgress, Alert, IconButton, Tooltip } from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { StarfieldBackground } from "../../components/shared/StarfieldBackground";
import { NebulaLayer } from "../../components/shared/NebulaLayer";
import { GlowCard } from "../../components/shared/GlowCard";
import StarMapGraph from "../../components/domain/starmap/StarMapGraph";
import SearchPanel from "../../components/search/SearchPanel";
import { starSystemApi } from "../../api/client";

/**
 * Csillagtérkép — a design system nyelvén (StarfieldBackground/NebulaLayer/
 * GlowCard), ugyanaz a minta, mint a Dashboard-on. A vissza-navigáció mostantól
 * a MainLayout navbar-ján keresztül megy (a router 2026-08-15-től ide ágyazza
 * ezt az oldalt), nincs szükség saját "VISSZA" gombra — a korábbi retro
 * "control-panel-casing"/CRT-monitor/WARP-DATA gombok (utóbbi kettő nem is
 * volt funkcionális) megszűntek.
 */
const StarMapPage: React.FC = () => {
  const { t } = useTranslation();
  const [searchOpen, setSearchOpen] = useState(false);

  const {
    data: systems = [],
    isLoading: loading,
    isError,
  } = useQuery({
    queryKey: ["starSystems", "with-progress"],
    queryFn: starSystemApi.getWithProgress,
  });

  return (
    <Box sx={{ position: "relative", minHeight: "100%" }}>
      <StarfieldBackground intensity="ambient" />
      <NebulaLayer intensity="ambient" />

      <Box sx={{ position: "relative", zIndex: 1 }}>
        <Box
          sx={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            mb: 2,
          }}
        >
          <Typography variant="h4" sx={{ fontWeight: "bold" }}>
            {t("starMap.title")}
          </Typography>
          <Tooltip title={t("starMap.scan")}>
            <IconButton
              onClick={() => setSearchOpen(true)}
              sx={{ color: "var(--color-accent-primary)" }}
              data-cy="star-map-search-toggle"
            >
              <SearchIcon />
            </IconButton>
          </Tooltip>
        </Box>

        <GlowCard sx={{ height: { xs: "60vh", md: "70vh" }, position: "relative", p: 0, overflow: "hidden" }}>
          {searchOpen ? (
            <SearchPanel onClose={() => setSearchOpen(false)} />
          ) : loading ? (
            <Box
              sx={{
                display: "flex",
                justifyContent: "center",
                alignItems: "center",
                height: "100%",
              }}
            >
              <CircularProgress />
            </Box>
          ) : isError ? (
            <Box sx={{ p: 2 }}>
              <Alert severity="error">{t("starMap.loadError")}</Alert>
            </Box>
          ) : (
            <StarMapGraph systems={systems} interactive height="100%" />
          )}
        </GlowCard>
      </Box>
    </Box>
  );
};

export default StarMapPage;
