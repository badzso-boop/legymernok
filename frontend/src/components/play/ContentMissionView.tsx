import React, { useEffect, useState } from "react";
import { Box, Typography, CircularProgress } from "@mui/material";
import { useNavigate } from "react-router-dom";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { forgeApi } from "../../api/client";
import type { ContentPageResponse } from "../../types/mission";
import RetroButton from "../RetroButton";

interface ContentMissionViewProps {
  missionId: string;
  onComplete?: () => void;
  starSystemId?: string;
}

const ContentMissionView: React.FC<ContentMissionViewProps> = ({
  missionId,
  onComplete,
  starSystemId,
}) => {
  const navigate = useNavigate();

  const [missionName, setMissionName] = useState("");
  const [loadedContent, setLoadedContent] = useState("");
  const [currentPage, setCurrentPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    const fetchFirst = async () => {
      try {
        const resp: ContentPageResponse = await forgeApi.getContentPage(missionId, 0, 100);
        setMissionName(resp.missionName);
        setLoadedContent(resp.content);
        setHasMore(resp.hasNextPage);
        setCurrentPage(0);
      } catch {
        setLoadedContent("_A tartalom nem érhető el._");
      } finally {
        setLoading(false);
      }
    };
    fetchFirst();
  }, [missionId]);

  const handleLoadMore = async () => {
    setLoadingMore(true);
    try {
      const nextPage = currentPage + 1;
      const resp: ContentPageResponse = await forgeApi.getContentPage(missionId, nextPage, 100);
      setLoadedContent((prev) => prev + "\n" + resp.content);
      setHasMore(resp.hasNextPage);
      setCurrentPage(nextPage);
    } finally {
      setLoadingMore(false);
    }
  };

  const handleNext = () => {
    if (onComplete) {
      onComplete();
    } else if (starSystemId) {
      navigate(`/star-systems/${starSystemId}`);
    } else {
      navigate(-1);
    }
  };

  if (loading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 200 }}>
        <CircularProgress sx={{ color: "#0f0" }} />
      </Box>
    );
  }

  return (
    <Box
      sx={{
        display: "flex",
        flexDirection: "column",
        height: "100%",
        minHeight: 0,
      }}
    >
      {/* Fejléc */}
      <Box
        sx={{
          pb: 1,
          mb: 2,
          borderBottom: "1px dashed #333",
          flexShrink: 0,
        }}
      >
        <Typography
          sx={{
            fontFamily: '"VT323", monospace',
            fontSize: "1.4rem",
            color: "#ffb000",
            letterSpacing: 2,
          }}
        >
          {missionName.toUpperCase()}
        </Typography>
      </Box>

      {/* Tartalom terület */}
      <Box
        sx={{
          flex: 1,
          overflow: "auto",
          bgcolor: "#080808",
          border: "1px solid #222",
          borderRadius: 1,
          p: 2,
          mb: 2,
          color: "#ddd",
          "& h1, & h2, & h3": {
            color: "#ffb000",
            fontFamily: '"VT323", monospace',
            letterSpacing: 1,
          },
          "& h4, & h5, & h6": { color: "#aaa" },
          "& p": { lineHeight: 1.7, fontFamily: "monospace", fontSize: "0.9rem" },
          "& code": {
            bgcolor: "#1a1a1a",
            px: 0.5,
            borderRadius: 0.5,
            fontFamily: "monospace",
            color: "#0f0",
            fontSize: "0.85rem",
          },
          "& pre": {
            bgcolor: "#0a1a0a",
            border: "1px solid #1a3a1a",
            p: 1.5,
            borderRadius: 1,
            overflow: "auto",
          },
          "& blockquote": {
            borderLeft: "3px solid #ffb000",
            pl: 2,
            ml: 0,
            color: "#aaa",
          },
          "& a": { color: "#32cd32" },
          "& ul, & ol": { pl: 3 },
          "& li": { fontFamily: "monospace", fontSize: "0.9rem", mb: 0.5 },
          "& table": { borderCollapse: "collapse", width: "100%" },
          "& th, & td": {
            border: "1px solid #333",
            p: "6px 12px",
            fontFamily: "monospace",
            fontSize: "0.85rem",
          },
          "& th": { bgcolor: "#111", color: "#ffb000" },
          "& hr": { borderColor: "#333", my: 2 },
        }}
      >
        <ReactMarkdown remarkPlugins={[remarkGfm]}>{loadedContent}</ReactMarkdown>
      </Box>

      {/* Gombok */}
      <Box
        sx={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          flexShrink: 0,
          gap: 2,
        }}
      >
        <Box>
          {hasMore && (
            <RetroButton
              color="blue"
              labelKey="play.loadMore"
              onClick={handleLoadMore}
              disabled={loadingMore}
            />
          )}
        </Box>
        <RetroButton
          color="green"
          labelKey="play.next"
          onClick={handleNext}
        />
      </Box>
    </Box>
  );
};

export default ContentMissionView;
