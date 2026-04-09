import React from "react";
import {
  ReactFlow,
  Background,
  BackgroundVariant,
  Controls,
  MiniMap,
  ConnectionMode,
  type Node,
  type Edge,
  type OnNodesChange,
  type OnEdgesChange,
  type Connection,
  type NodeTypes,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import type { CircuitNodeData } from "../../../types/circuit";
import { CircuitNode } from "./CircuitNode";
import { BoardNode } from "./BoardNode";

const nodeTypes: NodeTypes = {
  circuitNode: CircuitNode as NodeTypes[string],
  boardNode:   BoardNode   as NodeTypes[string],
};

interface CircuitCanvasProps {
  nodes: Node<CircuitNodeData>[];
  edges: Edge[];
  onNodesChange: OnNodesChange<Node<CircuitNodeData>>;
  onEdgesChange: OnEdgesChange<Edge>;
  onConnect: (params: Connection) => void;
  onNodeClick: (e: React.MouseEvent, node: Node) => void;
  onDrop: (e: React.DragEvent<HTMLDivElement>) => void;
  onNodeContextMenu?: (e: React.MouseEvent, node: Node) => void;
  readonly?: boolean;
}

const CircuitCanvas: React.FC<CircuitCanvasProps> = ({
  nodes,
  edges,
  onNodesChange,
  onEdgesChange,
  onConnect,
  onNodeClick,
  onDrop,
  onNodeContextMenu,
  readonly = false,
}) => {
  return (
    <ReactFlow
      nodes={nodes}
      edges={edges}
      onNodesChange={onNodesChange}
      onEdgesChange={onEdgesChange}
      onConnect={onConnect}
      onNodeClick={onNodeClick}
      onNodeContextMenu={onNodeContextMenu}
      onDrop={onDrop}
      onDragOver={(e) => e.preventDefault()}
      nodeTypes={nodeTypes}
      connectionMode={ConnectionMode.Loose}
      nodesDraggable={!readonly}
      nodesConnectable={!readonly}
      elementsSelectable={!readonly}
      fitView
      data-cy="circuit-canvas"
    >
      <Background variant={BackgroundVariant.Dots} gap={16} size={1} />
      <Controls />
      <MiniMap />
    </ReactFlow>
  );
};

export default CircuitCanvas;
