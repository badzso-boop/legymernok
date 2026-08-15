import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Grid from "@mui/material/Grid";
import CircularProgress from "@mui/material/CircularProgress";
import Alert from "@mui/material/Alert";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { adminStatsApi } from "../../../api/client";
import { AdminStatTiles } from "../../../components/domain/admin-dashboard/AdminStatTiles";
import { RecentCadetsCard } from "../../../components/domain/admin-dashboard/RecentCadetsCard";
import { PopularStarSystemsCard } from "../../../components/domain/admin-dashboard/PopularStarSystemsCard";
import { AdminLogSnippetCard } from "../../../components/domain/admin-dashboard/AdminLogSnippetCard";

/**
 * Admin dashboard főoldal — korábban egy üres "Work in Progress" placeholder
 * volt. Áttekintő statisztikák (kadét/rendszer/misszió számok, új
 * regisztrációk), legutóbb csatlakozott kadétok, legnépszerűbb
 * csillagrendszerek, és egy élő rendszernapló-előnézet.
 */
const AdminDashboardPage: React.FC = () => {
  const { t } = useTranslation();

  const { data: stats, isLoading, isError } = useQuery({
    queryKey: ["adminStats"],
    queryFn: adminStatsApi.getStats,
  });

  return (
    <Box>
      <Typography variant="h4" sx={{ fontWeight: "bold", mb: 3 }}>
        {t("adminOverview.title")}
      </Typography>

      {isLoading && (
        <Box sx={{ display: "flex", justifyContent: "center", py: 6 }}>
          <CircularProgress />
        </Box>
      )}

      {isError && <Alert severity="error">{t("adminOverview.loadError")}</Alert>}

      {stats && (
        <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
          <AdminStatTiles stats={stats} />

          <Grid container spacing={3}>
            <Grid size={{ xs: 12, md: 6 }}>
              <RecentCadetsCard cadets={stats.recentCadets} />
            </Grid>
            <Grid size={{ xs: 12, md: 6 }}>
              <PopularStarSystemsCard systems={stats.mostStartedStarSystems} />
            </Grid>
          </Grid>

          <AdminLogSnippetCard />
        </Box>
      )}
    </Box>
  );
};

export default AdminDashboardPage;
