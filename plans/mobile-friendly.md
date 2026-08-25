# Mobile-Friendly Platform — Design Document

> **📜 Historical planning document — not necessarily current.** This reflects the state of the project as of 2026-04-14 (its last edit), and may be superseded by later decisions or the actual implementation. Check the code or more recent docs in `plans/` before relying on a specific claim here.

## Vision

LégyMérnök.hu aims to be a TikTok-style learning platform where users learn programming, math, physics, and other sciences in short, 3–5 minute sessions. The platform is fully usable on mobile, and learning builds step by step: read → practice → do → check.

---

## Current state and gaps

- The existing CODING mission uses the Monaco Editor, which is unusable on mobile
- There's no "reading" mission type — course text isn't rendered in a structured way
- There's no fill-in-the-blank interaction
- The user-facing side of CODING missions isn't finished: no file creation/deletion, the full flow is missing
- Missions are independent units — they can't be grouped into a logical sequence
- There's no mobile-friendly coding alternative for beginners
- Admin side: you can't navigate directly from StarSystemEdit to a mission
- Admin side: direct axios calls instead of using `api/client.ts`, hardcoded URLs

---

## Stage 1 — Content and interaction (new branch off main)

### Mission Group concept

Missions will be **groupable**. The admin creates a Mission Group (e.g. "JavaScript Variables") and adds several sub-missions to it in a defined order. From the user's perspective this is **one large mission**, with sub-steps you can page through internally. Under the hood, every sub-mission is still a separate entity.

**Example structure:**
```
[Mission Group] JavaScript Variables
  ├── [CONTENT]       Description of variables (let, var, const)
  ├── [FILL_IN_BLANK] Fill in the sentences
  └── [QUIZ]          Short check-in quiz
```

---

### Ordering rules (UNIFIED)

Within a Star System, every item (whether a standalone mission or a mission group) is ordered by a shared `orderIndex`.

- **Standalone Mission**: `orderIndex` determines its place in the Star System's list. `groupId` = NULL.
- **Mission Group**: `orderIndex` determines the group's place in the list.
- **Mission inside a Group**: `groupId` NOT NULL. Its order within the group is determined by `groupOrder`. `orderIndex` is NULL in this case.

**Rendering:**
The backend returns the Star System's content as a single, sorted `items[]` array — groups and standalone missions mixed together, sorted by `orderIndex`, with a `type: "GROUP" | "MISSION"` discriminator. The frontend doesn't need to implement any merge logic.

---

### New mission types

#### CONTENT mission
- The admin writes the content in one large markdown textarea
- The frontend renders the markdown with a live preview
- The user only reads — no interaction, a "Next" button moves them forward
- Supports images, code blocks, and tables
- **Can also be a standalone mission** — route: `/play/content/:id`

**Content pagination (handling long content):**

The `content` TEXT field can hold several hundred lines. Loading it all at once is wasteful and slow on mobile. The backend splits the content into 100-line pages.

Backend endpoint: `GET /api/missions/{id}/content?page=0&size=100`

```json
// Response 200
{
  "missionId": "uuid",
  "missionName": "Description of variables",
  "content": "## Introduction\n\nVariables...",
  "page": 0,
  "pageSize": 100,
  "totalLines": 247,
  "totalPages": 3,
  "hasNextPage": true,
  "hasPreviousPage": false
}
```

**Backend pagination logic:**
1. Loads the full `content` TEXT field from the database
2. `String[] lines = content.split("\n", -1)` — with the `-1` flag empty lines are preserved
3. `totalPages = (int) Math.ceil((double) lines.length / pageSize)`
4. Given page: `Arrays.copyOfRange(lines, page * pageSize, Math.min((page + 1) * pageSize, lines.length))`
5. Joins it back: `String.join("\n", slice)` → returns it

**Note:** MVP limitation — if a code block or table gets cut at line 100, the frontend markdown renderer may show a broken block. It re-renders correctly once "Load More" has concatenated the rest. Can be improved in Stage 2 with smarter break logic.

**Frontend ContentMissionView logic:**
```typescript
const [loadedContent, setLoadedContent] = useState<string>("");
const [currentPage, setCurrentPage] = useState<number>(0);
const [hasMore, setHasMore] = useState<boolean>(false);
const [loadingMore, setLoadingMore] = useState<boolean>(false);

// Initial load: fetchPage(0) → setLoadedContent(resp.content), setHasMore(resp.hasNextPage)
// "Load More" click: fetchPage(currentPage + 1) →
//   setLoadedContent(prev => prev + "\n" + resp.content)
//   setCurrentPage(p => p + 1)
//   setHasMore(resp.hasNextPage)
// react-markdown renders the concatenated loadedContent
// "Load More" button only shown if hasMore === true
// "Next" button behavior: see below (standalone vs. group mode)
```

**ContentMissionView — standalone vs. group mode:**

`ContentMissionView` appears in two different contexts — handled via an optional `onComplete?: () => void` prop:

- **Group mode** (`onComplete` prop provided): pressing "Next" calls `onComplete()` → the Group Player performs the `complete-step` API call and loads the next sub-mission. The "Next" button is **always enabled** (the reader decides — not every page has to be loaded first).

- **Standalone mode** (no `onComplete` prop): the component receives `missionId` and `starSystemId` props. Pressing "Next":
  1. `GET /api/star-systems/{starSystemId}/with-missions` — loads the star system's items array (or this may already be available in navigation state)
  2. Finds the `orderIndex` for the current `missionId`
  3. Finds the next standalone mission (`orderIndex > current`, `type: "MISSION"`)
  4. If found → `navigate("/play/content/{nextMissionId}")` (or the route matching its type)
  5. If no more items → **Completion screen**: "You've explored all content in this star system!" + a "Back to star system" button (`navigate("/star-systems/{starSystemId}")`)

  > **MVP simplification**: on a standalone CONTENT mission, "next" navigation only works if the star system has already been loaded (e.g. passed via `navigation state`). If not, only the "Back" button is shown.

---

#### FILL_IN_BLANK mission

- **Group-only** — a standalone FILL_IN_BLANK is not allowed
- The backend returns 400 if the mission has no `groupId` at save time

**Fill-in-blank data model: dedicated entities, not JSON TEXT**

Fill-in-blank content is stored in dedicated entities. This guarantees:
- The `isCorrect` field is **structurally impossible** to leak into a user-facing DTO (not manual filtering, but a separate DTO class)
- Statistics can be queried in Stage 2 (which option is chosen, error rate per blank)
- No JSON parse/serialize headaches, no race conditions at the text level when updating
- Editing the definition can be handled transactionally (deleting + rewriting blank/option entities inside `@Transactional`)

**Entities:**
- `FillInBlankDefinition` — the full exercise: template text, passThreshold, FK to the mission (OneToOne)
- `FillInBlankBlank` — one `{key}` placeholder: key, orderIndex, FK to the definition
- `FillInBlankOption` — one option: optionText, **isCorrect** (this field never reaches a user-facing DTO), orderIndex, FK to the blank
- `FillInBlankAttempt` — one submission attempt: cadet, mission, score, passed, submittedAt
- `FillInBlankAnswerDetail` — the detail of one blank's evaluation: attempt, blank, selectedOption, correct

**Blank marker character — `[[blank_N]]` syntax:**

Instead of `{blank_N}` syntax, `[[blank_N]]` (double square brackets) is used. Reason: if the admin is teaching programming and writes code into the text (e.g. JavaScript: `const x = {value: 1}`), curly braces would collide with the blank marker. Double square brackets (`[[...]]`) don't naturally occur in normal programming code, whether JavaScript, Java, or Python.

For regex detection: `/\[\[(\w+)\]\]/g`

Example templateText: `"A const variable can be assigned [[blank_1]]. A let variable can be assigned [[blank_2]]."`

**Admin UI flow:**
1. The admin writes text into a textarea. The **"Add blank" button** appends the next-numbered `[[blank_N]]` marker to the end of the text (max 5 blanks allowed) — the admin can also type it manually anywhere
2. The frontend continuously watches the textarea contents (`onChange`), and for every detected `[[blank_N]]` pattern automatically shows an editor panel below the textarea:
   - Panel header shows the blank's name (e.g. `blank_1`)
   - Option entry: an **automatic empty input field** always appears after the already-filled options — when the admin types into it and leaves the field (onBlur), the option is saved to local state and a new empty input appears for the next one. Once there are 5 options, no empty input is shown
   - Every filled-in option row shows: the text, a checkbox (is it correct?), and a delete icon (X button)
   - Marking correctness: a blank can have more than one correct option (e.g. `[[blank_2]]` where both "once" and "multiple times" are correct)
3. If the admin **deletes the `[[blank_N]]` text** from the textarea, the corresponding editor panel and all its options disappear — this is the admin's responsibility, no warning dialog
4. If the admin **renames a blank's key** (e.g. `[[blank_1]]` → `[[blank_a]]`), the new key appears as a fresh, empty panel; the original `blank_1` panel and its option state are lost
5. At the bottom: a `passThreshold` field (0–100, null = no threshold)
6. On save, the frontend **only sends the blanks currently detectable in the textarea** — deleted/renamed blanks are not included in the request

**`POST /api/missions/{missionId}/fill-in-blank` request (admin, new definition):**
```json
{
  "templateText": "A const variable can be assigned [[blank_1]]. A let variable can be assigned [[blank_2]].",
  "passThreshold": 70,
  "blanks": [
    {
      "key": "blank_1",
      "orderIndex": 0,
      "options": [
        { "optionText": "once",      "isCorrect": true,  "orderIndex": 0 },
        { "optionText": "many times","isCorrect": false, "orderIndex": 1 },
        { "optionText": "never",     "isCorrect": false, "orderIndex": 2 }
      ]
    },
    {
      "key": "blank_2",
      "orderIndex": 1,
      "options": [
        { "optionText": "once",      "isCorrect": true,  "orderIndex": 0 },
        { "optionText": "many times","isCorrect": true,  "orderIndex": 1 },
        { "optionText": "never",     "isCorrect": false, "orderIndex": 2 }
      ]
    }
  ]
}
```

If a definition already exists: `PUT /api/missions/{missionId}/fill-in-blank` — full overwrite, deletes the existing blank/option entities inside `@Transactional`.

**User-side UX — mixed pool approach:**

The user sees the text with `[___]` blank slots (in place of the `{blank_N}` markers), and below it all options from every blank **shuffled together, in random order**, as clickable chip/button elements.

- **Selecting an option (automatic target):** the user clicks a pool chip → it jumps into the first empty blank slot in the text (left to right). The option disappears from the pool.
- **Selecting an option (targeted):** the user first clicks an empty blank slot in the text (the slot gets highlighted), then clicks a pool chip → it goes into the highlighted slot.
- **Returning an option:** the user clicks an already-filled blank slot → the option goes back to the bottom of the pool, and the slot becomes empty again.
- **Options are blank-specific in the data model**, but shown to the user **as a shared pool** — the user can't tell which option belongs to which blank. On submit, the backend accepts any optionId for any blank; a cross-blank submission gets a `correct: false` result (not a 400 error).
- **Submit:** the "Submit" button is enabled once every blank is filled. Request: `{ "answers": { "blank_1": "uuid-opt-X", "blank_2": "uuid-opt-Y" } }` — the optionIds can come from any blank.
- **Feedback after submit:** every blank slot shows a green ✓ / red ✗; incorrect blanks also show the names of the correct options.
- **Retry:** if `passed: false` (and there is a `passThreshold`) → a "Retry" button → blank slots are cleared, options go back to the pool in a new random order.
- If there's no threshold (`passThreshold: null`): always `passed: true`.

---

#### QUIZ mission
- The current implementation stays as-is
- **Refactor needed**: the current `QuizPlayer` splits into two parts:
  - `QuizPlayerPage` — stays on the existing standalone route (`/play/quiz/:id`)
  - `QuizPlayerComponent` — an embeddable component with `missionId` + `onComplete(result)` callback props
- The Group Player uses `QuizPlayerComponent`

#### CODING mission
- The current implementation stays as-is (fully fleshed out in Stage 2)
- Can be added to the group system

---

### Admin UX — Star System editor (reworked)

**Navigation flow:**
1. `/admin/star-systems` → list
2. Click a star system → `/admin/star-systems/:id` — star system data + tree structure
3. "Edit mission" → `/admin/missions/:id` — mission edit page
4. After save/delete, navigate back to the star system:
   - If the mission is in a group (`mission.groupId != null`): the backend returns `starSystemId` directly in the `MissionResponse` (denormalized), so the frontend can `navigate(/admin/star-systems/${mission.starSystemId})` — no need to walk through the `group.starSystem` chain

**Tree structure on the star system edit page:**
```
[Star System: JavaScript Fundamentals]
  ├── [Group] Variables                          [↑][↓] [edit] [delete]
  │     ├── CONTENT: Description of variables    [↑][↓] [→] [edit] [delete]
  │     ├── FILL_IN_BLANK: Fill in the blanks     [↑][↓] [→] [edit] [delete]
  │     └── QUIZ: Check-in quiz                   [↑][↓] [→] [edit] [delete]
  │     └── [+ Add]
  ├── [Group] String operations                   [↑][↓] [edit] [delete]
  │     └── ...
  ├── CODING: Standalone mission                   [↑][↓] [←] [edit] [delete]
  └── [+ Create new group]
```

