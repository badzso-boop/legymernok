# Navigátor Akadémia — implementációs terv

> **Állapot:** TERV, nincs implementáció · **Készült:** 2026-09-15
> **Szakmai háttér:** `dumb-research/terv/PISA_FEJLESZTESI_TERV.md` (a `dumb` branchen): koncepció, pedagógiai alapelvek, kritikánkénti megoldások és bizonyítékok. Ez a dokumentum csak a **technikai megvalósítás** sorrendjét és a PR-bontást rögzíti.

---

## 1. Rögzített döntések

| # | Döntés | Megjegyzés |
|---|---|---|
| D1 | **Az akadémia migrációi a `V10`-től indulnak.** Feltételezés: ez a munka a PR #67 (RAG chunking, `feat/rag-chunking`) előtt kerül a `main`-re | ⚠️ **FIGYELEM:** a PR #67 szintén `V10__create_content_chunks.sql`-t hoz. Merge előtt a PR #67 migrációját át kell számozni a következő szabad verzióra, és a `pr1_rag_chunking_architecture_2026.md` / `ai_chatbot_upgrade_2026.md` `V10`-hivatkozásait is frissíteni kell. Ha a PR #67 mégis előbb megy be, az akadémia számozása tolódik. Lásd `CLAUDE.md` Gotchas. |
| D2 | **Belépési pont most `/#/akademia`, később aldomain** (pl. `akademia.legymernok.ujjweb.hu`) | Az aldomain ugyanazt a frontend buildet szolgálja ki, a hostname alapján választ nyitóoldalt |
| D3 | **Előbb a teljes backend, utána a frontend** | A frontend-szakasz a backend-szakaszok lezárása után indul |
| D4 | **`audience` mező a felhasználón, nincs külön kadét-szerepkör** az akadémiának. A szerepkör a *jogosultságot* írja le, az `audience` a *termékélményt* | Részletek a 2. fejezetben |
| D5 | **Minden PR közvetlenül a `main`-re, a `navigator_academy` feature flag mögött** | Nincs hosszú életű gyűjtőbranch |

---

## 2. Szerepkör vs. `audience` mező

A két fogalom mást ír le:
- **szerepkör (Role / Permission):** *mit tehet* a felhasználó (pl. `mission:start`, `class:manage`);
- **`audience`:** *melyik terméket használja* (mérnöki felület vagy akadémia): nyitóoldal, navigáció, látható szektorok, streak-mód, téma.

**A választás: `audience` mező, és a meglévő `ROLE_CADET` marad.**

| Szempont | Külön `ROLE_ACADEMY_CADET` | `audience` mező + `ROLE_CADET` |
|---|---|---|
| Jogosultságok | Duplikálódnának: az akadémiás kadét ugyanazt teheti (feladatot indít, eredményt lát) | Egy helyen, a meglévő mintában |
| Tartalom láthatósága (mérnöki vs. akadémiás szektor) | Permissionnel rosszul kifejezhető („ezt a szektort látja”) | Szektor-szintű `audience` szűrés, egyszerű lekérdezés |
| Kiskorúak védelme | Nem szerepkör-kérdés: egy 15 éves mérnöki érdeklődésű diák is kiskorú | Külön mezők: `birth_year`, `guardian_consent_status`; a korlátozások ezekhez kötődnek |
| Átjárás (pl. később a mérnöki oldalra is átmegy) | Szerepkörcsere, jogosultság-migráció | Egy mező átállítása, vagy mindkét élmény elérhető |
| Mérnökhallgató mint mentor | Két kadét-szerepkör egy usernél, zavaros | `ROLE_CADET` + `ROLE_MENTOR`, `audience = ENGINEER` |

**Valódi új szerepkörök ott kellenek, ahol valóban más a jogosultság:**
- `ROLE_TEACHER`: osztálykezelés, kiosztás, a saját osztály eredményei, kódolósor;
- `ROLE_MENTOR`: hozzárendelt kadétok elakadásai;
- `ROLE_PARENT`: a saját gyermek összefoglalója.

