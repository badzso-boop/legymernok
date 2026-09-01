# LégyMérnök.hu — Frontend Redesign and Engagement Features (2026)

> **📜 Historical planning document — not necessarily current.** This reflects the state of the project as of 2026-08-14 (its last edit), and may be superseded by later decisions or the actual implementation. Check the code or more recent docs in `plans/` before relying on a specific claim here.

## 1. Starting point: the current UI is a demo surface

The backend is functionally complete and rich (Mission Group, CONTENT, FILL_IN_BLANK, QUIZ, CODING,
Gitea integration, RBAC, feature flags), but the frontend has so far served purely to test that
backend — **it is not a finished, deliberately designed product surface**. Concrete, code-visible
signs of this:

- **The same concept (creating/editing a mission) has two completely separate UIs.**
  On `MissionEdit.tsx` the admin sets the mission's base data, but for the **QUIZ type a separate
  button** ("Quiz editor") navigates away to the completely differently designed, raw Monaco
  JSON-editor page at `/forge/:missionId` — the Mission Forge page meant for cadets. The same is
  true for CODING: `MissionEdit.tsx` has a `MissionFileEditor` tab, BUT there's also a completely
  separate `MissionForgePage.tsx`, which edits the same repo with a different UI.
- **The landing page and the post-login "cockpit" use a minimalist, non-unified visual
  language** — there's no coherent design system, just Material UI defaults plus a handful of
  RetroUI.css classes.
- **The cadet-facing mission player surfaces (CONTENT, FILL_IN_BLANK, QUIZ, CODING, Group Player)
  were each built separately**, with their own layout, not inside one shared "player shell"
  component — and it shows: some work on mobile, some don't (e.g. the CODING Monaco Editor).
- **There is no engagement/retention mechanic whatsoever** (streak, friends, daily goal) — even
  though the project's original motivation was explicitly to replace doomscrolling with a
  learnable, "sticky" alternative (see the [[project_legymernok]] memory, `new_direction_2026.md`,
  `ux_ui_terv.md`).

