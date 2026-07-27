-- =============================================================================
-- V4: template_repository_url oszlop NOT NULL megszorításának feloldása
--
-- A Mission entitás mindig is nullable=true-t mondott erre a mezőre (a
-- CONTENT és FILL_IN_BLANK típusú missziókhoz szándékosan nincs Gitea repó,
-- lásd MissionService.initializeForgeMission), de az élő adatbázis oszlopa
-- ténylegesen NOT NULL maradt — soha nem lett Flyway-migrációval igazítva
-- az entitás definíciójához. Emiatt minden nem-CODING típusú misszió
-- létrehozása a sima admin űrlapon (nem a Forge-on át) not-null constraint
-- hibával elhasalt.
-- =============================================================================

ALTER TABLE missions ALTER COLUMN template_repository_url DROP NOT NULL;
