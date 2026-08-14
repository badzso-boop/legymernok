import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import { Handle, Position, type Node, type NodeProps } from "@xyflow/react";
import { useTranslation } from "react-i18next";
import { usePrefersReducedMotion } from "../../../hooks/usePrefersReducedMotion";
import type { StarSystemProgressStatus } from "../../../types/starSystem";

export interface StarSystemNodeData extends Record<string, unknown> {
  name: string;
  status: StarSystemProgressStatus;
  compact?: boolean;
}

/**
 * Egy Star System csillag-node a react-flow gráfban — ld. terv 5.3.
 *
 * Szándékosan KICSI, kör alakú, izzó "csillag" (nem doboz/kártya) — így sok
 * rendszer is elfér egymás mellett anélkül, hogy átfednének. Állapot-alapú
 * megjelenés: szürke/tompított (NOT_STARTED), pulzáló cián glow
 * (IN_PROGRESS), zöld pipa-jelvény (COMPLETED). A kattintható/tapintható
 * terület (`hitAreaSize`) nagyobb a látható csillagnál, hogy mobilon is
 * kényelmes touch target maradjon a vizuális méret csökkentése mellett is.
 *
 * Nincs bemenő/kimenő él — a Handle-ök csak azért kellenek, mert a react-flow
 * node-típus megköveteli őket, de ebben a körben nincs előfeltétel-gráf
 * (ld. terv 8. szekció).
 */
type StarSystemFlowNode = Node<StarSystemNodeData, "starSystem">;

const StarSystemNode: React.FC<NodeProps<StarSystemFlowNode>> = ({ data }) => {
  const { t } = useTranslation();
  const prefersReducedMotion = usePrefersReducedMotion();
  const { name, status, compact } = data;

  const statusLabel =
    status === "COMPLETED"
      ? t("starMap.statusCompleted")
      : status === "IN_PROGRESS"
        ? t("starMap.statusInProgress")
        : t("starMap.statusNotStarted");

  const starSize = compact ? 10 : 16;
  const hitAreaSize = compact ? 28 : 44;
  const glowColor =
    status === "NOT_STARTED" ? "var(--color-text-secondary)" : "var(--color-accent-primary)";

  return (
    <>
      <Handle type="target" position={Position.Top} style={{ opacity: 0 }} />
      <Box
        title={statusLabel}
        sx={{
          width: hitAreaSize,
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          gap: 0.5,
          cursor: "pointer",
          position: "relative",
        }}
      >
        <Box
          sx={{
            width: starSize,
            height: starSize,
            borderRadius: "50%",
            backgroundColor: glowColor,
            boxShadow:
              status === "NOT_STARTED"
                ? "none"
                : `0 0 6px 2px ${glowColor}, 0 0 14px 4px ${glowColor}`,
            opacity: status === "NOT_STARTED" ? 0.5 : 1,
            animation:
              status === "IN_PROGRESS" && !prefersReducedMotion
                ? "starmap-node-pulse 2.4s ease-in-out infinite"
                : "none",
          }}
        />
        {status === "COMPLETED" && (
          <CheckCircleIcon
            sx={{
              position: "absolute",
              top: -4,
              right: hitAreaSize / 2 - starSize,
              fontSize: compact ? 10 : 12,
              color: "var(--color-success)",
              backgroundColor: "var(--color-bg-base)",
              borderRadius: "50%",
            }}
          />
        )}
        {!compact && (
          <Typography
            variant="caption"
            sx={{
              color: "var(--color-text-primary)",
              fontSize: "0.65rem",
              lineHeight: 1.1,
              textAlign: "center",
              overflow: "hidden",
              textOverflow: "ellipsis",
              display: "-webkit-box",
              WebkitLineClamp: 2,
              WebkitBoxOrient: "vertical",
              maxWidth: hitAreaSize + 20,
            }}
          >
            {name}
          </Typography>
        )}
      </Box>
      <Handle type="source" position={Position.Bottom} style={{ opacity: 0 }} />
    </>
  );
};

export default StarSystemNode;
