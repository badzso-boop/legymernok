import React, { useEffect, useState, useCallback } from "react";
import { Box, Typography, Chip, Alert, CircularProgress } from "@mui/material";
import { Check as CheckIcon, Close as CloseIcon } from "@mui/icons-material";
import { useTranslation } from "react-i18next";
import { fillInBlankApi } from "../../api/client";
import RetroButton from "../RetroButton";
import { MissionPlayerActions } from "../shared/MissionPlayerShell";
import type {
  FillInBlankUserResponse,
  FillInBlankOptionResponse,
  FillInBlankResultResponse,
  LastAttemptResponse,
} from "../../types/fillinblank";

interface FillInBlankViewProps {
  missionId: string;
  onComplete: () => void;
}

// Fisher-Yates shuffle
function shuffle<T>(arr: T[]): T[] {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

// Sablon szétdarabolása szöveg + blank részekre
type TemplatePart = { type: "text"; value: string } | { type: "blank"; key: string };

function parseTemplateParts(template: string): TemplatePart[] {
  const parts: TemplatePart[] = [];
  const regex = /\[\[(\w+)\]\]/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  while ((match = regex.exec(template)) !== null) {
    if (match.index > lastIndex) {
      parts.push({ type: "text", value: template.slice(lastIndex, match.index) });
    }
    parts.push({ type: "blank", key: match[1] });
    lastIndex = match.index + match[0].length;
  }
  if (lastIndex < template.length) {
    parts.push({ type: "text", value: template.slice(lastIndex) });
  }
  return parts;
}

const FillInBlankView: React.FC<FillInBlankViewProps> = ({ missionId, onComplete }) => {
  const { t } = useTranslation();

  const [definition, setDefinition] = useState<FillInBlankUserResponse | null>(null);
  const [poolOptions, setPoolOptions] = useState<FillInBlankOptionResponse[]>([]);
  const [selectedSlots, setSelectedSlots] = useState<Record<string, string>>({});
  const [focusedBlank, setFocusedBlank] = useState<string | null>(null);
  const [result, setResult] = useState<FillInBlankResultResponse | null>(null);
  const [alreadyPassed, setAlreadyPassed] = useState<LastAttemptResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const initPool = useCallback((def: FillInBlankUserResponse) => {
    const all: FillInBlankOptionResponse[] = def.blanks.flatMap((b) => b.options);
    setPoolOptions(shuffle(all));
    setSelectedSlots({});
    setFocusedBlank(null);
    setResult(null);
  }, []);

  useEffect(() => {
    const load = async () => {
      try {
        const [def, lastAttempt] = await Promise.all([
          fillInBlankApi.getForUser(missionId),
          fillInBlankApi.getLastAttempt(missionId).catch(() => null as LastAttemptResponse | null),
        ]);
        if (lastAttempt?.passed) {
          setAlreadyPassed(lastAttempt);
        } else {
          setDefinition(def);
          initPool(def);
        }
      } catch {
        setDefinition(null);
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [missionId, initPool]);

  // Pool chip kattintás → focusedBlank-ba, vagy első üres slotba
  const handlePoolClick = (option: FillInBlankOptionResponse) => {
    if (!definition) return;
    const blankKeys = definition.blanks.map((b) => b.key);
    const targetKey =
      focusedBlank ??
      blankKeys.find((k) => !selectedSlots[k]) ??
      null;
    if (!targetKey) return;

    // Ha van már valami a célban, visszatoljuk a poolba
    const existing = selectedSlots[targetKey];
    if (existing) {
      const existingOption = definition.blanks
        .flatMap((b) => b.options)
        .find((o) => o.id === existing);
      if (existingOption) {
        setPoolOptions((prev) => [...prev, existingOption]);
      }
    }

    setSelectedSlots((prev) => ({ ...prev, [targetKey]: option.id }));
    setPoolOptions((prev) => prev.filter((o) => o.id !== option.id));
    setFocusedBlank(null);
  };

  // Slot kattintás: üres → focus; kitöltött → visszatol pool-ba
  const handleSlotClick = (blankKey: string) => {
    if (!selectedSlots[blankKey]) {
      setFocusedBlank(blankKey === focusedBlank ? null : blankKey);
      return;
    }
    const optionId = selectedSlots[blankKey];
    const option = definition?.blanks.flatMap((b) => b.options).find((o) => o.id === optionId);
    if (option) setPoolOptions((prev) => [...prev, option]);
    setSelectedSlots((prev) => {
      const next = { ...prev };
      delete next[blankKey];
      return next;
    });
    setFocusedBlank(blankKey);
  };

  const allFilled =
    definition !== null &&
    definition.blanks.every((b) => !!selectedSlots[b.key]);

  const handleSubmit = async () => {
    if (!allFilled || !definition) return;
    setSubmitting(true);
    try {
      const res = await fillInBlankApi.submit(missionId, { answers: selectedSlots });
      setResult(res);
    } finally {
      setSubmitting(false);
    }
  };

  const handleReset = () => {
    if (!definition) return;
    initPool(definition);
  };

  // ── Loading ──────────────────────────────────────
  if (loading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", minHeight: 200 }}>
        <CircularProgress sx={{ color: "#0f0" }} />
      </Box>
    );
  }

  // ── Már teljesítette ─────────────────────────────
  if (alreadyPassed) {
    return (
      <Box sx={{ display: "flex", flexDirection: "column", gap: 2, alignItems: "flex-start" }}>
        <Alert severity="success" sx={{ bgcolor: "#0a2a0a", color: "#0f0", border: "1px solid #0f0" }}>
          {t("play.alreadyPassed", { pct: alreadyPassed.percentage })}
        </Alert>
        <MissionPlayerActions>
          <RetroButton color="green" labelKey="play.next" onClick={onComplete} />
        </MissionPlayerActions>
      </Box>
    );
  }

  if (!definition) {
    return (
      <Box>
        <Typography sx={{ color: "#f00", fontFamily: "monospace" }}>A feladat nem érhető el.</Typography>
      </Box>
    );
  }

  const templateParts = parseTemplateParts(definition.templateText);

  // ── Eredmény képernyő ────────────────────────────
  if (result) {
    const detailMap = new Map(result.details.map((d) => [d.blankKey, d]));
    return (
      <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
        <Alert
          severity={result.passed ? "success" : "warning"}
          sx={{
            bgcolor: result.passed ? "#0a2a0a" : "#2a1a00",
            color: result.passed ? "#0f0" : "#ffb000",
            border: `1px solid ${result.passed ? "#0f0" : "#ffb000"}`,
            fontFamily: "monospace",
          }}
        >
          {result.passed
            ? `TELJESÍTVE — ${result.percentage}% (${result.score}/${result.maxScore} pont)`
            : `SIKERTELEN — ${result.percentage}% (${result.score}/${result.maxScore} pont)`}
        </Alert>

        {/* Sablon eredményekkel */}
        <Box
          sx={{
            bgcolor: "#080808",
            border: "1px solid #222",
            borderRadius: 1,
            p: 2,
            fontFamily: "monospace",
            fontSize: "1rem",
            lineHeight: 2,
            color: "#ccc",
            flexWrap: "wrap",
            display: "flex",
            alignItems: "center",
            gap: 0.5,
          }}
        >
          {templateParts.map((part, i) => {
            if (part.type === "text") {
              return (
                <span key={i} style={{ whiteSpace: "pre-wrap" }}>
                  {part.value}
                </span>
              );
            }
            const detail = detailMap.get(part.key);
            const optionText = definition.blanks
              .flatMap((b) => b.options)
              .find((o) => o.id === detail?.selectedOptionId)?.optionText ?? "—";
            const isCorrect = detail?.correct ?? false;
            return (
              <Chip
                key={i}
                label={optionText}
                size="small"
                icon={
                  isCorrect ? (
                    <CheckIcon sx={{ color: "#0f0 !important", fontSize: 14 }} />
                  ) : (
                    <CloseIcon sx={{ color: "#f00 !important", fontSize: 14 }} />
                  )
                }
                sx={{
                  bgcolor: isCorrect ? "#0a3d0a" : "#3d0a0a",
                  color: isCorrect ? "#0f0" : "#f66",
                  border: `1px solid ${isCorrect ? "#0f0" : "#f00"}`,
                  fontFamily: "monospace",
                  fontSize: "0.85rem",
                }}
              />
            );
          })}
        </Box>

        <MissionPlayerActions>
          {result.passed ? (
            <RetroButton color="green" labelKey="play.next" onClick={onComplete} />
          ) : (
            <RetroButton color="red" labelKey="play.tryAgain" onClick={handleReset} />
          )}
        </MissionPlayerActions>
      </Box>
    );
  }

  // ── Játék képernyő ───────────────────────────────
  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
      {/* Sablon bekezdés slot-okkal */}
      <Box
        sx={{
          bgcolor: "#080808",
          border: "1px solid #222",
          borderRadius: 1,
          p: 2,
          fontFamily: "monospace",
          fontSize: "1rem",
          lineHeight: 2.2,
          color: "#ccc",
          display: "flex",
          flexWrap: "wrap",
          alignItems: "center",
          gap: 0.5,
        }}
      >
        {templateParts.map((part, i) => {
          if (part.type === "text") {
            return (
              <span key={i} style={{ whiteSpace: "pre-wrap" }}>
                {part.value}
              </span>
            );
          }
          const filledId = selectedSlots[part.key];
          const filledOption = filledId
            ? definition.blanks.flatMap((b) => b.options).find((o) => o.id === filledId)
            : null;
          const isFocused = focusedBlank === part.key;
          return (
            <Chip
              key={i}
              label={filledOption ? filledOption.optionText : `[[${part.key}]]`}
              onClick={() => handleSlotClick(part.key)}
              size="small"
              sx={{
                bgcolor: filledOption ? "#1a2a1a" : isFocused ? "#1a1a2a" : "#111",
                color: filledOption ? "#0f0" : isFocused ? "#88aaff" : "#555",
                border: `1px dashed ${
                  filledOption ? "#0f0" : isFocused ? "#88aaff" : "#444"
                }`,
                fontFamily: "monospace",
                fontSize: "0.85rem",
                cursor: "pointer",
                minWidth: 80,
                "&:hover": {
                  borderColor: "#ffb000",
                  color: "#ffb000",
                },
              }}
            />
          );
        })}
      </Box>

      {/* Opció pool */}
      <Box>
        <Typography
          variant="caption"
          sx={{ color: "#555", fontFamily: "monospace", display: "block", mb: 1 }}
        >
          OPTION_POOL — kattints egy opcióra a kiválasztott slotba helyezéshez
        </Typography>
        <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
          {poolOptions.map((opt) => (
            <Chip
              key={opt.id}
              label={opt.optionText}
              onClick={() => handlePoolClick(opt)}
              size="small"
              sx={{
                bgcolor: "#111",
                color: "#ffb000",
                border: "1px solid #333",
                fontFamily: "monospace",
                fontSize: "0.85rem",
                cursor: "pointer",
                "&:hover": {
                  bgcolor: "#1a1500",
                  borderColor: "#ffb000",
                },
              }}
            />
          ))}
          {poolOptions.length === 0 && (
            <Typography sx={{ color: "#333", fontFamily: "monospace", fontSize: "0.8rem" }}>
              POOL_EMPTY — minden opció ki van osztva
            </Typography>
          )}
        </Box>
      </Box>

      {/* Submit */}
      <MissionPlayerActions>
        <RetroButton
          color="green"
          labelKey="play.submit"
          onClick={handleSubmit}
          disabled={!allFilled || submitting}
        />
        {!allFilled && (
          <Typography sx={{ color: "#555", fontFamily: "monospace", fontSize: "0.8rem" }}>
            ({Object.keys(selectedSlots).length}/{definition.blanks.length} slot kitöltve)
          </Typography>
        )}
      </MissionPlayerActions>
    </Box>
  );
};

export default FillInBlankView;
