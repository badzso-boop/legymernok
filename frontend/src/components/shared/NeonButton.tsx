import React from "react";
import Button, { type ButtonProps } from "@mui/material/Button";

/**
 * MUI Button wrapper a design system glow-nyelvével — fókusz/hover állapotban
 * `var(--glow-*)`-ból színez, nem oldalanként újra kitalált box-shadow.
 */
export const NeonButton: React.FC<ButtonProps> = ({ sx, ...rest }) => (
  <Button
    variant="contained"
    disableElevation
    {...rest}
    sx={{
      boxShadow: "none",
      "&:hover": {
        boxShadow: "var(--glow-md)",
      },
      "&:focus-visible": {
        boxShadow: "var(--glow-accent)",
      },
      ...sx,
    }}
  />
);

export default NeonButton;