**Order management:**
- `[↑][↓]` arrows: move an item up/down (`orderIndex` swap within the star system; `groupOrder` swap within the group)
- `[→]` button on a mission inside a group: takes it out → it becomes standalone (`groupOrder` null, `orderIndex` set); for FILL_IN_BLANK the backend returns `400` → snackbar error
- `[←]` button on a standalone mission: opens a dialog → pick which group it should join

**Reorder response — minimal payload:**
The reorder endpoint returns **only the new orderIndex values of the two affected items**. The backend has already performed the swap, so the frontend only needs to update its local state:
```json
// PUT /api/mission-groups/{id}/reorder or PUT /api/missions/{id}/group-order
{ "updated": [ { "id": "uuid1", "orderIndex": 0 }, { "id": "uuid2", "orderIndex": 1 } ] }
```
No need to reload the whole star system — the frontend patches its own state based on the `updated[]` array.

**Deleting a group:**
- If the group contains a FILL_IN_BLANK mission: deletion is **refused with `400 Bad Request`** — the admin must delete the FILL_IN_BLANK mission manually before deleting the group. Frontend snackbar: `"This group contains a FILL_IN_BLANK mission — delete it first."`
- CONTENT, QUIZ, CODING missions become standalone missions. **OrderIndex assignment rules** (backend, in one transaction):
  1. Starting from the group's `orderIndex` position, missions get numbered according to their `groupOrder` within the group: the first mission gets `= group.orderIndex`, the second `= group.orderIndex + 1`, and so on.
  2. Every standalone mission and group that was previously positioned after the group (`orderIndex > group.orderIndex`) shifts by `+N` (where N = the number of missions that became standalone)
  - **Example:** group at `orderIndex: 2`, containing 3 missions (groupOrder 0, 1, 2). After deletion: the 3 new standalone missions get `orderIndex` values 2, 3, 4. Every item that previously had `orderIndex ≥ 3` gets +3 → the whole ordered list stays consistent

**Adding a mission to a group — conflict handling:**
If the selected mission is already in another group, the backend throws `ResourceConflictException` (409), whose `data` field contains the conflicting group's name and ID:
```json
{
  "status": 409, "error": "Conflict",
  "message": "This mission is already assigned to another group",
  "data": { "conflictingGroupId": "uuid", "conflictingGroupName": "String operations" }
}
```
The frontend shows in a snackbar: `"This mission already belongs to the 'String operations' group."`

---

### User side — Star System Detail (reworked)

**Progress-aware loading:**

When `StarSystemDetailPage` loads, two parallel requests fire:
1. `GET /api/star-systems/{id}/with-missions` — the items[] array
2. From the returned items, all `GROUP` item IDs → `Promise.all` of parallel `GET /api/group-progress/{groupId}` calls — one per group

The progress results are stored in a `Map<groupId, GroupProgressResponse | "NOT_STARTED">` state. Missions inside a group (FillInBlank, Content, Quiz) don't have their own separate progress state — they're managed within the Group Player.

**List:** based on the backend's `items[]` array, in order, **with a progress badge**:
- `type: "GROUP"` → group name + number of missions + progress badge + action button:
  - `NOT_STARTED` (404 on the progress GET): "Start" button (blue) → `/play/group/:groupId`
  - `IN_PROGRESS` (`completed: false`): "Continue" button (yellow) + a `"N / M steps"` indicator (where N = `completedMissionIds.length`, M = `totalMissions`)
  - `COMPLETED` (`completed: true`): green ✓ badge + "Replay" button (gray) → `/play/group/:groupId`
- `type: "MISSION"` standalone → name + type icon + "Start" button (no progress tracking — extendable in Stage 2)

**Routing:**
- Starting/continuing a group: `/play/group/:groupId`
- Standalone CONTENT: `/play/content/:missionId`
- Standalone QUIZ: `/play/quiz/:missionId` (existing)
- Standalone CODING: Stage 2

**TypeScript addition (`types/groupProgress.ts`):**
```typescript
type GroupProgressStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED"

// Derived type for display
interface GroupDisplayProgress {
  status: GroupProgressStatus;
  completedCount: number;   // 0 if NOT_STARTED
  totalCount: number;       // number of missions in the group (items[].missions.length)
}
```

---

### User side — Group Player (`/play/group/:groupId`)

**Initial load:**
1. `GET /api/mission-groups/{groupId}/missions` — loads the group's mission list (the response also includes `starSystemId`, for back navigation)
2. `GET /api/group-progress/{groupId}` — fetch progress
   - If `404 ResourceNotFoundException`: `POST /api/group-progress/{groupId}/start` → creates the record → GET again (or the POST response is enough)
   - If `409 ResourceConflictException` on the POST: read the existing one via GET

**Sub-mission stepping (ID-based, robust):**
- A step indicator at the top (e.g. `2 / 4`) — `completedMissionIds.length + 1 / missions.length`
- The Group Player picks the component to render based on `nextMissionId`
- "Next" button → `POST /api/group-progress/{groupId}/complete-step` → the backend returns the updated progress with `nextMissionId` → the frontend advances
- **Robustness:** ID-based progress doesn't break if the admin modifies the group (e.g. adds a new mission at the end), because already-completed steps stay valid UUIDs

**Back navigation:**
- "Back" button: `navigate(/star-systems/${groupMissions.starSystemId})` — the group missions response includes `starSystemId`

**CONTENT:** markdown display with pagination (Load More), "Next" = `complete-step` call
**FILL_IN_BLANK:** fill it in, submit → if `passed: true` → `complete-step` call; if `passed: false` → error feedback, retry
**QUIZ:** renders `QuizPlayerComponent`, `onComplete` callback → `complete-step` call

**Browser Back button behavior — expected and documented:**

If the user navigates away from the Group Player using the browser's Back button:

1. The React component unmounts, in-memory state is lost
2. Progress **persists in the database** — the `MissionGroupStepCompletion` records remain valid
3. If the user returns to the `/play/group/:groupId` route:
   - `GET /group-progress/{groupId}` → returns the latest state with `nextMissionId`
   - The Group Player resumes from the exact step where the user left off — **this is the correct behavior, no "session resume" logic is needed**

4. **FILL_IN_BLANK special case** — the user completed the exercise (`passed: true` attempt was created in the database), navigated away, then returned:
   - The `FillInBlankAttempt` table holds the `passed: true` record
   - On return, the Group Player reopens at the FILL_IN_BLANK step
   - `FillInBlankView` queries the latest attempt on load: `GET /api/missions/{missionId}/fill-in-blank/last-attempt`
   - If the latest attempt is `passed: true` → a banner is shown: **"You've already completed this task successfully."** + a "Next →" button (which calls `complete-step`)
   - If there's no attempt, or `passed: false` → the exercise must be redone
   - This extra endpoint (`GET .../last-attempt`) is an **MVP necessity** — otherwise, after navigating back, the exercise would always have to be redone

   > **Backend endpoint:** `GET /api/missions/{missionId}/fill-in-blank/last-attempt` — `mission:start` permission. Response: `{ "passed": boolean, "percentage": number, "submittedAt": string }` or 404 if there's no attempt

---

### Content Creator role

**Backend role:** `ROLE_CONTENT_CREATOR` — seeded in DataInitializer

**Permissions:**
`starsystem:create`, `starsystem:edit`, `starsystem:read`,
`mission:create`, `mission:edit`, `mission:read`,
`group:create`, `group:edit`, `group:delete`, `group:read`

**Note:** the `StarSystem`, `Mission`, and `MissionGroup` entities all have an `owner` (Cadet) field. A content creator can reach their own content via the `/api/star-systems/my-systems`, `/api/missions/my-missions`, and `/api/mission-groups/my-groups` endpoints. Ownership checks happen at the service layer.

**Frontend:** the admin sidebar is permission-aware. A content creator only sees the "Star Systems", "Missions", and "Groups" tabs.

---

## Stage 2 — More advanced features (separate PR, later)

### Full user-side CODING mission flow
- Create/delete/rename files in the editor
- The admin can pre-configure the file structure

### Mobile Coding mission (new type)
- Card-based, drag-and-drop coding on mobile

### Blockly / visual programming
- Integrating Google Blockly, Scratch-style blocks

### Fill-in-blank statistics (admin dashboard)
- Which option is chosen most often
- Error rate per blank, per mission (queryable from FillInBlankAnswerDetail)

---

## Architecture plan — Stage 1

### Backend — entities

---

#### `MissionGroup`
| Field | Type | Description |
|---|---|---|
| `id` | UUID | PK |
| `name` | String | Group name |
| `description` | String (nullable) | Short description |
| `starSystem` | ManyToOne → StarSystem | FK |
| `owner` | ManyToOne → Cadet | Creator — also the `createdBy` (for content-creator filtering) |
| `updatedBy` | ManyToOne → Cadet (nullable) | Cadet who last modified it |
| `orderIndex` | int | Order within the star system (shared with standalone missions) |
| `createdAt` | Instant | `@CreationTimestamp` |
| `updatedAt` | Instant | `@UpdateTimestamp` |

---

#### `Mission` — modified/new fields
| Field | Type | Description |
|---|---|---|
| `group` | ManyToOne → MissionGroup **(nullable)** | Which group it belongs to |
| `groupOrder` | Integer **(nullable)** | Order within the group; NULL → standalone |
| `orderIndex` | Integer **(nullable, unified)** | Order within the star system; NULL → inside a group |
| `content` | TEXT **(nullable)** | Markdown text for CONTENT type |

> **Note:** the `fillInBlankData` TEXT field **should be removed** — fill-in-blank data is stored in dedicated entities instead (see below).
> The `MissionResponse` DTO includes `starSystemId` **directly** (denormalized), so navigation doesn't need to walk a chain of queries.

---

#### `FillInBlankDefinition`
| Field | Type | Description |
|---|---|---|
| `id` | UUID | PK |
| `mission` | OneToOne → Mission | FK, unique |
| `templateText` | TEXT | The text with `{key}` markers |
| `passThreshold` | Integer (nullable) | 0–100, null = no threshold |
| `createdAt` | Instant | `@CreationTimestamp` |
| `updatedAt` | Instant | `@UpdateTimestamp` |

#### `FillInBlankBlank`
| Field | Type | Description |
|---|---|---|
| `id` | UUID | PK |
| `definition` | ManyToOne → FillInBlankDefinition | FK |
| `blanksKey` | String | The template key (e.g. `"blank_1"`) |
| `orderIndex` | int | Display order |

#### `FillInBlankOption`
| Field | Type | Description |
|---|---|---|
| `id` | UUID | PK |
| `blank` | ManyToOne → FillInBlankBlank | FK |
| `optionText` | String | The option's text |
| `isCorrect` | boolean | **Lives only in this entity. Never reaches a user-facing DTO.** |
| `orderIndex` | int | Display order |

#### `FillInBlankAttempt`
| Field | Type | Description |
|---|---|---|
| `id` | UUID | PK |
| `cadet` | ManyToOne → Cadet | FK |
| `mission` | ManyToOne → Mission | FK |
| `score` | int | Number of correct answers |
| `maxScore` | int | Total number of blanks |
| `percentage` | int | 0–100 |
| `passed` | boolean | `percentage >= passThreshold` (or always true if threshold is null) |
| `submittedAt` | Instant | Submission time |

#### `FillInBlankAnswerDetail`
| Field | Type | Description |
|---|---|---|
| `id` | UUID | PK |
| `attempt` | ManyToOne → FillInBlankAttempt | FK |
| `blank` | ManyToOne → FillInBlankBlank | FK |
| `selectedOption` | ManyToOne → FillInBlankOption **(nullable)** | What the user chose (null if left empty) |
| `correct` | boolean | Whether the chosen option was correct |

---

#### `MissionGroupProgress`
| Field | Type | Description |
|---|---|---|
| `id` | UUID | PK |
| `cadet` | ManyToOne → Cadet | FK |
| `group` | ManyToOne → MissionGroup | FK |
| `nextMissionId` | UUID (nullable) | ID of the next mission to complete; null if the group is done |
| `completed` | boolean | Whether the whole group is completed |
| `startedAt` | Instant | First opened |
| `lastUpdatedAt` | Instant | Time of the last step |
| `completedAt` | Instant (nullable) | Completion time |

> **Unique constraint:** `(cadet_id, group_id)` — a user can only have one progress record per group

#### `MissionGroupStepCompletion` (join table instead of a completedMissionIds field)
| Field | Type | Description |
|---|---|---|
| `id` | UUID | PK |
| `progress` | ManyToOne → MissionGroupProgress | FK |
| `mission` | ManyToOne → Mission | FK |
| `completedAt` | Instant | Time the step was completed |

> **Unique constraint:** `(progress_id, mission_id)` — idempotent: clicking "Next" twice in a row won't create a duplicate record, enforced at the database level.

**Why a join table instead of JSON TEXT:**
- **Race condition eliminated:** two concurrent `complete-step` requests do `INSERT INTO ... ON CONFLICT DO NOTHING` — one succeeds, the other is silently dropped, no data is lost or duplicated
- **Stage 2 statistics:** `SELECT mission_id, COUNT(*) FROM step_completions GROUP BY mission_id` — shows which step has the most drop-off
- **Resilient to reordering:** if the admin reorders the group's missions, the UUID-based step completion records remain valid
- **Computing `nextMissionId`:** the service loads the group's missions sorted by groupOrder, and picks the first one whose id is NOT in step_completions — that's the current step

---

