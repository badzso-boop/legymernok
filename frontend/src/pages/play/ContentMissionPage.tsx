import React from "react";
import { Box } from "@mui/material";
import { useParams, useNavigate } from "react-router-dom";
import ContentMissionView from "../../components/play/ContentMissionView";
import "../../styles/RetroUI.css";

const ContentMissionPage: React.FC = () => {
  const { missionId } = useParams<{ missionId: string }>();
  const navigate = useNavigate();

  return (
    <Box
      sx={{
        width: "100vw",
        minHeight: "100vh",
        bgcolor: "#1a1a1a",
        p: 2,
        display: "flex",
        justifyContent: "center",
      }}
    >
      <div
        className="control-panel-casing"
        style={{
          width: "100%",
          maxWidth: 900,
          display: "flex",
          flexDirection: "column",
        }}
      >
        <div className="screw top-left" />
        <div className="screw top-right" />
        <div className="screw bottom-left" />
        <div className="screw bottom-right" />

        <Box sx={{ flex: 1, display: "flex", flexDirection: "column", minHeight: 0 }}>
          <ContentMissionView
            missionId={missionId!}
            onComplete={() => navigate(-1)}
          />
        </Box>
      </div>
    </Box>
  );
};

export default ContentMissionPage;
