-- =============================================================================
-- V1: Domain séma tiszta újragenerálása
--
-- MEGMARAD (adat megőrzés): cadets, cadet_roles, permissions, roles, roles_permissions
-- TÖRLÉS + ÚJRA: star_systems, missions és minden kapcsolódó tábla
-- TÖRLÉS (elavult circuit/analog): minden régi áramkör és mérés tábla
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Elavult táblák törlése (circuit simulation, units)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS cadet_verification_results CASCADE;
DROP TABLE IF EXISTS analog_verification_checks CASCADE;
DROP TABLE IF EXISTS cadet_analog_saves CASCADE;
DROP TABLE IF EXISTS analog_circuit_definitions CASCADE;
DROP TABLE IF EXISTS cadet_circuit_component_properties CASCADE;
DROP TABLE IF EXISTS cadet_circuit_connections CASCADE;
DROP TABLE IF EXISTS cadet_circuit_components CASCADE;
DROP TABLE IF EXISTS cadet_circuit_saves CASCADE;
DROP TABLE IF EXISTS circuit_verification_checks CASCADE;
DROP TABLE IF EXISTS circuit_def_component_properties CASCADE;
DROP TABLE IF EXISTS circuit_def_connections CASCADE;
DROP TABLE IF EXISTS circuit_def_components CASCADE;
DROP TABLE IF EXISTS circuit_definitions CASCADE;
DROP TABLE IF EXISTS component_electrical_specs CASCADE;
DROP TABLE IF EXISTS component_pin_definitions CASCADE;
DROP TABLE IF EXISTS units_of_measure CASCADE;

-- -----------------------------------------------------------------------------
-- 2. Domain táblák törlése (FK sorrendben)
-- -----------------------------------------------------------------------------
DROP TABLE IF EXISTS fill_in_blank_answer_details CASCADE;
DROP TABLE IF EXISTS fill_in_blank_attempts CASCADE;
DROP TABLE IF EXISTS fill_in_blank_options CASCADE;
DROP TABLE IF EXISTS fill_in_blank_blanks CASCADE;
DROP TABLE IF EXISTS fill_in_blank_definitions CASCADE;
DROP TABLE IF EXISTS mission_group_step_completions CASCADE;
DROP TABLE IF EXISTS mission_group_progress CASCADE;
DROP TABLE IF EXISTS cadet_missions CASCADE;
DROP TABLE IF EXISTS mission_results CASCADE;
DROP TABLE IF EXISTS quiz_sessions CASCADE;
DROP TABLE IF EXISTS missions CASCADE;
DROP TABLE IF EXISTS mission_groups CASCADE;
DROP TABLE IF EXISTS star_systems CASCADE;

-- -----------------------------------------------------------------------------
-- 2b. Alap táblák (cadets, roles, permissions, RBAC junction táblák)
--
-- Ezeket eredetileg Hibernate ddl-auto hozta létre, mielőtt a projekt Flywayre
-- állt át — ezért soha nem került SQL-be. Fresh (üres) adatbázison enélkül a
-- lenti CREATE TABLE-ök FK-referenciái (pl. star_systems.owner_id → cadets.id)
-- meghiúsulnak, mert a cadets tábla nem létezne. Az élő éles adatbázison ez a
-- migráció sosem fut le ténylegesen (spring.flyway.baseline-version=1 miatt a
-- séma V1-ig baseline-olt, nem migrált) — ezért ez a blokk csak fresh
-- telepítésen fut, az élő adatot nem érinti. IF NOT EXISTS a biztonság kedvéért.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS cadets (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(255) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(255),
    avatar_url    VARCHAR(255),
    gitea_user_id BIGINT,
    created_at    TIMESTAMPTZ,
    updated_at    TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS permissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS cadet_roles (
    cadet_id UUID NOT NULL REFERENCES cadets(id),
    role_id  UUID NOT NULL REFERENCES roles(id),
    PRIMARY KEY (cadet_id, role_id)
);

CREATE TABLE IF NOT EXISTS roles_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id),
    permission_id UUID NOT NULL REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

-- -----------------------------------------------------------------------------
-- 3. Domain táblák újralétrehozása (helyes séma)
-- -----------------------------------------------------------------------------