### Backend — new MissionType values
- `CONTENT`
- `FILL_IN_BLANK`

---

### Backend — permission system (the `group:*` category)

The existing `mission:*` category doesn't cover the `MissionGroup` entity — as its own entity, it gets its own permission category.

**New permissions (seeded in DataInitializer):**
| Permission | Description |
|---|---|
| `group:create` | Create a MissionGroup |
| `group:edit` | Edit a MissionGroup, change order, add/remove missions |
| `group:delete` | Delete a MissionGroup |
| `group:read` | Read a MissionGroup and its contents |

**Assignment to roles (DataInitializer):**
| Role | group permissions |
|---|---|
| `ROLE_ADMIN` | `group:create`, `group:edit`, `group:delete`, `group:read` |
| `ROLE_CONTENT_CREATOR` | `group:create`, `group:edit`, `group:delete`, `group:read` (on their own groups, service-level ownership check) |
| `ROLE_CADET` | `group:read` |

**Controller annotation pattern:**
```java
@PreAuthorize("hasAuthority('group:create')")
@PreAuthorize("hasAuthority('group:edit')")
@PreAuthorize("hasAuthority('group:delete')")
@PreAuthorize("hasAuthority('group:read')")
```

**Frontend sidebar permission check:**
```typescript
// AdminLayout sidebar render condition
const canManageGroups = permissions.includes("group:create") || permissions.includes("group:read");
// If true → the "Groups" menu item is shown
```

---

### DB migration strategy (Flyway)

The project currently uses `spring.jpa.hibernate.ddl-auto=update` (or `create-drop`). That's acceptable for development, but not safe for production: a `NOT NULL → nullable` column change can't be applied automatically to existing data, tables aren't dropped automatically either, and schema drift can't be tracked.

The Stage 1 changes touch the existing schema (e.g. `orderInSystem` → nullable `orderIndex`, removing `fillInBlankData`), so introducing Flyway is necessary.

**Steps to introduce it:**

**1. `pom.xml` dependency:**
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```
Spring Boot automatically runs Flyway on startup once `flyway-core` is on the classpath.

**2. `application.properties` change:**
```properties
# After introducing Flyway, ddl-auto=validate: Hibernate checks but doesn't modify the schema
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

**3. Migration file location:**
```
backend/src/main/resources/db/migration/
├── V1__baseline.sql          ← existing schema (written by hand, if the database already has data)
└── V2__stage1_mobile.sql     ← Stage 1 changes
```

**V1 (baseline) handling strategies:**
- If the dev database **is rebuilt from scratch by docker compose on every startup** (no persistent volume): V1 isn't needed, Hibernate's `create-drop` is sufficient for development, Flyway only runs in production → `spring.flyway.baseline-on-migrate=true` in the production properties
- If there's a **persistent dev database**: `V1__baseline.sql` needs to be created describing the existing tables, run the `flyway baseline` command once, after which Flyway tracks changes

**V2__stage1_mobile.sql contents (draft sketch):**
```sql
-- Rename orderInSystem to orderIndex and make it nullable
ALTER TABLE missions RENAME COLUMN order_in_system TO order_index;
ALTER TABLE missions ALTER COLUMN order_index DROP NOT NULL;

-- Remove fillInBlankData (replaced by new entities)
ALTER TABLE missions DROP COLUMN IF EXISTS fill_in_blank_data;

-- Add group FK and groupOrder
ALTER TABLE missions ADD COLUMN group_id UUID REFERENCES mission_groups(id);
ALTER TABLE missions ADD COLUMN group_order INTEGER;
ALTER TABLE missions ADD COLUMN content TEXT;

-- MissionGroup table
CREATE TABLE mission_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    star_system_id UUID NOT NULL REFERENCES star_systems(id),
    owner_id UUID NOT NULL REFERENCES cadets(id),
    updated_by_id UUID REFERENCES cadets(id),
    order_index INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Fill-in-blank entities
CREATE TABLE fill_in_blank_definitions ( ... );
CREATE TABLE fill_in_blank_blanks ( ... );
CREATE TABLE fill_in_blank_options ( ... );
CREATE TABLE fill_in_blank_attempts ( ... );
CREATE TABLE fill_in_blank_answer_details ( ... );

-- Group progress entities
CREATE TABLE mission_group_progresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cadet_id UUID NOT NULL REFERENCES cadets(id),
    group_id UUID NOT NULL REFERENCES mission_groups(id),
    next_mission_id UUID REFERENCES missions(id),
    completed BOOLEAN NOT NULL DEFAULT false,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    completed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (cadet_id, group_id)
);

CREATE TABLE mission_group_step_completions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    progress_id UUID NOT NULL REFERENCES mission_group_progresses(id),
    mission_id UUID NOT NULL REFERENCES missions(id),
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (progress_id, mission_id)
);
```

---

### Backend — `GET /api/auth/me` extension (generic profile endpoint)

The current `/api/auth/me` only returns `username` and `roles`. Because of the permission-aware frontend sidebar and future feature gating, the response needs to be extended.

**`GET /api/auth/me`** — requires Bearer token
```json
// Response 200 — UserProfileResponse (NEW DTO)
{
  "id": "uuid",
  "username": "badzso",
  "email": "norbert@example.com",
  "fullName": "Norbert Ujj",
  "avatarUrl": null,
  "roles": ["ROLE_ADMIN"],
  "permissions": [
    "mission:read", "mission:create", "mission:edit", "mission:delete",
    "group:create", "group:edit", "group:delete", "group:read",
    "starsystem:read", "starsystem:create",
    "user:read", "role:read",
    "logs:read"
  ]
}
```

**Backend DTO — `UserProfileResponse.java`:**
```java
@Data
@Builder
public class UserProfileResponse {
    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
    private List<String> roles;
    private List<String> permissions;  // flat list: every role's permissions combined, deduplicated
}
```

**Backend service logic** (`AuthService.getMe()`):
```java
public UserProfileResponse getMe(String username) {
    Cadet cadet = cadetRepository.findByUsername(username).orElseThrow(...);
    Set<String> permissions = cadet.getRoles().stream()
        .flatMap(role -> role.getPermissions().stream())
        .map(Permission::getName)
        .collect(Collectors.toSet());  // Set → automatic dedup

    return UserProfileResponse.builder()
        .id(cadet.getId())
        .username(cadet.getUsername())
        .email(cadet.getEmail())
        .fullName(cadet.getFullName())
        .avatarUrl(cadet.getAvatarUrl())
        .roles(cadet.getRoles().stream().map(Role::getName).toList())
        .permissions(new ArrayList<>(permissions))
        .build();
}
```

**Frontend — `types/auth.ts` change:**
```typescript
export interface User {
  id: string;
  username: string;
  email?: string;
  fullName?: string;
  avatarUrl?: string | null;
  roles: string[];
  permissions: string[];  // ← NEW, populated from /auth/me
  exp?: number;
}
```

**Frontend — `AuthContext.tsx` change (in the setState block):**
```typescript
setState(prev => ({
  ...prev,
  user: {
    ...prev.user!,
    id: response.data.id,
    username: response.data.username,
    email: response.data.email,
    fullName: response.data.fullName,
    avatarUrl: response.data.avatarUrl,
    roles: response.data.roles,
    permissions: response.data.permissions,  // ← NEW
  },
  isLoading: false,
}));
```

**Generic frontend usage — `hasPermission` for all feature gating:**
```typescript
// In AuthContextType:
hasPermission: (permission: string) => boolean
// Implementation:
const hasPermission = (p: string) => state.user?.permissions.includes(p) ?? false

// Usage in components:
const { hasPermission } = useAuth()
if (hasPermission("group:create")) { ... }
if (hasPermission("mission:edit")) { ... }
// Never role-check directly: user?.roles.includes("ROLE_ADMIN") — reserve that ONLY for auth guards
```

> **Principle:** UI element visibility should always be decided by `hasPermission()`, not by role. That way `ROLE_CONTENT_CREATOR` automatically sees the right menu items based on its own permission set, and when a new role is introduced in the future, the frontend code doesn't need to change.

---

### Backend — all new endpoints + DTOs

---

#### MissionGroup CRUD

**`POST /api/mission-groups`** — `group:create`
```json
// Request
{ "name": "JavaScript Variables", "description": "...", "starSystemId": "uuid", "orderIndex": 1 }

// Response 201
{ "id": "uuid", "name": "JavaScript Variables", "description": "...", "starSystemId": "uuid",
  "orderIndex": 1, "missions": [], "createdAt": "...", "updatedAt": "...",
  "ownerId": "uuid", "updatedById": null }
```

**`GET /api/mission-groups/{id}`** — `group:read`
```json
// Response 200
{ "id": "uuid", "name": "...", "starSystemId": "uuid", "orderIndex": 1,
  "createdAt": "...", "updatedAt": "...", "ownerId": "uuid", "updatedById": "uuid",
  "missions": [
    { "id": "uuid", "name": "...", "missionType": "CONTENT",       "groupOrder": 0 },
    { "id": "uuid", "name": "...", "missionType": "FILL_IN_BLANK", "groupOrder": 1 }
  ]
}
```

**`PUT /api/mission-groups/{id}`** — `group:edit`
```json
// Request
{ "name": "New name", "description": "..." }
// Response 200 — MissionGroupResponse (as above, updatedById refreshed)
```

**`DELETE /api/mission-groups/{id}`** — `group:delete`
```
// Response 204 — CONTENT/QUIZ/CODING missions become standalone
// Response 400 — if there's a FILL_IN_BLANK mission in the group:
{ "error": "GROUP_HAS_FILL_IN_BLANK", "message": "Move or delete the fill-in-blank mission first" }
```

**`POST /api/mission-groups/{id}/missions`** — `group:edit` — add a mission
```json
// Request
{ "missionId": "uuid", "groupOrder": 2 }
// Response 200 — MissionGroupResponse
// Response 409 (ResourceConflictException) — if the mission already belongs to another group:
{ "status": 409, "error": "Conflict",
  "message": "This mission is already assigned to another group",
  "data": { "conflictingGroupId": "uuid", "conflictingGroupName": "String operations" } }
```

**`DELETE /api/mission-groups/{id}/missions/{missionId}`** — `group:edit` — remove a mission (becomes standalone)
```
// Response 204 — mission gets an orderIndex, groupOrder becomes null, group FK becomes null
// Response 400 — if FILL_IN_BLANK:
{ "error": "FILL_IN_BLANK_REQUIRES_GROUP", "message": "A fill-in-blank mission cannot be standalone" }
```

**`PUT /api/mission-groups/{id}/reorder`** — `group:edit` — group's order within the star system
```json
// Request: { "direction": "up" }  // or "down"
// Response 200 — only the two affected items:
{ "updated": [ { "id": "uuid1", "orderIndex": 0 }, { "id": "uuid2", "orderIndex": 1 } ] }
```

**`PUT /api/missions/{id}/group-order`** — `group:edit` — mission's order within a group
```json
// Request: { "direction": "up" }  // or "down"
// Response 200 — only the two affected items:
{ "updated": [ { "id": "uuid1", "groupOrder": 0 }, { "id": "uuid2", "groupOrder": 1 } ] }
```

**`PUT /api/missions/{id}/reorder`** — `mission:edit` — standalone mission's order within the star system
```json
// Request: { "direction": "up" }  // or "down"
// Response 200 — only the two affected items (either standalone mission or group):
{ "updated": [ { "id": "uuid1", "orderIndex": 0 }, { "id": "uuid2", "orderIndex": 1 } ] }
// Response 400 — if the mission is inside a group (groupId != null)
```
> **Note:** this endpoint handles order management between standalone missions and groups within a star system. The orderIndex values are interpreted across every item in the star system (groups + standalone). Swap logic: finds the neighboring item (group or standalone mission) at the `orderIndex ± 1` position, then swaps the two items' orderIndex values.

**`GET /api/mission-groups/my-groups`** — `group:read` — own groups (content creator)
```json
// Response 200
[{ "id": "uuid", "name": "...", "starSystemId": "uuid", "orderIndex": 0, "missions": [...] }]
```

---

#### Fill-in-blank (admin)

**`POST /api/missions/{missionId}/fill-in-blank`** — `mission:edit` — create the definition
```
// Request: see above (blanks array, templateText, passThreshold)
// Response 201 — FillInBlankAdminResponse (includes isCorrect)
// Response 400 — if the mission isn't of type FILL_IN_BLANK
// Response 409 — if a definition already exists for this mission (use PUT instead)
```

**`PUT /api/missions/{missionId}/fill-in-blank`** — `mission:edit` — full overwrite
```
// Request: same as POST
// Response 200 — FillInBlankAdminResponse
// Logic: @Transactional — deletes all existing FillInBlankBlank + FillInBlankOption entities,
//        then rewrites everything from the request
```

**`GET /api/missions/{missionId}/fill-in-blank/admin`** — `mission:edit` — admin view
```json
// Response 200
{
  "id": "uuid-def", "missionId": "uuid", "templateText": "...", "passThreshold": 70,
  "blanks": [
    { "id": "uuid-blank-1", "key": "blank_1", "orderIndex": 0,
      "options": [
        { "id": "uuid-opt-1", "optionText": "once",       "isCorrect": true,  "orderIndex": 0 },
        { "id": "uuid-opt-2", "optionText": "many times", "isCorrect": false, "orderIndex": 1 }
      ]
    }
  ]
}
// Response 404 — if there's no definition
```

---

