import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react";
import { createPortal } from "react-dom";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import Typography from "@mui/material/Typography";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { GlowCard } from "./GlowCard";

interface MissionPlayerHeaderState {
  title: React.ReactNode;
  subtitle?: React.ReactNode;
}

interface MissionPlayerShellContextValue {
  actionsContainer: HTMLDivElement | null;
  setHeader: (header: MissionPlayerHeaderState | null) => void;
}

const MissionPlayerShellContext =
  createContext<MissionPlayerShellContextValue | null>(null);

/**
 * Belső tartalom-komponensek (ContentMissionView, FillInBlankView, QuizPlayer,
 * CodingMissionPlayer) ezzel érhetik el a körülöttük futó MissionPlayerShell-t
 * — vagy `null`-t kapnak, ha valahol máshol (pl. régi standalone use-case-ben)
 * futnak Shell nélkül, ekkor a MissionPlayerActions inline fallback-re esik.
 */
export function useMissionPlayerShell() {
  return useContext(MissionPlayerShellContext);
}

/**
 * A misszió/csoport/kvíz aktuális címét jelenti be a körülötte futó
 * Shell-nek (portál nélkül, egyszerű context-callback-kel) — azért nem props,
 * mert a cím gyakran csak a belső tartalom-komponens saját adatlekérése után
 * derül ki (pl. a CONTENT misszió neve az oldal-tartalommal egy hívásban jön).
 * Ha nincs körülötte Shell, nem csinál semmit.
 */
export function MissionPlayerHeaderPortal({
  title,
  subtitle,
}: MissionPlayerHeaderState) {
  const ctx = useMissionPlayerShell();
  useEffect(() => {
    ctx?.setHeader({ title, subtitle });
    return () => ctx?.setHeader(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ctx, title, subtitle]);
  return null;
}

/**
 * A belső tartalom-komponens ide teszi a saját "Következő"/"Beküldés"/"Mentés"
 * gombjait — ez portál-lal a Shell rögzített alsó akció-sávjába kerül, hogy
 * MINDEN lejátszási módnál (Content/FillInBlank/Quiz/Coding/Group Player
 * lépései) a gomb ugyanabban a DOM-pozícióban, ugyanazzal a stílussal
 * jelenjen meg — nem kell a belső komponensek saját state-jét/control-flow-ját
 * szétszedni ehhez, mindegyik továbbra is maga dönti el MIKOR és MILYEN
 * gombot mutat, csak a HOVA kérdést oldja meg a portál.
 *
 * Ha nincs körülötte Shell (pl. egy jövőbeli, Shell-en kívüli beágyazás),
 * inline-ban rendereli a gombokat, hogy sose tűnjenek el.
 */
export const MissionPlayerActions: React.FC<{ children: React.ReactNode }> = ({
  children,
}) => {
  const ctx = useMissionPlayerShell();
  if (ctx?.actionsContainer) {
    return createPortal(children, ctx.actionsContainer);
  }
  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        gap: 2,
        flexWrap: "wrap",
        mt: 2,
      }}
    >
      {children}
    </Box>
  );
};

export interface MissionPlayerShellProps {
  /** Alapértelmezett cím, amíg a belső tartalom (MissionPlayerHeaderPortal) még nem jelentett be sajátot. */
  title?: React.ReactNode;
  subtitle?: React.ReactNode;
  onBack?: () => void;
  /** Jobb oldali fejléc-elem, pl. "2 / 4 lépés" szöveg vagy egyéni node. */
  progress?: React.ReactNode;
  /** Lépésenkénti (0-1) lineáris progress-csík a fejléc alatt — opcionális. */
  progressValue?: number;
  /** CODING-nál a teljes képernyős, padding nélküli módhoz. */
  fullBleed?: boolean;
  maxWidth?: number | string;
  children: React.ReactNode;
}

/**
 * Közös layout minden misszió-lejátszási módhoz (terv 6.1): fix fejléc
 * (vissza-gomb, cím, progress), görgethető tartalom-terület, és egy mindig
 * ugyanott lévő, mobilon a képernyő aljához rögzített akció-sáv — amit a
 * belső tartalom a MissionPlayerActions portálon keresztül tölt fel.
 */
