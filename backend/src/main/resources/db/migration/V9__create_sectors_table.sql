-- Sector Map (issue #38): felső szintű, témakör szerinti csoportosítás a
-- star_systems fölött. Nullable FK — a meglévő rendszerek "Besorolatlan"
-- állapotban maradnak, nincs kényszerített backfill (ld. plans/sector_map_2026.md).

CREATE TABLE sectors (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    icon_url    VARCHAR(255),
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE star_systems
    ADD COLUMN sector_id UUID REFERENCES sectors(id) ON DELETE SET NULL;
