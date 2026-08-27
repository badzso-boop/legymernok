-- RAG chunk-index (plans/pr1_rag_chunking_architecture_2026.md).
--
-- Mindhárom source_type ugyanarra a missions.id-ra hivatkozik (a
-- MISSION_FILL_IN_BLANK is a misszió ID-jához kötött, nem a FillInBlankDefinition
-- sajátjához), ezért egyetlen FK mindhármat lefedi. Az ON DELETE CASCADE tudatos:
-- a reindex-hook-pontok csak a létrehozást/módosítást fedik le, a törlést NEM —
-- egy elfelejtett service-hívás csendben árva chunkokat hagyna az indexben,
-- amiket a chatbot továbbra is felszolgálna a törölt tartalomból.
CREATE TABLE IF NOT EXISTS content_chunks (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    source_type       VARCHAR(32) NOT NULL,
    source_id         UUID NOT NULL REFERENCES missions(id) ON DELETE CASCADE,
    -- NOT NULL DEFAULT '' és nem NULL-abilis: Postgres-ben két NULL sose egyenlő egy
    -- UNIQUE constraintben, tehát NULL-abilis file_path esetén a content_chunks_unique_chunk
    -- csendben KIkapcsolódna a fájlhoz nem köthető (MISSION / MISSION_FILL_IN_BLANK)
    -- chunkokra. Üres string-gel a védelem mindhárom source_type-ra megmarad.
    file_path         VARCHAR(500) NOT NULL DEFAULT '',
    chunk_index       INT NOT NULL,
    chunk_text        TEXT NOT NULL,
    content_embedding vector(768),
    -- Melyik modellel készült a vektor. Enélkül egy modellváltás (vagy a task-prefix
    -- bevezetése) csendben inkonzisztens állapotot hagyna, amíg valaki le nem futtatja
    -- a reindexet — semmi nem jelezné, hogy megtörtént-e.
    embedding_model   VARCHAR(64) NOT NULL,
    -- PUBLIC: bárki kontextusába kerülhet. AUTHOR_ONLY: csak a misszió tulajdonosa /
    -- admin láthatja (ld. plans/pr0_retrieval_security_2026.md 3. szakasz) — ide esik a
    -- referencia megoldás (solution.*).
    visibility        VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
    -- 'hungarian' és NEM 'simple': a 'simple' konfiguráció nem szótövez, tehát a
    -- "függvényt" keresés nem találná meg a "függvény" szót tartalmazó chunkot.
    search_vector     tsvector GENERATED ALWAYS AS (to_tsvector('hungarian', chunk_text)) STORED,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT content_chunks_source_type_check CHECK (source_type IN ('MISSION', 'MISSION_FILL_IN_BLANK', 'MISSION_CODE_FILE')),
    CONSTRAINT content_chunks_visibility_check CHECK (visibility IN ('PUBLIC', 'AUTHOR_ONLY')),
    CONSTRAINT content_chunks_unique_chunk UNIQUE (source_type, source_id, file_path, chunk_index)
);

-- SZÁNDÉKOSAN NINCS vektor-index ezen a táblán. A várható lépték néhány ezer chunk,
-- ahol az egzakt keresés milliszekundumokban mérhető; cserébe egy ANN-index a hibrid
-- keresésnél nehezen debugolható recall-hibákat hozna (nem lehetne megmondani, hogy egy
-- chunk azért nem jött fel, mert rossz az embedding, vagy mert az index nem találta meg).
-- Ha valaha mégis kell: HNSW, nem ivfflat (az ivfflat üres táblán épülve használhatatlan).
CREATE INDEX IF NOT EXISTS idx_content_chunks_search_vector
    ON content_chunks USING gin (search_vector);
CREATE INDEX IF NOT EXISTS idx_content_chunks_source
    ON content_chunks (source_type, source_id);
