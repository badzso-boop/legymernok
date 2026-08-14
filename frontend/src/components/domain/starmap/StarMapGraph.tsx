import React, { useMemo } from "react";
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
import StarSystemNode, { type StarSystemNodeData } from "./StarSystemNode";
import type { StarSystemWithProgressResponse } from "../../../types/starSystem";

const nodeTypes = { starSystem: StarSystemNode };

export interface StarMapGraphProps {
  systems: StarSystemWithProgressResponse[];
  /**
   * Interaktív (teljes Star Map oldal): pan/zoom/tap-navigáció engedélyezve.
   * Nem-interaktív (Dashboard előnézet-kártya): statikus, csak dekoratív.
   * Alapértelmezetten interaktív.
   */
  interactive?: boolean;
  /** Kompakt node-méret (csak ikon, felirat nélkül) — a Dashboard előnézethez. */
  compact?: boolean;
  height?: number | string;
}

/**
 * Determinisztikus, hash-alapú pozicionálás — ugyanaz az elv, mint a régi
 * StarMapCanvas-ban, csak react-flow node-koordinátaként felhasználva.
 * MVP-ben nincs valódi előfeltétel-gráf/él a rendszerek között (ld. terv
 * 8. szekció) — a node-ok egymástól függetlenek.
 */
function hashPosition(id: string): { x: number; y: number } {
  let hash = 0;
  for (let i = 0; i < id.length; i++) {
    hash = id.charCodeAt(i) + ((hash << 5) - hash);
  }
  const x = (Math.abs(hash % 1000) / 1000) * 900;
  const y = (Math.abs((hash >> 16) % 1000) / 1000) * 600;
  return { x, y };
}

/**
 * A Star Map csillag-vizualizációja, react-flow-alapon — ld. terv 5.3.
 * Ugyanez a komponens szolgálja ki a teljes, interaktív `/star-map` oldalt
 * ÉS a Dashboard kicsinyített előnézet-kártyáját is (terv 5.3/5. pont),
 * `interactive`/`compact` prop-okkal paraméterezve.
 */
export const StarMapGraph: React.FC<StarMapGraphProps> = ({
  systems,
  interactive = true,
  compact = false,
  height = "100%",
}) => {
  const navigate = useNavigate();

  const nodes = useMemo<Node<StarSystemNodeData, "starSystem">[]>(
    () =>
      systems.map((system) => {
        const position = hashPosition(system.id);
        return {
          id: system.id,
          type: "starSystem" as const,
          position,
          data: { name: system.name, status: system.status, compact },
          draggable: false,
          selectable: interactive,
          connectable: false,
        };
      }),
    [systems, compact, interactive],
  );

  const handleNodeClick: NodeMouseHandler = (_event, node) => {
    if (!interactive) return;
    navigate(`/star-systems/${node.id}`);
  };

  return (
    <Box sx={{ width: "100%", height, position: "relative" }} data-cy="star-map-graph">
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
        fitViewOptions={{ padding: compact ? 0.4 : 0.2 }}
      >
        <Background color="var(--color-border)" gap={32} />
        {interactive && <Controls showInteractive={false} />}
      </ReactFlow>
    </Box>
  );
};

export default StarMapGraph;
