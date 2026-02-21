import React, { useEffect, useState } from "react";
import { Box, Typography, CircularProgress } from "@mui/material";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next"; // <--- Import
import StarMapCanvas from "./StarMapCanvas";
import apiClient from "../../api/client";
import type { StarSystemResponse } from "../../types/starSystem";
import "../../styles/ControlPanel.css";
import "../../styles/RetroUI.css";

const StarMapPage: React.FC = () => {
  const { t, i18n } = useTranslation(); // <--- Hook
  const navigate = useNavigate();
  const [systems, setSystems] = useState<StarSystemResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeBtn, setActiveBtn] = useState<string | null>(null);

  useEffect(() => {
    const fetchSystems = async () => {
      try {
        const response =
          await apiClient.get<StarSystemResponse[]>("/star-systems");
        setSystems(response.data);
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    };
    fetchSystems();
  }, []);

  const handleBtnClick = (id: string, action: () => void) => {
    setActiveBtn(id);
    setTimeout(() => {
      action();
      setActiveBtn(null);
    }, 200);
  };

  // Nyelvváltó logika
  const toggleLanguage = () => {
    const newLang = i18n.language === "hu" ? "en" : "hu";
    i18n.changeLanguage(newLang);
  };

  return (
    <Box
      sx={{
        width: "100vw",
        height: "100vh",
        bgcolor: "#1a1a1a",
        display: "flex",
        justifyContent: "center",
        alignItems: "center",
        p: 2,
      }}
    >
      <div
        className="control-panel-casing"
        style={{
          width: "100%",
          height: "100%",
          maxWidth: "none",
          display: "flex",
          flexDirection: "column",
        }}
      >
        <div className="screw top-left" />
        <div className="screw top-right" />
        <div className="screw bottom-left" />
        <div className="screw bottom-right" />

        {/* Felső Sáv */}
        <Box
          sx={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            mb: 2,
            borderBottom: "2px solid #333",
            pb: 1,
          }}
        >
          <Typography
            variant="h5"
            sx={{
              fontFamily: '"Share Tech Mono", monospace',
              color: "#ccc",
              letterSpacing: 2,
            }}
          >
            {t("starMap.title")}
          </Typography>
          <Typography
            variant="body1"
            sx={{
              fontFamily: '"VT323", monospace',
              color: "#0f0",
              textShadow: "0 0 5px #0f0",
            }}
          >
            {t("starMap.status")}:{" "}
            {loading ? t("starMap.scanning") : t("starMap.online")}
          </Typography>
        </Box>

        <Box sx={{ flexGrow: 1, display: "flex", gap: 2, overflow: "hidden" }}>
          {/* Bal oldali Gombsor */}
          <Box
            sx={{
              display: "flex",
              flexDirection: "column",
              gap: 3,
              justifyContent: "center",
              width: "100px",
            }}
          >
            <div className="button-group">
              <button
                className={`retro-btn red ${activeBtn === "back" ? "active" : ""}`}
                onClick={() => handleBtnClick("back", () => navigate(-1))}
              />
              <div className="label-plate">{t("starMap.back")}</div>
            </div>

            <div className="button-group">
              <button
                className={`retro-btn blue ${activeBtn === "scan" ? "active" : ""}`}
                onClick={() =>
                  handleBtnClick("scan", () => alert("Deep Scan Initiated..."))
                }
              />
              <div className="label-plate">{t("starMap.scan")}</div>
            </div>

            <div className="button-group">
              <button
                className={`retro-btn yellow ${activeBtn === "warp" ? "active" : ""}`}
                onClick={() =>
                  handleBtnClick("warp", () => alert("Warp Drive Charging..."))
                }
              />
              <div className="label-plate">{t("starMap.warp")}</div>
            </div>
          </Box>

          {/* A Térkép Monitor */}
          <Box sx={{ flexGrow: 1, position: "relative" }}>
            <div
              className="crt-monitor"
              style={{ height: "100%", borderRadius: "5px" }}
            >
              <div
                className="screen-overlay"
                style={{ pointerEvents: "none" }}
              />

              {loading ? (
                <Box
                  sx={{
                    display: "flex",
                    justifyContent: "center",
                    alignItems: "center",
                    height: "100%",
                    color: "#0f0",
                    fontFamily: '"VT323"',
                  }}
                >
                  <CircularProgress color="inherit" />
                </Box>
              ) : (
                <StarMapCanvas systems={systems} />
              )}
            </div>
          </Box>

          {/* Jobb oldali Gombsor */}
          <Box
            sx={{
              display: "flex",
              flexDirection: "column",
              gap: 3,
              justifyContent: "center",
              width: "100px",
            }}
          >
            <div className="button-group">
              <button
                className={`retro-btn green ${activeBtn === "data" ? "active" : ""}`}
                onClick={() => handleBtnClick("data", () => {})}
              />
              <div className="label-plate">{t("starMap.data")}</div>
            </div>

            {/* Nyelvváltó Gomb */}
            <div className="button-group">
              <button
                className={`retro-btn blue ${activeBtn === "lang" ? "active" : ""}`}
                onClick={() => handleBtnClick("lang", toggleLanguage)}
              />
              <div className="label-plate">{t("starMap.lang")}</div>
              {/* Opcionális: Kiírhatod az aktuális nyelvet is a gomb alá kis betűvel */}
              <Typography
                variant="caption"
                sx={{ color: "#555", mt: 0.5, fontFamily: "monospace" }}
              >
                {i18n.language.toUpperCase()}
              </Typography>
            </div>
          </Box>
        </Box>
      </div>
    </Box>
  );
};

export default StarMapPage;
