import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";

export interface ProgressRingProps {
  /** 0-100 közötti érték. */
  value: number;
  size?: number;
  strokeWidth?: number;
  label?: string;
  color?: string;
}

/**
 * Kör alakú progress indikátor — pl. Star System/Group teljesítettség
 * megjelenítéséhez. A szín alapból a design system accent tokenjéből jön.
 */
export const ProgressRing: React.FC<ProgressRingProps> = ({
  value,
  size = 64,
  strokeWidth = 6,
  label,
  color = "var(--color-accent-primary)",
}) => {
  const clamped = Math.max(0, Math.min(100, value));
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (clamped / 100) * circumference;

  return (
    <Box
      sx={{
        position: "relative",
        width: size,
        height: size,
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
      }}
    >
      <svg width={size} height={size} style={{ transform: "rotate(-90deg)" }}>
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="var(--color-border)"
          strokeWidth={strokeWidth}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={color}
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          style={{ transition: "stroke-dashoffset 400ms ease" }}
        />
      </svg>
      <Box sx={{ position: "absolute", textAlign: "center" }}>
        <Typography variant="caption" sx={{ fontWeight: 700 }}>
          {label ?? `${Math.round(clamped)}%`}
        </Typography>
      </Box>
    </Box>
  );
};

export default ProgressRing;
