import React, { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { Box, Typography, InputBase, CircularProgress } from "@mui/material";
import CloseIcon from "@mui/icons-material/Close";
import type { StarSystemSearchResult } from "../../types/starSystem";
import { searchApi } from "../../api/client";

interface SearchPanelProps {
  onClose: () => void;
}

/**
 * Csillagrendszer-kereső — a design system tokenjeivel, a Star Map
 * ambient hátterén jelenik meg (a szülő gondoskodik a StarfieldBackground/
 * NebulaLayer rétegekről, ez csak egy áttetsző, glassmorphism panel felül).
 */
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
        bgcolor: "var(--color-bg-glass)",
        backdropFilter: "blur(16px)",
        display: "flex",
        flexDirection: "column",
        p: 3,
        zIndex: 10,
        borderRadius: "var(--radius-lg)",
        border: "1px solid var(--color-border-glow)",
      }}
      data-cy="star-map-search-panel"
    >
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 2 }}>
        <Typography sx={{ color: "var(--color-text-primary)", fontWeight: 600 }}>
          {t("search.title")}
        </Typography>
        <CloseIcon
          onClick={onClose}
          sx={{
            color: "var(--color-text-secondary)",
            cursor: "pointer",
            "&:hover": { color: "var(--color-accent-primary)" },
          }}
        />
      </Box>

      <InputBase
        inputRef={inputRef}
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder={t("search.placeholder")}
        fullWidth
        sx={{
          mb: 2,
          px: 1.5,
          py: 1,
          borderRadius: "var(--radius-md)",
          border: "1px solid var(--color-border)",
          color: "var(--color-text-primary)",
          bgcolor: "var(--color-bg-elevated)",
        }}
      />

      <Box sx={{ flex: 1, overflowY: "auto" }}>
        {loading && (
          <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
            <CircularProgress size={16} />
            <Typography variant="body2" sx={{ color: "var(--color-text-secondary)" }}>
              {t("search.scanning")}
            </Typography>
          </Box>
        )}

        {!loading && searched && results.length === 0 && (
          <Typography variant="body2" sx={{ color: "var(--color-text-secondary)" }}>
            {t("search.noResults")}
          </Typography>
        )}

        {results.map((r) => (
          <Box
            key={r.id}
            onClick={() => handleSelect(r.id)}
            sx={{
              p: 1.5,
              mb: 1,
              borderRadius: "var(--radius-md)",
              border: "1px solid var(--color-border)",
              cursor: "pointer",
              transition: "border-color 150ms ease, box-shadow 150ms ease",
              "&:hover": {
                borderColor: "var(--color-border-glow)",
                boxShadow: "var(--glow-sm)",
              },
            }}
          >
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <Typography sx={{ color: "var(--color-text-primary)", fontWeight: 600 }}>
                {r.name}
              </Typography>
              <Typography variant="caption" sx={{ color: "var(--color-accent-primary)" }}>
                {(r.similarity * 100).toFixed(1)}%
              </Typography>
            </Box>
            {r.description && (
              <Typography
                variant="body2"
                sx={{ color: "var(--color-text-secondary)", mt: 0.5 }}
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
