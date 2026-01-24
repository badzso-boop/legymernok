import React, { useState } from "react";
import { Box, Typography, Button, Container, Paper } from "@mui/material";
import { motion, AnimatePresence } from "framer-motion";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import "../styles/LandingPage.css";
import ControlPanel from "../components/ControlPanel";

const LandingPage: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [phase, setPhase] = useState<"intro" | "transition" | "dashboard">(
    "intro",
  );

  const handleStart = () => {
    setPhase("transition");
    // Amikor a robot lecsúszik, megjelenik a vezérlőpult
    setTimeout(() => {
      setPhase("dashboard");
    }, 1200); // Kicsit gyorsabb átmenet
  };

  return (
    <Box
      sx={{
        width: "100%",
        height: "80vh",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <AnimatePresence mode="wait">
        {(phase === "intro" || phase === "transition") && (
          <Box
            key="intro-content"
            sx={{
              position: "relative",
              zIndex: 10,
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
            }}
          >
            {/* Szövegbuborék - csak az intro fázisban látszik */}
            {phase === "intro" && (
              <motion.div
                initial={{ opacity: 0, scale: 0.5, y: 50 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.5, y: 20 }}
                className="speech-bubble"
              >
                <Typography
                  variant="h5"
                  sx={{ fontWeight: "bold", mb: 1, color: "#1e293b" }}
                >
                  {t("welcome") || "Üdvözöllek a galaktikus utazásban!"}
                </Typography>
                <Typography variant="body1" sx={{ mb: 3, color: "#475569" }}>
                  Készen állsz a kezdésre?
                </Typography>

                <Box sx={{ display: "flex", gap: 2, justifyContent: "center" }}>
                  <Button
                    variant="contained"
                    size="large"
                    onClick={handleStart}
                    sx={{
                      bgcolor: "#3b82f6",
                      px: 4,
                      fontWeight: "bold",
                      "&:hover": { bgcolor: "#2563eb" },
                    }}
                  >
                    Igen
                  </Button>
                  <Button
                    variant="outlined"
                    color="error"
                    onClick={() => alert("Pedig az utazás nem vár! :)")}
                  >
                    Nem
                  </Button>
                </Box>
              </motion.div>
            )}

            {/* A Robot - Sprite.png */}
            <motion.div
              initial={{ y: 0, opacity: 1 }}
              animate={
                phase === "transition"
                  ? { y: 1000, rotate: 10 } // Lecsúszik
                  : { y: [0, -15, 0] } // Finom lebegés az intro alatt
              }
              transition={
                phase === "transition"
                  ? { duration: 1, ease: "anticipate" }
                  : { duration: 3, repeat: Infinity, ease: "easeInOut" } // Folyamatos lebegés
              }
              style={{
                width: "256px", // Legyen szép nagy
                height: "256px",
                backgroundImage: 'url("/Sprite.png")',
                backgroundSize: "contain",
                backgroundRepeat: "no-repeat",
                backgroundPosition: "center",
                imageRendering: "pixelated",
              }}
            />
          </Box>
        )}

        {phase === "dashboard" && (
          /* CONTROL PANEL PHASE */
          <motion.div
            key="dashboard-content"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 1 }}
            style={{
              width: "100%",
              height: "100%",
              display: "flex",
              justifyContent: "center",
              alignItems: "center",
            }}
          >
            <ControlPanel />
          </motion.div>
        )}
      </AnimatePresence>
    </Box>
  );
};

export default LandingPage;
