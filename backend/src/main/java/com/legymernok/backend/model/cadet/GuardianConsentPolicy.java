package com.legymernok.backend.model.cadet;

import java.time.Year;
import java.time.ZoneId;

/**
 * Mikor kell szülői hozzájárulás a regisztrációhoz.
 *
 * <p>GDPR 8. cikk: információs társadalommal összefüggő szolgáltatásnál a gyermek saját
 * hozzájárulása a tagállami korhatár alatt csak szülői hozzájárulással érvényes.
 * <b>A hazai korhatár pontos értékét jogi szakértővel ellenőrizni kell</b> — addig 16 évvel számolunk
 * (plans/navigator_academy_implementation.md, na-20).
 *
 * <p>Csak születési évet tárolunk (adattakarékosság), ezért óvatosan döntünk: aki az adott évben
 * legfeljebb {@value #DIGITAL_CONSENT_AGE} éves lesz, még lehet a korhatár alatt.
 */
public final class GuardianConsentPolicy {

    public static final int DIGITAL_CONSENT_AGE = 16;

    /** A felhasználók Magyarországon regisztrálnak; a JVM (UTC) éve év fordulón órákig eltérhet. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Budapest");

    private GuardianConsentPolicy() {
    }

    public static int currentYear() {
        return Year.now(ZONE).getValue();
    }

    public static GuardianConsentStatus initialStatus(Short birthYear, int currentYear) {
        if (birthYear == null) {
            return GuardianConsentStatus.NOT_REQUIRED;
        }
        return currentYear - birthYear <= DIGITAL_CONSENT_AGE
                ? GuardianConsentStatus.PENDING
                : GuardianConsentStatus.NOT_REQUIRED;
    }
}
