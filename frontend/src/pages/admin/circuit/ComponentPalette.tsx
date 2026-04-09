import React from "react";
import { Box, Typography, Divider } from "@mui/material";
import { useTranslation } from "react-i18next";
import type { BoardType, ComponentType } from "../../../types/circuit";

interface ComponentPaletteProps {
  boardType: BoardType;
  disabled: boolean;
}

const PALETTE_GROUPS: { label: string; items: ComponentType[] }[] = [
  { label: "Board", items: ["BOARD"] },
  { label: "Power", items: ["VCC_5V", "VCC_3V3", "GND"] },
  { label: "Input", items: ["PUSHBUTTON", "POTENTIOMETER", "DHT11", "HC_SR04"] },
  { label: "Output", items: ["LED", "RESISTOR", "CAPACITOR", "SERVO"] },
];

const ComponentPalette: React.FC<ComponentPaletteProps> = ({ disabled }) => {
  const { t } = useTranslation();

  const handleDragStart = (
    e: React.DragEvent<HTMLDivElement>,
    componentType: ComponentType
  ) => {
    e.dataTransfer.setData("application/circuitNodeType", componentType);
    e.dataTransfer.effectAllowed = "copy";
  };

  return (
    <Box
      sx={{
        width: 220,
        flexShrink: 0,
        borderRight: "1px solid",
        borderColor: "divider",
        overflowY: "auto",
        p: 1.5,
        bgcolor: "background.paper",
      }}
    >
      <Typography variant="overline" sx={{ fontWeight: 700, color: "text.secondary" }}>
        {t("circuit.paletteTitle")}
      </Typography>

      {PALETTE_GROUPS.map((group) => (
        <Box key={group.label} sx={{ mt: 1.5 }}>
          <Typography variant="caption" sx={{ color: "text.disabled", textTransform: "uppercase" }}>
            {group.label}
          </Typography>
          <Divider sx={{ my: 0.5 }} />
          {group.items.map((ct) => (
            <Box
              key={ct}
              data-cy={`palette-item-${ct}`}
              draggable={!disabled}
              onDragStart={(e) => handleDragStart(e, ct)}
              sx={{
                px: 1,
                py: 0.75,
                my: 0.25,
                borderRadius: 1,
                border: "1px solid",
                borderColor: "divider",
                cursor: disabled ? "not-allowed" : "grab",
                opacity: disabled ? 0.5 : 1,
                bgcolor: "action.hover",
                fontSize: "0.78rem",
                fontFamily: "monospace",
                userSelect: "none",
                "&:hover": disabled ? {} : { bgcolor: "action.selected" },
              }}
            >
              {ct}
            </Box>
          ))}
        </Box>
      ))}
    </Box>
  );
};

export default ComponentPalette;
