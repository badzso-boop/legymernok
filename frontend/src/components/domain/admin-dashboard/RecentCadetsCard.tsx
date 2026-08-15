import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Avatar from "@mui/material/Avatar";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { GlowCard } from "../../shared/GlowCard";
import type { RecentCadetSummary } from "../../../types/admin";

interface RecentCadetsCardProps {
  cadets: RecentCadetSummary[];
}

function formatRelativeDate(iso: string, locale: string): string {
  const date = new Date(iso);
  return date.toLocaleDateString(locale === "hu" ? "hu-HU" : "en-US", {
    month: "short",
    day: "numeric",
  });
}

export const RecentCadetsCard: React.FC<RecentCadetsCardProps> = ({ cadets }) => {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();

  return (
    <GlowCard sx={{ height: "100%" }}>
      <Typography variant="overline" sx={{ color: "var(--color-text-secondary)" }}>
        {t("adminOverview.recentCadets.label")}
      </Typography>

      {cadets.length === 0 ? (
        <Typography variant="body2" sx={{ color: "var(--color-text-secondary)", mt: 2 }}>
          {t("adminOverview.recentCadets.empty")}
        </Typography>
      ) : (
        <Box sx={{ mt: 1 }}>
          {cadets.map((cadet) => (
            <Box
              key={cadet.id}
              onClick={() => navigate(`/admin/users/${cadet.id}`)}
              sx={{
                display: "flex",
                alignItems: "center",
                gap: 1.5,
                py: 1,
                cursor: "pointer",
                borderRadius: "var(--radius-md)",
                "&:hover": { bgcolor: "var(--color-bg-elevated)" },
              }}
            >
              <Avatar sx={{ width: 28, height: 28, fontSize: "0.8rem" }}>
                {cadet.username.charAt(0).toUpperCase()}
              </Avatar>
              <Box sx={{ minWidth: 0, flex: 1 }}>
                <Typography noWrap sx={{ fontWeight: 600 }}>
                  {cadet.fullName || cadet.username}
                </Typography>
                <Typography variant="caption" sx={{ color: "var(--color-text-secondary)" }}>
                  @{cadet.username}
                </Typography>
              </Box>
              <Typography variant="caption" sx={{ color: "var(--color-text-secondary)", flexShrink: 0 }}>
                {formatRelativeDate(cadet.createdAt, i18n.language)}
              </Typography>
            </Box>
          ))}
        </Box>
      )}
    </GlowCard>
  );
};

export default RecentCadetsCard;