A `Cadet.roles` már `Set<Role>` (`model/cadet/Cadet.java:48`), tehát egy felhasználónak több szerepköre lehet.

**Adatmodell (na-03):**
- `cadets.audience` — `VARCHAR`, CHECK (`ENGINEER`, `ACADEMY`), default `ENGINEER` a meglévő felhasználóknak;
- `cadets.birth_year` — nullable;
- `cadets.guardian_consent_status` — `NOT_REQUIRED` / `PENDING` / `GRANTED` / `REVOKED`;
- `sectors.audience` — melyik élményben látszik a szektor.

---

## 3. PR-szabályok

- **Branch:** `feat/na-<sorszám>-<rövid-leírás>`, pl. `feat/na-04-unit-schema`, mindig a friss `origin/main`-ről.
- **Méret:** egy logikai egység, lehetőleg kb. 300–600 sor kód (a tesztek ezen felül).
- **Tartalom:** tesztek, Flyway-migráció, ha a séma változik (`CLAUDE.md`), permission a `DataInitializer`-ben, ha új.
- **Láthatóság:** a `navigator_academy` flag kikapcsolt állapotában a `/api/academy/**` végpontok 404-et adnak; semmi nem látszik a felhasználóknak.
- **Minőség:** háttérben futó tesztek; 3+ fájl esetén `code-quality-reviewer`; biztonsági ellenőrzőlista (`CLAUDE.md`): owner-ellenőrzés, `@Valid`, nincs nyers `ex.getMessage()`.
- **Élesítés:** backend-PR után `docker compose up backend --build -d`, majd `docker restart legymernok-frontend`.

---

## 4. Backend szakaszok

### 0. szakasz — előfeltételek
| PR | Tartalom | Függ |
|---|---|---|
| na-01 | A group-lépés teljesítését a szerver ellenőrizze (`MissionGroupProgressService.completeStep`): FILL_IN_BLANK csak `passed` eredménnyel, QUIZ a küszöb felett | – |
| na-02 | `navigator_academy` feature flag (seed), backend szűrő a `/api/academy/**` útvonalra | – |
| na-03 | `audience`, `birth_year`, `guardian_consent_status` a kadéton; `audience` a szektoron; regisztráció fogadja az `audience`-t; szektorlista szűrése **(V10)** | na-02 |

### 1. szakasz — adatmodell
| PR | Tartalom | Függ |
|---|---|---|
| na-04 | Tartalom-séma: `AssessmentUnit`, `UnitStimulus`, `UnitItem`, `CodingGuide`, `ItemHintLadder`; `MissionType.ASSESSMENT_UNIT` + CHECK-bővítés; séma-tesztek | na-03 |
| na-05 | Válasz-séma: `UnitAttempt`, `ItemResponse` (kód 2/1/0/9, `code_source`, `review_status`), `ValidatedResponsePool` | na-04 |
| na-06 | Folyamatadat-séma: `ProcessEvent` (append-only, index: attempt + ts), megőrzési job váza | na-05 |
| na-07 | Önszabályozás-séma: magabiztosság, reflexió, `WeeklyPlan`, `ScoreAppeal` | na-05 |

### 2. szakasz — feladatkészítés és lejátszás
| PR | Tartalom | Függ |
|---|---|---|
| na-08 | Szerzői API (unit / stimulus / item / kódolási útmutató / tipp-létra), owner-ellenőrzés (`requireMissionEditAccess` minta), `@Valid`, JSON import/export | na-04 |
| na-09 | Lejátszó API: próbálkozás indítása, kérdés kiszolgálása **helyes válasz nélkül**, fokozatos forrásfeltárás, lezárás | na-05, na-08 |
| na-10 | Pontozómotor 1: `ItemScorer` interfész; egyszeres választás, igen/nem sorozat részponttal, legördülő kiegészítés | na-09 |

