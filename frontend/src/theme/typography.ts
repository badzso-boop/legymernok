import type { ThemeOptions } from "@mui/material/styles";

/**
 * Tipográfia — NEM téma-függő. A márka konzisztenciája a betűkészleten és a
 * spacing-en keresztül is érvényesül, nem csak a Space témán (ld. terv 3.1).
 *
 * Fejléc: Space Grotesk · Body: Inter · Kód: Fira Code
 * (betöltve: frontend/index.html Google Fonts linkje)
 */
export const typography: ThemeOptions["typography"] = {
  fontFamily: '"Inter", "Share Tech Mono", sans-serif',
  h1: { fontFamily: '"Space Grotesk", sans-serif', fontWeight: 700 },
  h2: { fontFamily: '"Space Grotesk", sans-serif', fontWeight: 700 },
  h3: { fontFamily: '"Space Grotesk", sans-serif', fontWeight: 600 },
  h4: { fontFamily: '"Space Grotesk", sans-serif', fontWeight: 600 },
  h5: { fontFamily: '"Space Grotesk", sans-serif', fontWeight: 600 },
  h6: { fontFamily: '"Space Grotesk", sans-serif', fontWeight: 600 },
  button: { fontFamily: '"Space Grotesk", sans-serif', fontWeight: 600, textTransform: "none" },
};

export const codeFontFamily = '"Fira Code", "Share Tech Mono", monospace';
