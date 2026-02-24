import React from "react";
import { Box } from "@mui/material";
import { useParams, useNavigate } from "react-router-dom";
import ForgeConfigPanel from "../../components/forge/ForgeConfigPanel";
import ForgeEditor from "../../components/forge/ForgeEditor";
import type { MissionForgeResponse } from "../../types/mission-forge";

const MissionForgePage: React.FC = () => {
  // 1. Paraméter lekérése az URL-ből (pl. /forge/123-456)
  const { missionId } = useParams<{ missionId: string }>();
  const navigate = useNavigate();

  /**
   * Ez a függvény fut le, amikor a ForgeConfigPanel-ben
   * sikeresen létrejött a misszió a Giteában és az adatbázisban is.
   */
  const handleMissionInitialized = (
    initializedMission: MissionForgeResponse,
  ) => {
    // Átnavigálunk az editor nézetre az új misszió ID-jával
    navigate(`/forge/${initializedMission.id}`);
  };

  return (
    <Box
      sx={{
        width: "100%",
        minHeight: "100vh",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        p: { xs: 1, md: 3 },
        bgcolor: "#121212", // Sötét háttér az egész oldalnak
      }}
    >
      {/* 2. Logikai elágazás az URL paraméter alapján */}
      {!missionId ? (
        /* HA NINCS ID: Megmutatjuk az inicializáló panelt */
        <ForgeConfigPanel onMissionInitialized={handleMissionInitialized} />
      ) : (
        /* HA VAN ID: Megnyitjuk a Monaco Editort */
        <ForgeEditor missionId={missionId} />
      )}
    </Box>
  );
};

export default MissionForgePage;
