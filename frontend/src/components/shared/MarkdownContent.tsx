import React from "react";
import { Box } from "@mui/material";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";

interface MarkdownContentProps {
  children: string;
}

// Read-only markdown renderer — ugyanaz a stílus-készlet, mint a
// ContentMissionView-ban, de megosztva, hogy a Mission Forge/Player más
// helyein (pl. CODING misszió feladatleírása) is újrafelhasználható legyen
// dangerouslySetInnerHTML/rehype-raw nélkül (ld. frontend/CLAUDE.md XSS-megjegyzés).
export const MarkdownContent: React.FC<MarkdownContentProps> = ({ children }) => (
  <Box
    sx={{
      color: "var(--color-text-primary)",
      "& h1, & h2, & h3": {
        color: "var(--color-accent-primary)",
        fontWeight: 700,
      },
      "& p": { lineHeight: 1.7 },
      "& code": {
        bgcolor: "var(--color-bg-elevated)",
        px: 0.5,
        borderRadius: 0.5,
        fontFamily: "monospace",
        fontSize: "0.9em",
      },
      "& pre": {
        bgcolor: "var(--color-bg-elevated)",
        border: "1px solid var(--color-border)",
        p: 1.5,
        borderRadius: 1,
        overflow: "auto",
      },
      "& blockquote": {
        borderLeft: "3px solid var(--color-accent-primary)",
        pl: 2,
        ml: 0,
        color: "var(--color-text-secondary)",
      },
      "& a": { color: "var(--color-accent-primary)" },
      "& ul, & ol": { pl: 3 },
      "& table": { borderCollapse: "collapse", width: "100%" },
      "& th, & td": { border: "1px solid var(--color-border)", p: "6px 12px" },
      "& th": { bgcolor: "var(--color-bg-elevated)" },
      "& hr": { borderColor: "var(--color-border)", my: 2 },
    }}
  >
    <ReactMarkdown remarkPlugins={[remarkGfm]}>{children}</ReactMarkdown>
  </Box>
);

export default MarkdownContent;
