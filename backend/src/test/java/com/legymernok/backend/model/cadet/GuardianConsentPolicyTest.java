package com.legymernok.backend.model.cadet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GuardianConsentPolicyTest {

    @Test
    void noBirthYear_ShouldNotRequireConsent() {
        assertEquals(GuardianConsentStatus.NOT_REQUIRED, GuardianConsentPolicy.initialStatus(null, 2026));
    }

    @Test
    void clearlyUnderAge_ShouldRequireConsent() {
        assertEquals(GuardianConsentStatus.PENDING, GuardianConsentPolicy.initialStatus((short) 2014, 2026));
    }

    @Test
    void turningSixteenThisYear_ShouldConservativelyRequireConsent() {
        // 2010-ben születettek 2026-ban még lehetnek 15 évesek
        assertEquals(GuardianConsentStatus.PENDING, GuardianConsentPolicy.initialStatus((short) 2010, 2026));
    }

    @Test
    void turningSeventeenThisYear_ShouldNotRequireConsent() {
        assertEquals(GuardianConsentStatus.NOT_REQUIRED, GuardianConsentPolicy.initialStatus((short) 2009, 2026));
    }
}
