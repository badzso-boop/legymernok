import React from "react";
import Box, { type BoxProps } from "@mui/material/Box";
import { useThemeMode } from "../../theme/ThemeModeProvider";

export interface GlowCardProps extends BoxProps {
  /** Kiemelt (erősebb glow) állapot — pl. az aktuális/aktív elemnél a Star Mapen. */
  active?: boolean;
}

/**
 * A design system alap kártya-komponense. Space témán glassmorphism HUD-panel
 * (backdrop-blur + halványan izzó szegély), Dark/Light témán sima, emelt
 * felületű kártya (ld. terv 3.2/3.3).
 */
export const GlowCard: React.FC<GlowCardProps> = ({
  active = false,
  sx,
  children,
  ...rest
}) => {
  const { mode } = useThemeMode();
  const isImmersive = mode === "space";

  return (
    <Box
      {...rest}
      sx={{
        borderRadius: "var(--radius-lg)",
        border: "1px solid var(--color-border)",
        backgroundColor: isImmersive
          ? "var(--color-bg-glass)"
          : "var(--color-bg-elevated)",
        ...(isImmersive && { backdropFilter: "blur(16px)" }),
        boxShadow: active ? "var(--glow-md)" : "var(--glow-sm)",
        ...(active && { borderColor: "var(--color-border-glow)" }),
        transition: "box-shadow 200ms ease, border-color 200ms ease",
        padding: 2,
        ...sx,
      }}
    >
      {children}
    </Box>
  );
};

export default GlowCard;
