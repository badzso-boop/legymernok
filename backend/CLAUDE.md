# Backend — Claude Code Útmutató

## Tech stack

- **Java 17**, Spring Boot 3.4.1
- **Spring Security** JWT (jjwt-api 0.11.5) + RBAC
- **Spring Data JPA** + PostgreSQL 16
- **WebSocket** (STOMP) — real-time log streaming
- **Lombok** — builder, getter/setter generálás
- **JUnit 5 + Mockito** — unit tesztek
- **SpringDoc OpenAPI** 2.8.9 — Swagger UI
- Build: Maven

---

## Csomagstruktúra

```
com.legymernok.backend/
├── config/
│   ├── AppConfig.java              # RestClient, ObjectMapper bean-ek
│   ├── DataInitializer.java        # Permission + Role seed (startup)
│   ├── SecurityConfig.java         # CORS, JWT filter, nyilvános útvonalak
│   ├── WebSocketConfig.java        # STOMP WebSocket konfig
│   └── WebSocketLogAppender.java   # Logback → WebSocket bridge
├── dto/
│   ├── cadet/                      # CreateCadetRequest, CadetResponse
│   ├── mission/                    # CreateMissionInitialRequest, MissionResponse,
│   │                               #   MissionForgeContentRequest, CreateForgeMissionRequest
│   ├── quiz/                       # QuizDefinition (config + questions + options)
│   ├── Roles/                      # CreateRoleRequest, RoleResponse
│   ├── starsystem/                 # CreateStarSystemRequest, StarSystemResponse,
│   │                               #   StarSystemWithMissionResponse
│   ├── user/                       # LoginRequest, LoginResponse, RegisterRequest/Response
│   └── Permission/                 # PermissionResponse
├── exception/
│   ├── GlobalExceptionHandler.java # @RestControllerAdvice, minden kivétel kezelés
│   ├── ResourceNotFoundException   # 404
│   ├── ResourceConflictException   # 409, .setData() extra payload-hoz
│   ├── UnauthorizedAccessException # 403
│   └── ExternalServiceException    # Gitea / külső API hiba
├── integration/
│   └── GiteaService.java           # Teljes Gitea REST API kliens
├── model/
│   ├── auth/
│   │   ├── Permission.java         # id, name (pl. "mission:read"), description
│   │   └── Role.java               # id, name (pl. "ROLE_ADMIN"), Set<Permission>
│   ├── cadet/
│   │   ├── Cadet.java              # id, username, email, passwordHash, fullName,
│   │   │                           #   avatarUrl, giteaUserId, Set<Role>
│   │   └── CadetRole.java          # Junction segéd entitás
│   ├── mission/
│   │   ├── Mission.java            # Ld. mezők lent
│   │   ├── MissionResult.java      # Kvíz eredmény: score, maxScore, percentage,
│   │   │                           #   detailedAnswers(JSON), submissionHash, isLate
│   │   ├── QuizSession.java        # quizSnapshot(JSON), currentAnswers(JSON),
│   │   │                           #   startTime, endTimeLimit, completed
│   │   ├── MissionType.java        # CODING, CIRCUIT_SIMULATION, QUIZ
│   │   ├── MissionStatus.java      # LOCKED, NOT_STARTED, IN_PROGRESS, COMPLETED
│   │   ├── VerificationStatus.java # DRAFT, PENDING, SUCCESS, FAILED,
│   │   │                           #   APPROVED, REJECTED, REVIEW_NEEDED
│   │   └── Difficulty.java         # EASY, MEDIUM, HARD, EXPERT
│   ├── starsystem/
│   │   └── StarSystem.java         # id, name, description, iconUrl, owner(Cadet)
│   └── ConnectTable/
│       └── CadetMission.java       # cadetId, missionId, status, repositoryUrl,
│                                   #   startedAt, completedAt
├── repository/
│   ├── auth/                       # PermissionRepository, RoleRepository
│   ├── cadet/                      # CadetRepository
│   ├── mission/                    # MissionRepository, MissionResultRepository
│   ├── quiz/                       # QuizSessionRepository
│   ├── starsystem/                 # StarSystemRepository
│   └── ConnectTables/              # CadetMissionRepository
├── security/
│   └── JwtAuthenticationFilter.java # Bearer token kinyerés, SecurityContext set
├── service/
│   ├── admin/                      # LogService, WebSocketLogService
│   ├── cadet/CadetService.java     # CRUD + Gitea user provision
│   ├── mission/
│   │   ├── MissionService.java     # Forge logika, fájlkezelés, hozzáférés-ellenőrzés
│   │   └── MissionLogService.java  # Audit logging
│   ├── quiz/QuizService.java       # Start, sync, submit, pontozás
│   ├── role/RoleService.java       # RBAC management
│   ├── starsystem/StarSystemService.java
│   └── user/AuthService.java       # Register, login, JWT generálás
└── web/
    ├── AdminLogController.java     # GET /api/admin-logs
    ├── mission/
    │   ├── MissionController.java          # Mission CRUD + Forge endpointok
    │   └── MissionVerificationController.java # Gitea callback
    ├── quiz/QuizController.java    # Quiz start/sync/submit/results
    ├── Role/RoleController.java
    ├── starsystem/StarSystemController.java
    ├── cadet/CadetController.java
    └── user/AuthController.java
```

