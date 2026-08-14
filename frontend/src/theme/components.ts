import type { ThemeOptions } from "@mui/material/styles";
import type { ThemeTokens } from "./tokens";
import { radiusScale } from "./tokens";

/**
 * MUI komponens-override-ok EGY helyen, a tokenekre hivatkozva — sosem
 * hardkódolt szín/árnyék egy adott komponensben (ld. terv 3.1/10.2).
 */
export function buildComponentOverrides(
  tokens: ThemeTokens,
): ThemeOptions["components"] {
  const { colors, glow, immersive } = tokens;

  return {
    MuiCssBaseline: {
      styleOverrides: {
        body: {
          backgroundColor: colors.bgBase,
          transition: "background-color 200ms ease",
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        root: {
          borderRadius: radiusScale.md,
          fontWeight: 600,
        },
        containedPrimary: {
          boxShadow: "none",
          "&:hover": {
            boxShadow: glow.sm,
          },
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: {
          backgroundImage: "none",
          backgroundColor: immersive ? colors.bgGlass : colors.bgElevated,
          ...(immersive
            ? {
                backdropFilter: "blur(16px)",
                border: `1px solid ${colors.border}`,
              }
            : {
                border: `1px solid ${colors.border}`,
              }),
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: radiusScale.lg,
        },
      },
    },
    MuiTextField: {
      defaultProps: {
        variant: "outlined",
      },
    },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: radiusScale.md,
          "&.Mui-focused": {
            boxShadow: glow.accent,
          },
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          borderRadius: radiusScale.full,
        },
      },
    },
  };
}
