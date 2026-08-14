import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { ThemeProvider as MuiThemeProvider } from "@mui/material/styles";
import CssBaseline from "@mui/material/CssBaseline";
import { applyThemeCssVariables, type ThemeMode } from "./tokens";
import { buildMuiTheme } from "./muiTheme";

const STORAGE_KEY = "theme-preference";

function readStoredMode(): ThemeMode {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === "space" || stored === "dark" || stored === "light") {
      return stored;
    }
  } catch {
    // localStorage elérhetetlen (pl. privát böngészés) — alapértelmezésre esünk.
  }
  return "space";
}

/**
 * TODO(backend): amint a `PUT /api/auth/me/theme` endpoint elkészül (terv 3.5
 * szekció), ez a függvény hívja majd meg a backendet a preferencia
 * elmentéséhez, hogy más eszközön/böngészőben is megmaradjon. Addig csak
 * localStorage-be írunk.
 */
function syncThemeToBackend(_mode: ThemeMode): void {
  // no-op — a backend endpoint még nincs kész (2. lépés, párhuzamos munka).
}

interface ThemeModeContextValue {
  mode: ThemeMode;
  setMode: (mode: ThemeMode) => void;
}

const ThemeModeContext = createContext<ThemeModeContextValue | undefined>(
  undefined,
);

export const ThemeModeProvider: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const [mode, setModeState] = useState<ThemeMode>(readStoredMode);

  // A data-theme attribútumot és a CSS változókat már az index.html inline
  // scriptje beállította az első render előtt (villanás-mentesen) — itt csak
  // szinkronban tartjuk, ha a state később változik.
  useEffect(() => {
    document.documentElement.setAttribute("data-theme", mode);
    applyThemeCssVariables(mode);
  }, [mode]);

  const setMode = useCallback((next: ThemeMode) => {
    setModeState(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // localStorage elérhetetlen — a preferencia csak a jelenlegi munkamenetre él.
    }
    syncThemeToBackend(next);
  }, []);

  const muiTheme = useMemo(() => buildMuiTheme(mode), [mode]);

  const value = useMemo(() => ({ mode, setMode }), [mode, setMode]);

  return (
    <ThemeModeContext.Provider value={value}>
      <MuiThemeProvider theme={muiTheme}>
        <CssBaseline />
        {children}
      </MuiThemeProvider>
    </ThemeModeContext.Provider>
  );
};

export function useThemeMode(): ThemeModeContextValue {
  const ctx = useContext(ThemeModeContext);
  if (!ctx) {
    throw new Error("useThemeMode must be used within a ThemeModeProvider");
  }
  return ctx;
}
