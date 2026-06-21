import React, { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Box, Typography } from "@mui/material";
import type { StarSystemSearchResult } from "../../types/starSystem";
import { searchApi } from "../../api/client";

interface SearchPanelProps {
  onClose: () => void;
}

const SearchPanel: React.FC<SearchPanelProps> = ({ onClose }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<StarSystemSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  useEffect(() => {
    if (!query.trim()) {
      setResults([]);
      setSearched(false);
      return;
    }
    const timer = setTimeout(async () => {
      setLoading(true);
      try {
        const data = await searchApi.searchStarSystems(query);
        setResults(data);
        setSearched(true);
      } catch {
        setResults([]);
        setSearched(true);
      } finally {
        setLoading(false);
      }
    }, 400);
    return () => clearTimeout(timer);
  }, [query]);

  const handleSelect = (id: string) => {
    navigate(`/star-systems/${id}`);
    onClose();
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") onClose();
  };

  return (
    <Box
      sx={{
        position: "absolute",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        bgcolor: "rgba(0,8,0,0.97)",
        display: "flex",
        flexDirection: "column",
        p: 3,
        zIndex: 10,
        borderRadius: "5px",
        fontFamily: '"Share Tech Mono", monospace',
      }}
    >
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
        <Typography
          sx={{
            color: "#0f0",
            fontFamily: "inherit",
            letterSpacing: 3,
            fontSize: "14px",
            textShadow: "0 0 8px #0f0",
          }}
        >
          &gt; {t("search.title")}
        </Typography>
        <Typography
          onClick={onClose}
          sx={{
            color: "#555",
            fontFamily: "inherit",
            fontSize: "12px",
            cursor: "pointer",
            "&:hover": { color: "#0f0" },
          }}
        >
          [ESC]
        </Typography>
      </Box>

      <Box sx={{ position: "relative", mb: 2 }}>
        <Typography
          component="span"
          sx={{ color: "#0f0", fontFamily: "inherit", fontSize: "16px", mr: 1 }}
        >
          &gt;
        </Typography>
        <input
          ref={inputRef}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={t("search.placeholder")}
          style={{
            background: "transparent",
            border: "none",
            borderBottom: "1px solid #0f0",
            color: "#0f0",
            fontFamily: '"Share Tech Mono", monospace',
            fontSize: "16px",
            padding: "4px 8px",
            outline: "none",
            width: "calc(100% - 32px)",
          }}
        />
      </Box>

      <Box sx={{ flex: 1, overflowY: "auto" }}>
        {loading && (
          <Typography sx={{ color: "#3a7a3a", fontFamily: "inherit", fontSize: "13px" }}>
            {t("search.scanning")}
          </Typography>
        )}

        {!loading && searched && results.length === 0 && (
          <Typography sx={{ color: "#555", fontFamily: "inherit", fontSize: "13px" }}>
            {t("search.noResults")}
          </Typography>
        )}

        {results.map((r) => (
          <Box
            key={r.id}
            onClick={() => handleSelect(r.id)}
            sx={{
              p: "10px 12px",
              mb: 1,
              border: "1px solid #1a3a1a",
              cursor: "pointer",
              transition: "all 0.15s",
              "&:hover": {
                borderColor: "#0f0",
                bgcolor: "rgba(0,255,0,0.04)",
                pl: "16px",
              },
            }}
          >
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <Typography
                sx={{
                  color: "#0f0",
                  fontFamily: "inherit",
                  fontSize: "14px",
                  textShadow: "0 0 4px #0f0",
                }}
              >
                {r.name}
              </Typography>
              <Typography sx={{ color: "#3a7a3a", fontFamily: "inherit", fontSize: "11px" }}>
                {(r.similarity * 100).toFixed(1)}%
              </Typography>
            </Box>
            {r.description && (
              <Typography
                sx={{ color: "#444", fontFamily: "inherit", fontSize: "11px", mt: 0.5 }}
              >
                {r.description.length > 90
                  ? r.description.slice(0, 90) + "..."
                  : r.description}
              </Typography>
            )}
          </Box>
        ))}
      </Box>
    </Box>
  );
};

export default SearchPanel;
