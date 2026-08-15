import React, { useEffect, useRef, useState } from "react";
import Editor from "@monaco-editor/react";
import {
  Box,
  Typography,
  Chip,
  Alert,
  Snackbar,
  Tooltip,
  IconButton,
  Button,
  CircularProgress,
} from "@mui/material";
import { Save as SaveIcon } from "@mui/icons-material";
import { Terminal as TerminalIcon, FolderOpen } from "lucide-react";
import { useTranslation } from "react-i18next";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { forgeApi } from "../../../api/client";
import type { MissionForgeContentRequest, VerificationStatus } from "../../../types/mission-forge";
import FileExplorer from "../../forge/FileExplorer";

interface CodeMissionEditorProps {
  missionId: string;
  /**
   * "template": admin/tulajdonos előre beállítja egy CODING misszió kezdő
   *   fájlstruktúráját (a régi MissionFileEditor use case-e) — nincs
   *   verifikációs státusz/élő log, csak fájlszerkesztés + mentés.
   * "workspace": a kadét Mission Forge munkaterülete (a régi ForgeEditor
   *   use case-e) — verifikációs státusz chip + élő STOMP log-terminál.
   */
  mode: "template" | "workspace";
}

const STATUS_COLORS: Record<VerificationStatus, "default" | "warning" | "success" | "error" | "primary"> = {
  DRAFT: "default",
  PENDING: "warning",
  APPROVED: "success",
  SUCCESS: "success",
  REJECTED: "error",
  FAILED: "error",
  REVIEW_NEEDED: "primary",
};

/**
 * Beágyazható fájlfa + Monaco szerkesztő, a MissionEditorPage CODING
 * típusú része (terv 4.1) — a korábbi MissionFileEditor/ForgeEditor
 * funkcionalitásának egyesítése.
 */
