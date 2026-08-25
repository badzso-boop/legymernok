# LégyMérnök.hu - Development Roadmap (Finalized)

> **📜 Historical planning document — not necessarily current.** This reflects the state of the project as of 2026-08-15 (its last edit), and may be superseded by later decisions or the actual implementation. Check the code or more recent docs in `plans/` before relying on a specific claim here.

This document is the project's official roadmap, combining the stable admin interface with the gamified Player experience (PWA).

## Development strategy
*   **Backend:** Java Spring Boot (monolithic core, with Gitea integration).
*   **Admin UI:** React Web (desktop-focused) - content management, user management.
*   **Player UI:** React PWA (mobile-focused) - player interface, inventory, squads.

---

## Phase 1: Admin dashboard & core stabilization (PRIORITY)
*Goal: declare the current backend and frontend admin functionality complete. The administrator should be able to fully manage the system.*

### 1.1 User management and permissions
*   [x] **Role management:** changing Role (USER, ADMIN) in the admin UI (backend & frontend DONE).
*   [ ] **Real-time role update:** when the admin grants a role, the user shouldn't have to log out. (Solution: `/auth/refresh` endpoint or proactive token refresh on the frontend.)
*   [x] **User edit:** editing profile data as an admin (DONE).

### 1.2 Content management (CMS)
*   [x] **StarSystem CRUD:** creating and editing star systems with an image and description (DONE).
*   [ ] **Mission editor (frontend MISSING):**
    *   **New page:** create `MissionEdit.tsx`.
    *   **Functionality:** Monaco Editor integration for editing template files (Java/Python code, README).
    *   **API call:** assembling `CreateMissionRequest` from the frontend (Map<String, String> templateFiles).
*   [x] **Mission backend logic:** `MissionService` already handles Gitea repo creation and file upload (DONE).
*   [x] **Gitea integration:** the RestClient-based implementation of `GiteaService` works (DONE).

---

## Phase 2: Game backend & database expansion
*Goal: prepare the database and backend for the game mechanics (inventory, squads, game logic).*

### 2.1 New data models (PostgreSQL)
*   [ ] **Inventory system:** `Item`, `Inventory`, `UserItem` tables (e.g. CPU, memory modules, skins).
*   [ ] **Squad system:** `Squad`, `SquadMember` tables (team name, logo, members, ranks).
*   [ ] **Mission logic:** extend the `Mission` table with solution criteria (e.g. `required_commands`, `max_lines`, `reward_xp`).

### 2.2 Game service logic
*   [ ] **Inventory service:** adding, removing, "equipping" items.
*   [ ] **Squad service:** invite, join, leave, team statistics.

---

## Phase 3: Player flow & game logic (functional prototype)
*Goal: build the logical skeleton of the player interface (PWA), without flashy animations.*

### 3.1 Player UI (frontend)
*   [ ] **Game layout:** a separate view for plain "User"-role accounts.
    *   Bottom navigation: Missions, Squad, Inventory, Profile.
*   [ ] **Mission selector:** listing unlocked StarSystems and Missions.

### 3.2 The "Command Deck" (logic)
*   [ ] **Input interface:** functioning command buttons (Walk, Grab, etc.).
*   [ ] **Translator service (backend):**
    *   Input: JSON command list (e.g. `[{cmd: "WALK", val: 2}]`).
    *   Processing: generating Java/Python code from the template.
    *   Output: commit to the user's Gitea repo.

---

## Phase 4: Polish & visuals (experience)
*Goal: dress up the "dry" logic.*

*   [ ] **2D grid rendering:** sprites, movement animation.
*   [ ] **Visual feedback:** visual indication of a successful/failed test run.
*   [ ] **Sound & haptics:** sound effects, vibration on mobile.
