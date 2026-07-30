# LégyMérnök.hu — Claude Code Útmutató

## Együttműködési szabályok

- **Tesztet és buildet futtathatsz saját döntésből, de kizárólag háttérben** (pl. `run_in_background`), hogy a futása alatt tudj más feladaton is dolgozni, ne blokkolja a session-t. A háttérfolyamat eredményét (siker/hiba, log) mindig oszd meg a userrel, mielőtt "kész"-nek mondanál egy feladatot.
- **Commitolhatsz és pusholhatsz is saját döntésből** — a repo ezen a szerveren fut (nem a user lokális gépén), neki macerás itt kódolni, szóval ez alapértelmezetten megengedett, nem kell külön kérni minden alkalommal.
- **Verziókezelési irányelv:** kis, gyakori commitokban dolgozz — egy logikai változás egy commit, ne gyűjts össze mindent egybe. Minden változtatáshoz külön branch + Pull Request tartozzon (`gh pr create`), még akkor is, ha a módosítás mérete ezt önmagában nem indokolná — így a `main` mindig tiszta és review-zható marad. SOHA ne pusholj közvetlenül `main`-re.
- A kommunikáció magyarul folyik.
- Ha hibajavítást kérsz, csak azt változtasd meg ami szükséges — ne refaktorálj, ne adj hozzá felesleges kommentet.
- **Nagyobb módosítás után (3+ fájl, vagy új service/komponens) indítsd el a `code-quality-reviewer` agentet** mielőtt azt mondod "kész". Hívás: `Agent({ subagent_type: "code-quality-reviewer", prompt: "..." })`

---

## Implementálás előtt — kötelező ellenőrzések

Mielőtt implementálni kezdesz, olvasd el az érintett területek kontextusát:

- **Új React komponens elhelyezése előtt:** olvasd el `frontend/src/App.tsx` és `frontend/src/router/index.tsx` — azonosítsd hol lesz a komponens a fa-hierarchiában
- **Docker módosítás előtt:** olvasd el a teljes `docker-compose.yml`-t — ellenőrizd az image verziókat és a volume-mountokat
- **Új backend endpoint előtt:** ellenőrizd kell-e `SecurityConfig.java`-ban fehérlistázni
- **`@Transactional` használatakor:** write-ot végző metódus soha ne legyen `readOnly=true`, és ne hívj write-ot `readOnly` outer tranzakcióból

---

## Implementálás után — kötelező ellenőrzések

Minden változtatás után, mielőtt "kész"-t mondasz:

1. **TypeScript ellenőrzés** (ha frontend módosult): jelezd a usernek futtassa: `cd frontend && npx tsc --noEmit`
2. **Docker rebuild** (ha `docker-compose.yml` vagy backend módosult): jelezd a usernek futtassa: `docker compose up <service> --build -d`
3. **3+ fájl módosítása esetén:** indítsd el a `code-quality-reviewer` agentet

---

## Architektúra megszorítások és Ismert Gotchas

### Docker / Infrastruktúra
- **pgvector:** A `postgres` service image-nek `pgvector/pgvector:pg16` kell lenni, **NEM** `postgres:16` — különben a `vector` extension nem érhető el
- **Ollama GGUF modellek:** A GGUF fájl mountolva van `/gguf/` alá, de az Ollamának regisztrálni kell: `ollama create <név> -f Modelfile` (ahol a Modelfile: `FROM /gguf/<fájlnév>.gguf`). A `CHAT_MODEL` env változónak ez a regisztrált név kell legyen
- **Backend rebuild:** `--force-recreate` NEM fordítja újra a Java kódot — ahhoz `--build` flag kell (vagy `mvn package` + image rebuild)
- **AI Service modell config:** `CHAT_MODEL` és `EMBED_MODEL` a `.env`-ből jön; default értékek: `nomic-embed-text` (embed) és `gemma4-coding` (chat)
- **Build-time bebakeolt érték (pl. `VITE_API_URL`) módosítása "nem fog":** `docker compose build <service>` cache nélkül gyakran egy régi rétegből hasznosítja újra az `npm run build`/`mvn package` eredményét. Ha egy `.env`-ben módosított, build-időben beégetett érték élesben nem változik, próbáld újra `--no-cache`-lel, mielőtt más okot keresnél
- **Backend konténer újraindítása/újraépítése után mindig indítsd újra a `frontend` konténert is** (`docker restart legymernok-frontend`) — a frontend saját nginx-e (`proxy_pass http://backend:8080/...`) a régi, már halott backend-konténer IP-jére marad gyorsítótárazva, amíg rá nem kényszeríted az újrafeloldásra, addig 502-t ad

### Frontend — React Router
- **Router hook-ok** (`useLocation`, `useParams`, `useNavigate`, `useNavigate`) **kizárólag a Router kontextuson belül** működnek. Ez azt jelenti: minden komponens, ami ezeket használja, a `createHashRouter`-en belül kell legyen — azaz az `App.tsx`-ben `<RouterProvider>` alá, NEM mellé
- **`ChatWidget` elhelyezése:** A widget a `RootLayout`-ban van (`router/index.tsx`), mert `useLocation()`-t használ. Ha valaha ki kell mozgatni, nem mehet `App.tsx`-be `RouterProvider` mellé
- **`AuthContext`** hasonlóan: `AuthProvider` az `App.tsx` gyökerén van, minden komponens alatta kell legyen