CREATE TABLE star_systems (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    icon_url    VARCHAR(255),
    owner_id    UUID REFERENCES cadets(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE mission_groups (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    star_system_id UUID NOT NULL REFERENCES star_systems(id),
    name          VARCHAR(255) NOT NULL,
    description   TEXT,
    owner_id      UUID REFERENCES cadets(id),
    updated_by_id UUID REFERENCES cadets(id),
    order_index   INTEGER NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE missions (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    star_system_id          UUID NOT NULL REFERENCES star_systems(id),
    name                    VARCHAR(255) NOT NULL,
    description_markdown    TEXT,
    template_repository_url VARCHAR(255),
    mission_type            VARCHAR(50) NOT NULL
                                CONSTRAINT missions_mission_type_check
                                CHECK (mission_type IN ('CODING','CIRCUIT_SIMULATION','QUIZ','CONTENT','FILL_IN_BLANK')),
    difficulty              VARCHAR(20) NOT NULL
                                CONSTRAINT missions_difficulty_check
                                CHECK (difficulty IN ('EASY','MEDIUM','HARD','EXPERT')),
    order_index             INTEGER,
    owner_id                UUID REFERENCES cadets(id),
    verification_status     VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
                                CONSTRAINT missions_verification_status_check
                                CHECK (verification_status IN ('PENDING','SUCCESS','FAILED','REVIEW_NEEDED','DRAFT')),
    group_id                UUID REFERENCES mission_groups(id),
    group_order             INTEGER,
    content                 TEXT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE mission_results (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cadet_id         UUID NOT NULL REFERENCES cadets(id),
    mission_id       UUID NOT NULL REFERENCES missions(id),
    score            DOUBLE PRECISION NOT NULL,
    max_score        DOUBLE PRECISION NOT NULL,
    percentage       DOUBLE PRECISION NOT NULL,
    detailed_answers JSONB,
    is_late          BOOLEAN NOT NULL DEFAULT false,
    completed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    submission_hash  VARCHAR(255) NOT NULL
);

CREATE TABLE quiz_sessions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cadet_id       UUID NOT NULL REFERENCES cadets(id),
    mission_id     UUID NOT NULL REFERENCES missions(id),
    start_time     TIMESTAMPTZ NOT NULL,
    end_time_limit TIMESTAMPTZ NOT NULL,
    quiz_snapshot  JSONB NOT NULL,
    current_answers JSONB,
    completed      BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE cadet_missions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cadet_id       UUID NOT NULL REFERENCES cadets(id),
    mission_id     UUID NOT NULL REFERENCES missions(id),
    status         VARCHAR(20) NOT NULL
                       CONSTRAINT cadet_missions_status_check
                       CHECK (status IN ('LOCKED','NOT_STARTED','IN_PROGRESS','COMPLETED')),
    repository_url VARCHAR(255),
    started_at     TIMESTAMPTZ,
    completed_at   TIMESTAMPTZ,
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (cadet_id, mission_id)
);

CREATE TABLE mission_group_progress (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cadet_id        UUID NOT NULL REFERENCES cadets(id),
    group_id        UUID NOT NULL REFERENCES mission_groups(id),
    next_mission_id UUID,
    completed       BOOLEAN NOT NULL DEFAULT false,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    UNIQUE (cadet_id, group_id)
);

CREATE TABLE mission_group_step_completions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    progress_id  UUID NOT NULL REFERENCES mission_group_progress(id),
    mission_id   UUID NOT NULL REFERENCES missions(id),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (progress_id, mission_id)
);

CREATE TABLE fill_in_blank_definitions (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mission_id    UUID NOT NULL UNIQUE REFERENCES missions(id),
    template_text TEXT NOT NULL,
    pass_threshold INTEGER,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fill_in_blank_blanks (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    definition_id UUID NOT NULL REFERENCES fill_in_blank_definitions(id),
    blanks_key    VARCHAR(255) NOT NULL,
    order_index   INTEGER NOT NULL
);

CREATE TABLE fill_in_blank_options (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    blank_id    UUID NOT NULL REFERENCES fill_in_blank_blanks(id),
    option_text VARCHAR(255) NOT NULL,
    correct     BOOLEAN NOT NULL,
    order_index INTEGER NOT NULL
);

CREATE TABLE fill_in_blank_attempts (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cadet_id     UUID NOT NULL REFERENCES cadets(id),
    mission_id   UUID NOT NULL REFERENCES missions(id),
    score        INTEGER NOT NULL,
    max_score    INTEGER NOT NULL,
    percentage   INTEGER NOT NULL,
    passed       BOOLEAN NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE fill_in_blank_answer_details (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id         UUID NOT NULL REFERENCES fill_in_blank_attempts(id),
    blank_id           UUID NOT NULL REFERENCES fill_in_blank_blanks(id),
    selected_option_id UUID REFERENCES fill_in_blank_options(id),
    correct            BOOLEAN NOT NULL
);
