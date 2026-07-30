-- =============================================================================
-- V6: Feature flag rendszer
--
-- Admin által ki/bekapcsolható funkció-kapcsolók. Egyetlen kezdeti flag: az
-- AI chatbot widget, alapértelmezetten KIKAPCSOLVA.
-- =============================================================================

CREATE TABLE feature_flags (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key         VARCHAR(100) NOT NULL UNIQUE,
    enabled     BOOLEAN NOT NULL DEFAULT false,
    description VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO feature_flags (key, enabled, description)
VALUES ('ai_chatbot', false, 'AI chatbot widget megjelenítése bejelentkezett felhasználóknak')
ON CONFLICT (key) DO NOTHING;
