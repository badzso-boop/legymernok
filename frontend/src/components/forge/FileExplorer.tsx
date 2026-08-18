import React, { useState } from "react";
import { Box, Typography, IconButton, Tooltip } from "@mui/material";
import { FileCode, FileText, Plus, Trash2, Pencil, Lock } from "lucide-react";
import { useTranslation } from "react-i18next";

function getFileIcon(fileName: string) {
  if (fileName.endsWith(".md")) return <FileText size={16} color="#007acc" />;
  if (fileName.endsWith(".js") || fileName.endsWith(".ts"))
    return <FileCode size={16} color="#f1e05a" />;
  if (fileName.endsWith(".py")) return <FileCode size={16} color="#3572A5" />;
  return <FileCode size={16} color="#ccc" />;
}

interface FileExplorerProps {
  fileNames: string[];
  activeFileName: string | null;
  onSelect: (fileName: string) => void;
  onCreate: (fileName: string) => void;
  onDelete: (fileName: string) => void;
  onRename: (oldName: string, newName: string) => void;
  /** Ezekhez a fájlnevekhez nincs átnevezés/törlés — a lejátszóban (kadét)
   * a küldetés készítője által megadott, írásvédett fájlokhoz (tesztek) használt. */
  readOnlyFileNames?: string[];
}

// Fájlböngésző oldalsáv fájl létrehozás/törlés/átnevezés lehetőséggel —
// megosztott a Mission Forge (ForgeEditor) és a CODING misszió-lejátszó
// (CodingMissionPlayer) között, hogy ne kelljen kétszer megírni.
export const FileExplorer: React.FC<FileExplorerProps> = ({
  fileNames,
  activeFileName,
  onSelect,
  onCreate,
  onDelete,
  onRename,
  readOnlyFileNames = [],
}) => {
  const { t } = useTranslation();
  const [renamingFile, setRenamingFile] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState("");

  const handleCreate = () => {
    const name = window.prompt(t("forge.newFilePrompt"));
    if (!name || !name.trim()) return;
    if (fileNames.includes(name.trim())) {
      window.alert(t("forge.fileAlreadyExists"));
      return;
    }
    onCreate(name.trim());
  };

  const handleDelete = (fileName: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (!window.confirm(t("forge.confirmDeleteFile", { fileName }))) return;
    onDelete(fileName);
  };

  const startRename = (fileName: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setRenamingFile(fileName);
    setRenameValue(fileName);
  };

  const commitRename = (oldName: string) => {
    // Enter unmounts the input, which fires a native blur too — guard
    // against handling the same rename commit twice.
    if (renamingFile !== oldName) return;
    const newName = renameValue.trim();
    setRenamingFile(null);
    if (!newName || newName === oldName) return;
    if (fileNames.includes(newName)) {
      window.alert(t("forge.fileAlreadyExists"));
      return;
    }
    onRename(oldName, newName);
  };

  return (
    <Box sx={{ display: "flex", flexDirection: "column", height: "100%" }}>
      <Box
        sx={{
          p: 1.5,
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          borderBottom: "1px solid #222",
        }}
      >
        <Typography
          variant="caption"
          sx={{ color: "#666", fontWeight: "bold" }}
        >
          {t("forge.explorerFiles").toUpperCase()}
        </Typography>
        <Tooltip title={t("forge.newFile")}>
          <IconButton size="small" onClick={handleCreate} data-cy="file-explorer-new">
            <Plus size={16} color="#aaa" />
          </IconButton>
        </Tooltip>
      </Box>
      <Box sx={{ py: 1, overflowY: "auto", flexGrow: 1 }}>
        {fileNames.map((name) => {
          const isReadOnly = readOnlyFileNames.includes(name);
          return (
          <Box
            key={name}
            onClick={() => onSelect(name)}
            data-cy="file-explorer-item"
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
              "&:hover .file-actions": { opacity: 1 },
              "&:hover": { bgcolor: "#1a1a1a" },
            }}
          >
            {getFileIcon(name)}
            {renamingFile === name ? (
              <input
                autoFocus
                value={renameValue}
                onChange={(e) => setRenameValue(e.target.value)}
                onClick={(e) => e.stopPropagation()}
                onBlur={() => commitRename(name)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") commitRename(name);
                  if (e.key === "Escape") setRenamingFile(null);
                }}
                style={{
                  background: "#000",
                  color: "#fff",
                  border: "1px solid #444",
                  fontFamily: "monospace",
                  fontSize: "0.85rem",
                  width: "100%",
                }}
              />
            ) : (
              <Typography
                sx={{
                  color: activeFileName === name ? "#fff" : "#aaa",
                  fontSize: "0.85rem",
                  fontFamily: "monospace",
                  flexGrow: 1,
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                }}
              >
                {name}
              </Typography>
            )}
            {isReadOnly ? (
              <Tooltip title={t("forge.readOnlyFileTooltip")}>
                <Box sx={{ display: "flex", px: 0.5 }} data-cy="file-explorer-readonly">
                  <Lock size={12} color="#666" />
                </Box>
              </Tooltip>
            ) : (
              <Box
                className="file-actions"
                sx={{ display: "flex", opacity: 0, transition: "opacity 0.15s" }}
              >
                <Tooltip title={t("forge.renameFile")}>
                  <IconButton
                    size="small"
                    onClick={(e) => startRename(name, e)}
                    data-cy="file-explorer-rename"
                  >
                    <Pencil size={12} color="#888" />
                  </IconButton>
                </Tooltip>
                <Tooltip title={t("forge.deleteFile")}>
                  <IconButton
                    size="small"
                    onClick={(e) => handleDelete(name, e)}
                    data-cy="file-explorer-delete"
                  >
                    <Trash2 size={12} color="#888" />
                  </IconButton>
                </Tooltip>
              </Box>
            )}
          </Box>
          );
        })}
      </Box>
    </Box>
  );
};

export default FileExplorer;
