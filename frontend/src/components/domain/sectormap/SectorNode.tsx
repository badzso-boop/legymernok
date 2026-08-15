import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { Handle, Position, type Node, type NodeProps } from "@xyflow/react";
import { useTranslation } from "react-i18next";
import { usePrefersReducedMotion } from "../../../hooks/usePrefersReducedMotion";

export interface SectorNodeData extends Record<string, unknown> {
  name: string;
  starSystemCount: number;
  /** Igaz a mindig-jelenlévő, nem-valódi "Besorolatlan" node-nál. */
  unassigned?: boolean;
}

/**
 * Egy Szektor node a felső szintű Sector Map-en — a StarSystemNode kis,
 * csillag-szerű pöttyeivel szemben ez szándékosan NAGYOBB, "galaxis-szerű"
 * ködös korong, hogy vizuálisan is egyértelmű legyen a hierarchia-szint
 * különbsége. Ld. plans/sector_map_2026.md.
 */
type SectorFlowNode = Node<SectorNodeData, "sector">;

const SectorNode: React.FC<NodeProps<SectorFlowNode>> = ({ data }) => {
  const { t } = useTranslation();
  const prefersReducedMotion = usePrefersReducedMotion();
  const { name, starSystemCount, unassigned } = data;
  const displayName = unassigned ? t("sectorMap.unassigned") : name;

  const size = 72;
  const color = unassigned ? "var(--color-text-secondary)" : "var(--color-accent-primary)";

  return (
    <>
      <Handle type="target" position={Position.Top} style={{ opacity: 0 }} />
      <Box
        sx={{
          width: size + 40,
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          gap: 1,
          cursor: "pointer",
        }}
        data-cy="sector-node"
      >
        <Box
          sx={{
            width: size,
            height: size,
            borderRadius: "50%",
            background: `radial-gradient(circle at 35% 35%, ${color}, transparent 70%)`,
            backgroundColor: unassigned ? "transparent" : "var(--color-bg-elevated)",
            border: `2px solid ${color}`,
            boxShadow: unassigned
              ? "none"
              : `0 0 16px 4px ${color}66, 0 0 32px 8px ${color}33`,
            opacity: unassigned ? 0.5 : 1,
            animation:
              !unassigned && !prefersReducedMotion
                ? "starmap-node-pulse 3.6s ease-in-out infinite"
                : "none",
          }}
        />
        <Typography
          variant="body2"
          sx={{
            color: "var(--color-text-primary)",
            fontWeight: 600,
            textAlign: "center",
            lineHeight: 1.2,
          }}
        >
          {displayName}
        </Typography>
        <Typography variant="caption" sx={{ color: "var(--color-text-secondary)" }}>
          {starSystemCount}
        </Typography>
      </Box>
      <Handle type="source" position={Position.Bottom} style={{ opacity: 0 }} />
    </>
  );
};

export default SectorNode;
