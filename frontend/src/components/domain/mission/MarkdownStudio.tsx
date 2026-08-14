import React, { useRef, useState } from "react";
import {
  Box,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  IconButton,
  TextField,
  Divider,
} from "@mui/material";
import {
  FormatBold,
  FormatItalic,
  FormatListBulleted,
  FormatListNumbered,
  FormatQuote,
  Code,
  Link as LinkIcon,
  Image as ImageIcon,
} from "@mui/icons-material";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { useTranslation } from "react-i18next";
import { useIsCompactViewport } from "../../../hooks/useIsCompactViewport";
import { GlowCard } from "../../shared/GlowCard";

type WrapAction = { prefix: string; suffix: string; placeholder: string };
type LinePrefixAction = { linePrefix: string };
type ToolbarAction = WrapAction | LinePrefixAction;

const HEADING_ACTIONS: Array<{ label: string; action: LinePrefixAction }> = [
  { label: "H1", action: { linePrefix: "# " } },
  { label: "H2", action: { linePrefix: "## " } },
  { label: "H3", action: { linePrefix: "### " } },
];

interface MarkdownStudioProps {
  value: string;
  onChange: (value: string) => void;
  /** Extra toolbar-gombok (pl. FILL_IN_BLANK "Blank hozzáadása" gombja) ugyanabba a sorba. */
  extraToolbarActions?: React.ReactNode;
  minRows?: number;
  placeholder?: string;
  "data-cy"?: string;
}

function isLinePrefix(a: ToolbarAction): a is LinePrefixAction {
  return "linePrefix" in a;
}

/**
 * Saját, kézzel írt markdown-szerkesztő toolbar + react-markdown preview —
 * nem kész editor-könyvtár (terv 4.2 indoklása: a FILL_IN_BLANK
 * "Blank hozzáadása" gombja projekt-specifikus, és a saját toolbar a
 * theme/tokens.ts-ből színez, nincs mit felülírni egy 3-témás rendszerben).
 */