**The goal from here:** not to write new backend features (that's done), but to design and build
**a unified, mobile-first, retro-sci-fi, Duolingo-style "sticky" product surface** on top of the
existing APIs — plus a handful of new features specifically aimed at engagement (streak, friends,
own profile).

---

## 2. Design principles

1. **Mobile-first, but desktop-compatible.** Every new page is designed for mobile viewports
   (360–430px) first, then extended to desktop — not the other way around, as it's been so far.
   **An important nuance on where "mobile-first" ends and "mobile-usable" begins:**
   - **Cadet-facing surfaces (landing, dashboard, mission playback, profile, friends,
     Star Map)** — these are the product's *core* surface, the doomscrolling-replacement
     experience. For these, mobile-first doesn't just mean responsiveness — it means the primary
     interaction (thumb-reach action bar, single-column layout, large touch targets, full-screen
     player) **is designed and tested on mobile first**, with the desktop view derived from that.
   - **Admin/content-creation surfaces (Mission Editor, QuizBuilder, CodeMissionEditor, Star
     System tree editor)** — these are **responsive and usable** on mobile (they don't break, no
     horizontal scrolling needed, forms stack in a single column), but they **won't be primarily
     optimized for mobile** when it comes to deeper content editing. **This is a deliberate choice,
     not neglect** — the product's core motivation (replacing doomscrolling) applies to the
     *learner* side, not to content administration. Within this there are two clearly separate
     tiers:

     **"Base" admin operations that work fully on mobile** (this list is NOT a compromise — it
     must work just as well as on desktop):
     - Browsing, searching, and filtering Star System / Group / Mission lists
     - Editing base data for any mission type (name, description, difficulty, order) — this is
       the top form of `MissionEditorPage`, which is always a plain, single-column mobile form
     - Reordering in the tree editor (`[↑][↓][→][←]` buttons — these are already button-based
       rather than drag-and-drop, so they work just as well on mobile as on desktop)
     - Toggling feature flags, the user list + role assignment, reviewing the feedback list
     - Quick edits to CONTENT text in `MarkdownStudio` (tab-switching edit/preview mode on
       mobile, not split-view — but the toolbar and the editing itself are fully functional)
     - Adding/editing FILL_IN_BLANK blanks/options (a form-based list, not drag-heavy — "Add
       blank" and toggling options on/off is comfortable on mobile too)
     - Basic use of `QuizBuilder`: adding questions/options, editing text, marking the correct
       answer — reordering here also uses up/down buttons rather than a drag handle, specifically
       so it works on mobile as well

     **Desktop-recommended, but doesn't break on mobile — just a narrower experience:**
     - The admin-side, template-authoring use of `CodeMissionEditor` (file tree + Monaco) — a
       quick edit to a single file's content works fine on mobile too, but browsing/organizing
       the file tree with many files is more cumbersome on a small screen
     - Wide admin tables with many columns (e.g. the role/permission matrix) — on mobile these
       switch to horizontal scrolling or a simplified card view, but that's not a primary
       optimization target
2. **One concept, one editor.** Creating and editing a mission (of any type) happens **on a
   single page**, with a type-dependent, embedded editor panel — you should never need to
   navigate to a separate page to edit "the actual content."
3. **One concept, one player shell.** Cadet-facing mission playback (CONTENT, FILL_IN_BLANK,
   QUIZ, CODING, and their appearance inside the Group Player) uses a shared `MissionPlayerShell`
   layout (header, progress, navigation, unified), with type-dependent content in the middle.
4. **The retro-sci-fi mood stays, but more polished.** Not a "thrown-together" mix of pixel art
   and Material UI, but a deliberate design system: a deep-space blue/black base, neon accent
   colors, consistent typography (following the existing direction already set in `ux_ui_terv.md`),
   animations defined in one place (not ad-hoc, rewritten per page).
5. **Duolingo pattern:** daily goal, streak, instant visual feedback on every completion, a
   friends/follow system for social pressure, an own profile for visibility/pride.
6. **The existing i18n (Hungarian/English) is preserved throughout — not a step back.** The
   project is already fully bilingual (`src/i18n/config.ts`, `en`/`hu` `resources`,
   `useTranslation()` hook everywhere — see `frontend/CLAUDE.md` conventions). **No component in
   the redesign may hardcode a Hungarian (or English) string** — every new UI element
   (`MissionEditorPage`, `MarkdownStudio`, `QuizBuilder`, `MissionPlayerShell`, the theme picker,
   the streak/friends/profile surfaces, etc.) gets new `config.ts` keys **in both languages at
   once**, in the same structure as before (see the "i18n keys" section in `mobile-friendly.md` —
   the same pattern continues). This matters especially because several components **replace**
   their old counterpart (e.g. `MissionForgePage` → `MissionEditorPage`) — old keys may only be
   removed from `config.ts` once the component they belonged to is actually gone and nothing else
   references them (checked by grepping for the key name), otherwise "live" translation keys get
   silently lost.

---

## 3. Theming system — Light / Dark / Space

The original plan assumed a single, always-on "sci-fi starfield" theme. That falls short in two
ways: (1) not everyone wants the full immersive sci-fi experience at all times (e.g. a moving
background can be distracting during longer CONTENT reading), (2) a static star pattern on its
own genuinely reads as "cheap" — a modern, premium-feeling surface needs more than that: layered
depth, subtle motion, a deliberate color and light language.

**Decision:** three selectable themes, configurable on the Settings page; on app startup it loads
the saved preference (backend `cadets.theme_preference` field + `localStorage` cache for instant
application without a flash):

| Theme | For whom | Character |
|---|---|---|
| **Space** (default) | The genuine product experience — this carries the brand identity | Fully immersive sci-fi: layered parallax starfield, nebula gradients, glow/HUD elements |
| **Dark** | Anyone who wants the functionality without the full sci-fi visuals (e.g. long reading sessions, battery saving) | Dark, clean, keeps the brand's color and typography language but with a static background, no animation |
| **Light** | Daytime/outdoor use, accessibility preference | Light base, same component system, contrast-optimized |

All three use **the same component set and layout** — the theme only swaps color tokens and (for
Space) a background layer, it's not a separate implementation.

### 3.1 Token architecture

- `theme/tokens.ts` — a CSS custom-property-based token layer (`--color-bg-base`,
  `--color-accent-primary`, `--color-accent-secondary`, `--glow-sm/md/lg`, `--radius-*`,
  `--spacing-*`), with **a separate value set per theme**, so a theme switch happens instantly
  via a `data-theme` attribute swap, without a reload (the MUI `ThemeProvider` builds its own
  palette from these CSS variables, so both Material components and our own components draw
  color from the same source).
- `theme/typography.ts` — shared across every theme: `Space Grotesk` for headings, `Inter` for
  body text, `Fira Code` for code. Typography is NOT theme-dependent — brand consistency comes
  through the typeface and spacing too, not just through the Space theme.
- `theme/components.ts` — MUI component overrides in one place (`MuiButton`, `MuiCard`,
  `MuiTextField`, etc.), referencing the tokens above — never a hardcoded color/shadow inside a
  specific component.

### 3.2 Fleshing out the "Space" theme — why it won't feel "cheap"

The goal isn't a PNG star wallpaper, but **layered depth + subtle, ambient motion + a light
language**, following today's premium UI trends (Linear, Arc Browser, Stripe's gradient
backgrounds), poured into a sci-fi palette:

1. **Layered parallax starfield** (not a static image): 3 layers at different speeds/sizes — a
   far layer (tiny, faint dots, barely-visible motion), a mid layer (medium stars, a slow,
   continuous "twinkle" — an opacity pulse, not a position animation, so it isn't distracting), a
   near layer (a few larger, faint, glowing stars that drift very slowly). CSS transforms with
   `requestAnimationFrame` throttling, **a DOM/SVG layer instead of canvas** — lighter on mobile
   battery, and fully switchable off with a single flag under `prefers-reduced-motion` (falls
   back to a static starfield).
2. **Nebula gradient patches in the background** — large, blurred (`filter: blur()`), slowly
   drifting radial gradient patches (indigo → magenta → cyan, low opacity), NOT sharp star points
   but color depth for the background — this is what gives the "premium" feel instead of flat
   black. The base color changes too: not pure black (`#000`), but a deep indigo-black (close to
   `#05040F`), which feels warmer and less "empty."
3. **Occasional "shooting star"** — rarely (every 30–90 seconds, with randomized delay), a short,
   faint streak animation crosses the screen — `aria-hidden`, purely decorative, and fully
   disabled under `prefers-reduced-motion`.
4. **Glassmorphism HUD panels** — cards (`GlowCard`) aren't solid Material cards, but
   semi-transparent, `backdrop-filter: blur()` panels with a subtle, faintly glowing border — as
   if the panel sits on a spacecraft display, not "pasted onto" the background.
5. **A consistent glow language for interactions** — focus/hover/active states use a unified,
   token-driven glow (`--glow-accent`), not `box-shadow` values reinvented per page.
6. **Mobile performance protection** — the number of layers and the star count automatically
   scale down on smaller viewports (e.g. mobile: 1–2 layers, fewer elements), so lower-end phones
   don't get frame drops or a noticeable battery hit.

The `StarfieldBackground`/`NebulaLayer` components are **parameterizable** (layer count,
intensity), so the landing page can get a fuller, "hero" version while the dashboard/player
surfaces get a more restrained, less distracting version — all from the same component.

### 3.3 Dark and Light themes

Not "Space theme minus the animation," but their own deliberate color decisions:

- **Dark:** starts from the same base palette family as Space (indigo-black base, the same accent
  colors), but with a **static** background (no parallax/nebula animation), plain, slightly
  raised cards instead of glassmorphism (`elevation`, not `backdrop-blur`) — cleaner, less
  distracting, but still visually unmistakably "the same brand."
- **Light:** a light base (not pure white — a slightly cool off-white, easier on the eyes), the
  accent colors (cyan/magenta) in a darker, higher-contrast variant (WCAG AA contrast ratio
  checked for text/background pairs), brand elements (logo, icons) in a light variant.

### 3.4 Shared components (`components/shared/`)

`GlowCard`, `NeonButton`, `ProgressRing`, `StreakFlame`, `XpBadge`, `StarfieldBackground`,
`NebulaLayer` — all draw their color from the token system, none of them hardcodes a theme.
**`framer-motion`** is used consistently for transitions/micro-animations (already in use, but
needs to be applied consistently rather than only in some places).

### 3.5 Backend — persisting the theme preference

- `Cadet` entity extension: `themePreference VARCHAR(10) DEFAULT 'SPACE'` (`SPACE`/`DARK`/`LIGHT`).
- `PUT /api/auth/me/theme` — a simple, self-scoped update (no admin permission needed, everyone
  only writes their own preference).
- A three-way switcher on the Settings page (with a card-based preview, not a plain dropdown — so
  the user can actually see what they're picking).

---

## 4. Admin page — a unified content editor

### 4.1 Eliminating today's fragmented flow

| Today | New |
|---|---|
| `MissionEdit.tsx` for base data + a separate `/forge/:id` page for the QUIZ JSON | A single `MissionEditorPage`, where for the QUIZ type an embedded **`QuizBuilder`** component appears (a question/option UI, not raw JSON) |
| `MissionEdit.tsx`'s file tab + a completely separate `MissionForgePage.tsx` for the CODING repo | An embedded **`CodeMissionEditor`** component (file tree + Monaco), on the same page |
| `ContentEditor` on a separate tab, a plain textarea | An embedded **`MarkdownStudio`** (see 4.2) |
| `FillInBlankEditor` on a separate tab | Stays embedded, but also gets `MarkdownStudio`'s toolbar elements for editing the template text |

**Principle:** `MissionEditorPage` is a single component that displays the type-dependent content
editor **directly** below the base-data form (not hidden behind a tab switch, not navigated to on
a separate route) — the editor and the preview are always visible as soon as the type is
selected.

This component layer is **shared with the cadet-facing Mission Forge as well** — `MissionEditorPage`
uses the same building blocks (`QuizBuilder`, `CodeMissionEditor`, `MarkdownStudio`), just in a
different permission/route context (`/forge/new`, `/forge/:id` for cadets, `/admin/missions/:id`
for admins) — **not two separate implementations**, but one component set with two entry points.

### 4.2 `MarkdownStudio` — a more intuitive content editor

The current `ContentEditor`/`MarkdownEditor` is a plain textarea + preview. The new version:

- **A formatting toolbar** above the textarea: H1/H2/H3, bold, italic, list, numbered list, code
  block, quote, link, image — each one inserts the corresponding markdown syntax around the
  cursor/selection (e.g. selected text + Bold button → `**selected text**`).
- **Split view on desktop** (editor | live preview side by side), **tab switching on mobile**
  (Edit / Preview tab, since side-by-side doesn't fit).
- The **"Add blank" button** (for FILL_IN_BLANK) is integrated into this same toolbar, not a
  separate UI.
- **Decision: a hand-built toolbar plus the already-present `react-markdown` (the project already
  uses it, no new dependency needed).** Not `@mdxeditor/editor` or a similar off-the-shelf
  library, for two reasons: (1) the "Add blank" button (FILL_IN_BLANK) is a project-specific,
  non-standard markdown element — this could only be added to an off-the-shelf editor library via
  a plugin or workaround, whereas with a custom toolbar it's just another button, like H1 or Bold;
  (2) a ready-made editor library with its own styling system would mean a constant fight to keep
  it in sync with the 3-theme (Space/Dark/Light) token system — a custom toolbar draws its color
  from `theme/tokens.ts`, nothing to override.

### 4.3 `QuizBuilder` — replacing the current raw JSON editing

An actual form-based UI: a list of questions, each with text + points + options (text +
correct/incorrect checkbox), a drag handle for reordering, "+ Add question" / "+ Add option"
buttons. The `quiz.json` structure (see `backend/CLAUDE.md`) stays unchanged — only the editor UI
switches from raw Monaco to a form. On save, the builder assembles the JSON and sends it to the
same `/forge/{missionId}/save` endpoint as before.

### 4.4 Star System tree editor — stays, gets refined

The tree structure already designed in `mobile-friendly.md` (Star System → Group → Mission, with
`[↑][↓][→][←]` buttons) is conceptually sound and stays — it just gets a retro-sci-fi visual
overhaul (a card-based, icon-driven, drag-and-drop-ready tree view instead of the current
Material UI list).

### 4.5 Existing admin surfaces that stay — just in a more visible place

There are a few admin features that are **already functionally complete today**, but easily get
lost in the current chaotic/long sidebar list, which makes them non-trivial to even find:

- **Feature flag management** (`/admin/feature-flags`, `FeatureFlagList.tsx`) — a table view, each
  row with an actual `Switch` for on/off, with optimistic UI updates. The backend side
  (`FeatureFlagController`/`Service`) already has full CRUD. **There's no functional work needed
  for this in the redesign** — it just gets a clear, easy-to-find spot in the unified admin
  navigation (similar to 4.4, a card-based/grouped menu instead of one long, undifferentiated
  list), so it doesn't get "lost" the way it does today.
- Similarly kept, just better organized in the navigation: user/role/permission management, admin
  logs (the real-time WebSocket view) — these don't get new functionality in this round either,
  just a new place in the unified admin navigation structure.

---

## 5. Landing page and dashboard

### 5.1 Landing (unauthenticated)

The current `HeroSection`/`FeaturesSection`/`AboutSection`/`FaqSection` structure stays (the
content breakdown is sound), but is visually redesigned:

- The landing page always gets the Space theme's **"hero" intensity** `StarfieldBackground`/
  `NebulaLayer` pairing (see 3.2) — an unregistered visitor doesn't even see the theme picker
  yet, this is the brand's introductory experience. This replaces the current, isolated,
  landing-only `SpaceStationCanvas.tsx` solution with the shared, parameterized component.
- The hero section gets a character animation (the "friendly robot" already planned in
  `new_direction_2026.md`). **Decision: start with a simple SVG placeholder** (no waiting for a
  finished illustrator asset) — a simple geometric/line-art SVG character, animated with
  `framer-motion` (waving, floating), swappable later for a final illustration without changing
  the layout/animation logic.
- CTAs and cards use the design system components above (`GlowCard`, `NeonButton`).

### 5.2 Dashboard (authenticated "cockpit")

Currently minimal. New structure, with a Duolingo-inspired information hierarchy from top to
bottom:

1. **Streak + daily goal bar** (see 7.1) — the topmost, most prominent element.
2. **"Continue where you left off"** card — the last active Star System/Group/Mission.
3. **Star Map preview** — a mini-map of discovered systems, with an "Open map" CTA, from the same
   component as the full Star Map described in 5.3 (scaled down, non-interactive).
4. **Friends' activity** (see 7.2) — a compact list of who did what recently.

**Backend — BOTH cards need a NEW endpoint, neither exists today:**
- **"Continue where you left off" card:** `GET /api/dashboard/continue` — finds the cadet's most
  recently modified `CadetMission`/`MissionGroupProgress` record (the most recent by
  `lastUpdatedAt`/`startedAt`, considering both tables), and returns the minimal data needed to
  continue (`type`, `missionId`/`groupId`, `starSystemId`, `name`). Today **no aggregating query
  exists for this** — `MissionService.startMission()` has a "resumed mission" log line, but that's
  only logged, not queryable data.
- **Friends' activity:** `GET /api/social/activity-feed` — the most recent N completions by
  cadets the current cadet follows (the `follows` table, see 7.2), in reverse chronological order,
  merged `UNION`-style from the `MissionGroupStepCompletion`, `FillInBlankAttempt`, and
  `MissionResult` tables (each already has a `completedAt`/`submittedAt`/`completedAt` timestamp).
  A simple but new service method and DTO.

### 5.3 Star Map — a full rethink

The current `StarMapCanvas.tsx` is a hand-written Canvas 2D rendering that **fits neither the
design system nor the mobile-first principle**:

- **Visually a green "radar/matrix" style** (`VT323` monospace font, a green scanning line,
  concentric radar circles) — a completely different language from the cyan/magenta neon sci-fi
  world planned for every other surface (`ux_ui_terv.md`). It's an isolated, never-aligned design
  island.
- **System positions are `hash(system-id)`-based pseudo-random** — there's no real structure or
  relationship behind them (even though `new_direction_2026.md` originally planned graph edges
  and prerequisite relationships between systems).
- **Works with mouse events only** (`onMouseMove`/`onClick`) — no touch/pinch/pan handling. On
  mobile this means a tap can hit a star (since `onClick` also fires on touch at the browser
  level), but hover-driven name/coordinate reveal and zoom/pan **don't work at all** — breaking
  down hardest exactly for the most important, mobile-only user segment.
- **A star's color is always the same** (`#0f0`, static) — there's no visual status feedback
  (in progress / completed / not started yet), even though that data already exists
  (`CadetMission`/`MissionGroupProgress` records) — the original plan in `ux_ui_terv.md`
  ("Blinking = current, gray = locked, green = done") was never actually wired up.

**The new approach:**

1. **Replace the hand-written Canvas with a dedicated, touch-first graph visualization
   library** — `react-flow` is recommended (already targeted earlier by `new_direction_2026.md`
   too): it natively supports pan/zoom/pinch, node click/tap handling, and is well tested on
   mobile. This alone solves the missing touch interaction, without hand-rolled gesture handling.
2. **Star nodes get the Space theme's design language** (`GlowCard`/glow effects, the
   cyan/magenta palette) instead of the green radar style — visually unified with the
   landing/dashboard/player surfaces, not an isolated "different app" feel.
3. **Real status-based node coloring**: gray = not started yet, a pulsing cyan/glow = current/
   resumable, a green checkmark badge = completed. **This needs a NEW backend endpoint** —
   currently there's NO query that returns the aggregated status for all of a given cadet's Star
   Systems at once (the existing `with-missions` returns the INSIDE of one given system, not an
   overview status across all systems). New endpoint:
   `GET /api/star-systems/with-progress` — adds a computed `status: "NOT_STARTED" |
   "IN_PROGRESS" | "COMPLETED"` field to every Star System, aggregated from the `cadet_missions`
   and `mission_group_progress` tables (a simple rule: if every mission/group in the system is
   COMPLETED → COMPLETED; if at least one has started → IN_PROGRESS; otherwise NOT_STARTED). No
   new table needed, just an aggregating service method and a DTO extension.
4. **Positioning stays a simple, deterministic layout in the MVP** (radial or grid layout based on
   the id, similar to today, just as `react-flow` node coordinates) — the real "prerequisite
   graph" (which system unlocks which, connected by graph edges) would be a separate,
   **Stage 2 backend effort** (introducing a `StarSystem.prerequisiteId` field plus locking
   logic) — see section 8, deliberately NOT part of this round.
5. **The dashboard preview** (5.2, item 3) comes from this same component, just scaled down and
   parameterized as non-interactive — not a separate implementation.

---

## 6. Cadet-facing mission playback — a unified, mobile-first shell

### 6.1 `MissionPlayerShell`

One shared layout component for every playback mode (standalone CONTENT/QUIZ, the Group Player's
internal steps, CODING):

- Header: back button, mission/group name, a progress indicator (`x / y steps`, or a "saved"
  status for CODING).
- Middle area: the type-dependent content (already exists per component, just without a shared
  frame).
- Bottom action bar: "Next" / "Submit" / "Save" — always in the same position, pinned to the
  bottom of the screen on mobile (the thumb-reach zone), not scattered at the end of the content.

This single component replaces the currently independent layouts of `ContentMissionPage`,
`GroupPlayerPage`, `QuizPlayerPage`, and `CodingMissionPage` — each of them just supplies the
*middle content* to the shell.

### 6.2 CODING missions on mobile

Monaco Editor stays (decision: not switching to CodeMirror — less migration risk, and the desktop
UX doesn't suffer), but around it:

- **A full-screen editor mode on mobile** — the file tree and the diagnostics panel are collapsed
  by default, pullable up as a bottom sheet, so the editor itself gets the maximum available
  space.
- **A quick-action bar above the virtual keyboard** (brackets, tab, indentation) — this is
  currently mobile coding's biggest friction point, not Monaco itself.
- The "Save" / "Run diagnostics" buttons live in `MissionPlayerShell`'s bottom action bar, not
  hidden above the file tree.

### 6.3 Star System detail view

The progress-badge list planned in `mobile-friendly.md` (Group/standalone Mission with status) is
sound content-wise, it just gets aligned visually with the new design system, and the cards
appear in a single column with large touch targets on mobile.

---

## 7. New engagement features

### 7.1 Streak

**Backend:**
- `Cadet` entity extension: `currentStreak INT DEFAULT 0`, `longestStreak INT DEFAULT 0`,
  `lastActivityDate DATE`.
- A countable "activity" = any completed step (a Group step completion, standalone CONTENT
  read/"Next", a QUIZ submission, a successful FILL_IN_BLANK submission, a successful CODING
  verification). A shared `StreakService.recordActivity(cadetId)` call is added to the existing
  endpoints for these (`complete-step`, quiz submit, fill-in-blank submit, the mission-verification
  callback).
- `recordActivity` logic: if `lastActivityDate == today` → nothing to do. If `== yesterday` →
  `currentStreak++`. If earlier or null → `currentStreak = 1`. `longestStreak = max(longestStreak,
  currentStreak)`. `lastActivityDate = today`.
- **No separate scheduled job** — a broken streak (missing a day) isn't actively detected as
  "breaking at midnight," but lazily: on the next activity it becomes clear that
  `lastActivityDate` isn't yesterday, and it resets to 1. The "current streak" shown on the
  frontend always reflects this last-computed value — an acceptable MVP simplification; Stage 2
  could add a "streak freeze"/reminder push.
- The `GET /api/auth/me` response gains `currentStreak` and `longestStreak` fields (every page
  already fetches this when logged in anyway).

**Frontend:**
- A `StreakFlame` component at the top of the dashboard and in `MissionPlayerShell`'s header — a
  flame icon + number, with a short "streak +1" micro-animation on completion.

### 7.2 Friends / following

**Decision on the mechanic:** the user explicitly gave Duolingo as a reference — Duolingo uses
**one-way following** (no acceptance step, unlike a Twitter follow), which is simpler and fits the
"social pressure, see where everyone stands" motivation better than a two-way friend
request/accept flow. **Recommendation: a one-way `Follow` relation**, not the Wrenchly-style
two-way `FriendRequest`/PENDING-ACCEPTED model.

**Backend:**
- New table: `follows (follower_id UUID, followee_id UUID, created_at TIMESTAMPTZ, PRIMARY KEY
  (follower_id, followee_id))` — both FKs pointing to `cadets(id)`.
- `FollowService`: `follow(followerId, followeeId)`, `unfollow(...)`, `getFollowing(cadetId)`,
  `getFollowers(cadetId)`.
- `GET /api/cadets/search?username=...` — username-based search (`cadets.username` is already
  unique and indexed).
- `POST /api/cadets/{id}/follow` / `DELETE /api/cadets/{id}/follow` — similar to
  `GET /api/auth/me`, **just being logged in is enough**, no fine-grained permission is attached
  (see section 11 for the exact reasoning — this isn't a permission that belongs in the
  `mission:start` category).

**Frontend:**
- A user search (on the profile page, or a separate "Fleet" page).
- The dashboard's "Friends' activity" card — calls the `GET /api/social/activity-feed` endpoint
  (backend described in 5.2, not duplicated here).

### 7.3 Own profile page

**Backend:**
- `GET /api/cadets/{id}/profile` — public (any logged-in user can view anyone else's profile too):
  `username`, `fullName`, `avatarUrl`, `currentStreak`, `longestStreak`, `totalCompletedMissions`,
  `totalCompletedGroups`, `followerCount`, `followingCount`, `memberSince`.
- The computed fields (`totalCompletedMissions`, etc.) are simple aggregating queries over the
  existing `cadet_missions`/`mission_group_progress` tables — no new table needed.

**Frontend:**
- `ProfilePage` — avatar, base data, streak, stat cards, a "Follow" button (if not your own
  profile), a place for badges (expandable in Stage 2, the numbers are enough for the MVP).
- Editing your own profile (avatar, fullName, username) — this already partly exists on the
  Settings page, just needs to be connected to `ProfilePage`.

---

## 8. Not part of this round (deliberately excluded)

- **Star System prerequisite graph** (5.3) — the real "which system unlocks which" relationship
  and locking logic between systems (`StarSystem.prerequisiteId` plus backend permission logic)
  is separate backend work; in this round the Star Map only gets a visual/interaction refresh,
  positions in the MVP still come from a simple, deterministic layout.
- **XP/score system and badges** — the `reward_xp` mechanic planned in `gamification_roadmap.md`
  is not part of this round; the streak and friends system alone already brings a significant
  engagement improvement, the XP system is a well-separable, standalone feature.
- **Squad/team system** (`gamification_roadmap.md` Phase 2) — a separate, larger plan.
- **`subscription_box_pivot.md`'s physical-product direction** — this is a business decision at a
  different scale, not frontend work, and has no overlap with this plan.
- **Blockly/visual programming, the card-based mobile coding mode** (`mobile-friendly.md` Stage 2)
  — the mobile UX improvement for CODING (see 6.2) is enough for this round; a full alternative
  interaction mode is a separate plan.
- **AI search (`ai_embedding.md`), Social AI (`social_ai.md`)** — independent of this round.
- **Streak "freeze" and reminder push notifications** (7.1) — the MVP streak logic computes
  lazily on the next activity (see 7.1 for details), which is enough for this round; breakage
  protection and reminder pushes are a separate, well-separable feature.

---

## 9. Implementation order (within a single PR, as a checklist)

Per the user's decision, this ships as **one large PR** (not separate phase-PRs), but the internal
work order builds up logically like this:

1. **Theme system and design system foundations** — `theme/tokens.ts` with all three themes
   (Space/Dark/Light), `theme/components.ts`, shared `components/shared/` components
   (`StarfieldBackground`, `NebulaLayer`, `GlowCard`, `NeonButton`, `StreakFlame`, `ProgressRing`),
   theme-switching logic (`data-theme` attribute + `localStorage` + backend persistence). This is
   the foundation everything else builds on.
2. **Backend: theme preference + streak + follow + profile + dashboard/star-map aggregating
   endpoints** — per sections 3.5, 5.2, 5.3, 7.1–7.3 above. One shared Flyway migration (`cadets`
   table extension: `theme_preference`, `current_streak`, `longest_streak`,
   `last_activity_date` + the `follows` table) and 4 NEW endpoints that don't exist at all today:
   `PUT /api/auth/me/theme`, `GET /api/dashboard/continue`, `GET /api/social/activity-feed`,
   `GET /api/star-systems/with-progress` — all simple aggregations over existing tables, no new
   domain model needed (the `follows` table is the only genuinely new table). Can run in parallel
   with the frontend work.
3. **`MissionPlayerShell`** + reworking the 4 existing player pages so they only supply content.
4. **`MarkdownStudio`, `QuizBuilder`, `CodeMissionEditor`** components + unifying them into
   **`MissionEditorPage`** (this replaces the `MissionForgePage`/`MissionEdit` duality).
5. **Landing + dashboard redesign**, built on the design system plus the streak/friends cards.
6. **Profile page + user search.**
7. **Mobile CODING UX refinement** (bottom-sheet file tree, quick-action bar).
8. **Visual overhaul of the Star System tree editor.**
9. **Replacing the Star Map with a `react-flow`-based, touch-first visualization** (5.3) — the
   Canvas 2D rendering goes away, node coloring comes from real progress data.
10. End-to-end testing on mobile viewports (360px, 390px, 430px) on every affected page, plus
    updating the existing Cypress/Vitest tests for the reworked components.

**A cross-cutting requirement for every step above (not a separate step, but a continuous
expectation):** every new string immediately goes into `config.ts` **in both languages** (`en`/`hu`),
the component uses `useTranslation()`, never a hardcoded string — the same convention already
required today (`frontend/CLAUDE.md`). When an old component (e.g. `MissionForgePage`) is actually
retired and replaced, the last step is removing its `config.ts` keys once nothing else references
them — leaving neither unlocalized live code nor dead translation keys in the file.

---

## 10. Frontend architecture and patterns — more reliable, more readable code

This isn't just a visual redesign — the current frontend code's inconsistency **isn't only at the
design level**, it's also at the architecture level, and that's just as real a quality risk as the
fragmented UI. Concrete, code-level evidence of the current state:

- **11 files bypass the central `api/client.ts`**, importing `axios` directly
  (`MissionList.tsx`, `UserEdit.tsx`, `PermissionList.tsx`, `StarSystemList.tsx`, `UserList.tsx`,
  `RoleList.tsx`, `RoleEdit.tsx` and their tests) — meaning these pages **bypass the 401
  interceptor**, so on an expired token they don't automatically log the user out, contrary to the
  behavior already documented as expected in `frontend/CLAUDE.md`.
- **`@tanstack/react-query` is already installed and wired up** (`QueryClientProvider` in
  `main.tsx`), and the **newer** surfaces (Mission Forge, the Play pages, `FillInBlankEditor`,
  `ContentEditor`, `MissionFileEditor`, the Feedback page) already use it — BUT the **older**
  admin CRUD lists (`MissionList`, `StarSystemList`, `UserList`, `RoleList`, `PermissionList`,
  `LogList`, `FeatureFlagList`) all repeat hand-written `useEffect` + `useState(loading/error/data)`
  boilerplate, with slightly different error handling per page. This is exactly the kind of
  inconsistency that makes the whole codebase hard to follow — **two different data-fetching
  patterns coexist for the same problem**.
- **`react-hook-form` + `zod`** (already the chosen form stack per `frontend/CLAUDE.md`) is only
  actually used in 3 places (`LoginPage`, `RegisterPage`, `ForgeConfigPanel`) — the remaining
  forms (e.g. `MissionEdit.tsx`) are written with a raw `useState` object and manual `onChange`
  handlers, with no validation or only ad-hoc validation.

**That's why the redesign also means introducing explicit architecture patterns, not just visual
work:**

### 10.1 Data fetching — React Query everywhere, no exceptions

Everything that's currently solved with `useEffect` + `useState` + manual `loading`/`error`
handling (including the admin lists above) **moves to `useQuery`/`useMutation`**. This isn't a new
library — the project already uses it, just not consistently. Concrete benefit: automatic
caching, retries, and race-condition-free state (with the hand-written versions there's a real
risk that a fast back-to-back navigation lets a stale response overwrite state — React Query
guarantees this is handled), and **a single, shared error-handling pattern** across every page
(see 10.3).

### 10.2 Discipline in the API layer — `client.ts` as the only way in

Direct `axios` imports **disappear from every component** — this is an explicit cleanup task of
the redesign too (not just something to follow on new surfaces, the existing 11 affected files
get rewritten as they're touched). Recommended: an ESLint rule (`no-restricted-imports` on the
`axios` package, except inside `client.ts` itself), so this mistake surfaces at build/lint time
going forward, not just in an audit.

### 10.3 A unified loading/error state component

A shared `components/shared/QueryStateHandler.tsx` (or similar) — takes a `useQuery` state
(`isLoading`/`isError`/`data`) and consistently renders a skeleton/spinner, or a retry-button
`Alert` on error, in the Space design system's language. Every list/detail page uses this instead
of its own hand-written loading/error JSX — on its own, this eliminates the kind of inconsistency
where every page currently looks slightly different while loading or erroring.

### 10.4 Forms — `react-hook-form` + `zod` everywhere

Every new/reworked form (`MissionEditorPage`'s base-data form, `QuizBuilder`, `FillInBlankEditor`,
Settings, the theme picker, the user search) is built with `react-hook-form` and `zod` schema
validation — not a raw `useState` object, the way today's `MissionEdit.tsx` is. Validation schemas
go into a `schemas/` folder next to `types/`, so the frontend and the backend's `@Valid`
validation (see section 11) deliberately mirror each other (e.g. the same max length on both
sides).

### 10.5 Folder structure refinement

The current `pages/`/`components/` split is fundamentally sound, it just needs to be followed more
strictly:
- **`pages/`** — purely route-level composition (layout + pulling in the right domain
  components), **no business logic or direct API calls inside them**.
- **`hooks/`** — all data-fetching/business logic moves here, named by domain
  (`useMissionEditor`, `useStarMap`, `useDashboard`, `useFollowList`, etc.) — these call
  `useQuery`/`useMutation` and the `api/client.ts` modules, and `pages/` just calls this hook and
  passes the resulting state down to components.
- **`components/shared/`** — design system primitives (section 3.4).
- **`components/domain/<domain>/`** (e.g. `components/domain/mission/`,
  `components/domain/social/`) — domain-specific UI pieces reused across multiple pages (e.g.
  `QuizBuilder`, `MarkdownStudio`'s usage sites, `FriendCard`).

This layer separation (pages = thin composition, hooks = logic, components = presentation) is the
concrete, checkable definition of "more reliable, more readable code" for this round — not an
abstract quality goal, but a rule that can be checked in every PR review.

### 10.6 Testing expectations for new/reworked components

Following the existing convention (`pages/admin/__tests__/`, `components/forge/quiz/__tests__/`):
every new shared component (`GlowCard`, `MissionPlayerShell`, `QuizBuilder`, etc.) gets a Vitest
unit test, every new user flow (theme switching, streak increment, follow/unfollow, Star Map
interaction) gets at least one Cypress E2E happy-path test — in the same `cypress/e2e/` structure
as the existing `admin_missions.cy.ts`, etc.

---

## 11. Backend — following existing patterns, not introducing new ones

The new backend work listed in section 7 and the updated 5.2/5.3 (streak, follow, profile, theme
preference, dashboard/continue, activity-feed, star-systems/with-progress) **all follows patterns
already established and documented in the project** (`backend/CLAUDE.md`, `api_spec.md`) — there's
no new architectural decision here, just an extension of the existing layer structure:

- **The layering stays unchanged:** every new feature gets a `web/<domain>` (Controller) →
  `service/<domain>` (Service) → `repository/<domain>` (Repository) → `model/<domain>` (Entity)
  → `dto/<domain>` (Request/Response) package structure, exactly like e.g. the `featureflag` or
  `fillinblank` domain is already built. Specifically: `service/social/` (`FollowService`,
  `ActivityFeedService`), `service/dashboard/` (`DashboardService`), `web/social/`
  (`FollowController`, `SocialController`), `web/dashboard/` (`DashboardController`).
- **Exception handling with the existing custom classes** (`exception/` package) — e.g. "you
  can't follow yourself" → `ResourceConflictException` or a new, similarly simple
  `IllegalStateException`-based business exception, **not** a raw `ex.getMessage()` sent to the
  client (per the existing security rule for `GlobalExceptionHandler`).
- **`@Transactional` discipline** — write operations (`follow`, `unfollow`, streak update, theme
  preference save) get a plain `@Transactional`, read-only aggregations (`with-progress`,
  `dashboard/continue`, `activity-feed`) get `@Transactional(readOnly = true)`, per the existing
  rule (a write should never run inside a `readOnly` outer transaction).
- **Owner/permission pattern correction:** an earlier draft of this plan mistakenly named the
  `mission:start` permission for the `follow`/`unfollow` endpoints — that would be misleading,
  since it's related to `mission:start` neither in content nor in permission category. The
  correct pattern mirrors `GET /api/auth/me`: **simply being logged in is enough, no
  fine-grained permission is needed** (no `@PreAuthorize`, just the authentication the JWT filter
  already provides). The same applies to the `dashboard/continue`, `activity-feed`,
  `star-systems/with-progress`, and `auth/me/theme` endpoints — all of them are data "about the
  logged-in cadet themself," not an admin-level or ownership-checked resource.
- **DTO + `@Valid` discipline** — every new request DTO (`FollowRequest` if needed,
  `ThemePreferenceRequest`) gets Bean Validation annotations, `@Valid` on the controller method,
  per the already documented security pattern.
- **Flyway migration, additive, one logical unit per file** — extending the `cadets` table and
  creating the `follows` table can be **two separate migration files**
  (`Vn__add_cadet_engagement_fields.sql`, `Vn+1__create_follows_table.sql`), continuing the
  project's existing "one logical change, easy-to-follow migration" practice (see the current
  `V1`–`V6` migrations).
- **Test coverage per the existing pattern:** every new service gets a `*ServiceTest.java`
  (JUnit5 + Mockito, the `MockDatabaseTest` pattern), every new controller gets a
  `*ControllerSecurityTest.java` (to verify `@PreAuthorize`/auth behavior) — exactly like the
  `FeatureFlagServiceTest`/`FeatureFlagControllerSecurityTest` pair is already built.
- **The code review requirement is unchanged:** for 3+ modified files, the
  `code-quality-reviewer` agent runs, per the rule set in the repo root `CLAUDE.md` — this applies
  to this new backend work exactly as it does to any earlier feature.

---

## 12. Open questions

No open questions remain — all three earlier points have been settled: the basis for
`MarkdownStudio` and the scope of streak freeze (see 4.2 and section 8 respectively), and the
robot character starts out as an SVG placeholder (see 5.1). With that, the plan is fully ready for
implementation.
