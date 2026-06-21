CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE star_systems
    ADD COLUMN IF NOT EXISTS content_embedding vector(768);

CREATE INDEX IF NOT EXISTS idx_star_system_embedding
    ON star_systems USING ivfflat (content_embedding vector_cosine_ops)
    WITH (lists = 10);