#### Fill-in-blank (user)

**`GET /api/missions/{missionId}/fill-in-blank`** — `mission:read` — user view
```json
// Response 200 — WITHOUT isCorrect
{
  "missionId": "uuid", "templateText": "...", "passThreshold": 70,
  "blanks": [
    { "id": "uuid-blank-1", "key": "blank_1", "orderIndex": 0,
      "options": [
        { "id": "uuid-opt-1", "optionText": "once",       "orderIndex": 0 },
        { "id": "uuid-opt-2", "optionText": "many times", "orderIndex": 1 }
      ]
    }
  ]
}
```

**`POST /api/missions/{missionId}/submit-fill-blank`** — `mission:start`
```json
// Request — sends optionIds, not text (to prevent manipulation)
{
  "answers": {
    "blank_1": "uuid-opt-1",
    "blank_2": "uuid-opt-4"
  }
}

// Response 200
{
  "attemptId": "uuid",
  "score": 1, "maxScore": 2, "percentage": 50,
  "passed": false, "passThreshold": 70,
  "results": [
    { "blankKey": "blank_1", "correct": true,  "selectedOptionText": "once", "correctOptionTexts": ["once"] },
    { "blankKey": "blank_2", "correct": false, "selectedOptionText": "once", "correctOptionTexts": ["once", "many times"] }
  ]
}

// Response 400 — if the mission isn't of type FILL_IN_BLANK
// Response 400 — if an optionId doesn't exist in the database at all (completely unknown UUID)
// IMPORTANT: a cross-blank submission (e.g. blank_2's option submitted for blank_1) is NOT a 400, just correct: false
```

---

#### Group Progress (user)

**`GET /api/group-progress/{groupId}`** — `mission:start` — fetch progress
```json
// Response 200 — if a progress record exists
{
  "groupId": "uuid",
  "nextMissionId": "uuid-mission-1",
  "completedMissionIds": ["uuid-mission-0"],
  "completed": false,
  "startedAt": "2026-04-09T10:00:00Z",
  "lastUpdatedAt": "2026-04-09T10:05:00Z",
  "completedAt": null,
  "totalMissions": 3
}

// Response 404 (ResourceNotFoundException) — if there's no progress record
// Frontend response: POST /api/group-progress/{groupId}/start
```

**`POST /api/group-progress/{groupId}/start`** — `mission:start` — create the progress record
```json
// Request — empty body
// Response 201 — GroupProgressResponse (nextMissionId = the first mission's id, completedMissionIds=[])
// Response 409 (ResourceConflictException) — if a progress record already exists
// Frontend response to 409: read the existing one via GET
```

**`POST /api/group-progress/{groupId}/complete-step`** — `mission:start`
```json
// Request — empty body

// Backend logic:
// 1. Loads the progress record (404 if none)
// 2. Determines the current mission based on nextMissionId
// 3. If missionType == FILL_IN_BLANK:
//      Checks whether a FillInBlankAttempt exists where cadet=current AND mission=current AND passed=true
//      If not → 400 Bad Request
// 4. INSERT INTO mission_group_step_completions (progress_id, mission_id) ON CONFLICT DO NOTHING
// 5. Computes the new nextMissionId: the group's missions sorted by groupOrder,
//    the first one whose id is NOT in step_completions
// 6. If there's no such one → completed=true, completedAt=now(), nextMissionId=null
// 7. Saves and returns the updated progress record

// Response 200 — GroupProgressResponse with the updated state
// Response 400 — { "error": "FILL_IN_BLANK_NOT_PASSED",
//                   "message": "You haven't completed this task successfully yet." }
```

---

#### Content pagination

**`GET /api/missions/{id}/content`** — `mission:read`

Query params: `page` (default: 0), `size` (default: 100, max: 500)
```json
// Response 200
{
  "missionId": "uuid",
  "missionName": "Description of variables",
  "content": "## Introduction\n\nVariables...",
  "page": 0,
  "pageSize": 100,
  "totalLines": 247,
  "totalPages": 3,
  "hasNextPage": true,
  "hasPreviousPage": false
}
// Response 400 — if the mission isn't of type CONTENT
// Response 404 — if the mission doesn't exist
```

---

#### Star System with-missions extension

**`GET /api/star-systems/{id}/with-missions`** — `starsystem:read` — **sorted items array**
```json
// Response 200
{
  "id": "uuid",
  "name": "JavaScript Fundamentals",
  "description": "...",
  "iconUrl": "...",
  "items": [
    {
      "type": "GROUP",
      "id": "uuid-group-1",
      "name": "Variables",
      "orderIndex": 0,
      "missions": [
        { "id": "uuid", "name": "Description of variables", "missionType": "CONTENT",       "groupOrder": 0 },
        { "id": "uuid", "name": "Fill-in-the-blank exercise", "missionType": "FILL_IN_BLANK", "groupOrder": 1 },
        { "id": "uuid", "name": "Quiz",                       "missionType": "QUIZ",          "groupOrder": 2 }
      ]
    },
    {
      "type": "MISSION",
      "id": "uuid-mission-5",
      "name": "Standalone CODING",
      "missionType": "CODING",
      "difficulty": "EASY",
      "orderIndex": 1
    }
  ]
}
```
The backend returns every group and standalone mission sorted by `orderIndex`. The frontend handles this in TypeScript via a discriminated union (`type: "GROUP" | "MISSION"`).

**Backend DTO classes (Jackson polymorphism):**

```java
// Base interface — Jackson can distinguish based on the type field
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StarSystemGroupItemDTO.class,   name = "GROUP"),
    @JsonSubTypes.Type(value = StarSystemMissionItemDTO.class, name = "MISSION")
})
public abstract class StarSystemItemDTO {
    public abstract String getId();
    public abstract String getName();
    public abstract int getOrderIndex();
}

@Data @Builder
public class StarSystemGroupItemDTO extends StarSystemItemDTO {
    private String id;
    private String name;
    private int orderIndex;
    private List<MissionInGroupResponse> missions;
    // type = "GROUP" — Jackson fills this in automatically
}

@Data @Builder
public class StarSystemMissionItemDTO extends StarSystemItemDTO {
    private String id;
    private String name;
    private int orderIndex;
    private String missionType;
    private String difficulty;
    // type = "MISSION"
}

@Data @Builder
public class StarSystemDetailResponse {
    private String id;
    private String name;
    private String description;
    private String iconUrl;
    private List<StarSystemItemDTO> items;  // mixed list, sorted by orderIndex
}
```

**Backend service method (`StarSystemService.getWithMissions`):**

```java
public StarSystemDetailResponse getWithMissions(UUID starSystemId) {
    StarSystem ss = starSystemRepository.findById(starSystemId)
        .orElseThrow(() -> new ResourceNotFoundException("StarSystem", "id", starSystemId));

    // 1. All groups for the star system (eagerly fetches the missions list)
    List<MissionGroup> groups = missionGroupRepository
        .findByStarSystemIdOrderByOrderIndex(starSystemId);

    // 2. Standalone missions (groupId IS NULL), sorted
    List<Mission> standalone = missionRepository
        .findByStarSystemIdAndGroupIsNullOrderByOrderIndex(starSystemId);

    // 3. Merge + sort
    List<StarSystemItemDTO> items = new ArrayList<>();
    for (MissionGroup g : groups) {
        List<MissionInGroupResponse> mInGroup = g.getMissions().stream()
            .sorted(Comparator.comparingInt(Mission::getGroupOrder))
            .map(MissionInGroupResponse::from)
            .toList();
        items.add(StarSystemGroupItemDTO.builder()
            .id(g.getId().toString())
            .name(g.getName())
            .orderIndex(g.getOrderIndex())
            .missions(mInGroup)
            .build());
    }
    for (Mission m : standalone) {
        items.add(StarSystemMissionItemDTO.builder()
            .id(m.getId().toString())
            .name(m.getName())
            .orderIndex(m.getOrderIndex())
            .missionType(m.getMissionType().name())
            .difficulty(m.getDifficulty().name())
            .build());
    }
    items.sort(Comparator.comparingInt(StarSystemItemDTO::getOrderIndex));

    return StarSystemDetailResponse.builder()
        .id(ss.getId().toString())
        .name(ss.getName())
        .description(ss.getDescription())
        .iconUrl(ss.getIconUrl())
        .items(items)
        .build();
}
```

**Required repository methods:**
```java
// MissionGroupRepository:
List<MissionGroup> findByStarSystemIdOrderByOrderIndex(UUID starSystemId);

// MissionRepository (may partly already exist, but this is the new one):
List<Mission> findByStarSystemIdAndGroupIsNullOrderByOrderIndex(UUID starSystemId);
```

> **Avoiding N+1:** for the `findByStarSystemId...` query, `MissionGroup.missions` is `@OneToMany(fetch = LAZY)` → the service method needs a `JOIN FETCH` or `@EntityGraph`. Recommended: `@EntityGraph(attributePaths = {"missions"})` on the repository method, so every group and its missions come back in a single query.

---

#### User-accessible Group endpoint

**`GET /api/mission-groups/{id}/missions`** — `group:read`
```json
// Response 200
{
  "groupId": "uuid",
  "groupName": "Variables",
  "starSystemId": "uuid",
  "missions": [
    { "id": "uuid", "name": "Description of variables", "missionType": "CONTENT",       "groupOrder": 0 },
    { "id": "uuid", "name": "Fill-in-the-blank exercise", "missionType": "FILL_IN_BLANK", "groupOrder": 1 },
    { "id": "uuid", "name": "Quiz",                       "missionType": "QUIZ",          "groupOrder": 2 }
  ]
}
// fillInBlankData is NOT included here — the Group Player loads it separately via GET /fill-in-blank
```

---

### Frontend — TypeScript types

**`types/mission.ts` extension:**
```typescript
type MissionType = "CODING" | "CIRCUIT_SIMULATION" | "QUIZ" | "CONTENT" | "FILL_IN_BLANK"

interface MissionResponse {
  // ... existing fields ...
  starSystemId: string          // denormalized — for direct navigation
  groupId?: string | null
  groupOrder?: number | null
  orderIndex?: number | null
  content?: string | null
}

// Fill-in-blank user DTO (WITHOUT isCorrect)
interface FillInBlankOptionUser { id: string; optionText: string; orderIndex: number }
interface FillInBlankBlankUser  { id: string; key: string; orderIndex: number; options: FillInBlankOptionUser[] }
interface FillInBlankUserResponse {
  missionId: string; templateText: string; passThreshold: number | null
  blanks: FillInBlankBlankUser[]
}

// Fill-in-blank admin DTO (includes isCorrect)
interface FillInBlankOptionAdmin { id?: string; optionText: string; isCorrect: boolean; orderIndex: number }
interface FillInBlankBlankAdmin  { id?: string; key: string; orderIndex: number; options: FillInBlankOptionAdmin[] }
interface FillInBlankAdminResponse {
  id: string; missionId: string; templateText: string; passThreshold: number | null
  blanks: FillInBlankBlankAdmin[]
}
interface SaveFillInBlankRequest {
  templateText: string; passThreshold: number | null; blanks: FillInBlankBlankAdmin[]
}

// Fill-in-blank submit
interface FillInBlankSubmitRequest { answers: Record<string, string> } // blankKey → optionId
interface FillInBlankResultDetail {
  blankKey: string; correct: boolean
  selectedOptionText: string; correctOptionTexts: string[]
}
interface FillInBlankSubmitResponse {
  attemptId: string; score: number; maxScore: number; percentage: number
  passed: boolean; passThreshold: number | null; results: FillInBlankResultDetail[]
}

// Content pagination
interface ContentPageResponse {
  missionId: string; missionName: string; content: string
  page: number; pageSize: number; totalLines: number; totalPages: number
  hasNextPage: boolean; hasPreviousPage: boolean
}
```

**`types/missionGroup.ts` (new file):**
```typescript
interface MissionGroupResponse {
  id: string; name: string; description?: string
  starSystemId: string; orderIndex: number
  ownerId: string; updatedById: string | null
  createdAt: string; updatedAt: string
  missions: MissionInGroupResponse[]
}
interface MissionInGroupResponse { id: string; name: string; missionType: MissionType; groupOrder: number }
interface CreateMissionGroupRequest { name: string; description?: string; starSystemId: string; orderIndex: number }
interface GroupMissionsResponse { groupId: string; groupName: string; starSystemId: string; missions: MissionInGroupResponse[] }
interface ReorderUpdatedItem { id: string; orderIndex?: number; groupOrder?: number }
interface ReorderResponse { updated: ReorderUpdatedItem[] }
```

**`types/groupProgress.ts` (new file):**
```typescript
interface GroupProgressResponse {
  groupId: string; nextMissionId: string | null
  completedMissionIds: string[]; completed: boolean
  startedAt: string; lastUpdatedAt: string; completedAt: string | null; totalMissions: number
}
```

**`types/starSystem.ts` extension:**
```typescript
interface StarSystemGroupItem {
  type: "GROUP"; id: string; name: string; orderIndex: number; missions: MissionInGroupResponse[]
}
interface StarSystemMissionItem {
  type: "MISSION"; id: string; name: string; missionType: MissionType; difficulty: string; orderIndex: number
}
type StarSystemItem = StarSystemGroupItem | StarSystemMissionItem
interface StarSystemDetailResponse {
  id: string; name: string; description: string; iconUrl?: string; items: StarSystemItem[]
}
```

---

