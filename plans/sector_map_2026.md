# Sector Map — two-tier Star Map (issue #38)

> **📜 Historical planning document — not necessarily current.** This reflects the state of the project as of 2026-08-15 (its last edit), and may be superseded by later decisions or the actual implementation. Check the code or more recent docs in `plans/` before relying on a specific claim here.

**Status:** design finalized, implementation in progress in this same session.
**Decisions clarified with Norbi (2026-08-15):**
- Naming: **Sector** (not Galaxy, not Milky Way).
- A Star System can belong to **exactly one** Sector (or none) — a nullable FK, no join table.

## Goal

The current Star Map shows a single, flat level: the cadet sees all of their Star Systems at
once. This is being expanded into a **two-tier** hierarchy:

1. **`/sector-map`** — the top level, showing Sectors (grouped by topic, e.g. "Physics Sector",
   "Computer Science Sector"). This becomes the navigation entry point.
2. Selecting a Sector **warps** the cadet to the Star Systems that belong to it — this is
   today's `/star-map` view, just filtered by a `:sectorId` parameter.
3. From there, the current flow is unchanged: Star System → Group/Mission → play.

## Explicitly out of scope / consciously simplified decisions

I made these decisions myself while writing the plan (low-risk, reversible choices), without
waiting for Norbi's approval on each one:

- **Unassigned (sector-less) Star Systems**: there is NO mandatory migration backfill into an
  "Other" sector. `star_systems.sector_id` simply stays NULL on existing systems. `/sector-map`
  always shows an **"Unassigned"** pseudo-sector node (not a real DB record, but computed by
  the frontend: every system with `sectorId == null`), which navigates to the old, unfiltered
  `/star-map` view. An admin can gradually assign systems at any time — there's no forced,
  one-off migration.
- **Deleting a Sector** does NOT delete the Star Systems that belong to it — they just get
  `sector_id = NULL` (`ON DELETE SET NULL`) — falling back into the "Unassigned" group.
- **Permission model**: a Sector is an admin-curated taxonomy, NOT a cadet-owned entity (unlike
  a Star System, which cadets can also create) — so it doesn't follow the 6-permission
  `starsystem:create/edit/delete` pattern, but rather the simpler, 2-permission pattern used by
  `feature_flag:read`/`feature_flag:write`: `sector:read` (anyone logged in) +
  `sector:write` (admin only, CRUD + reorder).
- **The dashboard's Star Map preview card** (`StarMapPreviewCard`) stays UNCHANGED for now
  (still shows the current, unfiltered all-systems view) — it hasn't been moved up to the
  sector level. This could be a separate, small follow-up if Norbi asks for it.
- **Warp transition animation**: a simple fade/scale (Framer Motion, already a dependency), NOT
  a large, custom canvas animation — the existing design system
  (`StarfieldBackground`/`NebulaLayer`) already provides the atmosphere, we build on that.
- **The prerequisite-graph topic** (the "which system unlocks which" problem, previously and
  consciously excluded, also mentioned in `frontend_redesign_2026.md` and in the issue):
  **still excluded** — this feature is purely about categorization/grouping, not a prerequisite
  chain.

## Data model

A new entity + 1 nullable FK on the existing `StarSystem`, Flyway `V9`:

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

The `Sector` entity (`model/sector/Sector.java`) follows the existing `StarSystem`/
`MissionGroup` Lombok pattern (`@Data @Builder @NoArgsConstructor @AllArgsConstructor`,
`@CreationTimestamp`/`@UpdateTimestamp`).

The `StarSystem` entity gets a new `@ManyToOne(fetch = LAZY) @JoinColumn(name = "sector_id")
private Sector sector;` field.

## Backend API

