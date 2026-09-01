# Security & Logic Audit — 2026-07-30

> **📜 Historical planning document — not necessarily current.** This reflects the state of the project as of 2026-08-15 (its last edit), and may be superseded by later decisions or the actual implementation. Check the code or more recent docs in `plans/` before relying on a specific claim here.

Result of a full-repo audit (backend, frontend, infra/Docker, roadmap gaps).
This is a **state assessment**, not a series of fix commits — the goal is to let Norbi
prioritize what to fix first, in small, standalone PRs (per the repo's usual workflow).
The file:line references in the findings apply to `main`'s state as of 2026-07-30; line
numbers may drift slightly with further commits.

Legend: 🔴 critical · 🟠 high · 🟡 medium · ⚪ low

---

## 🔴 Critical

### 1. The entire server log is streamed publicly, without authentication
`config/LoggingConfig.java:38-39` attaches the `WebSocketLogAppender` to the **ROOT logger** —
every INFO+ level log line (HTTP request bodies from `LogFilter`, stack traces, the Gitea admin
username, error messages) flows through it onto the `/topic/logs` channel
(`WebSocketLogService.java:16`). The `/ws-log` STOMP endpoint is `permitAll()` in
`SecurityConfig.java:45-48`, and `WebSocketConfig.java:25` allows any origin for the handshake
with `setAllowedOriginPatterns("*")`.

**Risk:** anyone on the internet can subscribe without authentication and watch the full server
log in real time — including the password-hash leak described in item 9.
**Recommendation:** exclude `/ws-log` and `/ws-mission-logs` from the `permitAll` list; add a
STOMP CONNECT interceptor with JWT verification + the `logs:read` permission; restrict the
origin pattern to the actual frontend domain.

### 2. Cadets can run arbitrary CI jobs on the same network as the production DB
Per `plans/mission-forge.md` (lines 99-104), every template repo (JS/Python) ships a live
`.gitea/workflows/ci.yml`, which the Gitea Actions runner executes and calls back to the
backend. `GiteaService.createMissionRepository()` (`GiteaService.java:547-580`) copies this
workflow file into the cadet's own repo too, and adds the cadet as a **collaborator with write
access** (line 576). With write access, the cadet can push anything, **including modifying
`.gitea/workflows/ci.yml`** — meaning they can run an arbitrary CI job YAML on the runner.

The `runner` container has `/var/run/docker.sock` mounted (`docker-compose.yml:166`), and the
job containers run on the **`legymernok-net`** network (`runner-config.yaml:72`) — the same
network as `postgres` (see item 3, with the default `postgres`/`postgres` password), `backend`,
`ai-service`, and `ollama`. A malicious `ci.yml` could easily run `psql -h postgres -U
postgres` and **read/write the full production database directly**, bypassing every
application-level RBAC check.

**Recommendation:** put the runner's job containers on a separate, isolated Docker network (not
`legymernok-net`), from which only the necessary outbound access (e.g. the
`mission-verification/callback` endpoint) is allowed.

### 3. `docker-compose.yml` — published postgres port + hardcoded weak password
In `docker-compose.yml`:
- `postgres` service: `ports: - "5432:5432"` — the DB port is published outward, to the host.
- `POSTGRES_PASSWORD: postgres`, `GITEA__database__PASSWD=postgres`,
  `SPRING_DATASOURCE_PASSWORD: postgres` — a weak password **hardcoded directly into the
  compose file** (unlike other secrets, which come from `.env`).

**Note:** per memory, Norbi removed this once in production for security reasons, but that
change never made it into the repo (it's stuck in an uncommitted stash on a different,
`3dterv-plan` branch) — the `main` branch, and with it every future redeploy/rebuild, is
currently still in a vulnerable state.
**Recommendation:** remove the port publication (the backend/Gitea containers can reach it over
the internal network), move the passwords into `.env`, and use a strong, generated value.

