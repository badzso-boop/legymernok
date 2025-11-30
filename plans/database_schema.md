# LégyMérnök.hu - Adatbázis Séma (Git-integrált)

Ez a dokumentum a projekt adatbázisának javasolt struktúráját tartalmazza, `PostgreSQL` dialektusban. A séma a dedikált Git szerveres architektúrához van igazítva.

```sql
-- Kadétok (felhasználók) nyilvántartása
CREATE TYPE cadet_role_enum AS ENUM ('CADET', 'ADMIN');

CREATE TABLE cadets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role cadet_role_enum NOT NULL DEFAULT 'CADET',
    avatar_url VARCHAR(255),
    gitea_user_id BIGINT, -- A Gitea belső, numerikus user ID-ja a szinkronizációhoz
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Csillagrendszerek (kurzusok)
CREATE TABLE star_systems (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    icon_url VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Küldetések (a "bolygók" a csillagrendszereken belül)
CREATE TYPE mission_type_enum AS ENUM ('CODING', 'CIRCUIT_SIMULATION');
CREATE TYPE difficulty_enum AS ENUM ('EASY', 'MEDIUM', 'HARD', 'EXPERT');

CREATE TABLE missions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    star_system_id UUID NOT NULL REFERENCES star_systems(id),
    name VARCHAR(255) NOT NULL,
    description_markdown TEXT, -- A küldetés leírása, ami a frontend-en megjelenik
    mission_type mission_type_enum NOT NULL,
    difficulty difficulty_enum NOT NULL,
    template_repository_url VARCHAR(512) NOT NULL,
    order_in_system SMALLINT NOT NULL, -- Meghatározza a küldetések sorrendjét
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(star_system_id, order_in_system)
);

-- Küldetésekhez tartozó tesztek
CREATE TYPE test_language_enum AS ENUM ('PYTHON', 'JAVA', 'CSHARP');

CREATE TABLE mission_tests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mission_id UUID NOT NULL REFERENCES missions(id),
    test_code TEXT NOT NULL, -- A tesztkód, amit a felhasználó kódján futtatunk
    test_language test_language_enum NOT NULL,
    is_hidden BOOLEAN NOT NULL DEFAULT false, -- A felhasználó láthatja-e a tesztet
    description TEXT -- Rövid leírás a tesztesetről
);

-- A kadétok és a küldetéseik összerendelése, a haladás követése
CREATE TYPE mission_status_enum AS ENUM ('LOCKED', 'NOT_STARTED', 'IN_PROGRESS', 'COMPLETED');

CREATE TABLE cadet_missions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cadet_id UUID NOT NULL REFERENCES cadets(id),
    mission_id UUID NOT NULL REFERENCES missions(id),
    status mission_status_enum NOT NULL DEFAULT 'NOT_STARTED',
    
    -- A küldetéshez tartozó, kadét-specifikus Git repository URL-je
    repository_url VARCHAR(512),
    
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(cadet_id, mission_id)
);
```