export const MissionPlayerShell: React.FC<MissionPlayerShellProps> = ({
  title,
  subtitle,
  onBack,
  progress,
  progressValue,
  fullBleed = false,
  maxWidth = 900,
  children,
}) => {
  const [actionsContainer, setActionsContainer] =
    useState<HTMLDivElement | null>(null);
  const [header, setHeader] = useState<MissionPlayerHeaderState | null>(null);

  const setHeaderStable = useCallback(
    (h: MissionPlayerHeaderState | null) => setHeader(h),
    [],
  );

  const rawTitle = header?.title ?? title;
  const effectiveTitle =
    typeof rawTitle === "string" ? rawTitle.toUpperCase() : rawTitle;
  const effectiveSubtitle = header?.subtitle ?? subtitle;

  return (
    <MissionPlayerShellContext.Provider
      value={{ actionsContainer, setHeader: setHeaderStable }}
    >
      <Box
        sx={{
          width: "100%",
          minHeight: "100dvh",
          display: "flex",
          justifyContent: "center",
          bgcolor: "var(--color-bg-base)",
          p: fullBleed ? 0 : { xs: 1, sm: 2 },
        }}
      >
        <GlowCard
          sx={{
            width: "100%",
            maxWidth: fullBleed ? "100%" : maxWidth,
            display: "flex",
            flexDirection: "column",
            minHeight: fullBleed ? "100dvh" : "calc(100dvh - 32px)",
            p: 0,
            overflow: "hidden",
            borderRadius: fullBleed ? 0 : "var(--radius-lg)",
          }}
        >
          {/* Fejléc */}
          <Box
            sx={{
              display: "flex",
              alignItems: "center",
              gap: 1.5,
              px: { xs: 1.5, sm: 2.5 },
              py: 1.5,
              borderBottom: "1px solid var(--color-border)",
              flexShrink: 0,
            }}
          >
            {onBack && (
              <IconButton
                onClick={onBack}
                size="small"
                aria-label="back"
                sx={{ color: "var(--color-text-primary)" }}
              >
                <ArrowBackIcon />
              </IconButton>
            )}
            <Box sx={{ minWidth: 0, flex: 1 }}>
              <Typography
                noWrap
                sx={{
                  fontWeight: 700,
                  fontSize: { xs: "1rem", sm: "1.15rem" },
                  color: "var(--color-text-primary)",
                  textTransform: "uppercase",
                }}
              >
                {effectiveTitle}
              </Typography>
              {effectiveSubtitle && (
                <Typography
                  noWrap
                  sx={{ fontSize: "0.8rem", color: "var(--color-text-secondary)" }}
                >
                  {effectiveSubtitle}
                </Typography>
              )}
            </Box>
            {progress && (
              <Box sx={{ flexShrink: 0, color: "var(--color-accent-primary)" }}>
                {progress}
              </Box>
            )}
          </Box>

          {progressValue !== undefined && (
            <Box
              sx={{
                height: 3,
                bgcolor: "var(--color-border)",
                flexShrink: 0,
              }}
            >
              <Box
                sx={{
                  height: "100%",
                  width: `${Math.round(Math.min(1, Math.max(0, progressValue)) * 100)}%`,
                  bgcolor: "var(--color-accent-primary)",
                  boxShadow: "var(--glow-accent)",
                  transition: "width 200ms ease",
                }}
              />
            </Box>
          )}

          {/* Tartalom */}
          <Box
            sx={{
              flex: 1,
              overflow: "auto",
              p: { xs: 1.5, sm: 2.5 },
              minHeight: 0,
            }}
          >
            {children}
          </Box>

          {/* Akció-sáv — a belső tartalom portálozza ide a gombjait */}
          <Box
            ref={setActionsContainer}
            sx={{
              flexShrink: 0,
              borderTop: "1px solid var(--color-border)",
              px: { xs: 1.5, sm: 2.5 },
              py: 1.5,
              position: { xs: "sticky", sm: "static" },
              bottom: 0,
              bgcolor: "var(--color-bg-elevated)",
              display: "flex",
              alignItems: "center",
              gap: 2,
              flexWrap: "wrap",
              minHeight: 0,
              "&:empty": { display: "none" },
            }}
          />
        </GlowCard>
      </Box>
    </MissionPlayerShellContext.Provider>
  );
};

export default MissionPlayerShell;
