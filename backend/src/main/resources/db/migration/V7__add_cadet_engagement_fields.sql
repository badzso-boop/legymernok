-- =============================================================================
-- V7: Kadét engagement mezők (téma-preferencia, streak)
--
-- A frontend-redesign terv (plans/frontend_redesign_2026.md) 3.5 és 7.1
-- szekciója szerint. A streak "lustán" számol: nincs éjféli reset job, a
-- következő aktivitáskor derül ki, ha megszakadt a sorozat.
-- =============================================================================

ALTER TABLE cadets ADD COLUMN theme_preference VARCHAR(10) NOT NULL DEFAULT 'SPACE';
ALTER TABLE cadets ADD COLUMN current_streak INT NOT NULL DEFAULT 0;
ALTER TABLE cadets ADD COLUMN longest_streak INT NOT NULL DEFAULT 0;
ALTER TABLE cadets ADD COLUMN last_activity_date DATE;