export const MarkdownStudio: React.FC<MarkdownStudioProps> = ({
  value,
  onChange,
  extraToolbarActions,
  minRows = 10,
  placeholder,
  "data-cy": dataCy,
}) => {
  const { t } = useTranslation();
  const isCompact = useIsCompactViewport();
  const [mobileTab, setMobileTab] = useState<"edit" | "preview">("edit");
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);

  const applyAction = (action: ToolbarAction) => {
    const el = textareaRef.current;
    if (!el) return;
    const start = el.selectionStart ?? value.length;
    const end = el.selectionEnd ?? value.length;

    if (isLinePrefix(action)) {
      const lineStart = value.lastIndexOf("\n", start - 1) + 1;
      const next = value.slice(0, lineStart) + action.linePrefix + value.slice(lineStart);
      onChange(next);
      requestAnimationFrame(() => {
        el.focus();
        const pos = start + action.linePrefix.length;
        el.setSelectionRange(pos, pos);
      });
      return;
    }

    const selected = value.slice(start, end) || action.placeholder;
    const next = value.slice(0, start) + action.prefix + selected + action.suffix + value.slice(end);
    onChange(next);
    requestAnimationFrame(() => {
      el.focus();
      const selStart = start + action.prefix.length;
      const selEnd = selStart + selected.length;
      el.setSelectionRange(selStart, selEnd);
    });
  };

  const toolbar = (
    <Box sx={{ display: "flex", alignItems: "center", flexWrap: "wrap", gap: 0.5, mb: 1 }}>
      <ToggleButtonGroup size="small">
        {HEADING_ACTIONS.map(({ label, action }) => (
          <ToggleButton key={label} value={label} onClick={() => applyAction(action)}>
            {label}
          </ToggleButton>
        ))}
      </ToggleButtonGroup>

      <Divider orientation="vertical" flexItem sx={{ mx: 0.5 }} />

      <Tooltip title={t("markdownStudio.bold")}>
        <IconButton
          size="small"
          onClick={() => applyAction({ prefix: "**", suffix: "**", placeholder: t("markdownStudio.boldPlaceholder") })}
        >
          <FormatBold fontSize="small" />
        </IconButton>
      </Tooltip>
      <Tooltip title={t("markdownStudio.italic")}>
        <IconButton
          size="small"
          onClick={() => applyAction({ prefix: "_", suffix: "_", placeholder: t("markdownStudio.italicPlaceholder") })}
        >
          <FormatItalic fontSize="small" />
        </IconButton>
      </Tooltip>
      <Tooltip title={t("markdownStudio.bulletList")}>
        <IconButton size="small" onClick={() => applyAction({ linePrefix: "- " })}>
          <FormatListBulleted fontSize="small" />
        </IconButton>
      </Tooltip>
      <Tooltip title={t("markdownStudio.numberedList")}>
        <IconButton size="small" onClick={() => applyAction({ linePrefix: "1. " })}>
          <FormatListNumbered fontSize="small" />
        </IconButton>
      </Tooltip>
      <Tooltip title={t("markdownStudio.quote")}>
        <IconButton size="small" onClick={() => applyAction({ linePrefix: "> " })}>
          <FormatQuote fontSize="small" />
        </IconButton>
      </Tooltip>
      <Tooltip title={t("markdownStudio.codeBlock")}>
        <IconButton
          size="small"
          onClick={() =>
            applyAction({ prefix: "```\n", suffix: "\n```", placeholder: t("markdownStudio.codePlaceholder") })
          }
        >
          <Code fontSize="small" />
        </IconButton>
      </Tooltip>
      <Tooltip title={t("markdownStudio.link")}>
        <IconButton
          size="small"
          onClick={() => applyAction({ prefix: "[", suffix: "](https://)", placeholder: t("markdownStudio.linkPlaceholder") })}
        >
          <LinkIcon fontSize="small" />
        </IconButton>
      </Tooltip>
      <Tooltip title={t("markdownStudio.image")}>
        <IconButton
          size="small"
          onClick={() => applyAction({ prefix: "![", suffix: "](https://)", placeholder: t("markdownStudio.imagePlaceholder") })}
        >
          <ImageIcon fontSize="small" />
        </IconButton>
      </Tooltip>

      {extraToolbarActions && (
        <>
          <Divider orientation="vertical" flexItem sx={{ mx: 0.5 }} />
          {extraToolbarActions}
        </>
      )}
    </Box>
  );

  const editor = (
    <TextField
      inputRef={textareaRef}
      multiline
      fullWidth
      minRows={minRows}
      value={value}
      placeholder={placeholder}
      onChange={(e) => onChange(e.target.value)}
      inputProps={{ "data-cy": dataCy }}
      sx={{ "& .MuiInputBase-input": { fontFamily: "var(--font-mono, monospace)", fontSize: "0.9rem" } }}
    />
  );

  const preview = (
    <GlowCard sx={{ minHeight: 200, maxHeight: 480, overflow: "auto" }}>
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{value || t("markdownStudio.emptyPreview")}</ReactMarkdown>
    </GlowCard>
  );

  return (
    <Box>
      {toolbar}

      {isCompact ? (
        <>
          <ToggleButtonGroup
            exclusive
            size="small"
            value={mobileTab}
            onChange={(_, next) => next && setMobileTab(next)}
            sx={{ mb: 1 }}
          >
            <ToggleButton value="edit">{t("markdownStudio.editTab")}</ToggleButton>
            <ToggleButton value="preview">{t("markdownStudio.previewTab")}</ToggleButton>
          </ToggleButtonGroup>
          {mobileTab === "edit" ? editor : preview}
        </>
      ) : (
        <Box sx={{ display: "flex", gap: 2 }}>
          <Box sx={{ flex: 1 }}>{editor}</Box>
          <Box sx={{ flex: 1 }}>{preview}</Box>
        </Box>
      )}
    </Box>
  );
};

export default MarkdownStudio;
