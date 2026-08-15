# Sector Map — kétszintű Star Map (issue #38)

**Státusz:** tervezés kész, implementáció folyamatban ugyanebben a session-ben.
**Döntések Norbival tisztázva (2026-08-15):**
- Elnevezés: **Szektor** (nem Galaxis, nem Tejútrendszer).
- Egy Star System **pontosan egy** Szektorhoz tartozhat (vagy egyhez sem) — nullable FK, nincs join-tábla.

## Cél

A jelenlegi Star Map egyetlen, lapos szintet mutat: a kadét összes Star System-jét
egyszerre látja. Ez egy **kétszintű** hierarchiává bővül:

1. **`/sector-map`** — felső szint, Szektorok (témakörönként: pl. "Fizika Szektor",
   "Informatika Szektor"). Ez lesz a navigáció belépési pontja.
2. Egy Szektor kiválasztásakor a kadét **"átwarpol"** a hozzá tartozó Star System-ekre
   — ez a mai `/star-map` nézet, csak most `:sectorId` paraméterrel szűrve.
3. Onnantól a jelenlegi flow változatlan: Star System → Group/Mission → lejátszás.

## Explicit hatókörön kívüli / tudatosan egyszerűsített döntések

Ezeket a plan-készítéskor magam döntöttem el (alacsony kockázatú, visszafordítható
választások), nem vártam meg mindegyikhez Norbi jóváhagyását:

- **Sorolatlan (sector nélküli) Star System-ek**: NINCS kötelező migrációs backfill
  egy "Egyéb" szektorba. A `star_systems.sector_id` egyszerűen NULL marad a meglévő
  rendszereknél. A `/sector-map` mutat egy mindig-jelenlévő **"Besorolatlan"**
  pszeudo-szektor node-ot is (nem valódi DB-rekord, hanem a frontend számolja ki:
  minden system, aminek `sectorId == null`), ami a régi, szűretlen `/star-map`
  nézetre navigál. Admin bármikor, fokozatosan besorolhatja a rendszereket — nincs
  kényszerített egyszeri migráció.
- **Szektor törlésekor** a hozzá tartozó Star System-ek NEM törlődnek, csak
  `sector_id = NULL`-ra állnak (`ON DELETE SET NULL`) — visszakerülnek a
  "Besorolatlan" csoportba.
- **Jogosultság-modell**: a Szektor egy admin-kurátori taxonómia, NEM kadét-tulajdonolt
  entitás (ellentétben a Star System-mel, amit kadétok is létrehozhatnak) — ezért nem
  a `starsystem:create/edit/delete` 6-permission mintát követi, hanem a
  `feature_flag:read`/`feature_flag:write` egyszerűbb, 2-permission mintáját:
  `sector:read` (mindenki, aki be van jelentkezve) + `sector:write` (csak admin, CRUD +
  reorder).
- **Dashboard Star Map előnézet-kártya** (`StarMapPreviewCard`) egyelőre VÁLTOZATLAN
  marad (a jelenlegi, szűretlen összes-rendszer nézetet mutatja) — nem került át
  szektor-szintre. Ez egy külön, kis follow-up lehet, ha Norbi kéri.
- **Warp-átmenet animáció**: egyszerű fade/scale (Framer Motion, már meglévő
  dependency), NEM egy nagy, egyedi canvas-animáció — a meglévő design system
  (`StarfieldBackground`/`NebulaLayer`) már megadja az atmoszférát, erre épülünk.
- **Előfeltétel-gráf téma** (a `frontend_redesign_2026.md`-ben és az issue-ban is
  említett, korábban tudatosan kizárt "melyik rendszer nyit meg melyiket" probléma):
  **változatlanul kizárva**, ez a feature tisztán kategorizálás/csoportosítás, nem
  előfeltétel-lánc.

## Adatmodell

Új entitás + 1 nullable FK a meglévő `StarSystem`-en, Flyway `V9`:

```sql
-- V9__create_sectors_table.sql
CREATE TABLE sectors (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    icon_url    VARCHAR(255),
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE star_systems
    ADD COLUMN sector_id UUID REFERENCES sectors(id) ON DELETE SET NULL;
```

`Sector` entitás (`model/sector/Sector.java`) — a meglévő `StarSystem`/`MissionGroup`
Lombok-mintát követi (`@Data @Builder @NoArgsConstructor @AllArgsConstructor`,
`@CreationTimestamp`/`@UpdateTimestamp`).

`StarSystem` entitás bővül egy `@ManyToOne(fetch = LAZY) @JoinColumn(name = "sector_id")
private Sector sector;` mezővel.

## Backend API

Új csomagok, a meglévő `starsystem`/`mission/group` struktúrát tükrözve:
`dto/sector/`, `repository/sector/SectorRepository.java`,
`service/sector/SectorService.java`, `web/sector/SectorController.java`.

| Metódus | Path | Permission | Leírás |
|---|---|---|---|
| GET | `/api/sectors` | `sector:read` | Összes szektor, mindegyikhez a hozzá tartozó star system-ek száma |
| GET | `/api/sectors/{id}/star-systems` | `sector:read` | Egy szektor star system-jei (progress-szel, a meglévő `with-progress` mintát követve) |
| POST | `/api/sectors` | `sector:write` | Létrehozás |
| PUT | `/api/sectors/{id}` | `sector:write` | Szerkesztés |
| DELETE | `/api/sectors/{id}` | `sector:write` | Törlés (a hozzá tartozó rendszerek `sector_id`-ja NULL-ra áll, DB szinten `ON DELETE SET NULL`) |
| POST | `/api/sectors/{id}/reorder/{targetId}` | `sector:write` | Sorrendcsere, a `MissionGroupService.reorderGroup` mintáját követve |