### 3. szakasz — adatfogadás
| PR | Tartalom | Függ |
|---|---|---|
| na-11 | Folyamatadat-fogadás: kötegelt `POST`, kliens-UUID idempotencia, payload- és gyakoriságkorlát, eseménytípus-whitelist, a próbálkozás tulajdonosának ellenőrzése | na-06, na-09 |
| na-12 | Pontozómotor 2: hot spot, drag-and-drop (sorrend / párosítás / kategória), numerikus tűréssel és tizedes-normalizálással | na-10 |
| na-13 | Nyílt válasz MI nélkül: normalizálás → validált válaszkészlet → `DEFERRED` kódolósor → emberi kód → panasz | na-10 |
| na-14 | Önszabályozás API: magabiztosság, reflexió, heti terv, Navigátor-napló aggregátumok | na-07, na-11 |

### 4. szakasz — tanulási logika
| PR | Tartalom | Függ |
|---|---|---|
| na-15 | Tipp-létra API: szintenkénti kiadás csak kérésre, naplózva | na-09, na-11 |
| na-16 | Elsajátítási küszöb + adaptív útvonal: Felmérő Repülés, sávok, újraértékelés 3 egységenként, szerveroldali kapuzás | na-01, na-10 |
| na-17 | Napi Jelzés: térközös visszahozás (2/7/21/45 nap), váltogatott feladatválasztás | na-16 |

### 5. szakasz — iskola, szülő, adatvédelem
| PR | Tartalom | Függ |
|---|---|---|
| na-18 | `ROLE_TEACHER`, `Classroom`, `ClassMembership`, meghívókód; permissionök | na-03 |
| na-19 | Kiosztás határidővel, tanári összesítő API (hibaminták, egyéni haladás **csak a saját osztályban**) | na-18, na-10 |
| na-20 | Szülői hozzájárulás: e-mail megerősítés, hozzájárulás-napló, hozzájárulás nélkül korlátozott fiók. **Előtte jogi egyeztetés** (GDPR 8. cikk, hazai korhatár) | na-03 |
| na-21 | Szülői és mentor API (`ROLE_PARENT`, `ROLE_MENTOR`), adat-export és törlési kérés | na-20 |

A 3–4. és az 5. szakasz a 2. szakasz után egymástól függetlenül haladhat.

---

## 5. Frontend szakasz (a backend lezárása után)

| PR | Tartalom |
|---|---|
| na-22 | `/#/akademia` belépési pont: nyitóoldal, onboarding, `audience`-alapú regisztráció, gyerekbarát téma |
| na-23 | Lejátszó: forráspanel, fokozatos feltárás, reflexió, magabiztosság, `useProcessLogger` |
| na-24 | Kérdéstípusok 1: egyszeres választás, igen/nem sorozat, legördülő kiegészítés |
| na-25 | Kérdéstípusok 2: hot spot, drag-and-drop billentyűzetes alternatívával, numerikus |
| na-26 | Nyílt válasz CER-sablonnal, tipp-létra (bottom-sheet) |
| na-27 | Akadémia-térkép: csillagrendszerek, sávok, Napi Jelzés |
| na-28 | Navigátor-napló, heti terv |
| na-29 | Szerkesztőfelület: `UnitEditor` a `MissionEditorPage`-ben |
| na-30 | Tanári felület: osztály, kiosztás, hibaminták, kódolósor |
| na-31 | Szülői felület |

Ismert buktató: a `navigate()` két `play/*` route között nem cseréli a komponenst (`CLAUDE.md` „Nyitott ismert hibák”). Az akadémia lejátszója ezért egyetlen route-on belül váltson kérdéstípust, ne route-ok között.

---

## 6. Később (feltételes)
- Szimuláció és szimulált weboldal kérdéstípusok (T8, T9), legénységi menüs chat (T10).
- MI: szókratészi tutor és nyílt válaszos kódolási javaslat — csak az éles LLM-hosting döntése után.
- Jelvények, heti streak mód, itemstatisztika admin-nézet.
- Aldomain (`akademia.…`): nginx/Cloudflare bejegyzés, hostname-alapú elágazás.
