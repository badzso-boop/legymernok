import React, { useState, useEffect } from "react";
import { Box, Grid } from "@mui/material";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import SpaceStationCanvas from "./landing/SpaceStationCanvas";
import { mainNavigationControls } from "../router/index";
import "../styles/RetroUI.css";

const LandingPage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();

  const [launchingIndex, setLaunchingIndex] = useState<number | null>(null);
  const [progress, setProgress] = useState(0);
  const [statusMessage, setStatusMessage] = useState(
    "SYSTEMS READY. SELECT DESTINATION.",
  );
  const [countdown, setCountdown] = useState<number | null>(null);

  // Animációs loop a kilövéshez
  useEffect(() => {
    if (launchingIndex !== null && countdown === 0) {
      let animId: number;
      const startTime = Date.now();

      const animate = () => {
        const elapsed = (Date.now() - startTime) / 1000; // másodperc
        // 3 másodperc alatt érjen ki a képből
        const p = elapsed / 3;
        setProgress(p);

        if (p < 1.5) {
          animId = requestAnimationFrame(animate);
        } else {
          // Vége, navigáció
          // Itt döntjük el hova megyünk a launchingIndex alapján
          const targetPath = mainNavigationControls[launchingIndex].path;
          navigate(targetPath);
        }
      };
      animId = requestAnimationFrame(animate);
      return () => cancelAnimationFrame(animId);
    }
  }, [launchingIndex, countdown, navigate]);

  // Visszaszámláló logika
  useEffect(() => {
    if (countdown !== null && countdown > 0) {
      setStatusMessage(`LAUNCH IN T-${countdown}...`);
      const timer = setTimeout(() => setCountdown(countdown - 1), 1000);
      return () => clearTimeout(timer);
    } else if (countdown === 0) {
      setStatusMessage("LIFTOFF! ENGINES MAX POWER!");
    }
  }, [countdown]);

  const handleLaunch = (index: number) => {
    if (launchingIndex !== null) return; // Már megy egy
    setLaunchingIndex(index);
    setCountdown(3); // 3 mp visszaszámlálás indul
  };

  return (
    <Box
      sx={{
        width: "100%",
        height: "100%",
        minHeight: "80vh",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
      }}
    >
      {/* A GÉP / KONZOL (Szélesebb, fekvő tájolás) */}
      <Box
        sx={{
          aspectRatio: "16/9",
          maxHeight: "80vh",
          display: "flex",
          flexDirection: "column",
          border: "8px solid #2c2c2c",
          borderRadius: "10px",
          boxShadow: "0 20px 60px rgba(0,0,0,0.8)",
          overflow: "hidden",
          bgcolor: "#111",
        }}
      >
        {/* ABLAK (Felső rész - Nagyobb arányban) */}
        <Box
          sx={{
            flex: 2,
            position: "relative",
            borderBottom: "8px solid #2c2c2c",
            overflow: "hidden",
            bgcolor: "#000",
          }}
        >
          <SpaceStationCanvas
            launchingRocketIndex={launchingIndex}
            launchProgress={progress}
          />
        </Box>

        {/* CONTROL PANEL (Alsó rész - Kisebb, laposabb) */}
        <div
          className="control-panel-casing"
          style={{
            flex: 1,
            borderRadius: 0,
            border: "none",
            borderTop: "2px solid #444",
            display: "flex",
            flexDirection: "row",
            alignItems: "center",
            justifyContent: "space-around",
            boxSizing: "border-box",
          }}
        >
          {/* Csavarok (csak a sarkokban) */}
          <div
            className="screw top-left"
            style={{ left: "10px", top: "10px" }}
          />
          <div
            className="screw top-right"
            style={{ right: "10px", top: "10px" }}
          />
          <div
            className="screw bottom-left"
            style={{ left: "10px", bottom: "10px" }}
          />
          <div
            className="screw bottom-right"
            style={{ right: "10px", bottom: "10px" }}
          />

          {/* BAL OLDAL: Kijelző (Monitor) */}
          <Box
            sx={{
              width: "500px",
              height: "100%",
              display: "flex",
              alignItems: "center",
              justifyContent: "flex-end",
              paddingRight: "25px",
            }}
          >
            <div
              className="crt-monitor"
              style={{ width: "100%", height: "80%" }}
            >
              <div className="screen-overlay" />
              <div
                className="terminal-content"
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  fontSize: "1.1rem",
                  textAlign: "center",
                  padding: "0 10px",
                }}
              >
                {statusMessage}
                <span className="blinking-cursor">_</span>
              </div>
            </div>
          </Box>

          {/* JOBB OLDAL: Gombok (Rácsban vagy sorban) */}
          <Box
            sx={{
              display: "flex",
              justifyContent: "flex-start",
              alignItems: "center",
            }}
          >
            <Grid container spacing={2} justifyContent="center">
              {mainNavigationControls.map((btn, index) => (
                <Grid
                  key={index}
                  sx={{
                    display: "flex",
                    justifyContent: "center",
                    xs: 6,
                    sm: 3,
                  }}
                >
                  <div className="button-group">
                    <button
                      className={`retro-btn ${btn.color} ${launchingIndex === index ? "active" : ""}`}
                      onClick={() => handleLaunch(index)}
                      disabled={launchingIndex !== null}
                      style={{ width: "50px", height: "50px" }}
                    />
                    {/* Címke elhagyható, vagy nagyon kicsiben, ha zsúfolt */}
                    <div
                      className="label-plate"
                      style={{ fontSize: "0.6rem", marginTop: "5px" }}
                    >
                      {t(btn.labelKey)}
                    </div>
                  </div>
                </Grid>
              ))}
            </Grid>
          </Box>
        </div>
      </Box>
    </Box>
  );
};

export default LandingPage;
