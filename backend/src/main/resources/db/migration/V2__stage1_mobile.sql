-- ============================================================
-- V2 — Stage 1 Mobile: Mission Group, CONTENT, FILL_IN_BLANK
-- ============================================================

-- 1. Mission tábla módosítások
ALTER TABLE missions RENAME COLUMN order_in_system TO order_index;
ALTER TABLE missions ALTER COLUMN order_index DROP NOT NULL;
ALTER TABLE missions ALTER COLUMN template_repository_url DROP NOT NULL;
ALTER TABLE missions ADD COLUMN IF NOT EXISTS content TEXT;
ALTER TABLE missions ADD COLUMN IF NOT EXISTS group_order INTEGER;
ALTER TABLE missions ADD COLUMN IF NOT EXISTS group_id UUID;

-- 2. MissionGroup tábla
CREATE TABLE IF NOT EXISTS mission_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    star_system_id UUID NOT NULL REFERENCES star_systems(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id UUID REFERENCES cadets(id),
    updated_by_id UUID REFERENCES cadets(id),
    order_index INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- 3. group_id FK a missions-re
ALTER TABLE missions
    ADD CONSTRAINT fk_missions_group
    FOREIGN KEY (group_id) REFERENCES mission_groups(id);

-- 4. FillInBlank táblák
CREATE TABLE IF NOT EXISTS fill_in_blank_definitions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mission_id UUID NOT NULL UNIQUE REFERENCES missions(id),
    template_text TEXT NOT NULL,
    pass_threshold INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS fill_in_blank_blanks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    definition_id UUID NOT NULL REFERENCES fill_in_blank_definitions(id),
    blanks_key VARCHAR(100) NOT NULL,
    order_index INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS fill_in_blank_options (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blank_id UUID NOT NULL REFERENCES fill_in_blank_blanks(id),
    option_text VARCHAR(500) NOT NULL,
    correct BOOLEAN NOT NULL,
    order_index INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS fill_in_blank_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cadet_id UUID NOT NULL REFERENCES cadets(id),
    mission_id UUID NOT NULL REFERENCES missions(id),
    score INTEGER NOT NULL,
    max_score INTEGER NOT NULL,
    percentage INTEGER NOT NULL,
    passed BOOLEAN NOT NULL,
    submitted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS fill_in_blank_answer_details (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id UUID NOT NULL REFERENCES fill_in_blank_attempts(id),
    blank_id UUID NOT NULL REFERENCES fill_in_blank_blanks(id),
    selected_option_id UUID REFERENCES fill_in_blank_options(id),
    correct BOOLEAN NOT NULL
);

-- 5. MissionGroupProgress táblák
CREATE TABLE IF NOT EXISTS mission_group_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cadet_id UUID NOT NULL REFERENCES cadets(id),
    group_id UUID NOT NULL REFERENCES mission_groups(id),
    next_mission_id UUID,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE(cadet_id, group_id)
);

CREATE TABLE IF NOT EXISTS mission_group_step_completions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    progress_id UUID NOT NULL REFERENCES mission_group_progress(id),
    mission_id UUID NOT NULL REFERENCES missions(id),
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE(progress_id, mission_id)
);
