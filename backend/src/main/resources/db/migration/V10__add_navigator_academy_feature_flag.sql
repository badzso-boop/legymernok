-- =============================================================================
-- V10: Navigátor Akadémia feature flag
--
-- A `/api/academy/**` végpontokat kapuzza (AcademyFeatureGateFilter): kikapcsolt
-- állapotban 404-et adnak. Alapértelmezetten KIKAPCSOLVA.
--
-- FIGYELEM: a PR #67 (feat/rag-chunking) is V10-es migrációt hoz — lásd CLAUDE.md.
-- =============================================================================

INSERT INTO feature_flags (key, enabled, description)
VALUES ('navigator_academy', false, 'Navigátor Akadémia végpontok és felület engedélyezése')
ON CONFLICT (key) DO NOTHING;