### Frontend — file structure (Stage 1 changes)

**New files:**
```
src/
├── types/
│   ├── missionGroup.ts          # MissionGroupResponse, GroupMissionsResponse, etc.
│   └── groupProgress.ts         # GroupProgressResponse, GroupDisplayProgress, etc.
├── components/
│   ├── admin/
│   │   ├── MarkdownEditor.tsx   # Textarea + live react-markdown preview
│   │   └── FillInBlankEditor.tsx # Blank detector + option editor
│   └── play/
│       ├── ContentPlayer.tsx    # Content player logic (missionId + onComplete prop)
│       └── FillInBlankView.tsx  # Mixed pool fill-in-the-blank exercise
└── pages/
    └── play/
        ├── ContentMissionView.tsx  # Route wrapper: /play/content/:missionId
        └── MissionGroupPlayer.tsx  # Route wrapper: /play/group/:groupId
```

**Files to modify:**
```
src/types/auth.ts                    # + permissions: string[] on User
src/types/mission.ts                 # + CONTENT/FILL_IN_BLANK, + starSystemId etc.
src/types/starSystem.ts              # Full replacement: StarSystemDetailResponse + items[]
src/context/AuthContext.tsx          # + extract permissions from /auth/me + hasPermission()
src/layouts/AdminLayout.tsx          # permission-aware menuItems
src/router/index.tsx                 # + 2 new play routes
src/api/client.ts                    # + groupApi, groupProgressApi, fillInBlankApi modules
src/pages/admin/star-system/StarSystemEdit.tsx    # Full rewrite: tree structure
src/pages/admin/missions/MissionEdit.tsx          # + MarkdownEditor/FillInBlankEditor
src/pages/star-system-detail/StarSystemDetailPage.tsx  # Full rewrite: items[] + progress
src/components/forge/quiz/QuizPlayerComponent.tsx # New component (QuizPlayer page without the API logic)
src/pages/mission-forge/QuizPlayerPage.tsx        # Thin wrapper → QuizPlayerComponent
src/pages/admin/star-system/StarSystemList.tsx    # URL cleanup
src/pages/admin/missions/MissionList.tsx          # URL cleanup
```

---

## i18n keys — full list (Stage 1 new keys)

In the existing `src/i18n/config.ts` file, the following keys need to be inserted into both language objects (`en.translation`, `hu.translation`). The `{{placeholder}}` syntax is i18next interpolation.

```typescript
// EN translations (HU counterpart below)
admin: {
  // Existing admin keys stay, these are the new ones:
  groups: "Groups",
  group: {
    createGroup: "Create Group",
    groupName: "Group name",
    description: "Description (optional)",
    noGroups: "No groups in this star system",
    addMissionToGroup: "Add mission to group",
    selectGroup: "Select a group",
    moveToGroup: "Move to group",
    noGroupsForMove: "No groups — create one first",
    deleteConfirm: "Delete this group? Missions inside will become standalone.",
    deleteError_fillInBlank: "Group contains a FILL_IN_BLANK mission — delete it first",
    conflictError: "This mission already belongs to '{{name}}'",
    removeFillInBlankError: "FILL_IN_BLANK mission cannot be standalone",
    reorderError: "Failed to reorder",
    saveSuccess: "Group saved",
    deleteSuccess: "Group deleted",
  },
  mission: {
    // Existing mission keys stay, these are the new ones:
    addBlank: "Add [[Blank]]",
    passThreshold: "Pass threshold (%)",
    passThresholdHelp: "0–100. Leave empty to always pass.",
    noThreshold: "No threshold",
    contentPreview: "Content preview",
    markdownContent: "Markdown content",
    fillInBlankContent: "Fill-in-blank content",
    saveFirstForFillInBlank: "Save the mission first, then you can edit the fill-in-blank content.",
    blankLabel: "Options for {{key}}:",
    optionCorrect: "Correct",
    optionPlaceholder: "Option text...",
    maxOptionsReached: "Maximum 5 options allowed",
  },
  starSystem: {
    // Existing star system keys stay, these are the new ones:
    items: "{{count}} item(s)",
    reorderSuccess: "Order updated",
    groupCreated: "Group created",
    missionMoved: "Mission moved",
  },
},
play: {
  content: {
    loadMore: "Load more",
    next: "Next →",
    back: "← Back",
    systemComplete: "You've explored all content in this star system!",
    backToSystem: "Back to star system",
  },
  fillInBlank: {
    submit: "Submit",
    retry: "Try again",
    alreadyPassed: "You've already completed this task successfully.",
    nextStep: "Next step →",
    score: "Score: {{score}} / {{max}} ({{pct}}%)",
    passed: "Passed!",
    failed: "Not passed. Try again!",
    correctAnswers: "Correct answers:",
    fillAllBlanks: "Fill in all blanks to submit",
    optionPool: "Choose from the options below:",
  },
  group: {
    step: "Step {{current}} / {{total}}",
    back: "← Back",
    completed: "Group completed!",
    backToSystem: "Back to star system",
    loadError: "Failed to load the task.",
    stepError: "Failed to advance. Please try again.",
  },
},
starSystem: {
  startGroup: "Start",
  continueGroup: "Continue",
  replayGroup: "Replay",
  groupCompleted: "Completed",
  missions: "{{count}} mission(s)",
  progress: "{{done}} / {{total}} steps",
  loadError: "Failed to load star system.",
},
```

```typescript
// HU translations (full match of EN structure, different text)
admin: {
  groups: "Csoportok",
  group: {
    createGroup: "Csoport létrehozása",
    groupName: "Csoport neve",
    description: "Leírás (opcionális)",
    noGroups: "Nincsenek csoportok ebben a csillagrendszerben",
    addMissionToGroup: "Misszió hozzáadása a csoporthoz",
    selectGroup: "Válassz csoportot",
    moveToGroup: "Csoportba mozgatás",
    noGroupsForMove: "Nincs csoport — hozz létre egyet először",
    deleteConfirm: "Biztosan törlöd ezt a csoportot? A benne lévő missziók standalone-ná válnak.",
    deleteError_fillInBlank: "A csoport FILL_IN_BLANK missziót tartalmaz — töröld előbb",
    conflictError: "Ez a misszió már a '{{name}}' csoporthoz tartozik",
    removeFillInBlankError: "FILL_IN_BLANK misszió nem lehet standalone",
    reorderError: "Nem sikerült átrendezni",
    saveSuccess: "Csoport elmentve",
    deleteSuccess: "Csoport törölve",
  },
  mission: {
    addBlank: "[[Blank]] hozzáadása",
    passThreshold: "Sikerességi küszöb (%)",
    passThresholdHelp: "0–100 között. Üresen hagyva mindig sikeresnek számít.",
    noThreshold: "Nincs küszöb",
    contentPreview: "Tartalom előnézete",
    markdownContent: "Markdown tartalom",
    fillInBlankContent: "Fill-in-blank tartalom",
    saveFirstForFillInBlank: "Mentsd el a missziót, majd visszatérve szerkesztheted a fill-in-blank tartalmat.",
    blankLabel: "{{key}} opcióit:",
    optionCorrect: "Helyes",
    optionPlaceholder: "Opció szövege...",
    maxOptionsReached: "Maximum 5 opció érhető el",
  },
  starSystem: {
    items: "{{count}} elem",
    reorderSuccess: "Sorrend frissítve",
    groupCreated: "Csoport létrehozva",
    missionMoved: "Misszió átmozgatva",
  },
},
play: {
  content: {
    loadMore: "Több betöltése",
    next: "Következő →",
    back: "← Vissza",
    systemComplete: "Megvizsgáltad az összes anyagot ebben a csillagrendszerben!",
    backToSystem: "Vissza a csillagrendszerhez",
  },
  fillInBlank: {
    submit: "Beküldés",
    retry: "Újra",
    alreadyPassed: "Ezt a feladatot már sikeresen teljesítetted.",
    nextStep: "Következő lépés →",
    score: "Eredmény: {{score}} / {{max}} ({{pct}}%)",
    passed: "Sikeres!",
    failed: "Nem sikerült. Próbáld újra!",
    correctAnswers: "Helyes válaszok:",
    fillAllBlanks: "Töltsd ki az összes mezőt a beküldéshez",
    optionPool: "Válassz az alábbi lehetőségek közül:",
  },
  group: {
    step: "{{current}} / {{total}} lépés",
    back: "← Vissza",
    completed: "Csoport teljesítve!",
    backToSystem: "Vissza a csillagrendszerhez",
    loadError: "Nem sikerült betölteni a feladatot.",
    stepError: "Nem sikerült továbblépni. Próbáld újra.",
  },
},
starSystem: {
  startGroup: "Kezdd el",
  continueGroup: "Folytatás",
  replayGroup: "Újra",
  groupCompleted: "Teljesítve",
  missions: "{{count}} misszió",
  progress: "{{done}} / {{total}} lépés",
  loadError: "Nem sikerült betölteni a csillagrendszert.",
},
```

---

## Implementation steps (Stage 1)

### Backend

0. **Extend `GET /api/auth/me`** — create the `UserProfileResponse` DTO (`id`, `username`, `email`, `fullName`, `avatarUrl`, `roles`, `permissions`); `AuthService.getMe()` method: collects a flat permission list from the cadet's roles (Set → dedup); `AuthController.getMe()` returns `UserProfileResponse` instead of the old response. **This is the first step**, because the frontend sidebar expects the `permissions` field from the very first load.

1. **Introduce Flyway** — `flyway-core` + `flyway-database-postgresql` dependency, `V1__baseline.sql` (if needed), `application.properties` change (`ddl-auto=validate`, `flyway.enabled=true`)
2. **Seed `group:*` permissions** — `DataInitializer.java`: create 4 new permissions, assign to ROLE_ADMIN and ROLE_CONTENT_CREATOR, ROLE_CADET gets `group:read`
3. **Seed `ROLE_CONTENT_CREATOR` role** — `DataInitializer.java`: new role, assign all the necessary `starsystem:*`, `mission:*`, `group:*` permissions
4. **`MissionGroup` entity + repository** — with `owner`, `updatedBy`, `orderIndex`, `createdAt`, `updatedAt` fields; `MissionGroupRepository` (findByStarSystemId, findByOwnerId, findByStarSystemIdOrderByOrderIndex)
5. **`Mission` entity change** — `group` FK (nullable), `groupOrder` (nullable), `orderIndex` (nullable, renamed from `orderInSystem`), `content` (TEXT nullable); remove the `fillInBlankData` field; Flyway: `V2__mission_group_fields.sql`
6. **Extend `MissionType` enum** — add `CONTENT`, `FILL_IN_BLANK` values
7. **Fill-in-blank entities + repositories** — `FillInBlankDefinition`, `FillInBlankBlank`, `FillInBlankOption`, `FillInBlankAttempt`, `FillInBlankAnswerDetail`; Flyway: `V3__fill_in_blank_entities.sql`
8. **`MissionGroupProgress` + `MissionGroupStepCompletion` entities + repositories** — unique constraints; `findByCadetIdAndGroupId`; `existsByProgressIdAndMissionId`; Flyway: `V4__group_progress.sql`
9. **MissionGroup CRUD service + controller** — CRUD, reorder (swap), add mission (conflict check), remove (FILL_IN_BLANK block), my-groups, ownership check, `group:*` `@PreAuthorize`; **standalone mission reorder** (`PUT /api/missions/{id}/reorder`) here too, or in a separate `MissionController`: finds the neighboring item at the `orderIndex ± 1` position (group or standalone), swaps the orderIndex values, returns the `ReorderResponse`
10. **Fill-in-blank service + controller (admin)** — POST (new definition, 409 if it exists), PUT (`@Transactional` full replace), GET admin view (`FillInBlankAdminResponse` — includes isCorrect)
11. **Fill-in-blank service + controller (user)** — GET user view (`FillInBlankUserResponse` — without isCorrect), `submit-fill-blank` (optionId-based grading — a cross-blank optionId isn't a 400, just `correct: false`; saves `FillInBlankAttempt` + `FillInBlankAnswerDetail`), `GET .../last-attempt` (latest attempt's passed/percentage/submittedAt — 404 if none; needed for the Group Player when navigating back)
12. **Group Progress service + controller** — GET (404 if none), POST start (201, 409 if it exists), POST complete-step (FILL_IN_BLANK validation, `MissionGroupStepCompletion` INSERT, `nextMissionId` computation, completed flag)
13. **Content pagination endpoint** — `GET /api/missions/{id}/content?page&size`, line-based slicing, `ContentPageResponse` DTO
14. **`StarSystemController.getWithMissions` refactor** — new `StarSystemDetailResponse` DTO, sorted `items[]` array (groups + standalone missions mixed, sorted by `orderIndex`) — with `GroupItem` / `MissionItem` wrapper classes
15. **Extend `MissionResponse` DTO** — `starSystemId` (denormalized), `groupId`, `groupOrder`, `orderIndex` (nullable)

### Frontend — Admin

---

**16. TypeScript types + API client extension**

**`src/types/auth.ts` change:**
```typescript
export interface User {
  username: string;
  roles: string[];
  permissions: string[];  // ← NEW: comes from the backend's /auth/me
  exp?: number;
}
```

**`src/context/AuthContext.tsx` change:**
- In the `/api/auth/me` response, the backend now also sends a `permissions: string[]` field
- In the setState: `permissions: response.data.permissions || []`
- New context function: `hasPermission: (p: string) => boolean` → `state.user?.permissions.includes(p) ?? false`
- In `AuthContextType`: `hasPermission: (permission: string) => boolean`

