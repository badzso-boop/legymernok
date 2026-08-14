-- =============================================================================
-- V8: Egyirányú követés (Follow)
--
-- A frontend-redesign terv 7.2 szekciója szerint: Duolingo-mintázatú, egyirányú
-- követés (nincs elfogadás), nem a Wrenchly-stílusú kétirányú barát-kérés.
-- =============================================================================

CREATE TABLE follows (
    follower_id UUID NOT NULL REFERENCES cadets(id),
    followee_id UUID NOT NULL REFERENCES cadets(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followee_id)
);

CREATE INDEX idx_follows_followee ON follows (followee_id);