### 4. `/ws-mission-logs` — any cadet can see any other cadet's mission build log
`MissionVerificationController.java:72-104` sends Gitea Actions output to the
`/topic/mission/{missionId}` topic, but the subscription endpoint (`/ws-mission-logs`) is also
`permitAll` + wildcard origin, and there's **no ownership check** on the topic subscription.
Since `mission:read` is granted to everyone, any logged-in cadet can fetch the `GET
/api/missions` list, grab the IDs from it, and watch other cadets' build/test logs.
**Recommendation:** require auth for the WS handshake, and add an explicit
owner-or-`mission:edit_any` check on the topic subscription.

---

## 🟠 High

### 5. `ai-service` — unauthenticated, externally published LLM endpoint
The `/embed` and `/generate` endpoints in `ai-service/main.py` aren't gated behind auth, and
`docker-compose.yml` publishes them outward on port `8081:8081` too. Anyone who can reach the
host can use the local LLM for free, without limits (no rate limiting, no input-size limit),
bypassing the backend entirely.
**Recommendation:** remove the port publication (reachable only from `legymernok-net`), and/or
introduce a shared-secret-based auth between backend and ai-service.

### 6. `FillInBlankController` — missing owner check on the answer key
`FillInBlankController.java:37-41`: `@PreAuthorize("hasAuthority('mission:edit')")`, but it
doesn't follow the usual "owner OR `mission:edit_any`" pattern used in `MissionService` (see
`requireMissionEditAccess`, `MissionService.java:205-211`). Any user with `mission:edit` (but
without `edit_any`) can fetch the full answer key of **someone else's** FILL_IN_BLANK mission
(`GET .../fill-in-blank/admin`), and even overwrite their content via the `POST`/`PUT`
endpoints.
**Recommendation:** add an owner check to `FillInBlankService`, following the same pattern as
`MissionService`.

### 7. `StarSystemService.reorderItems` — IDOR, no owner or consistency check
`StarSystemService.java:201-242`: the `starSystemId` parameter is never used to check ownership,
nor to validate that `item1Id`/`item2Id` actually belong to that system. Contrast:
`updateStarSystem`/`deleteStarSystem` in the same file do perform an owner check. Any user with
`starsystem:edit` can reorder the items in **any other** user's star system.
**Recommendation:** add an owner-or-`edit_any` check + validate that both items belong to the
`starSystemId` from the path.

### 8. `MissionGroupService` — group CRUD isn't owner-scoped at all
`MissionGroupService` (create/update/delete/reorder) only checks that the star system exists,
never the owner — there's no `group:*_any` permission variant in `DataInitializer` either. Any
user with `group:edit`/`group:delete` can edit/delete any other content creator's group.
**Recommendation:** either explicitly document this as "collaborative by design" (if
intentional), or add an owner check the same way as for Mission/StarSystem.

### 9. Password hash ends up in the logs (and, per item 1, on the public WS too)
`CadetService.java:74, 128, 175`: `log.info("...Cadet: {}", cadet)` — Lombok's generated
`toString()` on the `Cadet` entity's `@Data` annotation **includes the `passwordHash` field**.
The BCrypt hash is data suitable for offline cracking, and due to item 1 it's currently visible
to anyone subscribed to the public log stream.
**Recommendation:** a dedicated `toString()` (excluding the password field), or just log
`username`.

### 10. `GlobalExceptionHandler` leaks the raw exception message
`GlobalExceptionHandler.java:81-85`: every unhandled exception returns `ex.getMessage()` to the
client in a 500 — NPE field names, SQL constraint/column names, filesystem or Gitea error
messages can all leak.
**Recommendation:** the generic branch should return a fixed, uninformative message; keep the
detail only in (protected!) logs.

### 11. `JwtAuthenticationFilter` — no try/catch around loading the user
`JwtAuthenticationFilter.java:57`: `userDetailsService.loadUserByUsername(username)` isn't
wrapped in try/catch. If a user with a valid JWT gets deleted in the meantime, the
`UsernameNotFoundException` gets thrown unhandled in front of the `DispatcherServlet` (inside
the filter) — `@ControllerAdvice` never catches it, resulting in a generic servlet-level 500
instead of a 401.
**Recommendation:** wrap `loadUserByUsername` in try/catch too, and don't set an auth on
failure.

---

## 🟡 Medium