---

## REST API endpointok

### Auth — `/api/auth`
| Metódus | Path | Permission | Leírás |
|---|---|---|---|
| POST | `/api/auth/register` | Nyilvános | Regisztráció |
| POST | `/api/auth/login` | Nyilvános | Bejelentkezés → JWT |
| GET | `/api/auth/me` | Auth | Saját profil |

### Missions — `/api/missions`
| Metódus | Path | Permission | Leírás |
|---|---|---|---|
| GET | `/api/missions` | `mission:read` | Összes mission (szűrhető: `?starSystemId=`) |
| GET | `/api/missions/{id}` | `mission:read` | Egy mission |
| GET | `/api/missions/my-missions` | `mission:read` | Saját (owned) missionök |
| GET | `/api/missions/next-order` | `mission:read` | Következő sorrend szám (`?starSystemId=`) |
| POST | `/api/missions/forge/initialize` | `mission:create` | Forge mission létrehozás |
| GET | `/api/missions/{id}/forge/files` | `mission:read` | Mission fájlok betöltése Gitea-ból |
| POST | `/api/missions/{id}/forge/save` | `mission:edit` | Mission fájlok mentése Gitea-ba |
| PUT | `/api/missions/{id}` | `mission:edit` | Mission frissítés |
| DELETE | `/api/missions/{id}` | `mission:delete` | Mission törlés |
| POST | `/api/missions/{id}/start` | `mission:start` | Mission indítás (CadetMission létrehozás) |

### Quiz — `/api/quiz`
| Metódus | Path | Permission | Leírás |
|---|---|---|---|
| POST | `/api/quiz/{missionId}/start` | `mission:start` | Kvíz indítás/folytatás → QuizDefinition (válaszok nélkül) |
| PUT | `/api/quiz/{missionId}/sync` | `mission:start` | Válaszok autosave |
| POST | `/api/quiz/{missionId}/submit` | `mission:start` | Beküldés → MissionResult |
| GET | `/api/quiz/{missionId}/results` | `quiz:view_results` | Korábbi eredmények |

### Star Systems — `/api/star-systems`
| Metódus | Path | Permission | Leírás |
|---|---|---|---|
| GET | `/api/star-systems` | `starsystem:read` | Összes |
| GET | `/api/star-systems/my-systems` | `starsystem:read` | Saját rendszerek |
| GET | `/api/star-systems/{id}` | `starsystem:read` | Missionökkel együtt |
| POST | `/api/star-systems` | `starsystem:create` | Létrehozás |
| PUT | `/api/star-systems/{id}` | `starsystem:edit` | Frissítés |
| DELETE | `/api/star-systems/{id}` | `starsystem:delete` | Törlés |

### Egyéb
| Metódus | Path | Permission | Leírás |
|---|---|---|---|
| POST | `/api/mission-verification/{id}/callback` | Nyilvános | Gitea Actions webhook |
| GET | `/api/admin-logs` | `logs:read` | Rendszerlogok |
| GET | `/api/users` | `user:read` | Felhasználók listája |
| PUT | `/api/users/{id}` | `user:edit` | Felhasználó szerkesztés |
| DELETE | `/api/users/{id}` | `user:delete` | Törlés |
| GET/POST/PUT/DELETE | `/api/roles/**` | `role:read/write` | RBAC kezelés |
| GET | `/api/permissions` | `role:read` | Permissionök listája |

---

## Mission entitás mezők

```java
UUID id
StarSystem starSystem           // ManyToOne
String name
String descriptionMarkdown
String templateRepositoryUrl    // Gitea repo clone URL
MissionType missionType         // CODING | CIRCUIT_SIMULATION | QUIZ
Difficulty difficulty           // EASY | MEDIUM | HARD | EXPERT
Integer orderInSystem           // Pozíció a star systemben (UNIQUE per system)
Cadet owner                     // ManyToOne, a készítő
VerificationStatus verificationStatus  // DRAFT → PENDING → SUCCESS/FAILED
Instant createdAt, updatedAt
```

---

## Mission Forge logika (MissionService)

