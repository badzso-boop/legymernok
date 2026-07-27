-- =============================================================================
-- V5: mission_type CHECK constraint frissítése CONTENT és FILL_IN_BLANK típusokkal
--
-- A MissionType Java enum a Stage 1 mobil-barát munka során bővült
-- CONTENT és FILL_IN_BLANK értékekkel, de az adatbázis
-- missions_mission_type_check megszorítása sosem lett migrációval
-- frissítve — még mindig csak CODING/CIRCUIT_SIMULATION/QUIZ-et engedett,
-- ezért minden CONTENT vagy FILL_IN_BLANK típusú misszió létrehozása
-- check constraint hibával elhasalt.
-- =============================================================================

ALTER TABLE missions DROP CONSTRAINT missions_mission_type_check;

ALTER TABLE missions ADD CONSTRAINT missions_mission_type_check
    CHECK (mission_type IN ('CODING', 'CIRCUIT_SIMULATION', 'QUIZ', 'CONTENT', 'FILL_IN_BLANK'));
