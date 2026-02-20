import React, { useState, useEffect, useRef, useMemo } from "react";
import Editor from "@monaco-editor/react";
import {
  Box,
  Button,
  CircularProgress,
  Alert,
  Snackbar,
  Tabs,
  Tab,
  Typography,
  Chip,
  Paper,
} from "@mui/material";
import { Save as SaveIcon } from "@mui/icons-material";
import { useTranslation } from "react-i18next";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { forgeApi } from "../../api/client";
import type {
  MissionForgeResponse,
  MissionForgeContentRequest,
  VerificationStatus,
} from "../../types/mission-forge";

interface ForgeEditorProps {
  mission: MissionForgeResponse;
  currentFileContents: Record<string, string>;
  setCurrentFileContents: React.Dispatch<
    React.SetStateAction<Record<string, string>>
  >;
  activeFileName: string | null;
  setActiveFileName: React.Dispatch<React.SetStateAction<string | null>>;
}

const ForgeEditor: React.FC<ForgeEditorProps> = ({
  mission,
  currentFileContents,
  setCurrentFileContents,
  activeFileName,
  setActiveFileName,
}) => {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const editorRef = useRef<any>(null);

  const [snackbarOpen, setSnackbarOpen] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState("");
  const [snackbarSeverity, setSnackbarSeverity] = useState<"success" | "error">(
    "success",
  );

  // --- Segédfüggvény a nyelv meghatározásához ---
  const getLanguageFromFileName = (fileName: string | null) => {
    if (!fileName) return "javascript";
    if (fileName.endsWith(".md")) return "markdown";
    if (fileName.endsWith(".py")) return "python";
    if (fileName.endsWith(".js") || fileName.endsWith(".ts"))
      return "javascript";
    if (fileName.endsWith(".json")) return "json";
    return "text";
  };

  // --- Adatok lekérése a Giteából ---
  const {
    data: fetchedFileContents,
    isLoading: isLoadingFiles,
    error: filesError,
  } = useQuery<Record<string, string>, Error>({
    queryKey: ["missionFiles", mission.id],
    queryFn: () => forgeApi.getMissionFiles(mission.id),
    enabled: !!mission.id,
  });

  // --- Szinkronizáció: Ha megjöttek a fájlok, töltsük be őket a helyi state-be ---
  useEffect(() => {
    if (fetchedFileContents && Object.keys(currentFileContents).length === 0) {
      setCurrentFileContents(fetchedFileContents);

      // Ha még nincs aktív fájl, legyen az első a listából
      const files = Object.keys(fetchedFileContents);
      if (files.length > 0 && !activeFileName) {
        setActiveFileName(files[0]);
      }
    }
  }, [
    fetchedFileContents,
    currentFileContents,
    setCurrentFileContents,
    activeFileName,
    setActiveFileName,
  ]);

  // Dinamikus fájllista a letöltött adatok alapján
  const fileNames = useMemo(() => {
    return fetchedFileContents ? Object.keys(fetchedFileContents) : [];
  }, [fetchedFileContents]);

  // --- Mentés mutáció ---
  const saveFilesMutation = useMutation<
    MissionForgeResponse,
    Error,
    MissionForgeContentRequest
  >({
    mutationFn: (data: MissionForgeContentRequest) =>
      forgeApi.saveMissionFiles(mission.id, data),
    onSuccess: () => {
      setSnackbarMessage(t("forge.filesSavedSuccess"));
      setSnackbarSeverity("success");
      setSnackbarOpen(true);
      // Frissítjük a misszió adatait (pl. státusz változás miatt)
      queryClient.invalidateQueries({ queryKey: ["mission", mission.id] });
    },
    onError: (error: any) => {
      const errMsg = error.response?.data?.message || error.message;
      setSnackbarMessage(`${t("forge.errorSavingMission")}: ${errMsg}`);
      setSnackbarSeverity("error");
      setSnackbarOpen(true);
    },
  });

  // --- Kezelők ---
  const handleEditorChange = (value: string | undefined) => {
    if (activeFileName && value !== undefined) {
      setCurrentFileContents((prev) => ({
        ...prev,
        [activeFileName]: value,
      }));
    }
  };

  const handleSave = () => {
    saveFilesMutation.mutate({
      missionId: mission.id,
      files: currentFileContents,
    });
  };

  const handleTabChange = (_: React.SyntheticEvent, newValue: string) => {
    setActiveFileName(newValue);
  };

  const getStatusChipColor = (status: VerificationStatus) => {
    switch (status) {
      case "DRAFT":
        return "default";
      case "PENDING":
        return "warning";
      case "APPROVED":
        return "success";
      case "REJECTED":
        return "error";
      default:
        return "primary";
    }
  };

  // --- Renderelési állapotok ---
  if (isLoadingFiles) {
    return (
      <Box
        sx={{
          display: "flex",
          flexDirection: "column",
          justifyContent: "center",
          alignItems: "center",
          height: "100%",
          gap: 2,
        }}
      >
        <CircularProgress />
        <Typography>{t("forge.loadingFiles")}</Typography>
      </Box>
    );
  }

  if (filesError) {
    return (
      <Alert severity="error">
        {t("forge.errorFetchingFiles")}: {filesError.message}
      </Alert>
    );
  }

  return (
    <Paper
      elevation={3}
      sx={{
        display: "flex",
        flexDirection: "column",
        height: "calc(100vh - 200px)", // Dinamikus magasság
        bgcolor: "#1e1e1e",
        color: "#fff",
        overflow: "hidden",
        borderRadius: 2,
        border: "1px solid #333",
      }}
    >
      {/* Eszköztár (Toolbar) */}
      <Box
        sx={{
          p: 2,
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          borderBottom: "1px solid #333",
        }}
      >
        <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
          <Typography variant="h6" sx={{ color: "#fff" }}>
            {mission.name}
          </Typography>
          <Chip
            label={t(
              `forge.status.${mission.verificationStatus.toLowerCase()}`,
            )}
            color={getStatusChipColor(mission.verificationStatus) as any}
            size="small"
          />
        </Box>

        <Button
          variant="contained"
          color="primary"
          startIcon={
            saveFilesMutation.isPending ? (
              <CircularProgress size={20} color="inherit" />
            ) : (
              <SaveIcon />
            )
          }
          onClick={handleSave}
          disabled={saveFilesMutation.isPending}
        >
          {t("forge.saveButton")}
        </Button>
      </Box>

      {/* Fájl választó lapok */}
      <Box sx={{ bgcolor: "#252526" }}>
        <Tabs
          value={activeFileName}
          onChange={handleTabChange}
          variant="scrollable"
          scrollButtons="auto"
          sx={{
            minHeight: 40,
            "& .MuiTab-root": {
              color: "#969696",
              textTransform: "none",
              minHeight: 40,
              px: 2,
              "&.Mui-selected": { color: "#fff", bgcolor: "#1e1e1e" },
            },
            "& .MuiTabs-indicator": { backgroundColor: "#007acc" },
          }}
        >
          {fileNames.map((name) => (
            <Tab key={name} value={name} label={name} />
          ))}
        </Tabs>
      </Box>

      {/* Monaco Editor terület */}
      <Box sx={{ flexGrow: 1, position: "relative" }}>
        {activeFileName ? (
          <Editor
            height="100%"
            theme="vs-dark"
            language={getLanguageFromFileName(activeFileName)}
            value={currentFileContents[activeFileName] || ""}
            onChange={handleEditorChange}
            onMount={(editor) => {
              editorRef.current = editor;
            }}
            options={{
              minimap: { enabled: false },
              fontSize: 14,
              wordWrap: "on",
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
            <Typography color="textSecondary">
              {t("forge.selectFileToEdit")}
            </Typography>
          </Box>
        )}
      </Box>

      {/* Visszajelzés */}
      <Snackbar
        open={snackbarOpen}
        autoHideDuration={4000}
        onClose={() => setSnackbarOpen(false)}
      >
        <Alert severity={snackbarSeverity} variant="filled">
          {snackbarMessage}
        </Alert>
      </Snackbar>
    </Paper>
  );
};

export default ForgeEditor;
