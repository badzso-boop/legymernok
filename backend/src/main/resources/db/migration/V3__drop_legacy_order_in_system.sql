-- =============================================================================
-- V3: Elavult 'order_in_system' oszlop törlése a missions táblából
--
-- A Mission entitás mezője már régen orderIndex-re lett átnevezve
-- (Hibernate oszlop: order_index, nullable), de a Hibernate
-- ddl-auto=update sosem törli az elárvult oszlopokat. A régi
-- 'order_in_system' oszlop NOT NULL maradt, semmi nem tölti ki,
-- ezért minden mission INSERT elhasalt rajta.
-- =============================================================================

ALTER TABLE missions DROP COLUMN IF EXISTS order_in_system;
