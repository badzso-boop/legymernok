# LégyMérnök.hu — Claude Code Útmutató

## Együttműködési szabályok

- **NE futtass tesztet, buildet, ne commitolj, ne pusholj.** Minden ilyen műveletnél jelezd a usernek mit futtasson.
- A kommunikáció magyarul folyik.
- Ha hibajavítást kérsz, csak azt változtasd meg ami szükséges — ne refaktorálj, ne adj hozzá felesleges kommentet.

---

## Projekt áttekintés

**LégyMérnök.hu** — gamifikált oktatási platform mérnökhallgatóknak, űrtéma.
- **Kadétok** küldetéseket teljesítenek **csillagrendszerekben** (kurzusok)
- Kódolás, kvíz, áramkörszimuláció mission típusok
- Gitea-alapú kódtárolás + CI/CD visszacsatolás
- Jelenlegi ág: `mission-forge`, nyitott PR: #9 (mission-forge → main)

---

## Monorepo struktúra

```
legymernok/
├── backend/          # Spring Boot 3.4.1, Java 17
├── frontend/         # React 19, TypeScript, Vite 7
├── plans/            # Tervezési dokumentumok (.md)
│   ├── mission-forge.md       # Mission Forge feature spec
│   ├── gamification_roadmap.md # Fejlesztési fázisok
│   └── new_direction_2026.md  # 2026-os UX/irányvonal
├── docker-compose.yml
└── runner-config.yaml         # Gitea Actions runner konfig
```

---

## Docker szolgáltatások

| Szolgáltatás | Port | Leírás |
|---|---|---|
| `postgres` | 5432 | PostgreSQL 16 |
| `gitea` | 3001 (UI), 2222 (SSH) | Gitea 1.25.0 self-hosted Git |
| `backend` | 8080 | Spring Boot REST API |
| `frontend` | 3000 | React SPA (Nginx prod) |
| `runner` | — | Gitea Actions runner |

Hálózat: `legymernok-net` (bridge, external)

---

## Környezeti változók (.env)

```
GITEA_API_URL=http://gitea:3000/api/v1
GITEA_ADMIN_USERNAME=legymernok_admin
GITEA_ADMIN_PASSWORD=<kötelező>
GITEA_ADMIN_TOKEN=<kötelező>
JWT_SECRET=<kötelező>
MISSION_VERIFICATION_SECRET=<kötelező>
REGISTRATION_TOKEN=<kötelező, Gitea runner>
```

Mindent `.env`-ből olvas — `application.properties`-ben nincsenek hardkódolt titkok.

---

## Gitea integráció — kulcsfontosságú tudnivalók

**Modell:** Admin-owned, user-collaborator
- Minden mission repo az admin fiók alatt van (`legymernok_admin`)
- A felhasználó write collaboratorként kap hozzáférést
- Regisztrációkor Gitea-felhasználó is létrejön a kadétnak

**Template repók** (admin fiókban):
- `mission-js-template` — JavaScript missions
- `mission-python-template` — Python missions
- `mission-quiz-template` — Quiz missions (quiz.json)

**Ismert architectural korlát:** Ha a DB tranzakció megbukik a Gitea repo létrehozása után, a repo árvává válik (nincs rollback Gitea-ra). Ez nyitott issue.

---

## Biztonság — Permission rendszer

**JWT stateless**, Spring Security RBAC.
Engedélyek `@PreAuthorize("hasAuthority('permission:action')")` annoátcióval.

### Kulcs permissionök

| Kategória | Permissionök |
|---|---|
| Mission | `mission:read`, `mission:start`, `mission:create`, `mission:edit`, `mission:delete`, `mission:edit_any`, `mission:delete_any`, `mission:create_any_system` |
| StarSystem | `starsystem:read`, `starsystem:create`, `starsystem:edit`, `starsystem:delete`, `starsystem:edit_any`, `starsystem:delete_any` |
| User | `user:read`, `user:create`, `user:edit`, `user:delete` |
| Role | `role:read`, `role:write` |
| Quiz | `quiz:view_results`, `quiz:manage` |
| Logs | `logs:read` |

### Alapértelmezett szerepkörök
- `ROLE_CADET`: mission:read, start, create | starsystem:read, create | quiz:view_results
- `ROLE_ADMIN`: minden permission

### Nyilvános endpointok (JWT nélkül)
- `POST /api/auth/**`
- `/api/mission-verification/**` (Gitea callback)
- `/ws-log/**`, `/v3/api-docs/**`, `/swagger-ui/**`

---

## Fejlesztői workflow

### Backend tesztelés (user futtatja)
```bash
cd backend
mvn test                              # Összes teszt
mvn test -Dtest=MissionServiceTest    # Egy teszt osztály
```

### Frontend tesztelés (user futtatja)
```bash
cd frontend
npm test          # Vitest unit tesztek
npm run cy:open   # Cypress E2E interaktív
npm run cy:run    # Cypress headless
```

### Build (user futtatja)
```bash
cd backend && mvn package -DskipTests
cd frontend && npm run build
```

### Fejlesztői szerver (user futtatja)
```bash
cd frontend && npm run dev    # http://localhost:5173
# vagy docker compose up
```

---

## Nyitott ismert hibák

- **Gitea orphan repo**: DB tranzakció buktán Gitea repo árva marad (nincs rollback)
- **Hardkódolt titkos adatok** az `application.properties`-ben (részben javítva)

> A következő hibák **már javítva vannak** (ne reportáld újra):
> - submissionHash NPE a MissionResult mentésekor
> - Dupla `/api/api/quiz/` prefix a client.ts-ben
> - Quiz timer nem auto-submitelt lejáratkor
