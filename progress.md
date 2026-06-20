# LégyMérnök.hu — Fejlesztési Napló

**Branch:** `user-firendly` → PR #14 "Mobile Friendly and some more missions"
**Utolsó frissítés:** 2026-04-21

---

## Jelenlegi állapot

A `user-firendly` branch be van pusholva, PR #14 nyitva. A backend tesztek zöldek (221 teszt, 0 hiba). A frontend build és a unit tesztek futtatása szükséges az összesítéshez.

---

## Mit teljesítettünk (Stage 1)

### Backend

#### Új mission típusok: CONTENT és FILL_IN_BLANK
- `CONTENT` misszió: markdown tartalom oldalankénti betöltéssel (`GET /missions/{id}/content`)
- `FILL_IN_BLANK` misszió: `[[blank_N]]` szintaxisú kitöltős feladattípus
  - FillInBlankDefinition, Blank, Option, Attempt, AnswerDetail modellek és teljes CRUD
  - Submit + pontozás + passThreshold + lastAttempt endpoint
  - Admin: `POST/PUT/GET /missions/{id}/fill-in-blank/...`
  - User: opciókból a `correct` mező nem kerül ki a válaszban

#### Mission Group (kurzusszerkezet)
- `MissionGroup` entitás: missziók csoportosítása, `orderIndex` a star systemben
- `MissionGroupProgress`: felhasználónkénti előrehaladás (started → in_progress → completed)
- `MissionGroupStepCompletion`: melyik missziót teljesítette a csoporton belül
- CRUD + add/remove mission + in-group reorder (`groupOrder`) + cross-type reorder (`orderIndex`)
- `GET /star-systems/{id}/with-missions`: interleaved lista (missziók + csoportok `orderIndex` szerint)

#### Cascade delete
- `MissionService.deleteMission`: FIB answer details → attempts → step completions → results → quiz sessions → cadet missions → FIB options/blanks/definitions → mission
- `StarSystemService.deleteStarSystem`: minden misszió cascade törlése, majd group progress, majd group-ok

#### StarSystem Editor UX javítások
- `POST /star-systems/{id}/reorder-items`: cross-type orderIndex swap (MISSION ↔ GROUP ↔ MISSION)
- `MissionGroupService.deleteGroup`: FIB missziók cascade törlése, nem-FIB-ek standalone-ná válnak
- `MissionGroupService.removeMissionFromGroup`: FIB misszió kivehető csoportból (korábban 400-at dobott)
- `MissionService.getNextOrderForStarSystem`: `Math.max(missionMax, groupMax) + 1` — groups is figyelembe véve
- `MissionService.createMission/updateMission/initializeForgeMission`: orderIndex ütközésellenőrzés groups ellen is, `shiftOrdersUp` mindkét táblában

#### Content mission PATCH endpoint
- `PATCH /missions/{id}/content`: csak a `content` mezőt írja felül (nem kell az összes mező)
- `MissionResponse`: `content` mező hozzáadva

#### Docker build optimalizálás
- `Dockerfile`: BuildKit cache mount a Maven `.m2` könyvtárra — nem tölti le újra a dependenciákat minden buildnél

#### DB migration
- `V1__reset_domain_schema.sql`: egységes séma (régi `V1__baseline.sql` + `V2__stage1_mobile.sql` helyett)

### Frontend

#### Új típusok és API client
- `types/group.ts`: MissionGroupResponse, GroupProgressResponse, ReorderResponse stb.
- `types/fillinblank.ts`: FillInBlankUserResponse, SaveFillInBlankRequest, SubmitFillInBlankRequest stb.
- `types/mission.ts`: CONTENT és FILL_IN_BLANK missionType, groupId/groupOrder/orderIndex/content mezők
- `api/client.ts`: missionGroupApi, groupProgressApi, fillInBlankApi, starSystemApi.reorderItems

#### Play oldalak (felhasználói élmény)
- `GroupPlayerPage`: mission group lejátszó — progress tracking, step-by-step CONTENT/FIB/QUIZ váltás, befejezési képernyő
- `ContentMissionView`: markdown tartalom oldalankénti betöltéssel ("Load More"), group/standalone mód
- `FillInBlankView`: pool chips + slot interakció, submit + eredmény visszajelzés, "már teljesítetted" banner
- `QuizPlayerComponent`: QuizPlayer refaktorálva — önállóan és GroupPlayer-ből is hívható
- `StarSystemDetailPage`: group kártyák progress badge-ekkel (NOT_STARTED / IN_PROGRESS / COMPLETED)

