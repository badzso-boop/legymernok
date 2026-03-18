# Frontend — Claude Code Útmutató

## Tech stack

- **React 19.2**, TypeScript 5.9, Vite 7.2
- **Routing**: React Router DOM 7.11 (HashRouter)
- **UI**: Material-UI 7.3 + Tailwind CSS 4.1 + framer-motion 12
- **Kódszerkesztő**: Monaco Editor 4.7 (@monaco-editor/react)
- **Form**: react-hook-form 7.68 + zod 4.2
- **HTTP**: Axios 1.13 — API kliens: `src/api/client.ts`
- **i18n**: i18next 25 + react-i18next 16
- **WebSocket**: @stomp/stompjs 7.2 + SockJS (admin logok)
- **Teszt**: Vitest 4.0 (unit), Cypress 15.8 (E2E)

---

## npm scriptjek

```bash
npm run dev          # Dev szerver → http://localhost:5173 (hot reload)
npm run build        # TypeScript ellenőrzés + Vite build
npm run lint         # ESLint
npm run preview      # Prod build előnézet
npm test             # Vitest unit tesztek
npm run cy:open      # Cypress interaktív
npm run cy:run       # Cypress headless
```

> **Fontos**: A `build` TypeScript-et is ellenőriz (`tsc -b && vite build`). Ha a tesztek futnak, de a build bukik, valószínűleg type hiba.

---

## Mappastruktúra

```
frontend/src/
├── api/
│   └── client.ts                    # Axios kliens + interceptorok + API modulok
├── components/
│   ├── ControlPanel.tsx             # Általános kezelőpanel UI
│   ├── LoadingScreen.tsx
│   ├── RetroButton.tsx              # Space-téma gomb stílus
│   ├── forge/
│   │   ├── ForgeConfigPanel.tsx     # Mission létrehozó form (baloldal)
│   │   ├── ForgeEditor.tsx          # Monaco editor + fájl tab-ok (jobboldal)
│   │   ├── RetroPanel.tsx           # Retro UI konténer wrapper
│   │   └── quiz/
│   │       ├── QuizEditor.tsx       # Kvíz szerkesztő (kérdés + opció kezelés)
│   │       ├── QuestionCard.tsx     # Egy kérdés kártyája (textarea + optionok)
│   │       ├── OptionRow.tsx        # Egy válaszopció sor (szöveg + helyes CB)
│   │       ├── QuizPlayer.tsx       # Kvíz lejátszó (timer, navigáció, beküldés)
│   │       └── __tests__/
│   │           ├── OptionRow.test.tsx
│   │           ├── QuestionCard.test.tsx
│   │           └── QuizPlayer.test.tsx
│   ├── mission/
│   │   └── MissionTable.tsx         # Admin: mission táblázat
│   └── star-system/
│       └── StarSystemTable.tsx      # Admin: star system táblázat
├── context/
│   └── AuthContext.tsx              # JWT auth + user state (React Context)
├── i18n/
│   └── config.ts                    # i18next konfig + fordítások
├── layouts/
│   ├── AdminLayout.tsx              # Admin panel sidebar + header
│   └── MainLayout.tsx              # Fő app layout (nav + outlet)
├── pages/
│   ├── LandingPage.tsx
│   ├── auth/
│   │   ├── LoginPage.tsx
│   │   └── RegisterPage.tsx
│   ├── admin/
│   │   ├── __tests__/               # Admin oldal unit tesztek
│   │   │   ├── MissionEdit.test.tsx
│   │   │   ├── MissionList.test.tsx
│   │   │   ├── RoleEdit.test.tsx
│   │   │   ├── RoleList.test.tsx
│   │   │   ├── UserEdit.test.tsx
│   │   │   └── UserList.test.tsx
│   │   ├── adminlogs/LogList.tsx     # Real-time logok WebSocket-en
│   │   ├── cadets/UserList.tsx + UserEdit.tsx
│   │   ├── missions/MissionList.tsx + MissionEdit.tsx
│   │   ├── permissions/PermissionList.tsx
│   │   ├── roles/RoleList.tsx + RoleEdit.tsx
│   │   └── star-system/StarSystemList.tsx + StarSystemEdit.tsx
│   ├── changelog/ChangelogPage.tsx
│   ├── landing/SpaceStationCanvas.tsx  # Landing canvas animáció
│   ├── mission-forge/
│   │   ├── MissionForgePage.tsx     # Forge fő oldal (ForgeEditor VAGY QuizEditor)
│   │   ├── MyForgePage.tsx          # Saját missionök listája
│   │   └── __tests__/
│   │       └── QuizPlayerPage.test.tsx
│   ├── star-system-detail/StarSystemDetailPage.tsx
│   └── starmap/
│       ├── StarMapCanvas.tsx        # Canvas-alapú galaxis térkép
│       └── StarMapPage.tsx
├── router/index.tsx                 # Összes route + ProtectedRoute guard
├── styles/
│   ├── ControlPanel.css
│   ├── LandingPage.css
│   └── RetroUI.css
└── types/                           # TypeScript interfészek (ld. lent)
    ├── auth.ts
    ├── mission.ts
    ├── mission-forge.ts
    ├── quiz.ts
    ├── role.ts
    ├── starSystem.ts
    └── user.ts
```

