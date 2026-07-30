import React, { useState, useEffect } from "react";
import { Box, Grid, Typography } from "@mui/material";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import SpaceStationCanvas from "./landing/SpaceStationCanvas";
import HeroSection from "./landing/HeroSection";
import AboutSection from "./landing/AboutSection";
import FeaturesSection from "./landing/FeaturesSection";
import FaqSection from "./landing/FaqSection";
import FinalCtaSection from "./landing/FinalCtaSection";
import { mainNavigationControls } from "../router/index";
import "../styles/RetroUI.css";

const LaunchConsole: React.FC = () => {
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
    const control = mainNavigationControls[index];
    if (control.disabled) {
      setStatusMessage(t("controlPanel.wipMessage"));
      return;
    }
    setLaunchingIndex(index);
    setCountdown(3); // 3 mp visszaszámlálás indul
  };

  return (
    <Box
      sx={{
        width: "100%",
        height: "100%",
        minHeight: { xs: "auto", sm: "80vh" },
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        boxSizing: "border-box",
      }}
    >
      {/* A GÉP / KONZOL (Szélesebb, fekvő tájolás) */}
      <Box
        sx={{
          width: "100%",
          maxWidth: 900,
          aspectRatio: { xs: "auto", sm: "16/9" },
          maxHeight: { xs: "none", sm: "80vh" },
          display: "flex",
          flexDirection: "column",
          border: "8px solid #2c2c2c",
          borderRadius: "10px",
          boxShadow: "0 20px 60px rgba(0,0,0,0.8)",
          overflow: "hidden",
          bgcolor: "#111",
          boxSizing: "border-box",
        }}
      >
        {/* ABLAK (Felső rész - Nagyobb arányban) */}
        <Box
          sx={{
            flex: { xs: "0 0 200px", sm: 2 },
            height: { xs: 200, sm: "auto" },
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
        <Box
          className="control-panel-casing"
          sx={{
            flex: { xs: "1 1 auto", sm: 1 },
            borderRadius: 0,
            border: "none",
            borderTop: "2px solid #444",
            display: "flex",
            flexDirection: { xs: "column", sm: "row" },
            alignItems: "center",
            justifyContent: "space-around",
            boxSizing: "border-box",
            gap: { xs: 2, sm: 0 },
            py: { xs: 2, sm: 0 },
            position: "relative",
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
              width: { xs: "100%", sm: "500px" },
              maxWidth: "100%",
              height: { xs: "auto", sm: "100%" },
              display: "flex",
              alignItems: "center",
              justifyContent: { xs: "center", sm: "flex-end" },
              paddingRight: { xs: 0, sm: "25px" },
              px: { xs: 2, sm: 0 },
              boxSizing: "border-box",
            }}
          >
            <div
              className="crt-monitor"
              style={{
                width: "100%",
                height: "clamp(90px, 18vw, 160px)",
                maxWidth: "100%",
                boxSizing: "border-box",
              }}
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
              width: { xs: "100%", sm: "auto" },
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
              boxSizing: "border-box",
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
                      style={{
                        width: "50px",
                        height: "50px",
                        opacity: btn.disabled ? 0.4 : 1,
                        cursor: btn.disabled ? "not-allowed" : "pointer",
                      }}
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
        </Box>
      </Box>
    </Box>
  );
};

const LandingPage: React.FC = () => {
  const { t } = useTranslation();

  return (
    <Box sx={{ width: "100%", maxWidth: "100%", overflowX: "hidden" }}>
      <HeroSection />
      <AboutSection />
      <FeaturesSection />

      <Box component="section" sx={{ py: { xs: 3, md: 5 } }}>
        <Box sx={{ textAlign: "center", mb: 3, px: 2 }}>
          <Typography
            className="retro-font-header"
            sx={{ color: "#ccc", fontSize: { xs: "1.2rem", md: "1.5rem" }, mb: 1 }}
          >
            {t("landingPage.launchConsole.title")}
          </Typography>
          <Typography sx={{ color: "#94a3b8", maxWidth: 640, mx: "auto" }}>
            {t("landingPage.launchConsole.subtitle")}
          </Typography>
        </Box>
        <LaunchConsole />
      </Box>

      <FaqSection />
      <FinalCtaSection />
    </Box>
  );
};

export default LandingPage;
