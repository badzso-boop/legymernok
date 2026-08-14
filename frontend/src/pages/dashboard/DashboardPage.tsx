import React from "react";
import Box from "@mui/material/Box";
import Container from "@mui/material/Container";
import Stack from "@mui/material/Stack";
import { useQuery } from "@tanstack/react-query";
import { StarfieldBackground } from "../../components/shared/StarfieldBackground";
import { NebulaLayer } from "../../components/shared/NebulaLayer";
import { StreakBar } from "../../components/domain/dashboard/StreakBar";
import { ContinueCard } from "../../components/domain/dashboard/ContinueCard";
import { StarMapPreviewCard } from "../../components/domain/dashboard/StarMapPreviewCard";
import { FriendActivityCard } from "../../components/domain/dashboard/FriendActivityCard";
import { authApi } from "../../api/client";

/**
 * Authentikált "pilótafülke" — terv 5.2. Duolingo-inspirált információs
 * hierarchia felülről lefelé: streak → folytatás → star map előnézet →
 * barátok aktivitása.
 */
const DashboardPage: React.FC = () => {
  const { data: me } = useQuery({
    queryKey: ["authMe"],
    queryFn: authApi.getMe,
    staleTime: 60_000,
  });

  return (
    <Box sx={{ position: "relative", minHeight: "100%", overflow: "hidden" }}>
      <StarfieldBackground intensity="ambient" />
      <NebulaLayer intensity="ambient" />
      <Container
        maxWidth="sm"
        sx={{ position: "relative", zIndex: 1, py: { xs: 3, md: 5 } }}
      >
        <Stack spacing={3}>
          <StreakBar
            currentStreak={me?.currentStreak ?? 0}
            longestStreak={me?.longestStreak ?? 0}
          />
          <ContinueCard />
          <StarMapPreviewCard />
          <FriendActivityCard />
        </Stack>
      </Container>
    </Box>
  );
};

export default DashboardPage;
