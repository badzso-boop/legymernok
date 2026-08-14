import React from "react";
import Box from "@mui/material/Box";
import { motion } from "framer-motion";
import { usePrefersReducedMotion } from "../../../hooks/usePrefersReducedMotion";

export interface RobotMascotProps {
  size?: number;
}

/**
 * Egyszerű, geometrikus SVG placeholder a "barátságos robot" narrátorhoz
 * (ld. `new_direction_2026.md`, terv 5.1) — TUDATOS placeholder, nem kész
 * illusztrátori asset. `framer-motion`-nal animálva (lebegés + integetés),
 * `prefers-reduced-motion`-nál statikus. Később cserélhető egy végleges
 * illusztrációra a layout/animációs logika módosítása nélkül.
 */
export const RobotMascot: React.FC<RobotMascotProps> = ({ size = 96 }) => {
  const prefersReducedMotion = usePrefersReducedMotion();

  return (
    <Box
      component={motion.div}
      animate={prefersReducedMotion ? undefined : { y: [0, -10, 0] }}
      transition={{ duration: 3, repeat: Infinity, ease: "easeInOut" }}
      sx={{ display: "inline-block", filter: "drop-shadow(var(--glow-md))" }}
    >
      <svg width={size} height={size} viewBox="0 0 96 96" fill="none" aria-hidden>
        {/* Fej */}
        <rect x="20" y="16" width="56" height="44" rx="14" fill="var(--color-bg-elevated)" stroke="var(--color-accent-primary)" strokeWidth="3" />
        {/* Antenna */}
        <line x1="48" y1="16" x2="48" y2="4" stroke="var(--color-accent-secondary)" strokeWidth="3" strokeLinecap="round" />
        <circle cx="48" cy="4" r="4" fill="var(--color-accent-secondary)" />
        {/* Szemek */}
        <motion.circle
          cx="36" cy="38" r="6" fill="var(--color-accent-primary)"
          animate={prefersReducedMotion ? undefined : { opacity: [1, 0.3, 1] }}
          transition={{ duration: 2.4, repeat: Infinity, ease: "easeInOut" }}
        />
        <motion.circle
          cx="60" cy="38" r="6" fill="var(--color-accent-primary)"
          animate={prefersReducedMotion ? undefined : { opacity: [1, 0.3, 1] }}
          transition={{ duration: 2.4, repeat: Infinity, ease: "easeInOut" }}
        />
        {/* Test */}
        <rect x="28" y="60" width="40" height="28" rx="10" fill="var(--color-bg-glass)" stroke="var(--color-border-glow)" strokeWidth="2" />
        {/* Integető kar (jobb) */}
        <motion.line
          x1="68" y1="70" x2="88" y2="58"
          stroke="var(--color-accent-secondary)"
          strokeWidth="4"
          strokeLinecap="round"
          style={{ transformOrigin: "68px 70px" }}
          animate={prefersReducedMotion ? undefined : { rotate: [0, 18, 0, 18, 0] }}
          transition={{ duration: 1.6, repeat: Infinity, repeatDelay: 1.4, ease: "easeInOut" }}
        />
        {/* Bal kar */}
        <line x1="28" y1="70" x2="12" y2="80" stroke="var(--color-accent-secondary)" strokeWidth="4" strokeLinecap="round" />
      </svg>
    </Box>
  );
};

export default RobotMascot;