**`src/types/mission.ts` change:**
```typescript
// MissionType extension
type MissionType = "CODING" | "CIRCUIT_SIMULATION" | "QUIZ" | "CONTENT" | "FILL_IN_BLANK"

// MissionResponse extension (existing fields stay, these are the new ones):
interface MissionResponse {
  // ... existing fields ...
  starSystemId: string          // denormalized from the backend
  groupId: string | null
  groupOrder: number | null
  orderIndex: number | null     // replaces orderInSystem (old field dropped)
  content: string | null
}
```

**`src/types/starSystem.ts` full replacement:**
- The old `StarSystemWithMissionsResponse` (flat `missions[]`) is replaced by the `StarSystemDetailResponse` (items[] discriminated union) defined in the TypeScript section of this plan

**`src/types/missionGroup.ts`** and **`src/types/groupProgress.ts`**: based on the interfaces defined in the "Architecture plan → TypeScript types" section, unchanged

**`src/api/client.ts` extension — new API modules to add:**
```typescript
// Alongside the existing module exports:

export const groupApi = {
  getByStarSystem: (starSystemId: string) =>
    apiClient.get<MissionGroupResponse[]>(`/mission-groups?starSystemId=${starSystemId}`),
  create: (data: CreateMissionGroupRequest) =>
    apiClient.post<MissionGroupResponse>('/mission-groups', data),
  update: (id: string, data: { name: string; description?: string }) =>
    apiClient.put<MissionGroupResponse>(`/mission-groups/${id}`, data),
  delete: (id: string) =>
    apiClient.delete(`/mission-groups/${id}`),
  addMission: (groupId: string, missionId: string, groupOrder: number) =>
    apiClient.post<MissionGroupResponse>(`/mission-groups/${groupId}/missions`, { missionId, groupOrder }),
  removeMission: (groupId: string, missionId: string) =>
    apiClient.delete(`/mission-groups/${groupId}/missions/${missionId}`),
  reorder: (groupId: string, direction: "up" | "down") =>
    apiClient.put<ReorderResponse>(`/mission-groups/${groupId}/reorder`, { direction }),
  getMissions: (groupId: string) =>
    apiClient.get<GroupMissionsResponse>(`/mission-groups/${groupId}/missions`),
};

export const missionReorderApi = {
  reorderStandalone: (missionId: string, direction: "up" | "down") =>
    apiClient.put<ReorderResponse>(`/missions/${missionId}/reorder`, { direction }),
  reorderInGroup: (missionId: string, direction: "up" | "down") =>
    apiClient.put<ReorderResponse>(`/missions/${missionId}/group-order`, { direction }),
};

export const groupProgressApi = {
  get: (groupId: string) =>
    apiClient.get<GroupProgressResponse>(`/group-progress/${groupId}`),
  start: (groupId: string) =>
    apiClient.post<GroupProgressResponse>(`/group-progress/${groupId}/start`, {}),
  completeStep: (groupId: string) =>
    apiClient.post<GroupProgressResponse>(`/group-progress/${groupId}/complete-step`, {}),
};

export const fillInBlankApi = {
  getUserView: (missionId: string) =>
    apiClient.get<FillInBlankUserResponse>(`/missions/${missionId}/fill-in-blank`),
  getAdminView: (missionId: string) =>
    apiClient.get<FillInBlankAdminResponse>(`/missions/${missionId}/fill-in-blank/admin`),
  create: (missionId: string, data: SaveFillInBlankRequest) =>
    apiClient.post<FillInBlankAdminResponse>(`/missions/${missionId}/fill-in-blank`, data),
  update: (missionId: string, data: SaveFillInBlankRequest) =>
    apiClient.put<FillInBlankAdminResponse>(`/missions/${missionId}/fill-in-blank`, data),
  submit: (missionId: string, data: FillInBlankSubmitRequest) =>
    apiClient.post<FillInBlankSubmitResponse>(`/missions/${missionId}/submit-fill-blank`, data),
  getLastAttempt: (missionId: string) =>
    apiClient.get<{ passed: boolean; percentage: number; submittedAt: string }>(`/missions/${missionId}/fill-in-blank/last-attempt`),
};

export const contentApi = {
  getPage: (missionId: string, page: number, size = 100) =>
    apiClient.get<ContentPageResponse>(`/missions/${missionId}/content?page=${page}&size=${size}`),
};
```

---

**17. `AuthContext.tsx` + `AdminLayout.tsx` — permission-aware sidebar**

**`AdminLayout.tsx` change:**

```typescript
// Old: const menuItems = [...] static array

// New: dynamically based on permissions
const { user, hasPermission } = useAuth();
const isAdmin = user?.roles.includes("ROLE_ADMIN") ?? false;
const canManageGroups = hasPermission("group:create") || hasPermission("group:read");

const menuItems = [
  { text: "users",       icon: <PeopleIcon />,     path: "/admin/users",        show: isAdmin },
  { text: "starSystems", icon: <SchoolIcon />,      path: "/admin/star-systems", show: true },
  { text: "missions",    icon: <AssignmentIcon />,  path: "/admin/missions",     show: true },
  { text: "groups",      icon: <FolderIcon />,      path: "/admin/groups",       show: canManageGroups },
  { text: "roles",       icon: <SecurityIcon />,    path: "/admin/roles",        show: isAdmin },
  { text: "permissions", icon: <PermissionIcon />,  path: "/admin/permissions",  show: isAdmin },
  { text: "logsTitle",   icon: <TerminalIcon />,    path: "/admin/logs",         show: isAdmin },
].filter(item => item.show);
```

Add the import: `Folder as FolderIcon` from `@mui/icons-material`
Add an i18n key (`config.ts`): `"groups": "Groups"` (EN) / `"Csoportok"` (HU)

> **Note:** the "Groups" admin page (`/admin/groups`) is a simple list of one's own groups — for the Stage 1 MVP it's enough if the admin manages groups from within StarSystemEdit, so this menu item can optionally be skipped; the `canManageGroups` flag is still needed for future extensibility.

---

**18. `StarSystemList.tsx` + `MissionList.tsx` + `StarSystemEdit.tsx` URL cleanup**

In all three files:
- `const API_URL = "http://localhost:8080/api"` → remove
- `import axios from "axios"` → remove (where it was only there for the URL)
- add `import apiClient from "../../../api/client"`
- `axios.get(${API_URL}/...)` → `apiClient.get(/...)`
- `axios.post(${API_URL}/...)` → `apiClient.post(/...)`
- `axios.put(${API_URL}/...)` → `apiClient.put(/...)`
- `axios.delete(${API_URL}/...)` → `apiClient.delete(/...)`
- Headers `{ Authorization: Bearer ${token} }` → remove (the apiClient interceptor handles it)

> The direct `axios` import in `MissionEdit.tsx` stays, following the current pattern — that cleanup can wait for Stage 2.

---

**19. `MissionEdit.tsx` extension + creating `MarkdownEditor.tsx` + `FillInBlankEditor.tsx`**

**`MissionEdit.tsx` changes:**

```typescript
// 1. MISSION_TYPES extension
const MISSION_TYPES = ["CODING", "CIRCUIT_SIMULATION", "QUIZ", "CONTENT", "FILL_IN_BLANK"];

// 2. mission state extension
const [mission, setMission] = useState({
  name: "",
  descriptionMarkdown: "",
  difficulty: "EASY",
  missionType: "CODING",
  orderIndex: 1,           // orderInSystem → orderIndex
  starSystemId: starSystemIdFromQuery || "",
  content: "",             // ← NEW: for the CONTENT type
  // starSystemId, groupId, groupOrder come back from the server, not editable
});

// 3. Back navigation — on the edit page after save/delete:
const handleBack = () => {
  if (mission.starSystemId) {
    navigate(`/admin/star-systems/${mission.starSystemId}`);
  } else {
    navigate(-1);
  }
};
// The ← button calls this same navigate too (not navigate(-1))

// 4. Save payload extension
const payload = {
  name: mission.name,
  descriptionMarkdown: mission.descriptionMarkdown,
  difficulty: mission.difficulty,
  missionType: mission.missionType,
  orderIndex: mission.orderIndex,
  starSystemId: mission.starSystemId,
  ...(mission.missionType === "CONTENT" && { content: mission.content }),
};

// 5. Type-dependent editor — at the bottom of the form, before the Save button:
{mission.missionType === "CONTENT" && (
  <MarkdownEditor
    value={mission.content || ""}
    onChange={(val) => setMission(prev => ({ ...prev, content: val }))}
  />
)}
{mission.missionType === "FILL_IN_BLANK" && !isNew && (
  <FillInBlankEditor missionId={id!} />
)}
{mission.missionType === "FILL_IN_BLANK" && isNew && (
  <Alert severity="info">
    Save the mission first, then you can edit the fill-in-blank content.
  </Alert>
)}
```

---

**`src/components/admin/MarkdownEditor.tsx` — NEW file:**

```typescript
interface MarkdownEditorProps {
  value: string;
  onChange: (value: string) => void;
}
```

Layout: `Grid container spacing={2}`:
- Left (xs=12, md=6): `<TextField multiline minRows={15} fullWidth value={value} onChange={e => onChange(e.target.value)} label="Markdown content" />`
- Right (xs=12, md=6): a Paper base, "Preview" heading, `<ReactMarkdown>{value}</ReactMarkdown>`

Requires importing: `react-markdown` — if missing: `npm install react-markdown`

---

**`src/components/admin/FillInBlankEditor.tsx` — NEW file:**

```typescript
interface FillInBlankEditorProps {
  missionId: string;
}

// Local state type
interface BlankEditorState {
  key: string;   // e.g. "blank_1"
  options: Array<{ tempId: string; optionText: string; isCorrect: boolean }>
}
```

State:
```typescript
const [templateText, setTemplateText] = useState("")
const [blanks, setBlanks] = useState<BlankEditorState[]>([])
const [passThreshold, setPassThreshold] = useState<number | null>(null)
const [saving, setSaving] = useState(false)
const [hasDefinition, setHasDefinition] = useState(false)  // decides POST vs PUT
const [saveError, setSaveError] = useState<string | null>(null)
```

Logic:
- **Mount:** `fillInBlankApi.getAdminView(missionId)` → 200: populate state + `setHasDefinition(true)`; 404: empty state
- **templateText onChange:** regex `/\{(\w+)\}/g` → extracts all blank keys → updates the `blanks` state (keeps the existing option state for blanks whose key is unchanged, gives an empty options array to new keys, removes deleted keys)
- **"Add blank" button:** `setTemplateText(prev => prev + " {blank_" + (blanks.length + 1) + "}")`
- **Option input (auto-extend):** for every blank, a `TextField` appears after the last filled-in option (empty, onBlur if not empty → the option is added + a new empty one appears). Max 5 options/blank
- **Save:** builds a `SaveFillInBlankRequest` → `fillInBlankApi.create` (if !hasDefinition) or `fillInBlankApi.update` (if hasDefinition)

---

**20. `StarSystemEdit.tsx` — full rewrite**

**State:**
```typescript
// Star system metadata (editable fields)
const [meta, setMeta] = useState({ name: "", description: "", iconUrl: "" })
// The tree (groups + standalone missions, sorted)
const [items, setItems] = useState<StarSystemItem[]>([])
// Loading / saving / error
const [loading, setLoading] = useState(!isNew)
const [saving, setSaving] = useState(false)
const [error, setError] = useState<string | null>(null)
// Snackbar (success / error / warning messages)
const [snackbar, setSnackbar] = useState<{ open: boolean; msg: string; severity: "success"|"error"|"warning" }>({ open: false, msg: "", severity: "success" })
// Create-group dialog
const [createGroupOpen, setCreateGroupOpen] = useState(false)
const [newGroupName, setNewGroupName] = useState("")
const [creatingGroup, setCreatingGroup] = useState(false)
// Move-mission-to-group dialog
const [moveDialog, setMoveDialog] = useState<{ missionId: string } | null>(null)
// Adding a mission to a group: target group selection
const targetGroups = items.filter((item): item is StarSystemGroupItem => item.type === "GROUP")
```

**Loading:** `GET /api/star-systems/{id}/with-missions` → `setMeta({name, description, iconUrl})` + `setItems(response.items)`

**Reorder state patch helper:**
```typescript
const applyReorder = (updated: ReorderUpdatedItem[], field: "orderIndex" | "groupOrder", groupId?: string) => {
  setItems(prev => {
    const newItems = [...prev]
    // orderIndex reorder: a group or standalone mission in the top-level list
    if (field === "orderIndex") {
      return newItems.map(item => {
        const match = updated.find(u => u.id === item.id)
        return match ? { ...item, orderIndex: match.orderIndex! } : item
      }).sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0))
    }
    // groupOrder reorder: a mission inside a group
    if (field === "groupOrder" && groupId) {
      return newItems.map(item => {
        if (item.type !== "GROUP" || item.id !== groupId) return item
        const newMissions = item.missions.map(m => {
          const match = updated.find(u => u.id === m.id)
          return match ? { ...m, groupOrder: match.groupOrder! } : m
        }).sort((a, b) => a.groupOrder - b.groupOrder)
        return { ...item, missions: newMissions }
      })
    }
    return newItems
  })
}
```

