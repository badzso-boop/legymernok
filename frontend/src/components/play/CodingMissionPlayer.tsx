import React, { useEffect, useRef, useState } from "react";
import Editor, { type OnMount } from "@monaco-editor/react";
import type { editor as MonacoEditorNS } from "monaco-editor";
import {
  Box,
  CircularProgress,
  Alert,
  Snackbar,
  Typography,
  Drawer,
  IconButton,
  Tooltip,
  Tabs,
  Tab,
} from "@mui/material";
import FolderOpenIcon from "@mui/icons-material/FolderOpen";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { forgeApi, missionApi } from "../../api/client";
import RetroButton from "../RetroButton";
import FileExplorer from "../forge/FileExplorer";
import MarkdownContent from "../shared/MarkdownContent";
import { useIsCompactViewport } from "../../hooks/useIsCompactViewport";
import { getMonacoLanguage, isProtectedCadetFile } from "../../utils/missionFiles";
import {
  MissionPlayerActions,
  MissionPlayerHeaderPortal,
} from "../shared/MissionPlayerShell";

// Mobilon a virtuális billentyűzet fölött nehezen elérhető karakterek —
// a Monaco `trigger("keyboard", "type", ...)` API-ján keresztül szúrjuk be
// a kurzor pozíciójába, hogy az undo-history és az auto-closing-bracket
// logika is helyesen kezelje (nem nyers value-csere).
const QUICK_INSERT_CHARS = ["(", ")", "{", "}", "[", "]", ";"];

interface CodingMissionPlayerProps {
  missionId: string;
}