New packages, mirroring the existing `starsystem`/`mission/group` structure:
`dto/sector/`, `repository/sector/SectorRepository.java`,
`service/sector/SectorService.java`, `web/sector/SectorController.java`.

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/api/sectors` | `sector:read` | All sectors, each with its star system count |
| GET | `/api/sectors/{id}/star-systems` | `sector:read` | A sector's star systems (with progress, following the existing `with-progress` pattern) |
| POST | `/api/sectors` | `sector:write` | Create |
| PUT | `/api/sectors/{id}` | `sector:write` | Edit |
| DELETE | `/api/sectors/{id}` | `sector:write` | Delete (the systems belonging to it get `sector_id` set to NULL, at the DB level via `ON DELETE SET NULL`) |
| POST | `/api/sectors/{id}/reorder/{targetId}` | `sector:write` | Reorder, following the `MissionGroupService.reorderGroup` pattern |

`StarSystemService.createStarSystem`/`updateStarSystem` gain an optional `sectorId` field
(`CreateStarSystemRequest` + `StarSystemResponse` + `StarSystemWithProgressResponse` all get a
`sectorId`/`sectorName` field).

Two permissions in `DataInitializer`: `sector:read` (ROLE_CADET + ROLE_ADMIN),
`sector:write` (ROLE_ADMIN only) — next to the `feature_flag:read`/`write` block.

## Frontend

**New route, `/sector-map`** — the entry point, replacing `/star-map` in the main navigation
(`mainNavigationControls` STAR_SYSTEMS path).

- `pages/sector-map/SectorMapPage.tsx` — the same `StarfieldBackground`/
  `NebulaLayer`/`GlowCard` scaffold as today's `StarMapPage`.
- `components/domain/sectormap/SectorMapGraph.tsx` — follows the existing `StarMapGraph`
  react-flow pattern, but with Sector nodes (a bigger, "galaxy-like" visual, not a tiny star
  dot) + an always-present "Unassigned" node for systems with no sector.
- Node click → `navigate(/star-map/:sectorId)` (or `/star-map` for the Unassigned node).

**`/star-map/:sectorId?`** — the existing `StarMapPage`/`StarMapGraph` gain an optional
`sectorId` route parameter; if present, the `with-progress` list is filtered client-side by
`system.sectorId === sectorId` (no new backend endpoint needed — the existing list is enough
for the one-page MVP). Title + a "back to the Sector Map" navigation with the sector's name.

**Admin CRUD**: `pages/admin/sector/SectorList.tsx` + `SectorEdit.tsx`, following the
`StarSystemList`/`StarSystemEdit` pattern, `/admin/sectors` route + sidebar menu item.
`StarSystemEdit.tsx` gains a "Sector" select field (with a "No sector" option).

**`api/client.ts`**: a `sectorApi` module (`getAll`, `getStarSystems`, `create`,
`update`, `delete`, `reorder`), following the pattern next to `starSystemApi`.
New `types/sector.ts` type file.

**i18n**: new `sectorMap`/`sector` (admin) namespaces in `en`+`hu`, next to the `starMap`
namespace.

## Migration / compatibility

- Backward compatible: every existing Star System starts with `sector_id = NULL`, and the
  "Unassigned" view shows exactly the same thing as today's `/star-map` — nothing breaks until
  an admin starts creating/assigning sectors.
- The old `/star-map` route (with no parameter) is also kept — this is the "Unassigned" node's
  target route, and it stays backward compatible with everything that has linked here so far.

## Testing

- Backend: `SectorServiceTest` (CRUD, reorder, cascade-SET-NULL behavior) +
  `SectorControllerSecurityTest` (`sector:read` vs `sector:write` gate), following the existing
  `MissionGroupService`/`Controller` test patterns.
- Frontend: Vitest for `SectorMapGraph` (following the existing `StarMapGraph.test.tsx`
  pattern) + for the admin CRUD pages.
- Cypress: at least one new smoke spec (`admin_sectors.cy.ts`) — creating a sector, assigning a
  star system, `/sector-map` → `/star-map/:id` warp navigation, the "Unassigned" node.
- A real functional test with the `qa_admin`/`qa_cadet` accounts in production (per the global
  CLAUDE.md rule): at least creating one Sector, assigning an existing QA star system to it, and
  verifying the full `/sector-map` → warp → `/star-map/:id` path in the browser/with a Cypress
  screenshot.

## Implementation steps

1. Backend: `Sector` entity + Flyway V9 + repository + DTOs + service (CRUD + reorder) +
   controller + permissions in `DataInitializer` + `StarSystem` extension (`sectorId` in the
   create/update/response DTOs) + tests.
2. Frontend types + `api/client.ts`'s `sectorApi` + i18n keys.
3. `SectorMapGraph.tsx` + `SectorMapPage.tsx` + router (`/sector-map`,
   `/star-map/:sectorId?`) + nav-link swap.
4. Admin `SectorList.tsx`/`SectorEdit.tsx` + `StarSystemEdit.tsx` sector field +
   admin sidebar link + router.
5. Full test pass: `mvn test`, `tsc --noEmit`, Vitest, a full Cypress run, then a real
   browser/Cypress-screenshot check with `qa_admin`.
6. `gh pr create` as a single PR (per the project's current convention: a small, standalone
   feature reviewable on its own, not an exception carved out of the big redesign day).