---

## Route konfiguráció (`router/index.tsx`)

| Path | Komponens | Auth szükséges | Megjegyzés |
|---|---|---|---|
| `/` | LandingPage | Nem | |
| `/login` | LoginPage | Nem | |
| `/register` | RegisterPage | Nem | |
| `/changelog` | ChangelogPage | Nem | |
| `/forge` | MissionForgePage | Igen | Új mission |
| `/forge/:missionId` | MissionForgePage | Igen | Meglévő szerkesztés |
| `/my-forge` | MyForgePage | Igen | Saját missionök |
| `/play/quiz/:missionId` | QuizPlayerPage | Igen | Kvíz lejátszó |
| `/star-map` | StarMapPage | Igen | Galaxis térkép |
| `/star-systems/:id` | StarSystemDetailPage | Igen | Mission lista |
| `/admin` | AdminLayout | Igen, ADMIN | Admin dashboard |
| `/admin/users` | UserList | Igen, ADMIN | |
| `/admin/users/new` + `/:id` | UserEdit | Igen, ADMIN | |
| `/admin/star-systems` | StarSystemList | Igen, ADMIN | |
| `/admin/star-systems/new` + `/:id` | StarSystemEdit | Igen, ADMIN | |
| `/admin/missions` | MissionList | Igen, ADMIN | |
| `/admin/missions/new` + `/:id` | MissionEdit | Igen, ADMIN | |
| `/admin/roles` | RoleList | Igen, ADMIN | |
| `/admin/roles/new` + `/:id` | RoleEdit | Igen, ADMIN | |
| `/admin/permissions` | PermissionList | Igen, ADMIN | |
| `/admin/logs` | LogList | Igen, ADMIN | |

---

## API kliens (`src/api/client.ts`)

```typescript
// BaseURL: import.meta.env.VITE_API_URL || "http://localhost:8080/api"
// Request interceptor: Authorization: Bearer <token from localStorage>
// Response interceptor: 401 → token törlés
```

### Exportált API modulok

**`forgeApi`**
```typescript
initializeMission(data: CreateMissionInitialRequest): Promise<MissionForgeResponse>
getMissionFiles(missionId: string): Promise<Record<string, string>>
saveMissionFiles(missionId: string, data: MissionForgeContentRequest): Promise<MissionForgeResponse>
getMyStarSystems(): Promise<StarSystemResponse[]>
getMissionById(id: string): Promise<MissionForgeResponse>
getMyMissions(): Promise<MissionResponse[]>
```

**`starSystemApi`**
```typescript
create(data: { name, description, iconUrl? }): Promise<StarSystemResponse>
```

**`quizApi`**
```typescript
startQuiz(missionId: string): Promise<QuizDefinition>
syncProgress(missionId: string, answers: Record<string, string[]>): Promise<void>
submitQuiz(missionId: string, answers: Record<string, string[]>): Promise<MissionResult>
getResults(missionId: string): Promise<MissionResult[]>
```

---

## TypeScript típusok

### `types/auth.ts`
```typescript
interface User { username: string; roles: string[]; exp?: number }
interface AuthState { user: User | null; token: string | null; isAuthenticated: boolean; isLoading: boolean }
```

### `types/mission.ts`
```typescript
type MissionType = "CODING" | "CIRCUIT_SIMULATION" | "QUIZ"
type Difficulty = "EASY" | "MEDIUM" | "HARD" | "EXPERT"
interface MissionResponse { id, starSystemId, name, descriptionMarkdown, templateRepositoryUrl, missionType, difficulty, orderInSystem, createdAt }
```