`StarSystemService.createStarSystem`/`updateStarSystem` bővül egy opcionális
`sectorId` mezővel (`CreateStarSystemRequest` + `StarSystemResponse` +
`StarSystemWithProgressResponse` mind kap egy `sectorId`/`sectorName` mezőt).

Két permission a `DataInitializer`-ben: `sector:read` (ROLE_CADET + ROLE_ADMIN),
`sector:write` (csak ROLE_ADMIN) — a `feature_flag:read`/`write` blokk mellé.

## Frontend

**Új route, `/sector-map`** — belépési pont, felváltja a `/star-map`-et a fő
navigációban (`mainNavigationControls` STAR_SYSTEMS path).

- `pages/sector-map/SectorMapPage.tsx` — ugyanaz a `StarfieldBackground`/
  `NebulaLayer`/`GlowCard` váz, mint a mai `StarMapPage`-en.
- `components/domain/sectormap/SectorMapGraph.tsx` — a meglévő `StarMapGraph`
  react-flow mintáját követi, de Szektor-node-okkal (nagyobb, "galaxis-szerű"
  vizuál, nem apró csillag-pötty) + egy mindig-jelenlévő "Besorolatlan" node a
  sector nélküli rendszerekhez.
- Node-kattintás → `navigate(/star-map/:sectorId)` (vagy `/star-map` a
  Besorolatlan node-nál).

**`/star-map/:sectorId?`** — a meglévő `StarMapPage`/`StarMapGraph` bővül egy
opcionális `sectorId` route-paraméterrel; ha van, a `with-progress` listát
kliens-oldalon szűri `system.sectorId === sectorId`-ra (nem kell új backend
endpoint, az egy-oldalas MVP-hez a meglévő lista elég). Cím + "vissza a
Szektortérképre" navigáció a szektor nevével.

**Admin CRUD**: `pages/admin/sector/SectorList.tsx` + `SectorEdit.tsx`, az
`StarSystemList`/`StarSystemEdit` mintáját követve, `/admin/sectors` route +
sidebar-menüpont. `StarSystemEdit.tsx` kap egy "Szektor" select mezőt
("Nincs szektor" opcióval).

**`api/client.ts`**: `sectorApi` modul (`getAll`, `getStarSystems`, `create`,
`update`, `delete`, `reorder`), a `starSystemApi` melletti mintát követve.
`types/sector.ts` új típusfájl.

**i18n**: új `sectorMap`/`sector` (admin) namespace-ek `en`+`hu`-ban, a `starMap`
namespace mellé.

## Migráció / kompatibilitás

- Backward-kompatibilis: minden meglévő Star System `sector_id = NULL`-lal indul,
  a "Besorolatlan" nézet ugyanazt mutatja, mint a mai `/star-map` — semmi nem
  törik el, amíg admin nem kezd szektorokat létrehozni/hozzárendelni.
- A régi `/star-map` (paraméter nélkül) útvonal is megmarad — ez a "Besorolatlan"
  node cél-útvonala, és visszafelé-kompatibilis mindennel, ami eddig ide linkelt.

## Tesztelés

- Backend: `SectorServiceTest` (CRUD, reorder, cascade-SET-NULL viselkedés) +
  `SectorControllerSecurityTest` (`sector:read` vs `sector:write` gate), a meglévő
  `MissionGroupService`/`Controller` teszt-mintáit követve.
- Frontend: Vitest a `SectorMapGraph`-hoz (a meglévő `StarMapGraph.test.tsx`
  mintájára) + admin CRUD oldalakhoz.
- Cypress: minimum egy új smoke-spec (`admin_sectors.cy.ts`) — szektor
  létrehozás, star system hozzárendelés, `/sector-map` → `/star-map/:id` warp
  navigáció, "Besorolatlan" node.
- Valódi funkcionális teszt a `qa_admin`/`qa_cadet` fiókokkal élesben (a globális
  CLAUDE.md szabály szerint): legalább egy Szektor létrehozása, egy meglévő QA
  star system hozzárendelése, `/sector-map` → warp → `/star-map/:id` teljes
  útvonal ellenőrzése böngészőben/Cypress screenshot-tal.

## Implementációs lépések

1. Backend: `Sector` entitás + Flyway V9 + repository + DTO-k + service (CRUD +
   reorder) + controller + permission-ek a `DataInitializer`-ben + `StarSystem`
   bővítés (`sectorId` a create/update/response DTO-kban) + tesztek.
2. Frontend types + `api/client.ts` `sectorApi` + i18n kulcsok.
3. `SectorMapGraph.tsx` + `SectorMapPage.tsx` + router (`/sector-map`,
   `/star-map/:sectorId?`) + nav-link csere.
4. Admin `SectorList.tsx`/`SectorEdit.tsx` + `StarSystemEdit.tsx` szektor-mező +
   admin sidebar link + router.
5. Végigtesztelés: `mvn test`, `tsc --noEmit`, Vitest, teljes Cypress-kör, majd
   valódi böngészős/Cypress-screenshot ellenőrzés `qa_admin`-nal.
6. `gh pr create` egyetlen PR-ben (a projekt jelenlegi konvenciója szerint: kis,
   önálló feature-önmagában is review-zható egységben, nem a nagy redesign-nap
   kivétele).
