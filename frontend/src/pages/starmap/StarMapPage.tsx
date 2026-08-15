import React, { useMemo, useState } from "react";
import { Box, Typography, CircularProgress, Alert, IconButton, Tooltip } from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { useParams, Link as RouterLink } from "react-router-dom";
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
 *
 * Kétszintű Sector Map (issue #38) óta opcionálisan `:sectorId` route-
 * paraméterrel is meghívható — ha van, csak az adott szektorhoz tartozó
 * rendszereket mutatja, "vissza a Sector Map-re" linkkel. Paraméter nélkül
 * (a régi, backward-kompatibilis `/star-map`) az ÖSSZES rendszert mutatja —
 * ez egyben a "Besorolatlan" (sector nélküli) nézet is.
 */
const StarMapPage: React.FC = () => {
  const { t } = useTranslation();
  const { sectorId } = useParams<{ sectorId?: string }>();
  const [searchOpen, setSearchOpen] = useState(false);

  const {
    data: allSystems = [],
    isLoading: loading,
    isError,
  } = useQuery({
    queryKey: ["starSystems", "with-progress"],
    queryFn: starSystemApi.getWithProgress,
  });

  const systems = useMemo(() => {
    if (!sectorId) return allSystems;
    return allSystems.filter((s) => s.sectorId === sectorId);
  }, [allSystems, sectorId]);

  const sectorName = sectorId ? systems[0]?.sectorName ?? null : null;

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
            gap: 1,
          }}
        >
          <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
            <Tooltip title={t("sectorMap.backToSectorMap")}>
              <IconButton
                component={RouterLink}
                to="/sector-map"
                sx={{ color: "var(--color-accent-primary)" }}
                data-cy="star-map-back-to-sectors"
              >
                <ArrowBackIcon />
              </IconButton>
            </Tooltip>
            <Typography variant="h4" sx={{ fontWeight: "bold" }}>
              {sectorName ?? t("starMap.title")}
            </Typography>
          </Box>
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
          {/* A GlowCard saját (glassmorphism) háttere elnyomná a lap-szintű
              csillagmezőt a doboz belsejében — ugyanaz a minta, mint a
              Dashboard kicsinyített előnézet-kártyáján: egy külön
              StarfieldBackground/NebulaLayer közvetlenül a dobozon belül. */}
          <StarfieldBackground intensity="ambient" layers={2} />
          <NebulaLayer intensity="ambient" />

          <Box sx={{ position: "relative", zIndex: 1, height: "100%" }}>
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
          </Box>
        </GlowCard>
      </Box>
    </Box>
  );
};

export default StarMapPage;
