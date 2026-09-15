-- =============================================================================
-- V11: Audience (termékélmény) és kiskorú-adatok
--
-- plans/navigator_academy_implementation.md, 2. fejezet és na-03:
-- - az `audience` azt írja le, melyik felületet használja a felhasználó / melyik
--   felületen látszik a szektor (a jogosultság továbbra is a szerepkörben él);
-- - `birth_year` + `guardian_consent_status` a kiskorúak szülői hozzájárulásához
--   (a hozzájárulási folyamat maga a na-20-ban jön).
-- A meglévő felhasználók és szektorok ENGINEER / NOT_REQUIRED értéket kapnak.
-- =============================================================================

ALTER TABLE cadets
    ADD COLUMN audience VARCHAR(20) NOT NULL DEFAULT 'ENGINEER',
    ADD COLUMN birth_year SMALLINT,
    ADD COLUMN guardian_consent_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED';

ALTER TABLE cadets
    ADD CONSTRAINT cadets_audience_check CHECK (audience IN ('ENGINEER', 'ACADEMY')),
    ADD CONSTRAINT cadets_birth_year_check CHECK (birth_year IS NULL OR birth_year BETWEEN 1900 AND 2100),
    ADD CONSTRAINT cadets_guardian_consent_status_check
        CHECK (guardian_consent_status IN ('NOT_REQUIRED', 'PENDING', 'GRANTED', 'REVOKED'));

ALTER TABLE sectors
    ADD COLUMN audience VARCHAR(20) NOT NULL DEFAULT 'ENGINEER';

ALTER TABLE sectors
    ADD CONSTRAINT sectors_audience_check CHECK (audience IN ('ENGINEER', 'ACADEMY'));
