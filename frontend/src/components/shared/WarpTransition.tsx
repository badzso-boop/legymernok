import React, { useEffect } from "react";
import Box from "@mui/material/Box";
import { motion, AnimatePresence } from "framer-motion";
import { usePrefersReducedMotion } from "../../hooks/usePrefersReducedMotion";

export interface WarpTransitionProps {
  /** Igaz, amíg a warp-animáció fut. */
  active: boolean;
  /** A navigáció, ami az animáció végén (vagy azonnal, reduced-motion esetén) fusson le. */
  onComplete: () => void;
}

const STREAK_COUNT = 20;
const streakAngles = Array.from({ length: STREAK_COUNT }, (_, i) => (360 / STREAK_COUNT) * i);

/**
 * Teljes képernyős "warp" átmenet a Sector Map → Star Map navigációhoz (egy
 * szektor kiválasztásakor) — a sci-fi narratívához illő, rövid (kb. 550ms)
 * hyperspace-effekt: a középpontból kifelé húzódó fénycsíkok + egy központi
 * felvillanás. `prefers-reduced-motion` esetén nincs animáció, `onComplete`
 * azonnal lefut.
 */
export const WarpTransition: React.FC<WarpTransitionProps> = ({ active, onComplete }) => {
  const prefersReducedMotion = usePrefersReducedMotion();

  useEffect(() => {
    if (!active) return;
    if (prefersReducedMotion) {
      onComplete();
      return;
    }
    const timer = setTimeout(onComplete, 550);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [active, prefersReducedMotion]);

  if (prefersReducedMotion) return null;

  return (
    <AnimatePresence>
      {active && (
        <Box
          data-cy="warp-transition"
          sx={{
            position: "fixed",
            inset: 0,
            zIndex: 2000,
            pointerEvents: "none",
            overflow: "hidden",
            backgroundColor: "var(--color-bg-base)",
          }}
        >
          {streakAngles.map((angle) => (
            <motion.div
              key={angle}
              initial={{ scaleY: 0, opacity: 0 }}
              animate={{ scaleY: 1, opacity: [0, 1, 0] }}
              transition={{ duration: 0.55, ease: "easeIn" }}
              style={{
                position: "absolute",
                top: "50%",
                left: "50%",
                width: 2,
                height: "70%",
                background:
                  "linear-gradient(to top, transparent, var(--color-accent-primary))",
                transformOrigin: "top center",
                rotate: `${angle}deg`,
              }}
            />
          ))}
          <motion.div
            initial={{ scale: 0, opacity: 0.9 }}
            animate={{ scale: 14, opacity: 0 }}
            transition={{ duration: 0.55, ease: "easeIn" }}
            style={{
              position: "absolute",
              top: "50%",
              left: "50%",
              width: 24,
              height: 24,
              marginTop: -12,
              marginLeft: -12,
              borderRadius: "50%",
              backgroundColor: "var(--color-accent-primary)",
            }}
          />
        </Box>
      )}
    </AnimatePresence>
  );
};

export default WarpTransition;