**Action handlers (all with axios calls, loading state + snackbar):**
```typescript
// ↑↓ group within the star system
const handleReorderGroup = async (groupId: string, direction: "up" | "down") => {
  const res = await groupApi.reorder(groupId, direction)
  applyReorder(res.data.updated, "orderIndex")
}

// ↑↓ standalone mission within the star system
const handleReorderMission = async (missionId: string, direction: "up" | "down") => {
  const res = await missionReorderApi.reorderStandalone(missionId, direction)
  applyReorder(res.data.updated, "orderIndex")
}

// ↑↓ mission within a group
const handleReorderInGroup = async (missionId: string, groupId: string, direction: "up" | "down") => {
  const res = await missionReorderApi.reorderInGroup(missionId, direction)
  applyReorder(res.data.updated, "groupOrder", groupId)
}

// → remove a mission from a group (becomes standalone)
const handleRemoveFromGroup = async (missionId: string, groupId: string) => {
  // If FILL_IN_BLANK: the backend returns 400 → snackbar warning
  try {
    await groupApi.removeMission(groupId, missionId)
    // state: remove the mission from the group's missions array, add it to the top-level items
    // (simplest: full reload)
    await reloadItems()
  } catch (err: any) {
    if (err.response?.status === 400) {
      setSnackbar({ open: true, msg: "FILL_IN_BLANK mission cannot be standalone", severity: "warning" })
    }
  }
}

// ← move a standalone mission into a group (based on moveDialog)
const handleMoveToGroup = async (missionId: string, groupId: string) => {
  try {
    await groupApi.addMission(groupId, missionId, 999) // goes to the end of groupOrder
    setMoveDialog(null)
    await reloadItems()
  } catch (err: any) {
    if (err.response?.status === 409) {
      const name = err.response.data.data?.conflictingGroupName ?? "another group"
      setSnackbar({ open: true, msg: `This mission already belongs to '${name}'`, severity: "warning" })
    }
  }
}

// Delete a group
const handleDeleteGroup = async (groupId: string) => {
  // FILL_IN_BLANK check: if there's such a mission in the group, snackbar warning
  const group = items.find(i => i.type === "GROUP" && i.id === groupId) as StarSystemGroupItem
  if (group?.missions.some(m => m.missionType === "FILL_IN_BLANK")) {
    setSnackbar({ open: true, msg: "This group contains a FILL_IN_BLANK mission — delete it first", severity: "warning" })
    return
  }
  try {
    await groupApi.delete(groupId)
    await reloadItems()
  } catch (err: any) {
    if (err.response?.status === 400) {
      setSnackbar({ open: true, msg: err.response.data.message, severity: "error" })
    }
  }
}

// Helper: full reload after operations other than reordering
const reloadItems = async () => {
  const res = await apiClient.get<StarSystemDetailResponse>(`/star-systems/${id}/with-missions`)
  setItems(res.data.items)
}
```

**Render — tree structure:**
```tsx
{items.map((item, idx) => (
  item.type === "GROUP" ? (
    <GroupRow key={item.id}
      group={item}
      isFirst={idx === 0}
      isLast={idx === items.length - 1}
      onReorderUp={() => handleReorderGroup(item.id, "up")}
      onReorderDown={() => handleReorderGroup(item.id, "down")}
      onEdit={() => navigate(`/admin/missions/group/${item.id}`)}  // if a group edit page is added
      onDelete={() => handleDeleteGroup(item.id)}
      onAddMission={() => navigate(`/admin/missions/new?groupId=${item.id}`)}
      onMissionReorderUp={(mId) => handleReorderInGroup(mId, item.id, "up")}
      onMissionReorderDown={(mId) => handleReorderInGroup(mId, item.id, "down")}
      onMissionEdit={(mId) => navigate(`/admin/missions/${mId}`)}
      onMissionRemove={(mId) => handleRemoveFromGroup(mId, item.id)}
    />
  ) : (
    <StandaloneMissionRow key={item.id}
      mission={item}
      isFirst={idx === 0}
      isLast={idx === items.length - 1}
      onReorderUp={() => handleReorderMission(item.id, "up")}
      onReorderDown={() => handleReorderMission(item.id, "down")}
      onEdit={() => navigate(`/admin/missions/${item.id}`)}
      onMoveToGroup={() => setMoveDialog({ missionId: item.id })}
    />
  )
))}
```

**`GroupRow` and `StandaloneMissionRow` — a WordPress-style tree UI**

Every list item is a MUI `Paper` div. Missions inside a group are shown with a `paddingLeft: 32px` indent — visually making the hierarchy clear.

**`GroupRow` render (one group in the list):**
```
┌─────────────────────────────────────────────────────────────┐
│ [▶] JavaScript Variables               [↑][↓] [edit] [delete] │  ← group header row
│                                                             │
│   ┌────────────────────────────────────────────────────┐   │
│   │ CONTENT  Description of variables  [↑][↓] [→] [edit]│   │  ← indented mission row
│   └────────────────────────────────────────────────────┘   │
│   ┌────────────────────────────────────────────────────┐   │
│   │ FILL_IN_BLANK  Fill-in-blank  [↑][↓] [→⚠] [edit] │   │  ← FILL_IN_BLANK: [→] shown in red !
│   └────────────────────────────────────────────────────┘   │
│   [+ Add mission to group]                                  │
└─────────────────────────────────────────────────────────────┘
```

The `[→]` button (remove from group → becomes standalone):
- For CONTENT, QUIZ, CODING: a normal button, click triggers `handleRemoveFromGroup`
- For FILL_IN_BLANK: **instead of** `[→]`, a red `[→⚠]` with a Tooltip: *"FILL_IN_BLANK mission cannot be standalone"* — clicking it shows a snackbar warning, doesn't navigate

The group header's `[delete]` button: if the group has a FILL_IN_BLANK mission, clicking it shows a snackbar warning (the delete dialog doesn't open). If there's no FILL_IN_BLANK, a confirmation dialog opens.

**`StandaloneMissionRow` render (a standalone mission):**
```
┌──────────────────────────────────────────────────────────────┐
│ CODING  Standalone task              [↑][↓] [←] [edit]       │
└──────────────────────────────────────────────────────────────┘
```

The `[←]` button (move into a group): opens the `moveDialog` — a `Select` dropdown with the available groups (`targetGroups` derived state). If there's no group in the star system, the button is disabled: Tooltip: *"No groups — create one first"*.

**Icons and visual markers:**
- Mission type chip/badge: `CONTENT` → blue, `QUIZ` → purple, `FILL_IN_BLANK` → orange, `CODING` → green, `CIRCUIT_SIMULATION` → gray
- `[↑]` disabled if the item is first, `[↓]` disabled if last (within a group based on groupOrder, top-level based on orderIndex)
- Buttons on the right: `IconButton` components (`ArrowUpward`, `ArrowDownward`, `ArrowForward`/`ArrowBack`, `Edit`, `Delete`)

### Frontend — user side

---

21. **`QuizPlayer` refactor** → `QuizPlayerPage` + `QuizPlayerComponent`

   **Analysis of the current state:**
   - `QuizPlayer.tsx` (`src/components/forge/quiz/QuizPlayer.tsx`) — **purely presentational**: receives the `data: QuizDefinition` prop, handles the timer, navigation, answer selection, calls the `onSubmit` callback
   - `QuizPlayerPage.tsx` (`src/pages/mission-forge/QuizPlayerPage.tsx`) — **container**: API calls (`quizApi.startQuiz`, `quizApi.submitQuiz`), loading/error state, result display, 409 session handling

   **Goal of the refactor:** create a `QuizPlayerComponent` that `MissionGroupPlayer` can use directly with `missionId` + `onComplete` props — without the result display.

   ---

   **To create: `src/components/quiz/QuizPlayerComponent.tsx`**

   ```typescript
   interface QuizPlayerComponentProps {
     missionId: string;
     onComplete: (result: MissionResult) => void;
   }
   ```

   This component takes over `QuizPlayerPage`'s API logic:
   1. `useQuery(["quiz", missionId], () => quizApi.startQuiz(missionId))` — loads the `QuizDefinition`; loading + error state shown the same way `QuizPlayerPage` does today
   2. `useMutation(quizApi.submitQuiz)`:
      - `onSuccess(data)`: calls `onComplete(data)` — **the component doesn't display the result**, the parent handles that
      - `onError 409`: `onComplete(err.response.data.data)` — passes the old result to the parent
      - `onError 404` (session expired): `queryClient.resetQueries(["quiz", missionId])` — reloads the quiz, then renders `QuizPlayer` again (the user plays it again)
   3. Renders the presentational `QuizPlayer` component with `data={quiz}` and `onSubmit={submit}` props
   4. **Does not contain** a result display — the `onComplete` callback fires and the parent decides what to show

   ---

   **To modify: `QuizPlayerPage.tsx`**

   ```tsx
   const QuizPlayerPage: React.FC = () => {
     const { missionId } = useParams<{ missionId: string }>();
     const navigate = useNavigate();
     const [result, setResult] = useState<MissionResult | null>(null);

     if (result) {
       // The current MISSION_ACCOMPLISHED UI stays here
       return <QuizResultDisplay result={result} onBack={() => navigate(-1)} />;
     }

     return (
       <Box sx={{ width: "100vw", height: "100vh", bgcolor: "#000" }}>
         <QuizPlayerComponent
           missionId={missionId!}
           onComplete={(r) => setResult(r)}
         />
       </Box>
     );
   };
   ```

   The result-display logic (MISSION_ACCOMPLISHED panel, score, navigate -1 button) **stays in `QuizPlayerPage`** — it can be extracted into its own `QuizResultDisplay` component for readability, but this isn't required for the MVP.

   ---

   **Usage in `MissionGroupPlayer`:**

   ```tsx
   // In the Group Player's sub-mission render switch:
   case "QUIZ":
     return (
       <QuizPlayerComponent
         missionId={currentMission.id}
         onComplete={() => handleCompleteStep()}
         // In the group context we do NOT show the result — completeStep advances immediately
       />
     );
   ```

   `handleCompleteStep` is the function in the Group Player that calls the `POST /complete-step` endpoint and refreshes the progress state.

   ---

   **Testing implications:**
   - The existing `QuizPlayer.test.tsx` **doesn't change** (purely presentational, no API)
   - The existing `QuizPlayerPage.test.tsx` **doesn't change much** — the API mocks work the same way, now going through `QuizPlayerComponent`
   - New: `QuizPlayerComponent.test.tsx` — loading/error/submit/409 API tests (moved here from the former `QuizPlayerPage.test.tsx`)
---

22. **`ContentPlayer.tsx` + `ContentMissionView.tsx` + route**

> **Dependency:** `npm install react-markdown` is required for the `MarkdownEditor` and `ContentPlayer` components. Run it in the frontend directory before starting this step.

**`src/components/play/ContentPlayer.tsx` — NEW file:**

```typescript
interface ContentPlayerProps {
  missionId: string;
  onComplete?: () => void;   // undefined = standalone mode (self-managed navigation)
  starSystemId?: string;     // needed for "Next" navigation in standalone mode
}
```

State:
```typescript
const [loadedContent, setLoadedContent] = useState("")
const [currentPage, setCurrentPage] = useState(0)
const [hasMore, setHasMore] = useState(false)
const [loadingMore, setLoadingMore] = useState(false)
const [loading, setLoading] = useState(true)
const [missionName, setMissionName] = useState("")
```

Logic:
- Mount: `contentApi.getPage(missionId, 0)` → `setLoadedContent(resp.content)`, `setMissionName(resp.missionName)`, `setHasMore(resp.hasNextPage)`
- "Load More" button (only if `hasMore`): `contentApi.getPage(missionId, currentPage + 1)` → `setLoadedContent(prev => prev + "\n" + resp.content)`, page ++, `setHasMore`
- "Next" button:
  - If the `onComplete` prop is set (group mode): calls `onComplete()`
  - If not (standalone mode): see the ContentMissionView standalone navigation below

Render:
```tsx
<Box sx={{ maxWidth: "800px", mx: "auto", p: 2 }}>
  <Typography variant="h5">{missionName}</Typography>
  <ReactMarkdown>{loadedContent}</ReactMarkdown>
  {hasMore && <Button onClick={handleLoadMore} disabled={loadingMore}>Load More</Button>}
  <Button variant="contained" onClick={handleNext}>Next →</Button>
</Box>
```

---

**`src/pages/play/ContentMissionView.tsx` — NEW file (route wrapper):**

```typescript
// Route: /play/content/:missionId
// From navigation state (passed in by StarSystemDetailPage): { starSystemId, nextItem: { id, missionType } | null }
```

State:
```typescript
const { missionId } = useParams<{ missionId: string }>()
const navigate = useNavigate()
const location = useLocation()
const navState = location.state as { starSystemId?: string; nextItem?: { id: string; missionType: MissionType } | null } | null
```

Standalone navigation (`handleNext`):
```typescript
const handleNext = () => {
  if (navState?.nextItem) {
    // Navigate to the next mission based on its type
    const { id, missionType } = navState.nextItem
    if (missionType === "CONTENT") navigate(`/play/content/${id}`, { state: { starSystemId: navState.starSystemId, nextItem: null } })
    else if (missionType === "QUIZ") navigate(`/play/quiz/${id}`)
    else if (missionType === "GROUP") navigate(`/play/group/${id}`)
  } else {
    // No next item → completion screen
    navigate(`/star-systems/${navState?.starSystemId}`, {
      state: { completionMessage: "You've explored all content!" }
    })
  }
}
```

