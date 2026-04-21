import React, { useState, useEffect } from "react";
import { Box, TextField, Typography, Alert } from "@mui/material";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { useMutation } from "@tanstack/react-query";
import apiClient from "../../api/client";
import RetroButton from "../RetroButton";

interface ContentEditorProps {
  missionId: string;
}

const ContentEditor: React.FC<ContentEditorProps> = ({ missionId }) => {
  const [content, setContent] = useState("");
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    apiClient.get<{ content?: string }>(`/missions/${missionId}`).then((res) => {
      setContent(res.data.content ?? "");
    });
  }, [missionId]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      await apiClient.patch(`/missions/${missionId}/content`, { content });
    },
    onSuccess: () => {
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    },
  });

  return (
    <Box sx={{ display: "flex", gap: 2, height: "500px" }}>
      <Box sx={{ flex: 1, display: "flex", flexDirection: "column", gap: 1 }}>
        <Typography
          variant="caption"
          sx={{ color: "#888", fontFamily: "monospace" }}
        >
          MARKDOWN_INPUT
        </Typography>
        <TextField
          multiline
          fullWidth
          value={content}
          onChange={(e) => setContent(e.target.value)}
          sx={{
            flex: 1,
            "& .MuiInputBase-root": {
              height: "100%",
              alignItems: "flex-start",
              bgcolor: "#0a0a0a",
              color: "#e0e0e0",
              fontFamily: "monospace",
              fontSize: "0.85rem",
            },
            "& .MuiOutlinedInput-notchedOutline": {
              borderColor: "#333",
            },
          }}
          inputProps={{ style: { overflow: "auto", resize: "none" } }}
        />
      </Box>

      <Box
        sx={{
          flex: 1,
          display: "flex",
          flexDirection: "column",
          gap: 1,
          overflow: "hidden",
        }}
      >
        <Typography
          variant="caption"
          sx={{ color: "#888", fontFamily: "monospace" }}
        >
          PREVIEW
        </Typography>
        <Box
          sx={{
            flex: 1,
            overflow: "auto",
            border: "1px solid #333",
            borderRadius: 1,
            p: 2,
            bgcolor: "#0a0a0a",
            color: "#e0e0e0",
            "& h1, & h2, & h3": { color: "#ffb000" },
            "& code": {
              bgcolor: "#1a1a1a",
              px: 0.5,
              borderRadius: 0.5,
              fontFamily: "monospace",
            },
            "& pre": { bgcolor: "#1a1a1a", p: 1, borderRadius: 1 },
            "& a": { color: "#32cd32" },
          }}
        >
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
        </Box>
      </Box>

      <Box sx={{ display: "flex", flexDirection: "column", gap: 1, justifyContent: "flex-end" }}>
        {saved && (
          <Alert severity="success" sx={{ py: 0 }}>
            Mentve
          </Alert>
        )}
        {saveMutation.isError && (
          <Alert severity="error" sx={{ py: 0 }}>
            Hiba a mentéskor
          </Alert>
        )}
        <RetroButton
          color="green"
          labelKey="forge.save"
          onClick={() => saveMutation.mutate()}
          disabled={saveMutation.isPending}
        />
      </Box>
    </Box>
  );
};

export default ContentEditor;
