import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import { useTranslation } from "react-i18next";
import { GlowCard } from "../../components/shared/GlowCard";
import { useThemeMode } from "../../theme/ThemeModeProvider";
import type { ThemeMode } from "../../theme/tokens";

const THEME_OPTIONS: ThemeMode[] = ["space", "dark", "light"];

const SettingsPage: React.FC = () => {
  const { t } = useTranslation();
  const { mode, setMode } = useThemeMode();

  return (
    <Box sx={{ maxWidth: 720, mx: "auto", p: { xs: 2, md: 4 } }}>
      <Typography variant="h4" sx={{ fontWeight: "bold", mb: 3 }}>
        {t("settings.title")}
      </Typography>

      <Typography variant="h6" sx={{ mb: 0.5 }}>
        {t("settings.themeSectionTitle")}
      </Typography>
      <Typography variant="body2" sx={{ color: "var(--color-text-secondary)", mb: 2 }}>
        {t("settings.themeSectionSubtitle")}
      </Typography>

      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: { xs: "1fr", sm: "repeat(3, 1fr)" },
          gap: 2,
        }}
      >
        {THEME_OPTIONS.map((option) => {
          const selected = mode === option;
          return (
            <GlowCard
              key={option}
              active={selected}
              onClick={() => setMode(option)}
              role="radio"
              aria-checked={selected}
              tabIndex={0}
              data-cy={`settings-theme-${option}`}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  setMode(option);
                }
              }}
              sx={{
                cursor: "pointer",
                display: "flex",
                flexDirection: "column",
                gap: 1,
                minHeight: 140,
              }}
            >
              <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                <Typography sx={{ fontWeight: "bold" }}>
                  {t(`settings.theme${capitalize(option)}Label`)}
                </Typography>
                {selected && (
                  <CheckCircleIcon sx={{ color: "var(--color-accent-primary)", fontSize: 20 }} />
                )}
              </Box>
              <Typography variant="body2" sx={{ color: "var(--color-text-secondary)" }}>
                {t(`settings.theme${capitalize(option)}Description`)}
              </Typography>
            </GlowCard>
          );
        })}
      </Box>
    </Box>
  );
};

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1);
}

export default SettingsPage;
