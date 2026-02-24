import React, { useState, useEffect, useRef, useMemo } from "react";
import Editor from "@monaco-editor/react";
import {
  Box,
  CircularProgress,
  Alert,
  Snackbar,
  Typography,
  Chip,
  Tooltip,
} from "@mui/material";
import {
  FileCode,
  FileText,
  Terminal as TerminalIcon,
  FolderOpen,
  Info,
  ChevronRight,
  ChevronDown,
} from "lucide-react"; // Modern ikonok
import { useTranslation } from "react-i18next";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { forgeApi } from "../../api/client";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import type {
  MissionForgeResponse,
  MissionForgeContentRequest,
  VerificationStatus,
} from "../../types/mission-forge";
import { RetroPanel } from "./RetroPanel";
import RetroButton from "../RetroButton";

interface ForgeEditorProps {
  missionId: string;
}

const ForgeEditor: React.FC<ForgeEditorProps> = ({ missionId }) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const editorRef = useRef<any>(null);

  // --- State-ek ---
  const [currentFileContents, setCurrentFileContents] = useState<
    Record<string, string>
  >({});
  const [activeFileName, setActiveFileName] = useState<string | null>(null);
  const [isSidebarOpen, setSidebarOpen] = useState(true);
  const [isTerminalOpen, setTerminalOpen] = useState(true);
  const [logs, setLogs] = useState<string[]>([]);
  const terminalEndRef = useRef<HTMLDivElement>(null);
  const stompClientRef = useRef<Client | null>(null);

  const scrollToBottom = () => {
    terminalEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(() => {
    scrollToBottom();
  }, [logs]);

  useEffect(() => {
    const socketUrl = `${import.meta.env.VITE_API_URL?.replace("/api", "") || "http://localhost:8080"}/ws-mission-logs`;

    const client = new Client({
      webSocketFactory: () => new SockJS(socketUrl),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        console.log("Connected to Mission Logs WebSocket");
        client.subscribe(`/topic/mission/${missionId}`, (message) => {
          setLogs((prev) => {
            const newLogs = [...prev, message.body];
            return newLogs.slice(-200); // Csak az utolsó 200 sort tartjuk meg a memóriában
          });
        });
      },
      onStompError: (frame) => {
        console.error("STOMP error", frame);
      },
    });

    client.activate();
    stompClientRef.current = client;

    return () => {
      client.deactivate();
    };
  }, [missionId]);

  const clearLogs = () => setLogs([]);

  // Snackbar állapotok
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: "",
    severity: "success" as "success" | "error",
  });

  // --- API Hívások ---
  const { data: mission, isLoading: isLoadingMission } = useQuery({
    queryKey: ["mission", missionId],
    queryFn: () => forgeApi.getMissionById(missionId),
  });

  const { data: fetchedFiles, isLoading: isLoadingFiles } = useQuery({
    queryKey: ["missionFiles", missionId],
    queryFn: () => forgeApi.getMissionFiles(missionId),
  });

  // Szinkronizáció a Gitea fájlokkal
  useEffect(() => {
    if (fetchedFiles && Object.keys(currentFileContents).length === 0) {
      setCurrentFileContents(fetchedFiles);
      const files = Object.keys(fetchedFiles);
      if (files.length > 0 && !activeFileName) setActiveFileName(files[0]);
    }
  }, [fetchedFiles, currentFileContents, activeFileName]);

  const saveMutation = useMutation({
    mutationFn: (data: MissionForgeContentRequest) =>
      forgeApi.saveMissionFiles(missionId, data),
    onSuccess: () => {
      setSnackbar({
        open: true,
        message: t("forge.filesSavedSuccess"),
        severity: "success",
      });
      queryClient.invalidateQueries({ queryKey: ["mission", missionId] });
    },
    onError: (err: any) => {
      setSnackbar({
        open: true,
        message: `${t("forge.errorSavingMission")}: ${err.message}`,
        severity: "error",
      });
    },
  });

  // --- Segédfüggvények ---
  const getFileIcon = (fileName: string) => {
    if (fileName.endsWith(".md")) return <FileText size={16} color="#007acc" />;
    if (fileName.endsWith(".js") || fileName.endsWith(".ts"))
      return <FileCode size={16} color="#f1e05a" />;
    if (fileName.endsWith(".py")) return <FileCode size={16} color="#3572A5" />;
    return <FileCode size={16} color="#ccc" />;
  };

  const getStatusColor = (
    status: VerificationStatus,
  ): "default" | "warning" | "success" | "error" | "primary" => {
    const colors: Record<
      VerificationStatus,
      "default" | "warning" | "success" | "error" | "primary"
    > = {
      DRAFT: "default",
      PENDING: "warning",
      APPROVED: "success",
      SUCCESS: "success",
      REJECTED: "error",
      FAILED: "error",
      REVIEW_NEEDED: "primary",
    };

    return colors[status] || "primary";
  };

  if (isLoadingMission || isLoadingFiles || !mission) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", mt: 10 }}>
        <CircularProgress color="inherit" />
      </Box>
    );
  }

  return (
    <RetroPanel
      title={`ENGINEERING_STATION // ${mission.name.toUpperCase()}`}
      sx={{
        width: "98vw",
        height: "92vh",
        display: "flex",
        flexDirection: "column",
        p: 0,
        overflow: "hidden",
      }}
    >
      {/* 1. TOP TOOLBAR */}
      <Box
        sx={{
          p: 1.5,
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          borderBottom: "1px solid #333",
          bgcolor: "#1a1a1a",
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", gap: 2, ml: 2 }}>
          <Typography
            variant="caption"
            sx={{ color: "#888", fontFamily: "monospace" }}
          >
            {t("forge.statusWord")}:
          </Typography>
          <Chip
            label={t(
              `forge.status.${mission.verificationStatus.toLowerCase()}`,
            )}
            color={getStatusColor(mission.verificationStatus)}
            size="small"
            sx={{
              borderRadius: 0,
              fontWeight: "bold",
              fontFamily: "monospace",
            }}
          />
        </Box>

        <Box sx={{ display: "flex", gap: 3, mr: 2 }}>
          <RetroButton
            color="red"
            labelKey="starMap.back"
            size="small"
            onClick={() => window.history.back()}
          />
          <RetroButton
            color="green"
            labelKey="forge.saveButton"
            size="small"
            onClick={() =>
              saveMutation.mutate({ missionId, files: currentFileContents })
            }
            disabled={saveMutation.isPending}
            active={saveMutation.isPending}
          />
        </Box>
      </Box>

      {/* 2. MAIN WORKSPACE AREA */}
      <Box sx={{ flexGrow: 1, display: "flex", overflow: "hidden" }}>
        {/* ACTIVITY BAR (Ikonok a bal szélen) */}
        <Box
          sx={{
            width: "50px",
            flexShrink: 0,
            bgcolor: "#0a0a0a",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            py: 2,
            gap: 3,
            borderRight: "1px solid #222",
          }}
        >
          <Tooltip title="Explorer" placement="right">
            <FolderOpen
              size={24}
              color={isSidebarOpen ? "#fff" : "#444"}
              style={{ cursor: "pointer" }}
              onClick={() => setSidebarOpen(!isSidebarOpen)}
            />
          </Tooltip>
          <Tooltip title="Terminal" placement="right">
            <TerminalIcon
              size={24}
              color={isTerminalOpen ? "#fff" : "#444"}
              style={{ cursor: "pointer" }}
              onClick={() => setTerminalOpen(!isTerminalOpen)}
            />
          </Tooltip>
        </Box>

        {/* SIDEBAR (Fájlböngésző) */}
        {isSidebarOpen && (
          <Box
            sx={{
              width: "220px",
              minWidth: "220px",
              flexShrink: 0,
              bgcolor: "#111",
              borderRight: "1px solid #222",
              display: "flex",
              flexDirection: "column",
            }}
          >
            <Typography
              variant="caption"
              sx={{
                p: 1.5,
                color: "#666",
                fontWeight: "bold",
                borderBottom: "1px solid #222",
              }}
            >
              EXPLORER: PROJECT_FILES
            </Typography>
            <Box sx={{ py: 1 }}>
              {Object.keys(currentFileContents).map((name) => (
                <Box
                  key={name}
                  onClick={() => setActiveFileName(name)}
                  sx={{
                    display: "flex",
                    alignItems: "center",
                    gap: 1,
                    px: 2,
                    py: 0.8,
                    cursor: "pointer",
                    bgcolor: activeFileName === name ? "#222" : "transparent",
                    borderLeft:
                      activeFileName === name
                        ? "2px solid #fff"
                        : "2px solid transparent",
                    "&:hover": { bgcolor: "#1a1a1a" },
                  }}
                >
                  {getFileIcon(name)}
                  <Typography
                    sx={{
                      color: activeFileName === name ? "#fff" : "#aaa",
                      fontSize: "0.85rem",
                      fontFamily: "monospace",
                    }}
                  >
                    {name}
                  </Typography>
                </Box>
              ))}
            </Box>
          </Box>
        )}

        {/* EDITOR ÉS TERMINAL TERÜLET */}
        <Box
          sx={{
            flexGrow: 1,
            display: "flex",
            flexDirection: "column",
            bgcolor: "#000",
            minWidth: 0,
            minHeight: 0,
          }}
        >
          {/* EDITOR */}
          <Box sx={{ flexGrow: 1, position: "relative", minHeight: 0 }}>
            {activeFileName ? (
              <Editor
                height="100%"
                theme="vs-dark"
                language={
                  activeFileName.endsWith(".md") ? "markdown" : "javascript"
                }
                value={currentFileContents[activeFileName] || ""}
                onChange={(val) =>
                  activeFileName &&
                  val !== undefined &&
                  setCurrentFileContents((prev) => ({
                    ...prev,
                    [activeFileName]: val,
                  }))
                }
                options={{
                  minimap: { enabled: false },
                  fontSize: 14,
                  fontFamily: "monospace",
                  automaticLayout: true,
                  scrollBeyondLastLine: false,
                  padding: { top: 10 },
                }}
              />
            ) : (
              <Box
                sx={{
                  display: "flex",
                  justifyContent: "center",
                  alignItems: "center",
                  height: "100%",
                }}
              >
                <Typography sx={{ color: "#333", fontFamily: "monospace" }}>
                  SELECT_FILE_TO_BEGIN
                </Typography>
              </Box>
            )}
          </Box>

          {/* TERMINAL */}
          {isTerminalOpen && (
            <Box
              sx={{
                height: "250px",
                flexShrink: 0,
                bgcolor: "#050505",
                borderTop: "2px solid #333",
                display: "flex",
                flexDirection: "column",
              }}
            >
              {/* Terminál Fejléc */}
              <Box
                sx={{
                  p: 0.5,
                  px: 2,
                  bgcolor: "#111",
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                }}
              >
                <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                  <TerminalIcon size={14} color="#aaa" />
                  <Typography
                    variant="caption"
                    sx={{
                      color: "#aaa",
                      fontFamily: "monospace",
                      fontWeight: "bold",
                    }}
                  >
                    TERMINAL // GITEA_ACTIONS_STREAM
                  </Typography>
                </Box>
                <Box sx={{ display: "flex", gap: 2, alignItems: "center" }}>
                  <Typography
                    variant="caption"
                    onClick={clearLogs}
                    sx={{
                      color: "#666",
                      cursor: "pointer",
                      "&:hover": { color: "#fff" },
                      fontFamily: "monospace",
                    }}
                  >
                    [CLEAR_LOGS]
                  </Typography>
                  <Typography
                    variant="caption"
                    sx={{ color: "#0f0", fontFamily: "monospace" }}
                  >
                    ONLINE
                  </Typography>
                </Box>
              </Box>

              {/* Log Sorok */}
              <Box
                sx={{
                  p: 2,
                  flexGrow: 1,
                  overflowY: "auto",
                  fontFamily: "'Courier New', monospace",
                  color: "#ddd",
                  fontSize: "0.85rem",
                  lineHeight: 1.4,
                }}
              >
                {logs.length === 0 && (
                  <Typography variant="caption" sx={{ color: "#444" }}>
                    {">"} WAITING_FOR_STDOUT...
                  </Typography>
                )}

                {logs.map((log, index) => (
                  <div
                    key={index}
                    style={{
                      marginBottom: "2px",
                      wordBreak: "break-all",
                      color: log.includes("ERROR")
                        ? "#ff5555"
                        : log.includes("SUCCESS")
                          ? "#55ff55"
                          : "#ddd",
                    }}
                  >
                    <span style={{ color: "#555", marginRight: "8px" }}>
                      [{new Date().toLocaleTimeString()}]
                    </span>
                    {log}
                  </div>
                ))}

                {/* Automatikus görgetés célpontja */}
                <div ref={terminalEndRef} />

                {/* Villogó kurzor csak akkor, ha nincs hiba */}
                <div
                  className="blinking-cursor"
                  style={{
                    display: "inline-block",
                    width: "8px",
                    height: "15px",
                    background: "#fff",
                    marginLeft: "4px",
                  }}
                ></div>
              </Box>
            </Box>
          )}
        </Box>
      </Box>

      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert
          severity={snackbar.severity}
          variant="filled"
          sx={{ fontFamily: "monospace" }}
        >
          {snackbar.message.toUpperCase()}
        </Alert>
      </Snackbar>
    </RetroPanel>
  );
};

export default ForgeEditor;