**Hozzáférés-ellenőrzés minta** (minden Forge metódusban):
1. Betölti az aktuális cadetet az auth contextből
2. Ellenőrzi: `cadet == mission.owner` VAGY `cadet.hasAuthority("mission:edit_any")`
3. Ha sem: `UnauthorizedAccessException`

**`initializeForgeMission`**: StarSystem ownership-et ellenőriz → Mission létrehoz (DRAFT) → Gitea repo → templateRepositoryUrl beállítás

**`saveForgeMissionContent`**: Fájlokat Gitea-ra tölti → VerificationStatus = PENDING

**`getMissionFiles`**: Gitea-ból visszaolvassa → Quiz mission esetén nem-ownernek `isCorrect` mezőket kivágja

**Sorrend-shifting**: Ha az `orderInSystem`-re már van mission, az ütközőket +1-gyel tolja el.

---

## Quiz logika (QuizService)

**Pontozás**: `pointsPerCorrect = question.points / correctAnswers.count`
- Helyes válasz: +pointsPerCorrect
- Helytelen válasz: -pointsPerCorrect (de minimum 0 per kérdés)

**Duplikáció-megelőzés**: `submissionHash = SHA-256(sorba rendezett válaszok)` → `409 ResourceConflictException` ha már létezik, payload = az eredeti MissionResult

**Késői beküldés**: `isLate = Instant.now().isAfter(session.endTimeLimit)`

**quiz.json struktúra** (Gitea repo gyökérben):
```json
{
  "config": { "timeLimitSeconds": 900, "allowNavigation": true, "showSolutions": false },
  "questions": [
    {
      "id": "q1", "text": "...", "points": 10,
      "options": [
        { "id": "o1", "text": "...", "isCorrect": false },
        { "id": "o2", "text": "...", "isCorrect": true }
      ]
    }
  ]
}
```

---

## GiteaService — metódusok

```java
// Felhasználó
createGiteaUser(username, email, password) → Long (giteaUserId)
deleteGiteaUser(username)

// Repository
createEmptyRepository(repoName, isPrivate) → String (cloneUrl)
deleteAdminRepository(repoName)            // admin saját repója
deleteRepository(owner, repoName)
copyRepositoryContents(srcOwner, srcRepo, targetRepoName)
createMissionRepository(missionId, templateLanguage, cadet, missionType) → String

// Fájlok
uploadFile(owner, repo, filePath, content) → String
uploadFiles(owner, repo, Map<filename,content>, commitMessage, cadet)
getFileContent(owner, repo, filePath) → String
getRepoContents(owner, repo, path) → List<GiteaContent>

// Együttműködők
addCollaborator(repoOwner, repoName, username, permission)

// Getter
getAdminUsername() → String
```

---

## Teszt struktúra

```
backend/src/test/java/com/legymernok/backend/
├── config/MockDatabaseTest.java               # @MockDatabase annotation (no-DB unit tests)
├── service/
│   ├── cadet/CadetServiceTest.java            # Cadet CRUD + Gitea provision
│   ├── mission/MissionServiceTest.java        # Forge init/save/get/delete, quiz filtering
│   ├── quiz/ (nincs külön, QuizService coverage a MissionServiceTest-ben)
│   ├── role/RoleServiceTest.java              # RBAC CRUD
│   ├── starsystem/StarSystemServiceTest.java  # StarSystem CRUD + ownership
│   └── users/AuthServiceTest.java             # Login, register, JWT
└── web/
    ├── AdminLogControllerSecurityTest.java
    ├── cadet/CadetControllerSecurityTest.java
    ├── mission/MissionControllerSecurityTest.java   # Forge endpointok biztonsága
    ├── role/RoleControllerSecurityTest.java
    ├── starsystem/StarSystemControllerSecurityTest.java
    └── user/AuthControllerSecurityTest.java
```

### Teszt futtatás (user végzi)
```bash
mvn test                               # Összes
mvn test -Dtest=MissionServiceTest     # Egy osztály
```

---

## Konvenciók

- **Kivételek**: mindig a `exception/` csomag egyedi osztályait használd
- **Új permission hozzáadása**: `DataInitializer.java`-ban seed + Role-hoz rendelés + `@PreAuthorize` a controllerben
- **Új entitás**: model → repository → service → controller → DTO sorrend
- **Lombok**: `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor` — mindig
- **Response wrap**: `ResponseEntity<T>` minden controllerből
- **Tranzakció**: `@Transactional` a service rétegen, ahol szükséges
- **Verziókezelés**: kis, gyakori commitok (egy logikai változás = egy commit) + branch/PR minden változtatáshoz (`gh pr create`), a projekt gyökér `CLAUDE.md`-ben leírt irányelv szerint — ne push-olj közvetlenül `main`-re
