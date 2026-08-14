import React, { useMemo } from "react";
import Box from "@mui/material/Box";
import { useThemeMode } from "../../theme/ThemeModeProvider";
import { usePrefersReducedMotion } from "../../hooks/usePrefersReducedMotion";
import { useIsCompactViewport } from "../../hooks/useIsCompactViewport";

export interface NebulaLayerProps {
  intensity?: "hero" | "ambient";
}

interface Blob {
  id: number;
  leftPct: number;
  topPct: number;
  sizeVw: number;
  color: string;
  durationS: number;
}

/**
 * Nagy, elmosott, lassan mozgó radiális gradiens-foltok a háttérben — a
 * "drága" mélységérzetet adja a lapos, tiszta fekete helyett (ld. terv 3.2,
 * 2. pont). Kizárólag Space témán renderel bármit.
 */
export const NebulaLayer: React.FC<NebulaLayerProps> = ({
  intensity = "ambient",
}) => {
  const { mode } = useThemeMode();
  const prefersReducedMotion = usePrefersReducedMotion();
  const isCompact = useIsCompactViewport();

  const blobs: Blob[] = useMemo(() => {
    const count = intensity === "hero" ? 3 : 2;
    const palette = ["var(--color-accent-primary)", "var(--color-accent-secondary)", "#6d28d9"];
    return Array.from({ length: count }, (_, id) => ({
      id,
      leftPct: 15 + Math.random() * 70,
      topPct: 10 + Math.random() * 70,
      sizeVw: isCompact ? 35 + Math.random() * 15 : 45 + Math.random() * 20,
      color: palette[id % palette.length],
      durationS: 50 + Math.random() * 30,
    }));
  }, [intensity, isCompact]);

  if (mode !== "space") return null;

  return (
    <Box
      aria-hidden
      sx={{
        position: "absolute",
        inset: 0,
        overflow: "hidden",
        pointerEvents: "none",
        zIndex: 0,
      }}
    >
      {blobs.map((blob) => (
        <Box
          key={blob.id}
          sx={{
            position: "absolute",
            left: `${blob.leftPct}%`,
            top: `${blob.topPct}%`,
            width: `${blob.sizeVw}vw`,
            height: `${blob.sizeVw}vw`,
            borderRadius: "50%",
            background: `radial-gradient(circle, ${blob.color}33 0%, transparent 70%)`,
            filter: "blur(60px)",
            ...(!prefersReducedMotion && {
              animation: `nebula-drift ${blob.durationS}s ease-in-out infinite alternate`,
            }),
          }}
        />
      ))}
    </Box>
  );
};

export default NebulaLayer;
