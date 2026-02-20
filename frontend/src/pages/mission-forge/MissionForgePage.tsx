import React, { useState } from "react";
import { Box, Typography, Button } from "@mui/material";
import { useTranslation } from "react-i18next";
import ForgeConfigPanel from "../../components/forge/ForgeConfigPanel";
import ForgeEditor from "../../components/forge/ForgeEditor";
import type { MissionForgeResponse } from "../../types/mission-forge";

const MissionForgePage: React.FC = () => {
  const { t } = useTranslation();
  const [mission, setMission] = useState<MissionForgeResponse | null>(null);
  const [currentFileContents, setCurrentFileContents] = useState<
    Record<string, string>
  >({});
  const [activeFileName, setActiveFileName] = useState<string | null>(null);

  const handleMissionInitialized = (
    initializedMission: MissionForgeResponse,
  ) => {
    setMission(initializedMission);
    // Opcionálisan ide tölthetnénk be a kezdeti fájlokat, vagy hagyhatjuk a ForgeEditorre
  };

  return (
    <Box sx={{ flexGrow: 1, p: { xs: 1, md: 3 } }}>
      <Typography variant="h4" gutterBottom sx={{ mb: 4, fontWeight: "bold" }}>
        {t("forge.title")}
      </Typography>

      {!mission ? (
        /* KEZDETI ÁLLAPOT: Csak a Config Panel látszik középen */
        <Box sx={{ maxWidth: 600, mx: "auto" }}>
          <ForgeConfigPanel onMissionInitialized={handleMissionInitialized} />
        </Box>
      ) : (
        /* SZERKESZTŐ ÁLLAPOT: A teljes szélességet kitölti az editor */
        <Box sx={{ width: "100%" }}>
          <ForgeEditor
            mission={mission}
            currentFileContents={currentFileContents}
            setCurrentFileContents={setCurrentFileContents}
            activeFileName={activeFileName}
            setActiveFileName={setActiveFileName}
          />
          <Button
            variant="text"
            onClick={() => setMission(null)}
            sx={{ mt: 2, color: "text.secondary" }}
          >
            ← {t("forge.backToInitialization")}
          </Button>
        </Box>
      )}
    </Box>
  );
};

export default MissionForgePage;
