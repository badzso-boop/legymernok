import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import { Handle, Position, type Node, type NodeProps } from "@xyflow/react";
import { useTranslation } from "react-i18next";
import { GlowCard } from "../../shared/GlowCard";
import { usePrefersReducedMotion } from "../../../hooks/usePrefersReducedMotion";
import type { StarSystemProgressStatus } from "../../../types/starSystem";

export interface StarSystemNodeData extends Record<string, unknown> {
  name: string;
  status: StarSystemProgressStatus;
  compact?: boolean;
}

/**
 * Egy Star System csillag-node a react-flow gráfban — ld. terv 5.3.
 * Állapot-alapú megjelenés: szürke/tompított (NOT_STARTED), pulzáló glow
 * (IN_PROGRESS), zöld pipa-jelvény (COMPLETED). Nincs bemenő/kimenő él —
 * a Handle-ök csak azért kellenek, mert a react-flow node-típus megköveteli
 * őket, de ebben a körben nincs előfeltétel-gráf (ld. terv 8. szekció).
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

  return (
    <>
      <Handle type="target" position={Position.Top} style={{ opacity: 0 }} />
      <GlowCard
        active={status === "IN_PROGRESS"}
        title={statusLabel}
        sx={{
          width: compact ? 64 : 132,
          height: compact ? 64 : 96,
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          gap: compact ? 0 : 0.5,
          padding: compact ? 0.5 : 1.5,
          textAlign: "center",
          cursor: "pointer",
          opacity: status === "NOT_STARTED" ? 0.55 : 1,
          animation:
            status === "IN_PROGRESS" && !prefersReducedMotion
              ? "starmap-node-pulse 2.4s ease-in-out infinite"
              : "none",
          position: "relative",
        }}
      >
        {status === "COMPLETED" && (
          <CheckCircleIcon
            sx={{
              position: "absolute",
              top: compact ? 2 : 6,
              right: compact ? 2 : 6,
              fontSize: compact ? 14 : 18,
              color: "var(--color-success)",
            }}
          />
        )}
        <Box
          sx={{
            width: compact ? 10 : 14,
            height: compact ? 10 : 14,
            borderRadius: "50%",
            backgroundColor:
              status === "NOT_STARTED"
                ? "var(--color-text-secondary)"
                : "var(--color-accent-primary)",
          }}
        />
        {!compact && (
          <Typography
            variant="caption"
            sx={{
              color: "var(--color-text-primary)",
              fontWeight: 600,
              lineHeight: 1.2,
              overflow: "hidden",
              textOverflow: "ellipsis",
              display: "-webkit-box",
              WebkitLineClamp: 2,
              WebkitBoxOrient: "vertical",
            }}
          >
            {name}
          </Typography>
        )}
      </GlowCard>
      <Handle type="source" position={Position.Bottom} style={{ opacity: 0 }} />
    </>
  );
};

export default StarSystemNode;
