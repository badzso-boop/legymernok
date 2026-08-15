import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Chip from "@mui/material/Chip";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import OpenInFullIcon from "@mui/icons-material/OpenInFull";
import { GlowCard } from "../../shared/GlowCard";
import { NeonButton } from "../../shared/NeonButton";
import { useLiveLogs } from "../../../hooks/useLiveLogs";

const SNIPPET_SIZE = 8;

const LEVEL_COLOR: Record<string, string> = {
  ERROR: "#f87171",
  WARN: "var(--color-accent-secondary)",
  INFO: "var(--color-accent-primary)",
  DEBUG: "var(--color-text-secondary)",
  TRACE: "var(--color-text-secondary)",
  UNKNOWN: "var(--color-text-secondary)",
};

/**
 * Kompakt, élő rendszernapló-előnézet az admin dashboard-on — ugyanaz a
 * `useLiveLogs` hook adja az adatot, mint a teljes `/admin/logs` oldalnak,
 * csak itt csak az utolsó néhány sor látszik + egy "Megnyitás" gomb a
 * teljes nézetre.
 */
export const AdminLogSnippetCard: React.FC = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { logs, connected } = useLiveLogs({ historyLimit: SNIPPET_SIZE, maxKept: SNIPPET_SIZE });

  const snippet = logs.slice(0, SNIPPET_SIZE);

  return (
    <GlowCard>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 1.5 }}>
        <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
          <Typography variant="overline" sx={{ color: "var(--color-text-secondary)" }}>
            {t("adminOverview.logSnippet.label")}
          </Typography>
          <Chip
            label={connected ? t("logsConnected") : t("logsDisconnected")}
            color={connected ? "success" : "error"}
            size="small"
            variant="outlined"
          />
        </Box>
        <NeonButton
          size="small"
          startIcon={<OpenInFullIcon fontSize="small" />}
          onClick={() => navigate("/admin/logs")}
          data-cy="admin-dashboard-open-logs"
        >
          {t("adminOverview.logSnippet.openFull")}
        </NeonButton>
      </Box>

      {snippet.length === 0 ? (
        <Typography variant="body2" sx={{ color: "var(--color-text-secondary)" }}>
          {t("logsNoData")}
        </Typography>
      ) : (
        <Box
          sx={{
            fontFamily: "monospace",
            fontSize: "0.8rem",
            display: "flex",
            flexDirection: "column",
            gap: 0.5,
            overflow: "hidden",
          }}
        >
          {snippet.map((log) => (
            <Box key={log.id} sx={{ display: "flex", gap: 1, whiteSpace: "nowrap" }}>
              <Box component="span" sx={{ color: "var(--color-text-secondary)", flexShrink: 0 }}>
                {log.timestamp.split(" ")[1]?.split(".")[0] ?? log.timestamp}
              </Box>
              <Box
                component="span"
                sx={{ color: LEVEL_COLOR[log.level] ?? LEVEL_COLOR.UNKNOWN, flexShrink: 0, fontWeight: 700 }}
              >
                {log.level}
              </Box>
              <Box
                component="span"
                sx={{ color: "var(--color-text-primary)", overflow: "hidden", textOverflow: "ellipsis" }}
              >
                {log.message}
              </Box>
            </Box>
          ))}
        </Box>
      )}
    </GlowCard>
  );
};

export default AdminLogSnippetCard;
