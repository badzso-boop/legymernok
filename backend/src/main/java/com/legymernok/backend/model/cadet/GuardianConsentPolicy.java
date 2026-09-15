package com.legymernok.backend.model.cadet;

/**
 * Mikor kell szülői hozzájárulás a regisztrációhoz.
 *
 * <p>GDPR 8. cikk: információs társadalommal összefüggő szolgáltatásnál a gyermek saját
 * hozzájárulása a tagállami korhatár alatt csak szülői hozzájárulással érvényes.
 * <b>A hazai korhatár pontos értékét jogi szakértővel ellenőrizni kell</b> — addig 16 évvel számolunk
 * (dumb-research/terv/PISA_FEJLESZTESI_TERV.md, 8.4).
 *
 * <p>Csak születési évet tárolunk (adattakarékosság), ezért óvatosan döntünk: aki az adott évben
 * legfeljebb {@value #DIGITAL_CONSENT_AGE} éves lesz, még lehet a korhatár alatt.
 */
public final class GuardianConsentPolicy {

    public static final int DIGITAL_CONSENT_AGE = 16;

    private GuardianConsentPolicy() {
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
