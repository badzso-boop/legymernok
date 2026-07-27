import React, { useEffect, useState } from "react";
import Editor from "@monaco-editor/react";
import { Box, Typography, Alert } from "@mui/material";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { forgeApi } from "../../api/client";
import FileExplorer from "../forge/FileExplorer";
import RetroButton from "../RetroButton";

interface MissionFileEditorProps {
  missionId: string;
}

// Admin/tulajdonos ezen keresztül állítja be előre egy CODING misszió
// kezdő fájlstruktúráját — mielőtt egy kadét elindítaná a missziót, az
// itt látott fájlok kerülnek átmásolásra a kadét saját munkarepójába.
const MissionFileEditor: React.FC<MissionFileEditorProps> = ({
  missionId,
}) => {
  const { t } = useTranslation();
  const [files, setFiles] = useState<Record<string, string>>({});
  const [activeFileName, setActiveFileName] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const { data: fetchedFiles, isLoading } = useQuery({
    queryKey: ["missionTemplateFiles", missionId],
    queryFn: () => forgeApi.getMissionFiles(missionId),
  });

  useEffect(() => {
    if (fetchedFiles) {
      setFiles(fetchedFiles);
      const names = Object.keys(fetchedFiles);
      if (names.length > 0 && !activeFileName) setActiveFileName(names[0]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fetchedFiles]);

  const saveMutation = useMutation({
    mutationFn: () =>
      forgeApi.saveMissionFiles(missionId, { missionId, files }),
    onSuccess: () => {
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    },
  });

  const createFileMutation = useMutation({
    mutationFn: (path: string) => forgeApi.createFile(missionId, path),
    onSuccess: (_data, path) => {
      setFiles((prev) => ({ ...prev, [path]: "" }));
      setActiveFileName(path);
    },
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
  });

  if (isLoading) return null;

  const fileNames = Object.keys(files);

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
      <Alert severity="info">{t("forge.fileEditorTemplateNote")}</Alert>
      {saved && <Alert severity="success">{t("forge.fileEditorSaved")}</Alert>}

      <Box sx={{ display: "flex", height: "500px", border: "1px solid #333" }}>
        <Box sx={{ width: "220px", flexShrink: 0, bgcolor: "#111" }}>
          <FileExplorer
            fileNames={fileNames}
            activeFileName={activeFileName}
            onSelect={setActiveFileName}
            onCreate={(path) => createFileMutation.mutate(path)}
            onDelete={(path) => deleteFileMutation.mutate(path)}
            onRename={(oldPath, newPath) =>
              renameFileMutation.mutate({ oldPath, newPath })
            }
          />
        </Box>
        <Box sx={{ flexGrow: 1, minWidth: 0 }}>
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
              options={{ minimap: { enabled: false }, fontSize: 14 }}
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
              <Typography sx={{ color: "#555" }}>
                {fileNames.length === 0
                  ? t("forge.fileEditorNoFiles")
                  : t("forge.fileEditorSelectFile")}
              </Typography>
            </Box>
          )}
        </Box>
      </Box>

      <Box>
        <RetroButton
          color="green"
          labelKey="forge.saveButton"
          onClick={() => saveMutation.mutate()}
          disabled={saveMutation.isPending}
          active={saveMutation.isPending}
        />
      </Box>
    </Box>
  );
};

export default MissionFileEditor;
