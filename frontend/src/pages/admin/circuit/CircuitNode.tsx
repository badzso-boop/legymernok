import React, { useContext } from "react";
import { Handle, Position, type Node, type NodeProps } from "@xyflow/react";
import { Box, Tooltip, Typography } from "@mui/material";
import {
  Lightbulb as LedIcon,
  Cable as ResistorIcon,
  OfflineBolt as CapacitorIcon,
  TouchApp as ButtonIcon,
  Tune as PotIcon,
  Thermostat as DhtIcon,
  SensorsOff as UltrasonicIcon,
  ElectricMoped as ServoIcon,
  PowerInput as VccIcon,
  Power as GndIcon,
} from "@mui/icons-material";
import type { CircuitNodeData, ComponentType } from "../../../types/circuit";
import { PIN_TYPE_COLORS } from "../../../types/circuit";
import { CircuitContext, getPins } from "./CircuitContext";

const COMPONENT_ICONS: Partial<Record<ComponentType, React.ReactNode>> = {
  LED:           <LedIcon fontSize="small" />,
  RESISTOR:      <ResistorIcon fontSize="small" />,
  CAPACITOR:     <CapacitorIcon fontSize="small" />,
  PUSHBUTTON:    <ButtonIcon fontSize="small" />,
  POTENTIOMETER: <PotIcon fontSize="small" />,
  DHT11:         <DhtIcon fontSize="small" />,
  HC_SR04:       <UltrasonicIcon fontSize="small" />,
  SERVO:         <ServoIcon fontSize="small" />,
  VCC_5V:        <VccIcon fontSize="small" />,
  VCC_3V3:       <VccIcon fontSize="small" />,
  GND:           <GndIcon fontSize="small" />,
};

const COMPONENT_COLORS: Partial<Record<ComponentType, string>> = {
  LED:           "#f9a825",
  VCC_5V:        "#c62828",
  VCC_3V3:       "#e64a19",
  GND:           "#37474f",
};

export const CircuitNode: React.FC<NodeProps<Node<CircuitNodeData>>> = ({ data, selected }) => {
  const { pinCatalog } = useContext(CircuitContext);
  const nodeData = data as CircuitNodeData;
  const pins = getPins(pinCatalog, nodeData.componentType);
  const color = COMPONENT_COLORS[nodeData.componentType] ?? "#2e7d32";

  // Compute node dimensions from pin positions
  const maxX = pins.length > 0 ? Math.max(...pins.map((p) => p.posXOffset ?? 0)) : 60;
  const maxY = pins.length > 0 ? Math.max(...pins.map((p) => p.posYOffset ?? 0)) : 30;
  const nodeWidth = Math.max(maxX + 20, 80);
  const nodeHeight = Math.max(maxY + 20, 50);

  return (
    <Tooltip title={`${nodeData.componentType} — ${nodeData.label}`} placement="top">
      <Box
        data-cy={`circuit-node-${nodeData.componentType}`}
        sx={{
          position: "relative",
          width: nodeWidth,
          height: nodeHeight,
          border: selected ? "2px solid #90caf9" : `2px solid ${color}`,
          borderRadius: 1,
          bgcolor: selected ? "rgba(144,202,249,0.12)" : "rgba(0,0,0,0.75)",
          cursor: "grab",
          backdropFilter: "blur(4px)",
          transition: "border-color 0.15s",
          overflow: "visible",
        }}
      >
        {/* Icon + label — centered */}
        <Box
          sx={{
            position: "absolute",
            top: "50%",
            left: "50%",
            transform: "translate(-50%, -50%)",
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            pointerEvents: "none",
            userSelect: "none",
          }}
        >
          <Box sx={{ color }}>{COMPONENT_ICONS[nodeData.componentType]}</Box>
          <Typography
            variant="caption"
            sx={{ color: "text.secondary", fontSize: "0.6rem", fontFamily: "monospace" }}
          >
            {nodeData.label}
          </Typography>
        </Box>

        {/* Pin handles */}
        {pins.map((pin) => {
          const xOff = pin.posXOffset ?? 0;
          const yOff = pin.posYOffset ?? 0;
          const pos = xOff <= 0 ? Position.Left : xOff >= nodeWidth ? Position.Right
            : yOff <= 0 ? Position.Top : Position.Bottom;
          const pinColor = PIN_TYPE_COLORS[pin.pinType] ?? "#888";
          return (
            <Tooltip
              key={pin.pinName}
              title={`${pin.pinName}`}
              placement={pos === Position.Left ? "left" : pos === Position.Right ? "right" : "top"}
              arrow
            >
              <Box
                component="span"
                sx={{ position: "absolute", left: xOff, top: yOff }}
              >
                <Handle
                  type="source"
                  position={pos}
                  id={pin.pinName}
                  style={{
                    position: "relative",
                    left: "unset",
                    top: "unset",
                    transform: "none",
                    width: 8,
                    height: 8,
                    background: pinColor,
                    border: "2px solid rgba(255,255,255,0.5)",
                    borderRadius: "50%",
                    cursor: "crosshair",
                    zIndex: 10,
                  }}
                />
              </Box>
            </Tooltip>
          );
        })}

        {/* Fallback handles when no pins loaded yet */}
        {pins.length === 0 && (
          <>
            <Handle type="source" position={Position.Top}    style={{ background: "#888" }} />
            <Handle type="source" position={Position.Bottom} style={{ background: "#888" }} />
          </>
        )}
      </Box>
    </Tooltip>
  );
};

export default CircuitNode;
