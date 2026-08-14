import React, { useEffect, useMemo, useState } from "react";
import Box from "@mui/material/Box";
import { useThemeMode } from "../../theme/ThemeModeProvider";
import { usePrefersReducedMotion } from "../../hooks/usePrefersReducedMotion";
import { useIsCompactViewport } from "../../hooks/useIsCompactViewport";

export interface StarfieldBackgroundProps {
  /** "hero" = teltebb verzió (landing), "ambient" = visszafogottabb (dashboard/player). */
  intensity?: "hero" | "ambient";
  /** Kényszerített réteg-szám — ha nincs megadva, az intensity/viewport dönti el. */
  layers?: number;
}

interface Star {
  id: number;
  leftPct: number;
  topPct: number;
  sizePx: number;
  twinkleDelayS: number;
  twinkleDurationS: number;
}

function generateStars(count: number, sizeRangePx: [number, number]): Star[] {
  return Array.from({ length: count }, (_, id) => ({
    id,
    leftPct: Math.random() * 100,
    topPct: Math.random() * 100,
    sizePx:
      sizeRangePx[0] + Math.random() * (sizeRangePx[1] - sizeRangePx[0]),
    twinkleDelayS: Math.random() * 6,
    twinkleDurationS: 3 + Math.random() * 4,
  }));
}

function StarLayer({
  stars,
  color,
  twinkle,
  driftS,
}: {
  stars: Star[];
  color: string;
  twinkle: boolean;
  driftS?: number;
}) {
  return (
    <Box
      aria-hidden
      sx={{
        position: "absolute",
        inset: 0,
        ...(driftS && {
          animation: `starfield-drift ${driftS}s ease-in-out infinite alternate`,
        }),
      }}
    >
      {stars.map((star) => (
        <Box
          key={star.id}
          sx={{
            position: "absolute",
            left: `${star.leftPct}%`,
            top: `${star.topPct}%`,
            width: star.sizePx,
            height: star.sizePx,
            borderRadius: "50%",
            backgroundColor: color,
            boxShadow: `0 0 ${star.sizePx * 2}px ${color}`,
            ...(twinkle && {
              animation: `starfield-twinkle ${star.twinkleDurationS}s ease-in-out ${star.twinkleDelayS}s infinite`,
            }),
          }}
        />
      ))}
    </Box>
  );
}

/**
 * Réteges parallax csillagmező — kizárólag a "space" témán renderel bármit
 * (Dark/Light témán null, ld. terv 3.2/3.3). DOM/SVG-alapú, nem canvas —
 * kevesebb akkumulátor-terhelés, `prefers-reduced-motion`-nál statikusra esik
 * vissza, mobilon kevesebb réteg/elem.
 *
 * Implementációs döntés: a terv "requestAnimationFrame throttle"-t említ a
 * mozgáshoz, de itt tiszta CSS @keyframes animációt használunk — ez a
 * compositor szálon fut, nincs hozzá JS runtime-teher, és pontosan ugyanazt a
 * vizuális hatást adja (twinkle opacity-pulzálás, lassú drift), mint egy kézzel
 * írt rAF-loop, kevesebb kockázattal frame-dropra gyenge mobil eszközökön.
 */
export const StarfieldBackground: React.FC<StarfieldBackgroundProps> = ({
  intensity = "ambient",
  layers,
}) => {
  const { mode } = useThemeMode();
  const prefersReducedMotion = usePrefersReducedMotion();
  const isCompact = useIsCompactViewport();
  const [shootingStarKey, setShootingStarKey] = useState(0);

  const layerCount = layers ?? (isCompact ? 2 : 3);
  const baseCount = intensity === "hero" ? 90 : 45;
  const countMultiplier = isCompact ? 0.5 : 1;

  const farStars = useMemo(
    () => generateStars(Math.round(baseCount * 0.5 * countMultiplier), [1, 1.5]),
    [baseCount, countMultiplier],
  );
  const midStars = useMemo(
    () => generateStars(Math.round(baseCount * 0.35 * countMultiplier), [1.5, 2.5]),
    [baseCount, countMultiplier],
  );
  const nearStars = useMemo(
    () => generateStars(Math.round(baseCount * 0.15 * countMultiplier), [2.5, 3.5]),
    [baseCount, countMultiplier],
  );

  useEffect(() => {
    if (prefersReducedMotion) return;
    const scheduleNext = () => {
      const delayMs = (30 + Math.random() * 60) * 1000;
      return window.setTimeout(() => {
        setShootingStarKey((k) => k + 1);
        timeoutId = scheduleNext();
      }, delayMs);
    };
    let timeoutId = scheduleNext();
    return () => window.clearTimeout(timeoutId);
  }, [prefersReducedMotion]);

  if (mode !== "space") return null;

  return (
    <Box
      sx={{
        position: "absolute",
        inset: 0,
        overflow: "hidden",
        pointerEvents: "none",
        zIndex: 0,
      }}
    >
      <StarLayer stars={farStars} color="var(--color-text-secondary)" twinkle={false} />
      <StarLayer
        stars={midStars}
        color="var(--color-text-primary)"
        twinkle={!prefersReducedMotion}
      />
      {layerCount > 2 && (
        <StarLayer
          stars={nearStars}
          color="var(--color-accent-primary)"
          twinkle={!prefersReducedMotion}
          driftS={prefersReducedMotion ? undefined : 40}
        />
      )}
      {!prefersReducedMotion && (
        <Box
          key={shootingStarKey}
          aria-hidden
          sx={{
            position: "absolute",
            top: `${10 + Math.random() * 30}%`,
            left: "-5%",
            width: "3px",
            height: "3px",
            borderRadius: "50%",
            backgroundColor: "var(--color-accent-secondary)",
            boxShadow: "0 0 6px 2px var(--color-accent-secondary)",
            animation: "starfield-shoot 1.4s linear",
          }}
        />
      )}
    </Box>
  );
};

export default StarfieldBackground;