// A kadét saját munkarepójában dolgozik (a startMission() által
// létrehozott, admin-owned, a kadét collaboratorral biró repóban) — ez
// nem a Mission Forge önálló küldetés-alkotó felülete, hanem egy
// admin/tulajdonos által létrehozott CODING misszió in-app lejátszója.
// Automatikus tesztelés/CI-visszajelzés ehhez a folyamathoz még nincs
// bekötve (a meglévő Gitea Action webhook a Mission Forge saját-repós
// modelljéhez készült, ahol 1 misszió = 1 repó — itt viszont sok kadét
// dolgozik egyszerre, mindenki a saját másolatában).
const CodingMissionPlayer: React.FC<CodingMissionPlayerProps> = ({
  missionId,
}) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();

  const isCompact = useIsCompactViewport();
  const [currentFileContents, setCurrentFileContents] = useState<
    Record<string, string>
  >({});
  const [activeFileName, setActiveFileName] = useState<string | null>(null);
  // Desktopon a fájlfa alapból nyitva marad (oldalsáv); mobilon alapból
  // összecsukva indul, hogy a Monaco kapja a maximális helyet (terv 6.2) —
  // bottom-sheet Drawer-ként húzható elő.
  const [isSidebarOpen, setSidebarOpen] = useState(true);
  const [isMobileFilesOpen, setMobileFilesOpen] = useState(false);
  // A kadét először a feladatleírást lássa, ne a fájlfát — a "Task" fül az
  // alapértelmezett, csak utána vált "Files"-ra.
  const [sidebarTab, setSidebarTab] = useState<"task" | "files">("task");
  const [snackbar, setSnackbar] = useState({
    open: false,
    message: "",
    severity: "success" as "success" | "error",
  });
  const editorRef = useRef<MonacoEditorNS.IStandaloneCodeEditor | null>(null);

  const handleEditorMount: OnMount = (editorInstance) => {
    editorRef.current = editorInstance;
  };

  const insertAtCursor = (text: string) => {
    const ed = editorRef.current;
    if (!ed) return;
    ed.trigger("keyboard", "type", { text });
    ed.focus();
  };

  const insertTab = () => {
    const ed = editorRef.current;
    if (!ed) return;
    ed.trigger("keyboard", "tab", {});
    ed.focus();
  };

  const { data: mission, isLoading: isLoadingMission } = useQuery({
    queryKey: ["mission", missionId],
    queryFn: () => forgeApi.getMissionById(missionId),
  });

  const { data: fetchedFiles, isLoading: isLoadingFiles } = useQuery({
    queryKey: ["playFiles", missionId],
    queryFn: () => missionApi.getPlayFiles(missionId),
  });

  useEffect(() => {
    if (fetchedFiles && Object.keys(currentFileContents).length === 0) {
      setCurrentFileContents(fetchedFiles);
      const files = Object.keys(fetchedFiles);
      if (files.length > 0 && !activeFileName) setActiveFileName(files[0]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fetchedFiles]);

  const saveMutation = useMutation({
    mutationFn: () => missionApi.savePlayFiles(missionId, currentFileContents),
    onSuccess: () => {
      setSnackbar({
        open: true,
        message: t("forge.filesSavedSuccess"),
        severity: "success",
      });
    },
    onError: (err: any) => {
      setSnackbar({
        open: true,
        message: err.response?.data?.message || err.message,
        severity: "error",
      });
    },
  });

  const createFileMutation = useMutation({
    mutationFn: (path: string) => missionApi.createPlayFile(missionId, path),
    onSuccess: (_data, path) => {
      setCurrentFileContents((prev) => ({ ...prev, [path]: "" }));
      setActiveFileName(path);
    },
    onError: (err: any) => {
      setSnackbar({
        open: true,
        message: err.response?.data?.message || err.message,
        severity: "error",
      });
    },
  });

  const deleteFileMutation = useMutation({
    mutationFn: (path: string) => missionApi.deletePlayFile(missionId, path),
    onSuccess: (_data, path) => {
      setCurrentFileContents((prev) => {
        const next = { ...prev };
        delete next[path];
        return next;
      });
      setActiveFileName((prev) => (prev === path ? null : prev));
    },
    onError: (err: any) => {
      setSnackbar({
        open: true,
        message: err.response?.data?.message || err.message,
        severity: "error",
      });
    },
  });

  const renameFileMutation = useMutation({
    mutationFn: ({ oldPath, newPath }: { oldPath: string; newPath: string }) =>
      missionApi.renamePlayFile(missionId, oldPath, newPath),
    onSuccess: (_data, { oldPath, newPath }) => {
      setCurrentFileContents((prev) => {
        const next = { ...prev };
        next[newPath] = next[oldPath];
        delete next[oldPath];
        return next;
      });
      setActiveFileName((prev) => (prev === oldPath ? newPath : prev));
    },
    onError: (err: any) => {
      setSnackbar({
        open: true,
        message: err.response?.data?.message || err.message,
        severity: "error",
      });
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["playFiles", missionId] });
    },
  });

  if (isLoadingMission || isLoadingFiles || !mission) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", mt: 10 }}>
        <CircularProgress color="inherit" />
      </Box>
    );
  }

  const fileNames = Object.keys(currentFileContents);
  const readOnlyFileNames = fileNames.filter(isProtectedCadetFile);
  const activeFileIsReadOnly = activeFileName ? isProtectedCadetFile(activeFileName) : false;

  const sidebarPanel = (onFileSelected?: () => void) => (
    <Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <Tabs
        value={sidebarTab}
        onChange={(_e, value) => setSidebarTab(value)}
        variant="fullWidth"
        sx={{
          minHeight: 36,
          borderBottom: "1px solid #222",
          "& .MuiTab-root": { minHeight: 36, fontSize: "0.7rem", color: "#888" },
          "& .Mui-selected": { color: "#fff !important" },
        }}
      >
        <Tab value="task" label={t("forge.taskTab")} data-cy="coding-player-task-tab" />
        <Tab value="files" label={t("forge.filesTab")} data-cy="coding-player-files-tab" />
      </Tabs>
      {sidebarTab === "task" ? (
        <Box sx={{ p: 2, overflowY: "auto", flexGrow: 1 }}>
          {mission.descriptionMarkdown ? (
            <MarkdownContent>{mission.descriptionMarkdown}</MarkdownContent>
          ) : (
            <Typography variant="body2" sx={{ color: "#666" }}>
              {t("forge.noDescription")}
            </Typography>
          )}
        </Box>
      ) : (
        <Box sx={{ flexGrow: 1, minHeight: 0 }}>
          <FileExplorer
            fileNames={fileNames}
            activeFileName={activeFileName}
            readOnlyFileNames={readOnlyFileNames}
            onSelect={(name) => {
              setActiveFileName(name);
              onFileSelected?.();
            }}
            onCreate={(path) => createFileMutation.mutate(path)}
            onDelete={(path) => deleteFileMutation.mutate(path)}
            onRename={(oldPath, newPath) =>
              renameFileMutation.mutate({ oldPath, newPath })
            }
          />
        </Box>
      )}
    </Box>
  );

  return (
    <Box
      sx={{
        width: "100%",
        height: "100%",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
      }}
    >
      <MissionPlayerHeaderPortal
        title={mission.name}
        subtitle={t("forge.codingPlayerSavedNote")}
      />
      <MissionPlayerActions>
        <RetroButton
          color="green"
          labelKey="forge.saveButton"
          size="small"
          onClick={() => saveMutation.mutate()}
          disabled={saveMutation.isPending}
          active={saveMutation.isPending}
        />
      </MissionPlayerActions>

      <Box sx={{ flexGrow: 1, display: "flex", overflow: "hidden" }}>
        {!isCompact && (
          <>
            <Box
              sx={{
                width: "50px",
                flexShrink: 0,
                bgcolor: "#0a0a0a",
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                py: 2,
                borderRight: "1px solid #222",
              }}
            >
              <Box
                onClick={() => setSidebarOpen(!isSidebarOpen)}
                sx={{ cursor: "pointer", color: isSidebarOpen ? "#fff" : "#444" }}
              >
                📁
              </Box>
            </Box>

            {isSidebarOpen && (
              <Box
                sx={{
                  width: "260px",
                  minWidth: "260px",
                  flexShrink: 0,
                  bgcolor: "#111",
                  borderRight: "1px solid #222",
                }}
              >
                {sidebarPanel()}
              </Box>
            )}
          </>
        )}

        <Box
          sx={{
            flexGrow: 1,
            display: "flex",
            flexDirection: "column",
            minHeight: 0,
          }}
        >
          {isCompact && (
            <Box
              sx={{
                display: "flex",
                alignItems: "center",
                gap: 1,
                px: 1,
                py: 0.5,
                borderBottom: "1px solid #222",
                bgcolor: "#0a0a0a",
                flexShrink: 0,
              }}
            >
              <Tooltip title={t("forge.codingPlayerOpenFiles")}>
                <IconButton
                  size="small"
                  onClick={() => setMobileFilesOpen(true)}
                  sx={{ color: "#fff" }}
                  aria-label={t("forge.codingPlayerOpenFiles")}
                >
                  <FolderOpenIcon fontSize="small" />
                </IconButton>
              </Tooltip>
              <Typography
                noWrap
                sx={{ color: "#888", fontFamily: "monospace", fontSize: "0.75rem" }}
              >
                {activeFileName ?? t("forge.selectFileToBegin")}
              </Typography>
            </Box>
          )}

          <Box
            sx={{
              flexGrow: 1,
              display: "flex",
              flexDirection: "column",
              minHeight: 0,
              bgcolor: "#000",
            }}
          >
            {activeFileName && activeFileIsReadOnly && (
              <Alert severity="info" sx={{ borderRadius: 0, flexShrink: 0 }}>
                {t("forge.readOnlyFileBanner")}
              </Alert>
            )}
            <Box sx={{ flexGrow: 1, minHeight: 0, position: "relative" }}>
              {activeFileName ? (
                <Editor
                  height="100%"
                  theme="vs-dark"
                  language={getMonacoLanguage(activeFileName)}
                  value={currentFileContents[activeFileName] || ""}
                  onChange={(val) =>
                    activeFileName &&
                    val !== undefined &&
                    !activeFileIsReadOnly &&
                    setCurrentFileContents((prev) => ({
                      ...prev,
                      [activeFileName]: val,
                    }))
                  }
                  onMount={handleEditorMount}
                  options={{
                    minimap: { enabled: false },
                    fontSize: 14,
                    fontFamily: "monospace",
                    automaticLayout: true,
                    scrollBeyondLastLine: false,
                    padding: { top: 10 },
                    readOnly: activeFileIsReadOnly,
                    domReadOnly: activeFileIsReadOnly,
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
                    {fileNames.length === 0
                      ? t("forge.codingPlayerNoFiles")
                      : t("forge.selectFileToBegin")}
                  </Typography>
                </Box>
              )}
            </Box>
          </Box>

          {/* Virtuális billentyűzet feletti gyorsgombok — a mobil kódolás
              legnagyobb súrlódási pontja a nehezen elérhető speciális
              karakterek, nem maga a Monaco (terv 6.2, 2. pont). */}
          {isCompact && activeFileName && (
            <Box
              role="toolbar"
              aria-label={t("forge.codingPlayerQuickInsertLabel")}
              sx={{
                display: "flex",
                gap: 0.5,
                px: 1,
                py: 0.75,
                borderTop: "1px solid #222",
                bgcolor: "#0a0a0a",
                flexShrink: 0,
                overflowX: "auto",
              }}
            >
              <IconButton
                size="small"
                onClick={insertTab}
                sx={{
                  color: "#fff",
                  fontFamily: "monospace",
                  fontSize: "0.7rem",
                  border: "1px solid #333",
                  borderRadius: 1,
                  px: 1,
                  minWidth: 44,
                }}
              >
                {t("forge.codingPlayerQuickInsertTab")}
              </IconButton>
              {QUICK_INSERT_CHARS.map((char) => (
                <IconButton
                  key={char}
                  size="small"
                  onClick={() => insertAtCursor(char)}
                  sx={{
                    color: "#fff",
                    fontFamily: "monospace",
                    fontSize: "0.9rem",
                    border: "1px solid #333",
                    borderRadius: 1,
                    minWidth: 40,
                  }}
                >
                  {char}
                </IconButton>
              ))}
            </Box>
          )}
        </Box>
      </Box>

      {isCompact && (
        <Drawer
          anchor="bottom"
          open={isMobileFilesOpen}
          onClose={() => setMobileFilesOpen(false)}
          PaperProps={{
            sx: {
              maxHeight: "70vh",
              bgcolor: "#111",
              borderTop: "1px solid #222",
              borderTopLeftRadius: 12,
              borderTopRightRadius: 12,
            },
          }}
        >
          <Box sx={{ height: "60vh" }}>
            {sidebarPanel(() => setMobileFilesOpen(false))}
          </Box>
        </Drawer>
      )}

      <Snackbar
        open={snackbar.open}
        autoHideDuration={4000}
        onClose={() => setSnackbar({ ...snackbar, open: false })}
      >
        <Alert severity={snackbar.severity} variant="filled" sx={{ fontFamily: "monospace" }}>
          {snackbar.message.toUpperCase()}
        </Alert>
      </Snackbar>
    </Box>
  );
};

export default CodingMissionPlayer;
