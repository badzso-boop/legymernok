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
import { authApi } from "../api/client";

const STORAGE_KEY = "theme-preference";
const MODE_TO_BACKEND: Record<ThemeMode, "SPACE" | "DARK" | "LIGHT"> = {
  space: "SPACE",
  dark: "DARK",
  light: "LIGHT",
};

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
 * A preferenciát a backendbe is elmenti (`PUT /api/auth/me/theme`), hogy más
 * eszközön/böngészőben is megmaradjon. Ha a user nincs bejelentkezve vagy a
 * hívás hibázik, a localStorage-alapú alkalmazás akkor is működik — ez csak
 * a szinkronizálás, nem a téma tényleges alkalmazásának feltétele.
 */
function syncThemeToBackend(mode: ThemeMode): void {
  if (!localStorage.getItem("token")) return;
  authApi.updateTheme(MODE_TO_BACKEND[mode]).catch(() => {
    // Csendes hiba — a helyi téma-váltás enélkül is teljes értékű, a
    // szinkronizálás legközelebbi sikeres híváskor újra megpróbálódik.
  });
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