### Backend — Spring
- **`@Transactional(readOnly=true)`:** Belső repository hívás join-olja az outer tranzakciót, de ha az outer `readOnly`, write műveletek hibát dobnak. Write-ot végző service metódus mindig sima `@Transactional` legyen
- **Új permission:** Ha új permission-t vezetsz be, a `DataInitializer`-ben is fel kell venni a megfelelő szerepkörökhöz

---

## Projekt áttekintés

**LégyMérnök.hu** — gamifikált oktatási platform mérnökhallgatóknak, űrtéma.
- **Kadétok** küldetéseket teljesítenek **csillagrendszerekben** (kurzusok)
- Kódolás, kvíz, áramkörszimuláció mission típusok
- Gitea-alapú kódtárolás + CI/CD visszacsatolás

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
| `backend` | 8090 (hoszt) → 8080 (konténer) | Spring Boot REST API — a hoszt port 2026-07-21 óta 8090, korábban 8080 volt, de az ütközött a szerveren futó qbittorrenttel |
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

## Biztonsági alapelvek — kötelező ellenőrzőlista

Ezek konkrét, a repóban ténylegesen előfordult hibaosztályok alapján lettek felvéve
(lásd `plans/security_audit_2026-07-30.md` a teljes, aktuális állapotfelmérésért). Új
funkció/endpoint írásakor ezeket MINDIG ellenőrizd, ne csak a "boldog utat":

- **Minden mutáló endpoint owner-ellenőrzést igényel**, nem elég a `@PreAuthorize`
  permission-check önmagában. Minta: `mission:edit` jogú user NEM férhet hozzá más
  tulajdonában lévő entitáshoz, csak ha külön `*_any` permissionje is van (lásd
  `MissionService.requireMissionEditAccess`). Ez a minta hiányzott a `FillInBlank` és a
  `StarSystem.reorderItems`/`MissionGroup` végpontokon — mielőtt egy új service/controller
  metódust írsz, nézd meg van-e hasonló meglévő minta, és kövesd azt.
- **Minden Gitea-fájlkezelő metódus** (olvasó ÉS író) hívja a `GiteaService.validateFilePath`-et,
  ne csak az író metódusok (`uploadFile`, `deleteFile`, `renameFile`) — az olvasók
  (`getFileContent`, `getFileInfo`, `getRepoContents`) is, mihelyt bármelyik hívási útvonaluk
  user-supplied path-ot kaphat.
- **Minden POST/PUT DTO-hoz `@Valid` a controlleren + `@NotNull`/`@NotBlank`/`@Size`/`@Email`
  a DTO mezőin.** Ne bízz abban, hogy a service réteg majd validál.
- **Új WebSocket/STOMP endpoint SOSE legyen alapból `permitAll()` + wildcard origin.**
  Ha publikusnak kell lennie, az explicit, tudatos döntés legyen, indoklással — alapértelmezés
  a JWT-védelem + a topic-hoz tartozó ownership-ellenőrzés.
- **Kivétel-kezelésben (`GlobalExceptionHandler`) sose engedj ki nyers `ex.getMessage()`-et**
  a kliens felé generikus (nem üzleti) kivételeknél — csak fix, semmitmondó üzenetet.
- **Lombok `@Data`/`@ToString` entitásnál, aminek van jelszó/hash/titkos mezője, sose logold
  ki a teljes objektumot** (`log.info("...", entity)`) — a generált `toString()` mindent
  kiír. Csak a szükséges mezőt (pl. `.getUsername()`) logold.
- **`docker-compose.yml`-ben új service portot alapból NE publikálj kifelé** — csak akkor, ha
  kifejezetten szükséges a hoszt felől való közvetlen elérés. Jelszót/secretet mindig `.env`-ből
  vegyél át (`${VAR}`), sose írj nyers értéket közvetlenül a compose fájlba.
- **Új entitásmező/constraint bevezetésekor mindig adj hozzá Flyway migrációt is**, még ha
  `ddl-auto=validate` (jelenlegi beállítás) induláskor hibát is dobna eltérésnél — ez a
  projektben már 3× előfordult hibaosztály (`order_in_system`→`order_index`,
  `template_repository_url` NOT NULL, `mission_type` CHECK constraint).

## Nyitott ismert hibák

- **Gitea orphan repo**: DB tranzakció buktán Gitea repo árva marad (nincs rollback)
- A korábbi "hardkódolt titkos adatok az `application.properties`-ben" hiba **teljesen javítva
  van** (2026-07-30-i audit megerősítette — minden secret `${ENV_VAR}`-ból jön).
- **A teljes, aktuális biztonsági/logikai állapotfelmérés**: `plans/security_audit_2026-07-30.md`
  — ha biztonsági kérdésben dolgozol ezen a repón, először azt nézd meg, ne feltételezd hogy ez
  a lista (fent) minden nyitott pontot tartalmaz.

> A következő hibák **már javítva vannak** (ne reportáld újra):
> - submissionHash NPE a MissionResult mentésekor
> - Dupla `/api/api/quiz/` prefix a client.ts-ben
> - Quiz timer nem auto-submitelt lejáratkor