### `types/mission-forge.ts`
```typescript
type VerificationStatus = "DRAFT" | "PENDING" | "APPROVED" | "REJECTED" | "SUCCESS" | "FAILED" | "REVIEW_NEEDED"
interface CreateMissionInitialRequest { starSystemId, name, descriptionMarkdown?, missionType, difficulty, orderInSystem, templateLanguage: "javascript" | "python" }
interface MissionForgeContentRequest { missionId: string; files: Record<string, string> }
interface MissionForgeResponse extends MissionResponse { ownerId, ownerUsername, verificationStatus }
```

### `types/quiz.ts`
```typescript
interface QuizConfig { timeLimitSeconds: number; allowNavigation: boolean; showSolutions: boolean }
interface QuizOption { id: string; text: string; isCorrect?: boolean }  // isCorrect csak owner/admin látja
interface QuizQuestion { id: string; text: string; points: number; options: QuizOption[] }
interface QuizDefinition { config: QuizConfig; questions: QuizQuestion[] }
interface MissionResult { id, score, maxScore, percentage, detailedAnswers: string, isLate, completedAt }
```

### `types/starSystem.ts`
```typescript
interface StarSystemResponse { id, name, description, createdAt, updatedAt }
interface StarSystemWithMissionsResponse { id, name, description, iconUrl, missions: MissionResponse[] }
```

### `types/user.ts`
```typescript
interface UserResponse { id, username, fullName, email, roles: string[], avatarUrl, createdAt, updatedAt }
```

### `types/role.ts`
```typescript
interface PermissionResponse { id, name, description }
interface RoleResponse { id, name, description, permissions: PermissionResponse[] }
interface CreateRoleRequest { name, description, permissionIds: string[] }
```

---

## QuizPlayer komponens — fontos logika

**Auto-submit timer** (`QuizPlayer.tsx`):
```typescript
// 1. useEffect: timeLeft csökkentése másodpercenként
// 2. useEffect: ha timeLeft === 0 && !isPreview → onSubmit(answers) hívás
```

**Single vs Multi-select**: A helyes válaszok számából dönti el (`correctCount > 1` → checkbox, egyébként radio). *Megjegyzés: csak preview módban van elérhető az isCorrect mező, production játéknál a backend leszedi.*

---

## Teszt fájlok

```
components/forge/quiz/__tests__/
├── OptionRow.test.tsx        # onChange, onDelete callbacks, checkbox állapot
├── QuestionCard.test.tsx     # Index format, opció limit (max 5), SINGLE/MULTI mód
└── QuizPlayer.test.tsx       # Navigáció, single/multi select, timer auto-submit,
                              #   preview mód blokkolja auto-submitet

pages/mission-forge/__tests__/
└── QuizPlayerPage.test.tsx   # API loading/error/success, submit, 409 kezelés, eredmény

pages/admin/__tests__/
├── MissionList.test.tsx      # API fetch, star system nevek, törlés
├── MissionEdit.test.tsx
├── RoleList.test.tsx
├── RoleEdit.test.tsx
├── UserList.test.tsx
└── UserEdit.test.tsx

cypress/e2e/                  # E2E tesztek
├── admin_missions.cy.ts
├── admin_role.cy.ts
├── admin_star_systems.cy.ts
└── admin_users.cy.ts
```

### Teszt futtatás (user végzi)
```bash
npm test              # Vitest — watch mód
npm run cy:open       # Cypress interaktív (kell futó dev szerver)
```

---

## Vite konfiguráció

```typescript
base: isGithubPages ? "/legymernok/" : "/"
server.port: 5173
server.host: true
server.watch.usePolling: true   // WSL/Docker szükséges
test.environment: "jsdom"
test.globals: true
```

---

## Konvenciók

- **Stílus**: Material-UI komponensek + Tailwind utility class-ok, retro space témával (RetroUI.css)
- **API hívások**: mindig az `api/client.ts` moduljain keresztül, ne direkt `axios`-szal
- **Auth állapot**: `useContext(AuthContext)` — ne tárold külön
- **Form**: `react-hook-form` + `zod` validáció
- **Típusbiztonság**: Mindig a `types/` mappából importálj, ne inline interfészt definiálj
- **i18n**: `useTranslation()` hook, ne hardkódolt magyar szöveg a JSX-ben