### 12. Missing `@Valid` and validation annotations
`MissionController.java:33,48` (`initializeForgeMission`, `saveForgeMissionContent`) doesn't use
`@Valid`, even though the DTOs (`CreateMissionInitialRequest`, `MissionForgeContentRequest`) do
carry `@NotNull`/`@NotBlank` annotations — these currently never run in production. The
`CreateMissionRequest`, `CreateStarSystemRequest`, `RegisterRequest`, `LoginRequest`,
`CreateCadetRequest` DTOs have no validation annotations at all, and the controllers don't call
`@Valid` either. Practical effect: password length, email format, and username length aren't
enforced server-side.
**Recommendation:** add `@Valid` to every POST/PUT + minimal `@NotBlank`/`@Size`/`@Email` on the
user/star system/mission DTOs.

### 13. No `MethodArgumentNotValidException` handler
`GlobalExceptionHandler.java` has no dedicated branch for this — for the (rare) endpoints
protected by `@Valid`, a validation error falls into the generic 500 branch, with the message
leak described in item 10 along with it.
**Recommendation:** a dedicated handler, returning a 400 with per-field error messages.

### 14. Some `GiteaService` read methods don't call `validateFilePath`
`getFileContent` (516-535), `getFileInfo` (383-397), `getRepoContents` (491-508) don't validate
the path, unlike `uploadFile`/`uploadFiles`/`deleteFile`/`renameFile` (all of which call
`validateFilePath`, line 622-629). Currently every call site gets the path from a Gitea listing,
not from direct user input — so there's no exploitable traversal today. This is, however,
exactly the class of bug the project has already hit once before (PR #21 code review): any
future caller that passes a user-supplied `filePath` would reintroduce the vulnerability.
**Recommendation:** call `validateFilePath` defensively in all three methods.

### 15. Missing security HTTP headers (`frontend/nginx.conf`)
No `Content-Security-Policy`, `X-Frame-Options`/`frame-ancestors`, `X-Content-Type-Options`,
`Referrer-Policy`, `Permissions-Policy`. (HSTS is typically handled by the Cloudflare
Tunnel/edge, but if this nginx were ever to serve HTTPS directly, that would be missing too.)
**Recommendation:** add `add_header` directives to the `server {}` block — with CSP, pay
attention to the Monaco Editor's worker/blob: sources and the WebSocket `connect-src`.

### 16. Dockerfiles run as root
None of `backend/Dockerfile`, `frontend/Dockerfile`, `ai-service/Dockerfile` has a `USER`
directive — all three containers run internally as root. No build-time baked-in secrets were
found in any of them.
**Recommendation:** add a non-root `USER` at the end of all three Dockerfiles.

### 17. CORS allowlist contains dev origins in production
`SecurityConfig.java:63-74`: `localhost:3000/5173` and the `127.0.0.1` variants are hardwired
alongside the production origin, with no profile separation.
**Recommendation:** separate dev/prod CORS lists via a Spring profile (low practical risk,
more of a hygiene item).

### 18. Admin pages bypass the shared API client
`frontend/src/pages/admin/**` (UserList, UserEdit, MissionList, RoleList, RoleEdit,
StarSystemList, PermissionList, LogList) use raw `axios` with a manually assembled
`Authorization` header — against the project's own `frontend/CLAUDE.md` convention. This
bypasses `client.ts`'s response interceptor, which clears the token on a 401 — with an
expired/invalid token, these views don't automatically log the user out.
**Recommendation:** switch to `api/client.ts`, per `frontend/CLAUDE.md`'s own rule.

---

## ⚪ Low

19. **`application.properties:16`**: `gitea.template-repo-url="asd.com"` — unused, no Java code
    references it; literal quote characters in the value. Should be removed.
20. **JWT has a 24-hour expiry, no refresh/logout/revocation** — an acceptable design
    limitation for stateless JWT, but a stolen token stays valid for up to a day, with no
    server-side revocation mechanism.
21. **`LogFilter.java:41`**: naive `contains("\"password\"")` password filtering in logging —
    case-sensitive, easily bypassed with a differently named/cased field.
22. ~~**`frontend/.env` is committed** (`VITE_API_URL=/api`, not sensitive content), while the
    root `.env` is `.gitignore`d — an inconsistent pattern, a risk if a sensitive value were
    ever added to it by mistake.~~ — **FIXED (2026-08-15).** The `VITE_API_URL` default moved
    into the code (`|| "/api"`), so the file is no longer needed; removed from version control,
    and `frontend/.dockerignore` also excludes it so a local copy can't leak into the image.
23. **`.idea/`** directory (5 files) is committed at the repo root.
24. **`npm audit` is recommended** for the build chain/CI — with large, frequently updated
    dependencies (Monaco Editor, sockjs-client, etc.), this is the only reliable source of
    CVE information, rather than guessing.
25. **`GITEA__server__ROOT_URL=http://localhost:3001/`** (`docker-compose.yml:48`) is a
    hardcoded localhost, even though production runs behind `legymernok.ujjweb.hu`. Current
    functional impact is limited (the Forge/Play flow goes through the Gitea Contents API, not
    a clone URL), but if anyone ever accesses the Gitea web UI directly, they'll see incorrect
    clone URLs there.

---

## Open, known architectural/logic gaps

- **Gitea orphan repo**: if the DB transaction fails AFTER the Gitea repo is created, the repo
  is left orphaned (no compensating rollback against Gitea). Long-known, still open.
- **The `CIRCUIT_SIMULATION` mission type has no dedicated cadet-side UI** —
  `StarSystemDetailPage.tsx` follows the old, pre-PR #21 pattern of opening the raw Gitea repo
  in a new tab (`window.open`), rather than an in-app editor.
- **Mobile Coding type and Blockly/visual programming**
  (`plans/mobile-friendly.md:379-383`) — confirmed, based on the `MissionType` enum and
  `package.json`, that this hasn't been started.
- **Fill-in-blank admin statistics** (`plans/mobile-friendly.md:385-387`) — no admin-side
  statistics UI under `frontend/src/pages/admin`.
- **PWA / Galaxy Map graph visualization (react-flow/D3) / Robot control** — these parts of the
  2026 direction (`plans/new_direction_2026.md`) haven't started at the code level yet
  (`vite-plugin-pwa`, `react-flow`, `d3` are all absent from `package.json`).
- **The `cadets`/`roles`/`permissions`/`cadet_roles`/`roles_permissions` tables predate Flyway**
  (the `V1` migration starts from `baseline-version=1`) — there isn't a single migration file
  for them in the repo. If a new field is ever added to these entities, there's no existing
  pattern/history to follow — this could easily reproduce the schema-drift bug class that's
  already happened 3 times, if someone temporarily switches back to `ddl-auto=update`.

## Positive findings (explicitly confirmed by the review — NOT bugs)

- `spring.jpa.hibernate.ddl-auto=validate` is set in production (not `update`) — it throws an
  error on startup on a schema mismatch, instead of silently corrupting it. The `V1`–`V5`
  migrations cover all current entities, no new drift.
- **No** hardcoded secrets in `application.properties` — everything comes from `${ENV_VAR}`.
  The root `CLAUDE.md`'s "partially fixed" note is outdated — it's actually been fully fixed
  (updated in this PR).
- `react-markdown` runs **without** the `rehype-raw` plugin — no XSS risk through the markdown
  fields (`descriptionMarkdown`, content), because raw HTML never gets rendered.
- `postgres-init/init.sql` contains only `CREATE DATABASE` statements, no seed password or
  admin account.
- `.env` has never entered the git history (verified with `git log --all -- .env`).
- The quiz answer-key leak (a previously known bug) remains correctly protected —
  `stripAnswers()` consistently runs on cadet-facing responses.

---

## Suggested next steps

This document is a state assessment, it contains no code fixes. Suggested order, in separate
PRs:
1. The four 🔴 critical items (WS-log auth, CI runner network isolation, postgres port+password,
   mission-log IDOR) — these are severe, immediately exploitable access holes in production.
2. The 🟠 high items, primarily the IDORs (6-8) and the password-hash log leak (9).
3. The 🟡/⚪ items based on the team's capacity, and prioritizing the roadmap gaps based on
   Norbi's decision.
