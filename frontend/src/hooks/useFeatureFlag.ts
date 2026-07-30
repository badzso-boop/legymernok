import { useEffect, useState } from "react";
import { useAuth } from "../context/AuthContext";
import { featureFlagApi } from "../api/client";

/**
 * Lekérdezi egy feature flag aktuális értékét a bejelentkezett felhasználó
 * nevében (GET /api/feature-flags/{key} — bármely authentikált user hívhatja,
 * nem csak admin). Amíg a lekérdezés fut, vagy ha a user nincs bejelentkezve,
 * illetve hiba történt, a flag `false`-nak (kikapcsoltnak) számít — ez a
 * biztonságos "fail closed" alapértelmezés egy feature-kapcsolóhoz.
 */
export function useFeatureFlag(key: string): boolean {
  const { isAuthenticated } = useAuth();
  const [enabled, setEnabled] = useState(false);

  useEffect(() => {
    if (!isAuthenticated) {
      setEnabled(false);
      return;
    }

    let active = true;

    featureFlagApi
      .getByKey(key)
      .then((flag) => {
        if (active) setEnabled(flag.enabled);
      })
      .catch(() => {
        if (active) setEnabled(false);
      });

    return () => {
      active = false;
    };
  }, [key, isAuthenticated]);

  return enabled;
}