#### Admin oldalak és komponensek
- `ContentEditor`: markdown textarea + live preview, meglévő tartalom betöltése mountkor
- `FillInBlankEditor`: `[[blank_N]]` szintaxis, dinamikus blank panel-ek, opció kezelés, POST/PUT mentés
- `MissionEdit`: CONTENT és FIB típusnál szerkesztő tab, orderIndex min/default = 0
- `StarSystemEdit`: interleaved lista (missions + groups `orderIndex` szerint), ↑↓ minden elemhez, in-group misszió reorder, FIB-specifikus hibák eltávolítva

#### Tesztek
- Backend: 221 unit teszt, 0 hiba (MissionGroupServiceTest, FillInBlankServiceTest, MissionGroupProgressServiceTest frissítve)
- Frontend: Cypress E2E tesztek (admin_mission_groups, user_group_player, integrációs tesztek valódi backendel), Vitest unit tesztek (FillInBlankEditor, FillInBlankView, ContentMissionView, GroupPlayerPage)

---

## Nyitott hibák / ismert problémák

| # | Probléma | Súlyosság |
|---|----------|-----------|
| 1 | Gitea orphan repo: ha a DB tranzakció failt, a Gitea repo árva marad (nincs rollback) | Közepes |
| 2 | JWT token nem frissül role-váltás után (admin jogadás → ki kell jelentkezni) | Alacsony |
| 3 | Frontend build (`npm run build`) és E2E tesztek (`npx cypress run`) manuálisan ellenőrzendők | — |

---

## Következő lépések

### Rövid táv (PR #14 merge előtt)

- [ ] `npm run build` és `npm test` lefuttatása — TypeScript + Vitest ellenőrzés
- [ ] Cypress unit tesztek (`npx cypress run`) lefuttatása — E2E stabilitás
- [ ] Manuális tesztelés: Group Player teljes flow (CONTENT → FIB → QUIZ → befejezés)
- [ ] Manuális tesztelés: Admin StarSystem szerkesztő — reorder, FIB group törlés
- [ ] PR #14 review és merge → `main`

### Közép táv (Stage 2 — Player UX)

- [ ] **StarMap fejlesztés**: Star System-ek feloldási feltételei (prerequisite), vizuális gráf kapcsolatok
- [ ] **Kadét profil oldal**: XP, teljesített missziók, badge-ek megjelenítése
- [ ] **Mission Group haladás vizualizáció**: Részletesebb progress bar, idő adatok
- [ ] **Értesítések**: Misszió teljesítés, csoport befejezés push notification (PWA)
- [ ] **Real-time role update**: `/auth/refresh` endpoint, token csere szerepkörváltás után
- [ ] **FillInBlank: szabad szöveges mező**: Jelenleg csak opciók közüli választás van, szabad begépelés opció

### Hosszú táv (Stage 3+ — Gamifikáció)

- [ ] **XP / Level rendszer**: Misszió teljesítéskor XP szerzés, szint növekedés
- [ ] **Badge / Achievement rendszer**: Mérföldkövek (első misszió, 10 misszió, csoport befejezés stb.)
- [ ] **Inventory rendszer**: Item, UserItem entitások — CPU, memória modulok, skinek
- [ ] **Squad rendszer**: Csapatokba szervezés, csapat statisztikák
- [ ] **Circuit Simulation misszió típus** (PR #11 `circuit_forge` branch): Áramkör szimulátor integrálása
- [ ] **PWA**: `vite-plugin-pwa` — offline mód, telepíthetőség
- [ ] **Galaxis Térkép vizualizáció**: `react-flow` vagy D3.js gráf a star system függőségekhez
- [ ] **Gitea orphan repo fix**: Kompenzáló tranzakció vagy saga pattern

---

## Commit history (PR #14)

```
708b867 fix(test): update MissionGroupServiceTest for removed FIB restrictions
7f4dd55 chore(frontend): SpaceStationCanvas minor update
1778af1 test(frontend): Cypress E2E és Vitest unit tesztek
1fe48db feat(frontend): Stage 1 — layouts, shared components, misc pages, build config
7dda97f feat(frontend): Stage 1 — admin pages, ContentEditor, FillInBlankEditor, StarSystem UX
bfd370e feat(frontend): Stage 1 — play pages and components
cf121f2 feat(frontend): Stage 1 — types, API client, router, i18n
4fc680a test(backend): update and extend unit tests for Stage 1
4a8b79c feat(backend): Stage 1 — repositories, services, controllers
377d557 feat(backend): Stage 1 — build, config, models, DTOs, DB migration
94c00c3 chore: update infra, docs, IDE config and plans
ca0a8c8 Backend  ← a branch kiindulópontja volt
```
