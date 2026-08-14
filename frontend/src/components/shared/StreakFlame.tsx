import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import LocalFireDepartmentIcon from "@mui/icons-material/LocalFireDepartment";
import { motion, AnimatePresence } from "framer-motion";

export interface StreakFlameProps {
  currentStreak: number;
  size?: "small" | "medium" | "large";
}

const fontSizeBySize = { small: 20, medium: 28, large: 40 } as const;
const iconSizeBySize = { small: 18, medium: 24, large: 32 } as const;

/**
 * Láng-ikon + streak-szám. A tényleges streak-adatot props-ként kapja — ez a
 * komponens maga nem hív API-t (a hívó oldal/hook felelőssége a lekérdezés,
 * ld. terv 10.5 réteg-szétválasztás).
 */
export const StreakFlame: React.FC<StreakFlameProps> = ({
  currentStreak,
  size = "medium",
}) => (
  <Box
    sx={{
      display: "inline-flex",
      alignItems: "center",
      gap: 0.5,
      color: currentStreak > 0 ? "var(--color-warning)" : "var(--color-text-secondary)",
    }}
  >
    <LocalFireDepartmentIcon sx={{ fontSize: iconSizeBySize[size] }} />
    <AnimatePresence mode="popLayout">
      <motion.div
        key={currentStreak}
        initial={{ scale: 0.6, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: "spring", stiffness: 400, damping: 20 }}
      >
        <Typography
          sx={{ fontWeight: 700, fontSize: fontSizeBySize[size], lineHeight: 1 }}
        >
          {currentStreak}
        </Typography>
      </motion.div>
    </AnimatePresence>
  </Box>
);

export default StreakFlame;