Render:
```tsx
return <ContentPlayer missionId={missionId!} onComplete={undefined} starSystemId={navState?.starSystemId} />
// The ContentPlayer's handleNext calls back into the function above — for this, ContentPlayer takes an `onStandaloneNext` prop
```

> **Simpler MVP alternative:** ContentMissionView doesn't receive `nextItem` in navigation state, only `starSystemId`. The "Next" button always navigates back to the star system. True "next mission" navigation can be pushed to Stage 2.

**Router extension (`router/index.tsx`):**
```typescript
{ path: "play/content/:missionId", element: <ContentMissionView /> },
```

---

23. **`FillInBlankView.tsx` — NEW file**

File: `src/components/play/FillInBlankView.tsx`

```typescript
interface FillInBlankViewProps {
  missionId: string;
  onComplete: () => void;
}
```

State:
```typescript
const [definition, setDefinition] = useState<FillInBlankUserResponse | null>(null)
const [loading, setLoading] = useState(true)
// Pool: every option from every blank, shuffled together
const [pool, setPool] = useState<Array<FillInBlankOptionUser & { blankKey: string }>>([])
// Slots: blankKey → optionId | null
const [slots, setSlots] = useState<Record<string, string | null>>({})
// Highlighted blank slot (for targeted filling)
const [activeSlot, setActiveSlot] = useState<string | null>(null)
// Result after submit
const [result, setResult] = useState<FillInBlankSubmitResponse | null>(null)
const [submitting, setSubmitting] = useState(false)
// If there's already a passed attempt (back button recovery)
const [alreadyPassed, setAlreadyPassed] = useState(false)
```

Loading (on mount, in parallel):
```typescript
const [defResp, lastAttemptResp] = await Promise.allSettled([
  fillInBlankApi.getUserView(missionId),
  fillInBlankApi.getLastAttempt(missionId),
])
// lastAttempt: if fulfilled and passed → setAlreadyPassed(true)
// def: setDefinition, initializeSlotsAndPool
```

Pool initialization:
```typescript
const initPool = (def: FillInBlankUserResponse) => {
  const allOptions = def.blanks.flatMap(blank =>
    blank.options.map(opt => ({ ...opt, blankKey: blank.key }))
  )
  // Fisher-Yates shuffle
  for (let i = allOptions.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [allOptions[i], allOptions[j]] = [allOptions[j], allOptions[i]]
  }
  setPool(allOptions)
  setSlots(Object.fromEntries(def.blanks.map(b => [b.key, null])))
}
```

Interaction logic:
```typescript
const handlePoolClick = (optId: string) => {
  if (activeSlot) {
    // Targeted: goes into activeSlot
    placeOption(activeSlot, optId)
    setActiveSlot(null)
  } else {
    // Automatic: first empty slot
    const firstEmpty = definition!.blanks
      .sort((a, b) => a.orderIndex - b.orderIndex)
      .find(b => slots[b.key] === null)
    if (firstEmpty) placeOption(firstEmpty.key, optId)
  }
}

const placeOption = (blankKey: string, optId: string) => {
  const prevOptId = slots[blankKey]
  setSlots(prev => ({ ...prev, [blankKey]: optId }))
  setPool(prev => {
    const withoutNew = prev.filter(o => o.id !== optId)
    if (prevOptId) {
      // The previous option returns to the end of the pool
      const prev2 = definition!.blanks.flatMap(b => b.options).find(o => o.id === prevOptId)
      if (prev2) return [...withoutNew, { ...prev2, blankKey: definition!.blanks.find(b => b.options.some(o => o.id === prevOptId))!.key }]
    }
    return withoutNew
  })
}

const handleSlotClick = (blankKey: string) => {
  if (slots[blankKey]) {
    // Return it to the pool
    const optId = slots[blankKey]!
    setSlots(prev => ({ ...prev, [blankKey]: null }))
    const opt = definition!.blanks.flatMap(b => b.options).find(o => o.id === optId)!
    const bk = definition!.blanks.find(b => b.options.some(o => o.id === optId))!.key
    setPool(prev => [...prev, { ...opt, blankKey: bk }])
    setActiveSlot(null)
  } else {
    setActiveSlot(prev => prev === blankKey ? null : blankKey)
  }
}
```

Submit:
```typescript
const handleSubmit = async () => {
  setSubmitting(true)
  try {
    const res = await fillInBlankApi.submit(missionId, {
      answers: Object.fromEntries(
        Object.entries(slots).filter(([, v]) => v !== null) as [string, string][]
      )
    })
    setResult(res.data)
    if (res.data.passed) {
      // auto complete-step is called by the parent (Group Player) via onComplete
      onComplete()
    }
  } finally {
    setSubmitting(false)
  }
}
```

**Render structure:**

If `alreadyPassed`: Banner: "You've already completed this task successfully." + "Next →" button (`onComplete()`)

If `result` and `!result.passed`: feedback + "Retry" button (→ `setResult(null)`, `initPool(definition!)`, `setSlots(...)`)

If `result` and `result.passed`: this branch isn't shown (onComplete has already been called)

Main render: the text rendered from `definition.templateText` with inline blank slots, options pool below.

**Rendering the templateText — `[[blank_N]]` syntax, inline BlankSlot:**

Splitting the templateText: alternating text segments and blank slots.

Regex: `/\[\[(\w+)\]\]/g` — finds every `[[keyname]]` pattern, match[1] is the key's name (e.g. `blank_1`).

Algorithm: the text between regex matches goes into a `parts[]` array as `type: "text"` segments, the matches as `type: "blank"` segments. The `parts[]` array is mapped to React elements: text → `<span>`, blank → `<BlankSlot blankKey={...} />`.

`BlankSlot` render states (inline `<Box component="span">`):
- **Empty, not active:** an underlined box shape, `"___"` placeholder, gray border, `cursor: pointer`
- **Empty, active** (user clicked it, awaiting an option): blue border + blue background highlight
- **Filled:** the option's text inside, yellow/warning background — clicking returns it to the pool
- **Filled, after result is shown:** green (`correct: true`) or red (`correct: false`) + an icon

---

24. **`MissionGroupPlayer.tsx` + route — NEW file**

File: `src/pages/play/MissionGroupPlayer.tsx`

```typescript
// Route: /play/group/:groupId
```

State:
```typescript
const { groupId } = useParams<{ groupId: string }>()
const navigate = useNavigate()
const [groupMissions, setGroupMissions] = useState<GroupMissionsResponse | null>(null)
const [progress, setProgress] = useState<GroupProgressResponse | null>(null)
const [loading, setLoading] = useState(true)
const [completing, setCompleting] = useState(false)
const [error, setError] = useState<string | null>(null)
```

Derived (useMemo):
```typescript
const currentMission = useMemo<MissionInGroupResponse | null>(() => {
  if (!groupMissions || !progress) return null
  if (progress.completed) return null
  return groupMissions.missions.find(m => m.id === progress.nextMissionId) ?? null
}, [groupMissions, progress])

const stepNumber = useMemo(() => {
  if (!progress) return 1
  return (progress.completedMissionIds?.length ?? 0) + 1
}, [progress])
```

Loading sequence (on mount):
```typescript
useEffect(() => {
  const load = async () => {
    try {
      // 1. Load the group's missions
      const missionsResp = await groupProgressApi.get... // wait, that's groupApi
      const [missionsResp] = await Promise.all([
        groupApi.getMissions(groupId!)
      ])
      setGroupMissions(missionsResp.data)

      // 2. Load progress
      let prog: GroupProgressResponse
      try {
        const progResp = await groupProgressApi.get(groupId!)
        prog = progResp.data
      } catch (err: any) {
        if (err.response?.status === 404) {
          // No progress yet → create it
          try {
            const startResp = await groupProgressApi.start(groupId!)
            prog = startResp.data
          } catch (startErr: any) {
            if (startErr.response?.status === 409) {
              // Race condition: someone else already started it → GET again
              const retryResp = await groupProgressApi.get(groupId!)
              prog = retryResp.data
            } else throw startErr
          }
        } else throw err
      }
      setProgress(prog)
    } catch (e) {
      setError("Failed to load the task.")
    } finally {
      setLoading(false)
    }
  }
  load()
}, [groupId])
```

`handleCompleteStep`:
```typescript
const handleCompleteStep = async () => {
  if (!groupId || completing) return
  setCompleting(true)
  try {
    const res = await groupProgressApi.completeStep(groupId)
    setProgress(res.data)
  } catch (err: any) {
    // 400 FILL_IN_BLANK_NOT_PASSED: no snackbar shown, because this shouldn't happen
    // (FillInBlankView only calls this when passed=true)
    console.error("complete-step failed", err)
  } finally {
    setCompleting(false)
  }
}
```

Render:
```tsx
// Header: step indicator + back button
<Box sx={{ display: "flex", justifyContent: "space-between", p: 2 }}>
  <Button onClick={() => navigate(`/star-systems/${groupMissions.starSystemId}`)}>← Back</Button>
  <Typography>{groupMissions.groupName} — Step {stepNumber} / {groupMissions.missions.length}</Typography>
</Box>

// Content: based on the sub-mission's type
{progress.completed ? (
  <CompletionScreen
    groupName={groupMissions.groupName}
    onBack={() => navigate(`/star-systems/${groupMissions.starSystemId}`)}
  />
) : currentMission ? (
  <>
    {currentMission.missionType === "CONTENT" && (
      <ContentPlayer missionId={currentMission.id} onComplete={handleCompleteStep} />
    )}
    {currentMission.missionType === "FILL_IN_BLANK" && (
      <FillInBlankView missionId={currentMission.id} onComplete={handleCompleteStep} />
    )}
    {currentMission.missionType === "QUIZ" && (
      <QuizPlayerComponent missionId={currentMission.id} onComplete={handleCompleteStep} />
    )}
  </>
) : null}
```

`CompletionScreen`: a simple component — group name + "Group completed!" message + "Back to star system" button.

**Router extension (`router/index.tsx`):**
```typescript
{ path: "play/group/:groupId", element: <MissionGroupPlayer /> },
```

---

25. **`StarSystemDetailPage.tsx` — full rewrite**

File: `src/pages/star-system-detail/StarSystemDetailPage.tsx`

State:
```typescript
const { id } = useParams<{ id: string }>()
const navigate = useNavigate()
const [data, setData] = useState<StarSystemDetailResponse | null>(null)
const [progressMap, setProgressMap] = useState<Map<string, GroupDisplayProgress>>(new Map())
const [loading, setLoading] = useState(true)
const [error, setError] = useState<string | null>(null)
```

Loading:
```typescript
useEffect(() => {
  const load = async () => {
    try {
      const resp = await apiClient.get<StarSystemDetailResponse>(`/star-systems/${id}/with-missions`)
      setData(resp.data)

      // Parallel progress loading for every group
      const groupItems = resp.data.items.filter((i): i is StarSystemGroupItem => i.type === "GROUP")
      const progressResults = await Promise.allSettled(
        groupItems.map(g => groupProgressApi.get(g.id))
      )

      const map = new Map<string, GroupDisplayProgress>()
      groupItems.forEach((group, idx) => {
        const result = progressResults[idx]
        if (result.status === "fulfilled") {
          const p = result.value.data
          map.set(group.id, {
            status: p.completed ? "COMPLETED" : "IN_PROGRESS",
            completedCount: p.completedMissionIds?.length ?? 0,
            totalCount: group.missions.length,
          })
        } else {
          // 404 = NOT_STARTED
          map.set(group.id, { status: "NOT_STARTED", completedCount: 0, totalCount: group.missions.length })
        }
      })
      setProgressMap(map)
    } catch {
      setError("Failed to load star system.")
    } finally {
      setLoading(false)
    }
  }
  load()
}, [id])
```

Render (keeps the retro UI frame — only the mission list's inner content changes):
```tsx
// Old: data.missions.map(mission => ...)
// New:
{data.items.map((item) => (
  item.type === "GROUP" ? (
    <GroupCard
      key={item.id}
      group={item}
      progress={progressMap.get(item.id) ?? { status: "NOT_STARTED", completedCount: 0, totalCount: item.missions.length }}
      onStart={() => navigate(`/play/group/${item.id}`)}
    />
  ) : (
    <MissionCard
      key={item.id}
      mission={item}
      onStart={() => {
        if (item.missionType === "QUIZ") navigate(`/play/quiz/${item.id}`)
        else if (item.missionType === "CONTENT") navigate(`/play/content/${item.id}`, { state: { starSystemId: id } })
        else navigate(`/play/quiz/${item.id}`) // fallback
      }}
    />
  )
))}
```

`GroupCard` render:
- Group name + "(N missions)"
- Badge/chip based on progress state:
  - `NOT_STARTED`: blue "Start" button
  - `IN_PROGRESS`: yellow "Continue" button + `"{completedCount}/{totalCount}"` text
  - `COMPLETED`: green ✓ + gray "Replay" button

Old `data.missions.length` count → `data.items.length` (items include both groups and standalone missions)

### Wrap-up

26. **Full flow test:** admin creates a star system → a group with a CONTENT + FILL_IN_BLANK + QUIZ mission → the user opens it → the Group Player starts → reads CONTENT (test Load More with long content) → fills in FILL_IN_BLANK (fails → retries → succeeds) → completes the QUIZ → group completed → returns to the star system → re-enters → progress is retained → resumes from the correct step
