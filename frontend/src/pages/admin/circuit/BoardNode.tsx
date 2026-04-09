import React, { useContext } from "react";
import { Handle, Position, type Node, type NodeProps } from "@xyflow/react";
import { Box, Tooltip, Typography } from "@mui/material";
import { Memory as BoardIcon } from "@mui/icons-material";
import type { BoardType, CircuitNodeData } from "../../../types/circuit";
import { PIN_TYPE_COLORS } from "../../../types/circuit";
import { CircuitContext, getPins } from "./CircuitContext";

// Visual dimensions per board type — must match the posXOffset/posYOffset values in DataInitializer
const BOARD_DIMENSIONS: Record<BoardType, { width: number; height: number }> = {
  ARDUINO_UNO:      { width: 220, height: 370 },
  ARDUINO_MEGA_2560: { width: 280, height: 1330 },
  ESP8266:          { width: 200, height: 240 },
  ESP32:            { width: 220, height: 280 },
};

const BOARD_LABELS: Record<BoardType, string> = {
  ARDUINO_UNO:      "Arduino UNO",
  ARDUINO_MEGA_2560: "Arduino MEGA 2560",
  ESP8266:          "ESP8266",
  ESP32:            "ESP32",
};

// Determine the xyflow edge-exit direction based on the pin's position on the board
function pinPosition(xOff: number, yOff: number, boardWidth: number): Position {
  if (yOff === 0) return Position.Top;
  if (xOff <= 0) return Position.Left;
  if (xOff >= boardWidth) return Position.Right;
  return Position.Bottom;
}

export const BoardNode: React.FC<NodeProps<Node<CircuitNodeData>>> = ({ data, selected }) => {
  const { pinCatalog, boardType } = useContext(CircuitContext);
  const pins = getPins(pinCatalog, "BOARD", boardType);
  const dims = BOARD_DIMENSIONS[boardType] ?? { width: 220, height: 370 };

  return (
    <Box
      data-cy="circuit-node-BOARD"
      sx={{
        position: "relative",
        width: dims.width,
        height: dims.height,
        border: selected ? "2px solid #90caf9" : "2px solid #2e7d32",
        borderRadius: 1,
        bgcolor: selected ? "rgba(144,202,249,0.08)" : "#0d2e0d",
        backgroundImage: "radial-gradient(circle, #1a3d1a 1px, transparent 1px)",
        backgroundSize: "12px 12px",
        cursor: "grab",
        overflow: "visible",
      }}
    >
      {/* Board label */}
      <Box
        sx={{
          position: "absolute",
          top: "50%",
          left: "50%",
          transform: "translate(-50%, -50%)",
          textAlign: "center",
          pointerEvents: "none",
          userSelect: "none",
        }}
      >
        <BoardIcon sx={{ color: "#81c784", fontSize: 28 }} />
        <Typography
          variant="caption"
          sx={{ display: "block", color: "#81c784", fontWeight: 700, fontSize: "0.7rem" }}
        >
          {BOARD_LABELS[boardType] ?? boardType}
        </Typography>
        <Typography
          variant="caption"
          sx={{ display: "block", color: "#4caf50", fontSize: "0.6rem" }}
        >
          {data.label}
        </Typography>
      </Box>

      {/* Pin handles */}
      {pins.map((pin) => {
        const pos = pinPosition(pin.posXOffset ?? 0, pin.posYOffset ?? 0, dims.width);
        const color = PIN_TYPE_COLORS[pin.pinType] ?? "#888";
        return (
          <Tooltip
            key={pin.pinName}
            title={`${pin.pinName} — ${pin.pinType.replace(/_/g, " ")}`}
            placement={pos === Position.Left ? "left" : pos === Position.Right ? "right" : "top"}
            arrow
          >
            {/* Wrapper div needed: Tooltip requires a single child that can receive ref */}
            <Box
              component="span"
              sx={{ position: "absolute", left: pin.posXOffset, top: pin.posYOffset }}
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
                  width: 10,
                  height: 10,
                  background: color,
                  border: "2px solid rgba(255,255,255,0.6)",
                  borderRadius: "50%",
                  cursor: "crosshair",
                  zIndex: 10,
                }}
              />
            </Box>
          </Tooltip>
        );
      })}

      {/* Pin name labels — shown next to each handle */}
      {pins.map((pin) => {
        const pos = pinPosition(pin.posXOffset ?? 0, pin.posYOffset ?? 0, dims.width);
        const isLeft = pos === Position.Left;
        const isRight = pos === Position.Right;
        if (!isLeft && !isRight) return null;
        return (
          <Typography
            key={`label-${pin.pinName}`}
            variant="caption"
            sx={{
              position: "absolute",
              left: isLeft ? 14 : "auto",
              right: isRight ? 14 : "auto",
              top: (pin.posYOffset ?? 0) - 6,
              fontSize: "0.55rem",
              color: PIN_TYPE_COLORS[pin.pinType] ?? "#888",
              pointerEvents: "none",
              userSelect: "none",
              fontFamily: "monospace",
              whiteSpace: "nowrap",
            }}
          >
            {pin.pinName}
          </Typography>
        );
      })}
    </Box>
  );
};

export default BoardNode;
