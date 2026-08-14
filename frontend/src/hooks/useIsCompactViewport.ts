import { useEffect, useState } from "react";

/**
 * true, ha a viewport mobil-méretű (<= 600px). A StarfieldBackground ez
 * alapján csökkenti a réteg-/csillag-darabszámot mobil teljesítmény-védelem
 * miatt (ld. terv 3.2, 6. pont).
 */
export function useIsCompactViewport(breakpointPx = 600): boolean {
  const [isCompact, setIsCompact] = useState<boolean>(() => {
    if (typeof window === "undefined" || !window.matchMedia) return false;
    return window.matchMedia(`(max-width: ${breakpointPx}px)`).matches;
  });

  useEffect(() => {
    if (typeof window === "undefined" || !window.matchMedia) return;
    const query = window.matchMedia(`(max-width: ${breakpointPx}px)`);
    const handler = (event: MediaQueryListEvent) => setIsCompact(event.matches);
    query.addEventListener("change", handler);
    return () => query.removeEventListener("change", handler);
  }, [breakpointPx]);

  return isCompact;
}
