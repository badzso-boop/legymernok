/**
 * Design system tokenek — Space / Dark / Light témák.
 *
 * Egyetlen forrás mindenkinek: a StarfieldBackground/GlowCard stb. CSS custom
 * property-ken (var(--color-...)) keresztül olvassák ezeket, a MUI téma pedig
 * ugyanezekből a JS objektumokból épül fel (theme/muiTheme.ts) — nincs két
 * külön "igazság" a színekre.
 */

export type ThemeMode = "space" | "dark" | "light";

export interface ThemeColorTokens {
  bgBase: string;
  bgElevated: string;
  bgGlass: string; // félig áttetsző panel-háttér (Space glassmorphism)
  accentPrimary: string; // cián
  accentSecondary: string; // magenta
  textPrimary: string;
  textSecondary: string;
  border: string;
  borderGlow: string;
  success: string;
  error: string;
  warning: string;
}

export interface ThemeGlowTokens {
  sm: string;
  md: string;
  lg: string;
  accent: string; // fókusz/hover/aktív állapotokhoz
}

export interface ThemeTokens {
  colors: ThemeColorTokens;
  glow: ThemeGlowTokens;
  /** Space témán aktívak a háttér-animációk (StarfieldBackground/NebulaLayer, glassmorphism kártyák). */
  immersive: boolean;
}

// Radius és spacing NEM téma-függő — a márka konzisztenciája a betűkészleten,
// a spacing-en és a radius-on keresztül is érvényesül, nem csak a Space témán.
export const radiusScale = {
  sm: "6px",
  md: "12px",
  lg: "20px",
  full: "999px",
};

export const spacingUnit = 8; // px, MUI `theme.spacing()` ezt szorozza

export const themeTokens: Record<ThemeMode, ThemeTokens> = {
  space: {
    immersive: true,
    colors: {
      bgBase: "#05040f",
      bgElevated: "#0d0b1f",
      bgGlass: "rgba(20, 18, 40, 0.55)",
      accentPrimary: "#22d3ee",
      accentSecondary: "#e879f9",
      textPrimary: "#f4f3ff",
      textSecondary: "#a8a4c9",
      border: "rgba(244, 243, 255, 0.12)",
      borderGlow: "rgba(34, 211, 238, 0.45)",
      success: "#4ade80",
      error: "#f87171",
      warning: "#fbbf24",
    },
    glow: {
      sm: "0 0 8px rgba(34, 211, 238, 0.35)",
      md: "0 0 20px rgba(34, 211, 238, 0.35), 0 0 40px rgba(232, 121, 249, 0.15)",
      lg: "0 0 36px rgba(34, 211, 238, 0.4), 0 0 72px rgba(232, 121, 249, 0.2)",
      accent: "0 0 0 3px rgba(34, 211, 238, 0.35)",
    },
  },
  dark: {
    immersive: false,
    colors: {
      bgBase: "#0b0a14",
      bgElevated: "#15131f",
      bgGlass: "rgba(21, 19, 31, 0.9)",
      accentPrimary: "#22d3ee",
      accentSecondary: "#e879f9",
      textPrimary: "#f2f1f7",
      textSecondary: "#9d99b8",
      border: "rgba(242, 241, 247, 0.1)",
      borderGlow: "rgba(34, 211, 238, 0.25)",
      success: "#4ade80",
      error: "#f87171",
      warning: "#fbbf24",
    },
    glow: {
      sm: "0 0 4px rgba(34, 211, 238, 0.2)",
      md: "0 2px 12px rgba(0, 0, 0, 0.4)",
      lg: "0 4px 24px rgba(0, 0, 0, 0.5)",
      accent: "0 0 0 3px rgba(34, 211, 238, 0.25)",
    },
  },
  light: {
    immersive: false,
    colors: {
      bgBase: "#f4f6fb",
      bgElevated: "#ffffff",
      bgGlass: "rgba(255, 255, 255, 0.9)",
      accentPrimary: "#0e7490",
      accentSecondary: "#a21caf",
      textPrimary: "#12131a",
      textSecondary: "#585c6b",
      border: "rgba(18, 19, 26, 0.1)",
      borderGlow: "rgba(14, 116, 144, 0.3)",
      success: "#15803d",
      error: "#b91c1c",
      warning: "#b45309",
    },
    glow: {
      sm: "0 1px 3px rgba(18, 19, 26, 0.08)",
      md: "0 4px 12px rgba(18, 19, 26, 0.1)",
      lg: "0 8px 24px rgba(18, 19, 26, 0.14)",
      accent: "0 0 0 3px rgba(14, 116, 144, 0.25)",
    },
  },
};

const CSS_VAR_MAP: Record<keyof ThemeColorTokens, string> = {
  bgBase: "--color-bg-base",
  bgElevated: "--color-bg-elevated",
  bgGlass: "--color-bg-glass",
  accentPrimary: "--color-accent-primary",
  accentSecondary: "--color-accent-secondary",
  textPrimary: "--color-text-primary",
  textSecondary: "--color-text-secondary",
  border: "--color-border",
  borderGlow: "--color-border-glow",
  success: "--color-success",
  error: "--color-error",
  warning: "--color-warning",
};

const GLOW_VAR_MAP: Record<keyof ThemeGlowTokens, string> = {
  sm: "--glow-sm",
  md: "--glow-md",
  lg: "--glow-lg",
  accent: "--glow-accent",
};

/**
 * A jelenlegi téma tokenjeit CSS custom property-ként írja ki a dokumentum
 * gyökerére — így minden saját komponens (StarfieldBackground, GlowCard stb.)
 * `var(--color-...)`-ból színezhet, a MUI komponensek pedig a
 * theme/muiTheme.ts-ben ugyanezekből a JS objektumokból épülő palettából.
 */
export function applyThemeCssVariables(mode: ThemeMode): void {
  const tokens = themeTokens[mode];
  const root = document.documentElement.style;

  (Object.keys(CSS_VAR_MAP) as (keyof ThemeColorTokens)[]).forEach((key) => {
    root.setProperty(CSS_VAR_MAP[key], tokens.colors[key]);
  });
  (Object.keys(GLOW_VAR_MAP) as (keyof ThemeGlowTokens)[]).forEach((key) => {
    root.setProperty(GLOW_VAR_MAP[key], tokens.glow[key]);
  });
  root.setProperty("--radius-sm", radiusScale.sm);
  root.setProperty("--radius-md", radiusScale.md);
  root.setProperty("--radius-lg", radiusScale.lg);
  root.setProperty("--radius-full", radiusScale.full);
}
