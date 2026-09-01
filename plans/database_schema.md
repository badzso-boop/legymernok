# LégyMérnök.hu - Database Schema (Git-integrated)

> **📜 Historical planning document — not necessarily current.** This reflects the state of the project as of 2025-12-18 (its last edit), and may be superseded by later decisions or the actual implementation. Check the code or more recent docs in `plans/` before relying on a specific claim here.

This document contains the proposed structure of the project's database, in `PostgreSQL` dialect. The schema is tailored to the dedicated Git-server architecture.

```sql
-- Registry of cadets (users)
CREATE TYPE cadet_role_enum AS ENUM ('CADET', 'ADMIN');

CREATE TABLE cadets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role cadet_role_enum NOT NULL DEFAULT 'CADET',
    avatar_url VARCHAR(255),
    gitea_user_id BIGINT, -- Gitea's internal, numeric user ID, for synchronization
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

 CREATE TABLE permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE, -- e.g. 'mission:create'
    description VARCHAR(255) -- Human-readable description
);

CREATE TABLE roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(50) NOT NULL UNIQUE, -- e.g. 'ROLE_ADMIN'
    description VARCHAR(255)
);

CREATE TABLE roles_permissions (
    role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

 CREATE TABLE cadet_roles (
    cadet_id UUID REFERENCES cadets(id) ON DELETE CASCADE,
    role_id UUID REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (cadet_id, role_id)
);

-- Star systems (courses)
CREATE TABLE star_systems (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    icon_url VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Missions (the "planets" within a star system)
CREATE TYPE mission_type_enum AS ENUM ('CODING', 'CIRCUIT_SIMULATION');
CREATE TYPE difficulty_enum AS ENUM ('EASY', 'MEDIUM', 'HARD', 'EXPERT');

CREATE TABLE missions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    star_system_id UUID NOT NULL REFERENCES star_systems(id),
    name VARCHAR(255) NOT NULL,
    description_markdown TEXT, -- The mission's description, shown on the frontend
    mission_type mission_type_enum NOT NULL,
    difficulty difficulty_enum NOT NULL,
    template_repository_url VARCHAR(512) NOT NULL,
    order_in_system SMALLINT NOT NULL, -- Determines the ordering of missions
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(star_system_id, order_in_system)
);

-- Tests belonging to missions
CREATE TYPE test_language_enum AS ENUM ('PYTHON', 'JAVA', 'CSHARP');

CREATE TABLE mission_tests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mission_id UUID NOT NULL REFERENCES missions(id),
    test_code TEXT NOT NULL, -- The test code that runs against the user's code
    test_language test_language_enum NOT NULL,
    is_hidden BOOLEAN NOT NULL DEFAULT false, -- Whether the user can see this test
    description TEXT -- Short description of the test case
);

-- Linking cadets to their missions, and tracking progress
CREATE TYPE mission_status_enum AS ENUM ('LOCKED', 'NOT_STARTED', 'IN_PROGRESS', 'COMPLETED');

CREATE TABLE cadet_missions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cadet_id UUID NOT NULL REFERENCES cadets(id),
    mission_id UUID NOT NULL REFERENCES missions(id),
    status mission_status_enum NOT NULL DEFAULT 'NOT_STARTED',

    -- The cadet-specific Git repository URL for this mission
    repository_url VARCHAR(512),

    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(cadet_id, mission_id)
);
```
