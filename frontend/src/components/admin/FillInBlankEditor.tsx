import React, { useState, useCallback, useEffect } from "react";
import {
  Box,
  Typography,
  TextField,
  Checkbox,
  FormControlLabel,
  IconButton,
  Alert,
  Divider,
  Button,
} from "@mui/material";
import { X as XIcon } from "lucide-react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fillInBlankApi } from "../../api/client";
import RetroButton from "../RetroButton";
import MarkdownStudio from "../domain/mission/MarkdownStudio";
import type { FillInBlankBlankAdmin } from "../../types/fillinblank";

const MAX_BLANKS = 5;
const MAX_OPTIONS = 5;

interface OptionState {
  text: string;
  correct: boolean;
}

interface BlankState {
  key: string;
  options: OptionState[];
}

interface FillInBlankEditorProps {
  missionId: string;
}

function parseTemplate(template: string): string[] {
  const keys: string[] = [];
  const seen = new Set<string>();
  let match: RegExpExecArray | null;
  const regex = /\[\[(\w+)\]\]/g;
  while ((match = regex.exec(template)) !== null) {
    if (!seen.has(match[1])) {
      seen.add(match[1]);
      keys.push(match[1]);
    }
  }
  return keys;
}

const FillInBlankEditor: React.FC<FillInBlankEditorProps> = ({ missionId }) => {
  const { t } = useTranslation();
  const [templateText, setTemplateText] = useState("");
  const [blanks, setBlanks] = useState<BlankState[]>([]);
  const [passThreshold, setPassThreshold] = useState<number | null>(null);
  const [definitionExists, setDefinitionExists] = useState(false);
  const [saved, setSaved] = useState(false);
  const [pendingOptionText, setPendingOptionText] = useState<Record<string, string>>({});

  const { data: loadedData, isError: loadError } = useQuery({
    queryKey: ["fib-admin", missionId],
    queryFn: () => fillInBlankApi.getForAdmin(missionId),
    retry: false,
  });

  useEffect(() => {
    if (!loadedData) return;
    setTemplateText((loadedData as any).templateText);
    setPassThreshold((loadedData as any).passThreshold);
    setDefinitionExists(true);
    setBlanks(
      (loadedData as any).blanks.map((b: any) => ({
        key: b.key,
        options: b.options.map((o: any) => ({ text: o.optionText, correct: o.correct })),
      })),
    );
  }, [loadedData]);

  useEffect(() => {
    if (loadError) setDefinitionExists(false);
  }, [loadError]);

  const syncBlanksFromTemplate = useCallback((template: string) => {
    const keys = parseTemplate(template);
    setBlanks((prev) => {
      const existingMap = new Map(prev.map((b) => [b.key, b]));
      return keys.map((key) => existingMap.get(key) ?? { key, options: [] });
    });
  }, []);

  const handleTemplateChange = (value: string) => {
    setTemplateText(value);
    syncBlanksFromTemplate(value);
  };

  const handleAddBlank = () => {
    const keys = parseTemplate(templateText);
    if (keys.length >= MAX_BLANKS) return;
    const newKey = `blank_${keys.length + 1}`;
    const separator = templateText.length > 0 ? " " : "";
    setTemplateText((prev) => prev + separator + `[[${newKey}]]`);
    setBlanks((prev) => [...prev, { key: newKey, options: [] }]);
  };

  const handleOptionCommit = (blankKey: string, text: string) => {
    const trimmed = text.trim();
    if (!trimmed) return;
    setBlanks((prev) =>
      prev.map((b) => {
        if (b.key !== blankKey) return b;
        if (b.options.length >= MAX_OPTIONS) return b;
        return { ...b, options: [...b.options, { text: trimmed, correct: false }] };
      }),
    );
    setPendingOptionText((prev) => ({ ...prev, [blankKey]: "" }));
  };

  const handleOptionCorrectToggle = (blankKey: string, index: number) => {
    setBlanks((prev) =>
      prev.map((b) => {
        if (b.key !== blankKey) return b;
        return {
          ...b,
          options: b.options.map((o, i) => (i === index ? { ...o, correct: !o.correct } : o)),
        };
      }),
    );
  };

  const handleOptionDelete = (blankKey: string, index: number) => {
    setBlanks((prev) =>
      prev.map((b) => {
        if (b.key !== blankKey) return b;
        return { ...b, options: b.options.filter((_, i) => i !== index) };
      }),
    );
  };

  const buildPayload = () => ({
    templateText,
    passThreshold,
    blanks: blanks.map(
      (b, bi): FillInBlankBlankAdmin => ({
        key: b.key,
        orderIndex: bi,
        options: b.options.map((o, oi) => ({ optionText: o.text, correct: o.correct, orderIndex: oi })),
      }),
    ),
  });

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload = buildPayload();
      return definitionExists
        ? fillInBlankApi.update(missionId, payload)
        : fillInBlankApi.save(missionId, payload);
    },
    onSuccess: () => {
      setDefinitionExists(true);
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    },
  });

  const detectedKeys = parseTemplate(templateText);

  return (
    <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
      <Box>
        <Typography variant="caption" sx={{ color: "#888", fontFamily: "monospace", mb: 1, display: "block" }}>
          TEMPLATE_TEXT — szintaxis: [[blank_1]]
        </Typography>
        <MarkdownStudio
          value={templateText}
          onChange={handleTemplateChange}
          minRows={4}
          placeholder="Pl.: A víz forráspontja [[blank_1]] Celsius-fok."
          data-cy="fib-template-text"
          extraToolbarActions={
            <Button
              size="small"
              variant="outlined"
              onClick={handleAddBlank}
              disabled={detectedKeys.length >= MAX_BLANKS}
              data-cy="fib-add-blank"
            >
              {t("fillInBlank.addBlank")}
            </Button>
          }
        />
      </Box>

      <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
        <Typography sx={{ color: "#888", fontFamily: "monospace", fontSize: "0.85rem" }}>
          PASS_THRESHOLD (%):
        </Typography>
        <TextField
          type="number"
          size="small"
          value={passThreshold ?? ""}
          onChange={(e) => setPassThreshold(e.target.value === "" ? null : Number(e.target.value))}
          inputProps={{ min: 0, max: 100 }}
          sx={{
            width: 100,
            "& .MuiInputBase-root": { bgcolor: "#0a0a0a", color: "#e0e0e0" },
            "& .MuiOutlinedInput-notchedOutline": { borderColor: "#333" },
          }}
          placeholder="null"
        />
        <Typography sx={{ color: "#555", fontFamily: "monospace", fontSize: "0.75rem" }}>
          (üres = mindig teljesít)
        </Typography>
      </Box>

      {blanks.map((blank) => (
        <Box key={blank.key} sx={{ border: "1px solid #333", borderRadius: 1, p: 2, bgcolor: "#080808" }}>
          <Typography sx={{ fontFamily: "monospace", color: "#ffb000", fontSize: "0.9rem", mb: 1.5 }}>
            [[{blank.key}]]
          </Typography>
          {blank.options.map((opt, idx) => (
            <Box key={idx} sx={{ display: "flex", alignItems: "center", gap: 1, mb: 0.5 }}>
              <Typography
                sx={{
                  flex: 1, color: "#ccc", fontFamily: "monospace", fontSize: "0.85rem",
                  bgcolor: "#111", px: 1, py: 0.5, borderRadius: 0.5, border: "1px solid #222",
                }}
              >
                {opt.text}
              </Typography>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={opt.correct}
                    onChange={() => handleOptionCorrectToggle(blank.key, idx)}
                    size="small"
                    sx={{ color: "#32cd32", "&.Mui-checked": { color: "#32cd32" } }}
                  />
                }
                label={<Typography sx={{ color: "#888", fontSize: "0.75rem" }}>helyes</Typography>}
                sx={{ m: 0 }}
              />
              <IconButton size="small" onClick={() => handleOptionDelete(blank.key, idx)} sx={{ color: "#ff4444" }}>
                <XIcon size={14} />
              </IconButton>
            </Box>
          ))}
          {blank.options.length < MAX_OPTIONS && (
            <TextField
              size="small"
              placeholder="Új opció (Enter vagy fókuszvesztés menti)..."
              value={pendingOptionText[blank.key] ?? ""}
              onChange={(e) => setPendingOptionText((prev) => ({ ...prev, [blank.key]: e.target.value }))}
              onBlur={(e) => handleOptionCommit(blank.key, e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") handleOptionCommit(blank.key, (e.target as HTMLInputElement).value);
              }}
              fullWidth
              inputProps={{ "data-cy": `blank-option-input-${blank.key}` }}
              sx={{
                mt: 1,
                "& .MuiInputBase-root": { bgcolor: "#111", color: "#ccc", fontFamily: "monospace" },
                "& .MuiOutlinedInput-notchedOutline": { borderColor: "#222" },
              }}
            />
          )}
          {blank.options.length >= MAX_OPTIONS && (
            <Typography sx={{ color: "#555", fontFamily: "monospace", fontSize: "0.75rem", mt: 1 }}>
              MAX_OPTIONS_REACHED ({MAX_OPTIONS})
            </Typography>
          )}
        </Box>
      ))}

      {detectedKeys.length === 0 && templateText.length > 0 && (
        <Alert severity="warning">Nincs [[blank_N]] szintaxis a sablonban.</Alert>
      )}

      <Divider sx={{ borderColor: "#222" }} />

      <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
        {saved && <Alert severity="success" sx={{ py: 0 }}>Mentve</Alert>}
        {saveMutation.isError && <Alert severity="error" sx={{ py: 0 }}>Hiba a mentéskor</Alert>}
        <RetroButton
          color="green"
          labelKey="forge.save"
          data-cy="fib-save"
          onClick={() => saveMutation.mutate()}
          disabled={saveMutation.isPending || templateText.trim() === ""}
        />
      </Box>
    </Box>
  );
};

export default FillInBlankEditor;
