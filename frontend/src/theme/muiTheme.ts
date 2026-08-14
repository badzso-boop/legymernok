import { createTheme, type Theme } from "@mui/material/styles";
import { themeTokens, type ThemeMode } from "./tokens";
import { typography } from "./typography";
import { buildComponentOverrides } from "./components";

export function buildMuiTheme(mode: ThemeMode): Theme {
  const tokens = themeTokens[mode];

  return createTheme({
    palette: {
      mode: mode === "light" ? "light" : "dark",
      primary: { main: tokens.colors.accentPrimary },
      secondary: { main: tokens.colors.accentSecondary },
      background: {
        default: tokens.colors.bgBase,
        paper: tokens.colors.bgElevated,
      },
      text: {
        primary: tokens.colors.textPrimary,
        secondary: tokens.colors.textSecondary,
      },
      success: { main: tokens.colors.success },
      error: { main: tokens.colors.error },
      warning: { main: tokens.colors.warning },
      divider: tokens.colors.border,
    },
    typography,
    components: buildComponentOverrides(tokens),
  });
}