export const CodeMissionEditor: React.FC<CodeMissionEditorProps> = ({ missionId, mode }) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const isWorkspace = mode === "workspace";

  const [files, setFiles] = useState<Record<string, string>>({});
  const [activeFileName, setActiveFileName] = useState<string | null>(null);
  const [isTerminalOpen, setTerminalOpen] = useState(true);
  const [logs, setLogs] = useState<string[]>([]);
  const terminalEndRef = useRef<HTMLDivElement>(null);
  const [snackbar, setSnackbar] = useState({ open: false, message: "", severity: "success" as "success" | "error" });

  const { data: mission } = useQuery({
    queryKey: ["mission", missionId],
    queryFn: () => forgeApi.getMissionById(missionId),
    enabled: isWorkspace,
  });

  const { data: fetchedFiles, isLoading } = useQuery({
    queryKey: ["missionFiles", missionId],
    queryFn: () => forgeApi.getMissionFiles(missionId),
  });

  useEffect(() => {
    if (fetchedFiles && Object.keys(files).length === 0) {
      setFiles(fetchedFiles);
      const names = Object.keys(fetchedFiles);
      if (names.length > 0 && !activeFileName) setActiveFileName(names[0]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fetchedFiles]);

  useEffect(() => {
    if (!isWorkspace) return;
    const apiUrl = import.meta.env.VITE_API_URL || "/api";
    const socketUrl = `${apiUrl.replace(/\/api$/, "")}/ws-mission-logs`;
    const token = localStorage.getItem("token");

    const client = new Client({
      webSocketFactory: () => new SockJS(socketUrl),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        client.subscribe(`/topic/mission/${missionId}`, (message) => {
          const body = message.body;
          if (body.startsWith("[STATUS_UPDATED]:")) {
            queryClient.invalidateQueries({ queryKey: ["mission", missionId] });
            setLogs((prev) => [...prev, `\n--- ${t("forge.verificationCompleted")} ---`]);
          } else {
            setLogs((prev) => [...prev, body].slice(-200));
          }
        });
      },
      onStompError: (frame) => console.error("STOMP error", frame),
    });
    client.activate();
    return () => {
      client.deactivate();
    };
  }, [isWorkspace, missionId, queryClient, t]);

  useEffect(() => {
    terminalEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [logs]);

  const saveMutation = useMutation({
    mutationFn: (data: MissionForgeContentRequest) => forgeApi.saveMissionFiles(missionId, data),
    onSuccess: () => {
      setSnackbar({ open: true, message: t("forge.filesSavedSuccess"), severity: "success" });
      queryClient.invalidateQueries({ queryKey: ["mission", missionId] });
    },
    onError: (err: any) => setSnackbar({ open: true, message: err.message, severity: "error" }),
  });

  const createFileMutation = useMutation({
    mutationFn: (path: string) => forgeApi.createFile(missionId, path),
    onSuccess: (_data, path) => {
      setFiles((prev) => ({ ...prev, [path]: "" }));
      setActiveFileName(path);
    },
    onError: (err: any) => setSnackbar({ open: true, message: err.message, severity: "error" }),
  });

  const deleteFileMutation = useMutation({
    mutationFn: (path: string) => forgeApi.deleteFile(missionId, path),
    onSuccess: (_data, path) => {
      setFiles((prev) => {
        const next = { ...prev };
        delete next[path];
        return next;
      });
      setActiveFileName((prev) => (prev === path ? null : prev));
    },
    onError: (err: any) => setSnackbar({ open: true, message: err.message, severity: "error" }),
  });

  const renameFileMutation = useMutation({
    mutationFn: ({ oldPath, newPath }: { oldPath: string; newPath: string }) =>
      forgeApi.renameFile(missionId, oldPath, newPath),
    onSuccess: (_data, { oldPath, newPath }) => {
      setFiles((prev) => {
        const next = { ...prev };
        next[newPath] = next[oldPath];
        delete next[oldPath];
        return next;
      });
      setActiveFileName((prev) => (prev === oldPath ? newPath : prev));
    },
    onError: (err: any) => setSnackbar({ open: true, message: err.message, severity: "error" }),
  });

  if (isLoading) return null;

  const fileNames = Object.keys(files);

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
      {mode === "template" && <Alert severity="info">{t("forge.fileEditorTemplateNote")}</Alert>}

      {isWorkspace && mission && (
        <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
          <Typography variant="caption" color="text.secondary">
            {t("forge.statusWord")}:
          </Typography>
          <Chip
            label={t(`forge.status.${mission.verificationStatus.toLowerCase()}`)}
            color={STATUS_COLORS[mission.verificationStatus]}
            size="small"
          />
        </Box>
      )}

      <Box sx={{ display: "flex", height: 500, border: "1px solid #333" }}>
        <Box sx={{ width: 220, flexShrink: 0, bgcolor: "background.paper" }}>
          <FileExplorer
            fileNames={fileNames}
            activeFileName={activeFileName}
            onSelect={setActiveFileName}
            onCreate={(path) => createFileMutation.mutate(path)}
            onDelete={(path) => deleteFileMutation.mutate(path)}
            onRename={(oldPath, newPath) => renameFileMutation.mutate({ oldPath, newPath })}
          />
        </Box>

        <Box sx={{ flexGrow: 1, minWidth: 0, display: "flex", flexDirection: "column" }}>
          <Box sx={{ flexGrow: 1, minHeight: 0 }}>
            {activeFileName ? (
              <Editor
                height="100%"
                theme="vs-dark"
                language={activeFileName.endsWith(".md") ? "markdown" : "javascript"}
                value={files[activeFileName] || ""}
                onChange={(val) =>
                  activeFileName &&
                  val !== undefined &&
                  setFiles((prev) => ({ ...prev, [activeFileName]: val }))
                }
                options={{ minimap: { enabled: false }, fontSize: 14, automaticLayout: true }}
              />
            ) : (
              <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", height: "100%" }}>
                <Typography sx={{ color: "text.disabled" }}>
                  {fileNames.length === 0 ? t("forge.fileEditorNoFiles") : t("forge.fileEditorSelectFile")}
                </Typography>
              </Box>
            )}
          </Box>

          {isWorkspace && isTerminalOpen && (
            <Box sx={{ height: 200, flexShrink: 0, borderTop: "2px solid #333", display: "flex", flexDirection: "column" }}>
              <Box sx={{ p: 0.5, px: 2, display: "flex", justifyContent: "space-between", alignItems: "center", bgcolor: "background.paper" }}>
                <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                  <TerminalIcon size={14} />
                  <Typography variant="caption" fontWeight="bold">
                    {t("forge.terminalTitle")}
                  </Typography>
                </Box>
                <Tooltip title={t("forge.explorer")}>
                  <IconButton size="small" onClick={() => setTerminalOpen(false)}>
                    <FolderOpen size={14} />
                  </IconButton>
                </Tooltip>
              </Box>
              <Box sx={{ p: 1, flexGrow: 1, overflowY: "auto", fontFamily: "monospace", fontSize: "0.8rem" }}>
                {logs.length === 0 && (
                  <Typography variant="caption" color="text.disabled">
                    {t("forge.waitingForStdout")}
                  </Typography>
                )}
                {logs.map((log, i) => (
                  <div key={i}>{log}</div>
                ))}
                <div ref={terminalEndRef} />
              </Box>
            </Box>
          )}
        </Box>
      </Box>

      <Box>
        <Button
          variant="contained"
          startIcon={saveMutation.isPending ? <CircularProgress size={16} /> : <SaveIcon />}
          onClick={() => saveMutation.mutate({ missionId, files })}
          disabled={saveMutation.isPending}
          data-cy="forge-save-btn"
        >
          {t("forge.saveButton")}
        </Button>
      </Box>

      <Snackbar open={snackbar.open} autoHideDuration={4000} onClose={() => setSnackbar((s) => ({ ...s, open: false }))}>
        <Alert severity={snackbar.severity}>{snackbar.message}</Alert>
      </Snackbar>
    </Box>
  );
};

export default CodeMissionEditor;
