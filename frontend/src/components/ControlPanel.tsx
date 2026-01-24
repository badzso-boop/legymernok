import React, { useState, useEffect, useRef } from "react";
import { Grid } from "@mui/material";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import "../styles/ControlPanel.css";

const ControlPanel: React.FC = () => {
  const { t } = useTranslation(); // <--- Hook
  const navigate = useNavigate();

  // Kezdeti állapotot useEffect-ben állítjuk be, hogy a nyelvváltásnál frissüljön
  const [terminalOutput, setTerminalOutput] = useState<string[]>([]);

  const [activeButton, setActiveButton] = useState<string | null>(null);
  const terminalRef = useRef<HTMLDivElement>(null);

  // Inicializáláskor kiírjuk az alap üzeneteket
  useEffect(() => {
    setTerminalOutput([
      `> ${t("controlPanel.systemReady")}`,
      `> ${t("controlPanel.waiting")}`,
    ]);
  }, [t]); // Ha változik a nyelv, újraküldi az üzeneteket (vagy hagyhatjuk üresen [])

  // Terminál automatikus görgetése alulra
  useEffect(() => {
    if (terminalRef.current) {
      terminalRef.current.scrollTop = terminalRef.current.scrollHeight;
    }
  }, [terminalOutput]);

  const addLog = (text: string) => {
    setTerminalOutput((prev) => [...prev, `> ${text}`]);
  };

  const handleCommand = (
    moduleId: string,
    labelKey: string,
    path: string,
    color: string,
  ) => {
    setActiveButton(moduleId);

    // A gomb feliratát (lefordítva) használjuk a logban, vagy a kulcsot
    const moduleName = t(labelKey);
    addLog(t("controlPanel.initializing", { module: moduleName }));

    setTimeout(() => {
      addLog(t("controlPanel.loadingAssets"));
    }, 600);

    setTimeout(() => {
      addLog(t("controlPanel.redirecting"));
    }, 1200);

    setTimeout(() => {
      navigate(path);
    }, 1800);
  };

  // Gombok definíciója (labelKey-vel)
  const buttons = [
    {
      id: "STAR_SYSTEMS",
      labelKey: "controlPanel.starSystems",
      color: "red",
      path: "/login",
    },
    {
      id: "SPEC_OPS",
      labelKey: "controlPanel.specialOps",
      color: "blue",
      path: "/login",
    },
    {
      id: "MONTHLY",
      labelKey: "controlPanel.monthlyQuest",
      color: "yellow",
      path: "/login",
    },
    {
      id: "PROFILE",
      labelKey: "controlPanel.pilotData",
      color: "green",
      path: "/login",
    },
  ];

  return (
    <div className="control-panel-casing">
      {/* Csavarok a sarkokban */}
      <div className="screw top-left" />
      <div className="screw top-right" />
      <div className="screw bottom-left" />
      <div className="screw bottom-right" />

      {/* A panel teteje: Gombok */}
      <div className="panel-section buttons-section">
        <Grid container spacing={4} justifyContent="center">
          {buttons.map((btn) => (
            <Grid key={btn.id}>
              {" "}
              {/* Grid item kell a spacinghez */}
              <div className="button-group">
                <button
                  className={`retro-btn ${btn.color} ${activeButton === btn.id ? "active" : ""}`}
                  onClick={() =>
                    handleCommand(btn.id, btn.labelKey, btn.path, btn.color)
                  }
                />
                <div className="label-plate">
                  <span>{t(btn.labelKey)}</span>
                </div>
              </div>
            </Grid>
          ))}
        </Grid>
      </div>

      {/* A panel alja: Terminál */}
      <div className="panel-section terminal-section">
        <div className="crt-monitor">
          <div className="screen-overlay" />
          <div className="terminal-content" ref={terminalRef}>
            {terminalOutput.map((line, i) => (
              <div key={i} className="terminal-line">
                {line}
              </div>
            ))}
            <div className="cursor-line">
              <span>&gt;</span>
              <span className="blinking-cursor">_</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ControlPanel;
