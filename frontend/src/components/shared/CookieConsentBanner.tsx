import React, { useEffect, useState } from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import { useTranslation } from "react-i18next";
import { GlowCard } from "./GlowCard";
import { NeonButton } from "./NeonButton";
import { getCookie, setCookie } from "../../utils/cookies";

const CONSENT_COOKIE_NAME = "legymernok_cookie_consent";
const CONSENT_COOKIE_TTL_DAYS = 365;

/**
 * Egyszerű, tájékoztató jellegű cookie-banner — NEM egy teljes
 * consent-management-platform (nincsenek kategória-kapcsolók,
 * script-blokkolás stb.), mert jelenleg a projekt kizárólag egyetlen,
 * nem-követő, funkcionális célú cookie-t használ (admin táblázatok
 * szűrő/oldalméret-preferenciái, ld. `useDataGridPreferences`) —
 * se marketing-, se analitikai cookie nincs. Ha ez a jövőben változik
 * (pl. analitika bekerül), ezt a bannert kategória-választóra kell bővíteni,
 * vagy egy tényleges CMP-re váltani.
 *
 * A `RootLayout`-ban (`router/index.tsx`) van bekötve, minden oldalon
 * megjelenik, amíg a user el nem fogadja.
 */
export const CookieConsentBanner: React.FC = () => {
  const { t } = useTranslation();
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    setVisible(getCookie(CONSENT_COOKIE_NAME) !== "accepted");
  }, []);

  const handleAccept = () => {
    setCookie(CONSENT_COOKIE_NAME, "accepted", CONSENT_COOKIE_TTL_DAYS);
    setVisible(false);
  };

  if (!visible) return null;

  // SZÁNDÉKOSAN a normál dokumentum-flow-ban, a legtetején (nem `position:
  // fixed`/`sticky`) — több oldalon is van alul rögzített akció-sáv
  // (MissionPlayerShell, kvíz lejátszó), egy alulra fixált banner ezeket
  // eltakarná (élőben megerősítve: a Cypress cadet_quiz_player.cy.ts
  // suite-ja pontosan ezt a kattintás-blokkolást buktatta le). A tetején,
  // a navigációs sáv fölött, a tartalmat lejjebb tolva sosem takar el
  // interaktív elemet.
  return (
    <Box
      sx={{
        width: "100%",
        display: "flex",
        justifyContent: "center",
        p: { xs: 1.5, sm: 2 },
      }}
      data-cy="cookie-consent-banner"
    >
      <GlowCard
        sx={{
          width: "100%",
          maxWidth: 960,
          display: "flex",
          flexDirection: { xs: "column", sm: "row" },
          alignItems: { xs: "stretch", sm: "center" },
          gap: 2,
        }}
      >
        <Typography variant="body2" sx={{ color: "var(--color-text-secondary)", flex: 1 }}>
          {t("cookieConsent.message")}
        </Typography>
        <Box sx={{ display: "flex", gap: 1, flexShrink: 0 }}>
          <NeonButton
            size="small"
            onClick={handleAccept}
            data-cy="cookie-consent-accept"
          >
            {t("cookieConsent.accept")}
          </NeonButton>
        </Box>
      </GlowCard>
    </Box>
  );
};

export default CookieConsentBanner;
