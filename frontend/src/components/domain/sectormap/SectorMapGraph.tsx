import React, { useMemo, useState } from "react";
import Box from "@mui/material/Box";
import { useNavigate } from "react-router-dom";
import {
  ReactFlow,
  Background,
  Controls,
  type Node,
  type NodeMouseHandler,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import "../starmap/starmap.css";
import SectorNode, { type SectorNodeData } from "./SectorNode";
import { WarpTransition } from "../../shared/WarpTransition";
import type { SectorResponse } from "../../../types/sector";

const nodeTypes = { sector: SectorNode };

const UNASSIGNED_NODE_ID = "__unassigned__";

export interface SectorMapGraphProps {
  sectors: SectorResponse[];
  /** A sector nélküli (sectorId === null) Star System-ek száma. */
  unassignedCount: number;
  interactive?: boolean;
  height?: number | string;
}

/**
 * Determinisztikus, hash-alapú pozicionálás — ugyanaz a minta, mint a
 * StarMapGraph-ban, csak nagyobb szórással a nagyobb node-méret miatt.
 */
function hashPosition(id: string): { x: number; y: number } {
  let hash = 0;
  for (let i = 0; i < id.length; i++) {
    hash = id.charCodeAt(i) + ((hash << 5) - hash);
  }
  const x = (Math.abs(hash % 1000) / 1000) * 1000;
  const y = (Math.abs((hash >> 16) % 1000) / 1000) * 700;
  return { x, y };
}

/**
 * A Sector Map felső szintje (issue #38 / plans/sector_map_2026.md) — a
 * Szektorokat mutatja node-ként, react-flow-n. Egy node kiválasztása
 * "átwarpol" a hozzá tartozó Star System-ekre (`/star-map/:sectorId`).
 * Mindig van egy extra "Besorolatlan" node is a sector nélküli
 * rendszerekhez, ami a paraméter nélküli `/star-map`-re navigál.
 */
export const SectorMapGraph: React.FC<SectorMapGraphProps> = ({
  sectors,
  unassignedCount,
  interactive = true,
  height = "100%",
}) => {
  const navigate = useNavigate();
  const [warpTarget, setWarpTarget] = useState<string | null>(null);

  const nodes = useMemo<Node<SectorNodeData, "sector">[]>(() => {
    const sectorNodes: Node<SectorNodeData, "sector">[] = sectors.map((sector) => {
      const position = hashPosition(sector.id);
      return {
        id: sector.id,
        type: "sector" as const,
        position,
        data: { name: sector.name, starSystemCount: sector.starSystemCount },
        draggable: false,
        selectable: interactive,
        connectable: false,
      };
    });

    if (unassignedCount > 0) {
      const position = hashPosition(UNASSIGNED_NODE_ID);
      sectorNodes.push({
        id: UNASSIGNED_NODE_ID,
        type: "sector" as const,
        position,
        data: {
          name: "",
          starSystemCount: unassignedCount,
          unassigned: true,
        },
        draggable: false,
        selectable: interactive,
        connectable: false,
      });
    }

    return sectorNodes;
  }, [sectors, unassignedCount, interactive]);

  const handleNodeClick: NodeMouseHandler = (_event, node) => {
    if (!interactive) return;
    setWarpTarget(node.id === UNASSIGNED_NODE_ID ? "/star-map" : `/star-map/${node.id}`);
  };

  return (
    <Box sx={{ width: "100%", height, position: "relative" }} data-cy="sector-map-graph">
      <WarpTransition
        active={warpTarget !== null}
        onComplete={() => {
          if (warpTarget) navigate(warpTarget);
        }}
      />
      <ReactFlow
        nodes={nodes}
        edges={[]}
        nodeTypes={nodeTypes}
        onNodeClick={handleNodeClick}
        nodesDraggable={false}
        nodesConnectable={false}
        elementsSelectable={interactive}
        panOnDrag={interactive}
        panOnScroll={interactive}
        zoomOnScroll={interactive}
        zoomOnPinch={interactive}
        zoomOnDoubleClick={false}
        preventScrolling={interactive}
        proOptions={{ hideAttribution: true }}
        fitView
        fitViewOptions={{ padding: 0.3 }}
      >
        <Background color="var(--color-border)" gap={32} />
        {interactive && <Controls showInteractive={false} />}
      </ReactFlow>
    </Box>
  );
};

export default SectorMapGraph;
