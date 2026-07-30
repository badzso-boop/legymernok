# Biztonsági és logikai átvilágítás — 2026.07.30

Teljes repó-átvilágítás eredménye (backend, frontend, infra/Docker, roadmap-hiányosságok).
Ez egy **állapotfelmérés**, nem egy javító commit-sorozat — a cél, hogy Norbi priorizálni
tudja, mit javítunk ki elsőként, kis, önálló PR-ekben (a repo szokásos workflow-ja szerint).
A findingok fájl:sor hivatkozásai a `main` ág 2026-07-30-i állapotára vonatkoznak, a sorszámok
kis mértékben eltolódhatnak további commitokkal.

Jelmagyarázat: 🔴 kritikus · 🟠 magas · 🟡 közepes · ⚪ alacsony

---

## 🔴 Kritikus

### 1. A teljes szerver-log nyilvánosan, hitelesítés nélkül streamelve
`config/LoggingConfig.java:38-39` a `WebSocketLogAppender`-t a **ROOT loggerre** akasztja fel —
minden INFO+ szintű log (HTTP request body-k a `LogFilter`-ből, stack trace-ek, Gitea admin
username, hibaüzenetek) átmegy rajta a `/topic/logs` csatornára (`WebSocketLogService.java:16`).
A `/ws-log` STOMP endpoint `SecurityConfig.java:45-48`-ban `permitAll()`, a
`WebSocketConfig.java:25` pedig `setAllowedOriginPatterns("*")`-gal bármilyen origint enged a
handshake-hez.

**Kockázat:** bárki, az internetről, hitelesítés nélkül feliratkozhat és valós időben látja a
teljes szerverlogot — beleértve a 9. pontban leírt jelszó-hash szivárgást is.
**Javaslat:** `/ws-log` és `/ws-mission-logs` kivétele a `permitAll` listából; STOMP CONNECT
interceptor JWT-ellenőrzéssel + `logs:read` jog; origin pattern szigorítása a valós frontend
domainre.

### 2. Kadétok tetszőleges CI-jobot futtathatnak ugyanazon a hálózaton, mint a production DB
A `plans/mission-forge.md` (99-104. sor) szerint minden template repo (JS/Python) tartalmaz egy
élő `.gitea/workflows/ci.yml`-t, amit a Gitea Actions runner futtat és visszahív a backendnek.
A `GiteaService.createMissionRepository()` (`GiteaService.java:547-580`) ezt a workflow-fájlt
is lemásolja a kadét saját repójába, és a kadétot **write jogú kollaborátorként** adja hozzá
(576. sor). Write joggal a kadét bármit pushol, **beleértve a `.gitea/workflows/ci.yml`
módosítását is** — tehát tetszőleges CI-job YAML-t futtathat a runneren.

A `runner` konténer `/var/run/docker.sock`-ot kap (`docker-compose.yml:166`), és a job-konténerek
a **`legymernok-net`** hálózaton futnak (`runner-config.yaml:72`) — ugyanazon, mint a `postgres`
(lásd 3. pont, alapértelmezett `postgres`/`postgres` jelszóval), `backend`, `ai-service`,
`ollama`. Egy rosszindulatú `ci.yml` minden további nélkül futtathat `psql -h postgres -U
postgres`-t és **közvetlenül olvashatja/írhatja a teljes production adatbázist**, megkerülve
minden alkalmazás-szintű RBAC-ot.

**Javaslat:** a runner job-konténereit külön, izolált Docker hálózatra tenni (ne
`legymernok-net`), ahonnan csak a szükséges kimenő hozzáférés (pl. a
`mission-verification/callback` endpoint) engedélyezett.

### 3. `docker-compose.yml` — publikált postgres port + hardkódolt gyenge jelszó
`docker-compose.yml`:
- `postgres` service: `ports: - "5432:5432"` — a DB port kifelé, a hoszt felé publikálva.
- `POSTGRES_PASSWORD: postgres`, `GITEA__database__PASSWD=postgres`,
  `SPRING_DATASOURCE_PASSWORD: postgres` — **közvetlenül a compose fájlba hardkódolt**, gyenge
  jelszó (nem `.env`-ből jön, mint a többi secret).

**Megjegyzés:** a memória szerint Norbi ezt élesben egyszer már eltávolította biztonsági okból,
de az a módosítás soha nem került be a repóba (egy másik, `3dterv-plan` branch nem-commitolt
stash-ében ragadt) — a `main` ág, és ezzel minden jövőbeli újratelepítés/rebuild, jelenleg is
sebezhető állapotban van.
**Javaslat:** port publikálás eltávolítása (a backend/gitea konténerek a belső hálózaton elérik),
jelszavak áthelyezése `.env`-be, erős, generált érték.

### 4. `/ws-mission-logs` — bármely kadét bármely másik mission build-logját láthatja
`MissionVerificationController.java:72-104` a Gitea Actions kimenetét a
`/topic/mission/{missionId}` topicra küldi, de a feliratkozási endpoint (`/ws-mission-logs`)
szintén `permitAll` + wildcard origin, és **nincs ownership-ellenőrzés** a topic
feliratkozásnál. Mivel `mission:read` mindenkinek jár, bármely bejelentkezett kadét lekérheti a
`GET /api/missions` listát, onnan az ID-kat, és végignézheti más kadétok build/teszt logjait.
**Javaslat:** WS handshake-hez auth kötelező, a topic feliratkozásnál explicit
owner-vagy-`mission:edit_any` ellenőrzés.

---

## 🟠 Magas

### 5. `ai-service` — hitelesítés nélküli, kifelé publikált LLM endpoint
`ai-service/main.py` `/embed` és `/generate` endpointjai nincsenek auth-hoz kötve, és a
`docker-compose.yml` a `8081:8081` porton kifelé is publikálja őket. Bárki, aki eléri a hosztot,
ingyen és korlátlanul (nincs rate limit, nincs input-méret limit) használhatja a helyi LLM-et a
backend megkerülésével.
**Javaslat:** port publikálás eltávolítása (csak a `legymernok-net`-ről érhető el), és/vagy egy
megosztott secret-alapú auth bevezetése backend↔ai-service között.

### 6. `FillInBlankController` — hiányzó owner-ellenőrzés a megoldókulcsnál
`FillInBlankController.java:37-41`: `@PreAuthorize("hasAuthority('mission:edit')")`, de a
`MissionService`-ben megszokott "owner VAGY `mission:edit_any`" mintát (lásd
`requireMissionEditAccess`, `MissionService.java:205-211`) itt nem követi. Bármely
`mission:edit` jogú felhasználó (akinek nincs `edit_any`-je) lekérheti **más** FILL_IN_BLANK
missziójának teljes megoldókulcsát (`GET .../fill-in-blank/admin`), sőt a `POST`/`PUT`
végpontokkal felül is írhatja más tartalmát.
**Javaslat:** owner-check bevezetése a `FillInBlankService`-be, ugyanazzal a mintával, mint a
`MissionService`-ben.

### 7. `StarSystemService.reorderItems` — IDOR, nincs owner- és konzisztencia-ellenőrzés
`StarSystemService.java:201-242`: a `starSystemId` paraméter nincs kihasználva sem
tulajdonos-ellenőrzésre, sem annak validálására, hogy `item1Id`/`item2Id` valóban ehhez a
rendszerhez tartozik-e. Kontraszt: `updateStarSystem`/`deleteStarSystem` ugyanebben a fájlban
owner-check-et végez. Bármely `starsystem:edit` jogú user átrendezheti bármely **más**
felhasználó star systemjében lévő elemek sorrendjét.
**Javaslat:** owner-vagy-`edit_any` check + annak validálása, hogy mindkét item a path-beli
`starSystemId`-hoz tartozik.

### 8. `MissionGroupService` — a group CRUD egyáltalán nem owner-scoped
A `MissionGroupService` (create/update/delete/reorder) csak a star system létezését nézi,
tulajdonost sosem — nincs `group:*_any` permission-variáns sem a `DataInitializer`-ben. Bármely
`group:edit`/`group:delete` jogú felhasználó bármely más content creator csoportját
szerkesztheti/törölheti.
**Javaslat:** vagy explicit dokumentálni "collaborative by design"-ként (ha szándékos), vagy
owner-check hozzáadása, ahogy Mission/StarSystem esetén.

### 9. Jelszó-hash bekerül a logokba (és onnan a 1. pont miatt a nyilvános WS-be is)
`CadetService.java:74, 128, 175`: `log.info("...Cadet: {}", cadet)` — a `Cadet` entitás
Lombok `@Data`-generált `toString()`-je **tartalmazza a `passwordHash` mezőt**. A BCrypt hash
offline crackelésre alkalmas adat, és a 1. pont miatt jelenleg bárki számára látható, aki a
publikus log-streamre feliratkozik.
**Javaslat:** dedikált `toString()` (jelszó-mező kizárásával), vagy csak `username` logolása.

### 10. `GlobalExceptionHandler` kiszivárogtatja a nyers kivétel-üzenetet
`GlobalExceptionHandler.java:81-85`: minden le nem kezelt kivételnél `ex.getMessage()` megy
vissza a kliensnek 500-ban — NPE mezőnevek, SQL constraint/oszlopnevek, fájlrendszer- vagy
Gitea-hibaüzenetek szivároghatnak ki.
**Javaslat:** generikus ág fix, semmitmondó üzenetet adjon vissza; a részletet csak (védett!)
logba.

### 11. `JwtAuthenticationFilter` — nincs try/catch a user-betöltés körül
`JwtAuthenticationFilter.java:57`: `userDetailsService.loadUserByUsername(username)` nincs
try/catch-ben. Ha egy érvényes JWT-vel rendelkező usert időközben törölnek, a
`UsernameNotFoundException` a `DispatcherServlet` elé (a filter-ben) kezeletlenül dobódik, a
`@ControllerAdvice` ezt nem kapja el — generikus szervlet-szintű 500 lesz 401 helyett.
**Javaslat:** try/catch a `loadUserByUsername` köré is, hiba esetén ne állítson be authot.

---

## 🟡 Közepes

### 12. Hiányzó `@Valid` és validációs annotációk
`MissionController.java:33,48` (`initializeForgeMission`, `saveForgeMissionContent`) nem
használ `@Valid`-ot, holott a DTO-k (`CreateMissionInitialRequest`,
`MissionForgeContentRequest`) tartalmaznak `@NotNull`/`@NotBlank` annotációkat — ezek jelenleg
élesben sosem futnak le. `CreateMissionRequest`, `CreateStarSystemRequest`, `RegisterRequest`,
`LoginRequest`, `CreateCadetRequest` DTO-kon egyáltalán nincs validációs annotáció, a
controllerek sem hívnak `@Valid`-ot. Gyakorlati hatás: nincs kikényszerítve jelszóhossz,
email-formátum, username-hossz szerver oldalon.
**Javaslat:** `@Valid` pótlása minden POST/PUT-on + minimális `@NotBlank`/`@Size`/`@Email` a
user/star system/mission DTO-kra.

### 13. Nincs `MethodArgumentNotValidException` handler
`GlobalExceptionHandler.java`-ban nincs erre külön ág — a (ritka) `@Valid`-dal védett
endpointoknál egy validációs hiba a generikus 500-as ágra esik a 10. pontban leírt
üzenet-szivárgással együtt.
**Javaslat:** külön handler, 400-as válasz mezőnkénti hibaüzenettel.

### 14. `GiteaService` néhány olvasó metódusa nem hívja a `validateFilePath`-et
`getFileContent` (516-535), `getFileInfo` (383-397), `getRepoContents` (491-508) nem validál
útvonalat, szemben `uploadFile`/`uploadFiles`/`deleteFile`/`renameFile`-lel (mind hívja
`validateFilePath`-et, 622-629. sor). Jelenleg minden hívási hely Gitea-listázásból kapja a
path-ot, nem közvetlen user inputból — ma nincs kihasználható traversal. Ez viszont pontosan az
a hibaosztály, ami a projektben már egyszer (PR #21 code review) előfordult: bármely jövőbeli
hívó, aki user-supplied `filePath`-et ad át, visszahozza a sebezhetőséget.
**Javaslat:** `validateFilePath` hívása defenzíven mindhárom metódusba.

### 15. Hiányzó biztonsági HTTP headerek (`frontend/nginx.conf`)
Nincs `Content-Security-Policy`, `X-Frame-Options`/`frame-ancestors`, `X-Content-Type-Options`,
`Referrer-Policy`, `Permissions-Policy`. (HSTS-t jellemzően a Cloudflare Tunnel/edge oldja meg,
de ha ez a nginx valaha közvetlen HTTPS-t szolgálna ki, az is hiányzik.)
**Javaslat:** `add_header` direktívák a `server {}` blokkba — CSP-nél figyelni kell a Monaco
Editor worker/blob: forrásaira és a WebSocket `connect-src`-re.

### 16. Dockerfile-ok root userként futnak
`backend/Dockerfile`, `frontend/Dockerfile`, `ai-service/Dockerfile` egyikében sincs `USER`
direktíva — mindhárom konténer root userként fut belül. Build-time bebakeolt secretet egyikben
sem találtunk.
**Javaslat:** nem-root `USER` hozzáadása mindhárom Dockerfile végére.

### 17. CORS allowlist dev-origin-öket tartalmaz élesben
`SecurityConfig.java:63-74`: `localhost:3000/5173` és `127.0.0.1` variánsok fixen be vannak
drótozva a production origin mellé, profil-elkülönítés nélkül.
**Javaslat:** Spring profillal elválasztani dev/prod CORS listát (alacsony gyakorlati kockázat,
inkább higiénia).

### 18. Admin oldalak megkerülik a közös API klienst
`frontend/src/pages/admin/**` (UserList, UserEdit, MissionList, RoleList, RoleEdit,
StarSystemList, PermissionList, LogList) nyers `axios`-t használ, manuálisan összerakott
`Authorization` headerrel — a projekt saját `frontend/CLAUDE.md` konvenciója ellenére. Ez
kikerüli a `client.ts` response interceptorát, ami 401-nél törli a tokent — lejárt/érvénytelen
tokennél ezek a nézetek nem jelentkeznek ki automatikusan.
**Javaslat:** átállítás `api/client.ts`-re a `frontend/CLAUDE.md` saját szabálya szerint.

---

## ⚪ Alacsony

19. **`application.properties:16`**: `gitea.template-repo-url="asd.com"` — használaton kívüli,
    semmilyen Java kód nem hivatkozik rá; szó szerinti idézőjelek a value-ban. Törlendő.
20. **JWT 24 órás lejárat, nincs refresh/logout/revocation** — tervezési korlátnak elfogadható
    stateless JWT-nél, de egy ellopott token akár egy napig érvényben marad, semmilyen szerver
    oldali eszköz nincs visszavonásra.
21. **`LogFilter.java:41`**: naiv `contains("\"password\"")` jelszó-szűrés a naplózásban —
    case-sensitive, könnyen megkerülhető más elnevezésű/case-elt mezővel.
22. **`frontend/.env` be van commitolva** (`VITE_API_URL=/api`, nem titkos tartalom), miközben a
    gyökér `.env` `.gitignore`-olt — inkonzisztens minta, kockázat, ha valaha titkos érték
    kerülne bele tévedésből.
23. **`.idea/`** mappa (5 fájl) be van commitolva a repo gyökerében.
24. **`npm audit` javasolt** a build-lánchoz/CI-hoz — a nagy, gyakran frissülő függőségek
    (Monaco Editor, sockjs-client stb.) miatt megbízható CVE-információt csak ez ad, találgatás
    helyett.
25. **`GITEA__server__ROOT_URL=http://localhost:3001/`** (`docker-compose.yml:48`) hardkódolt
    localhost, holott élesben `legymernok.ujjweb.hu` mögött fut. Jelenlegi funkcionális hatás
    korlátozott (a Forge/Play flow a Gitea Contents API-n megy, nem clone URL-en), de ha valaha
    valaki a Gitea webes UI-ját közvetlenül eléri, ott hibás clone URL-eket fog látni.

---

## Nyitott, ismert architekturális/logikai hiányosságok

- **Gitea orphan repo**: ha a DB tranzakció a Gitea repo létrehozása UTÁN bukik, a repo árván
  marad (nincs kompenzáló rollback Gitea felé). Régóta ismert, még nyitott.
- **`CIRCUIT_SIMULATION` misszió típusnak nincs dedikált kadét-oldali felülete** — a
  `StarSystemDetailPage.tsx` a régi, PR #21 előtti mintát követve nyers Gitea repót nyit meg új
  tabban (`window.open`), nem app-on belüli szerkesztőt.
- **Mobile Coding típus és Blockly/vizuális programozás** (`plans/mobile-friendly.md:379-383`) —
  a `MissionType` enum és a `package.json` alapján megerősítve, hogy nincs kezdve.
- **Fill-in-blank admin statisztikák** (`plans/mobile-friendly.md:385-387`) — nincs admin
  oldali statisztikai UI a `frontend/src/pages/admin` alatt.
- **PWA / Galaxis Térkép gráf-vizualizáció (react-flow/D3) / Robot-vezérlés** — a 2026-os
  irányvonal (`plans/new_direction_2026.md`) e részei kódszinten még nem indultak el
  (`vite-plugin-pwa`, `react-flow`, `d3` egyike sincs a `package.json`-ban).
- **`cadets`/`roles`/`permissions`/`cadet_roles`/`roles_permissions` táblák Flyway előttiek**
  (a `V1` migráció `baseline-version=1`-től indul) — ezekhez nincs egyetlen migrációs fájl sem a
  repóban. Ha ezekhez az entitásokhoz valaha új mező kerül, nincs meglévő minta/history, amit
  követni kellene — könnyen újratermelheti a korábban 3× előfordult séma-drift hibaosztályt, ha
  valaki átmenetileg visszaáll `ddl-auto=update`-re.

## Pozitív megállapítások (amiket a review kifejezetten megerősített, NEM hiba)

- `spring.jpa.hibernate.ddl-auto=validate` van élesben (nem `update`) — induláskor hibát dob
  sémaeltérésnél, ahelyett hogy csendben elrontaná. A `V1`–`V5` migrációk lefedik az összes
  jelenlegi entitást, nincs új drift.
- `application.properties`-ben **nincs** hardkódolt secret — minden `${ENV_VAR}`-ból jön. A
  gyökér `CLAUDE.md` "részben javítva" megjegyzése elavult, ténylegesen teljesen javítva van
  (frissítve ebben a PR-ben).
- `react-markdown` `rehype-raw` plugin NÉLKÜL fut — nincs XSS-kockázat a markdown mezőkön
  (`descriptionMarkdown`, content) keresztül, mert raw HTML nem kerül renderelésre.
- `postgres-init/init.sql` kizárólag `CREATE DATABASE` parancsokat tartalmaz, nincs benne seed
  jelszó vagy admin fiók.
- `.env` soha nem került be a git történetbe (ellenőrizve `git log --all -- .env`-vel).
- A Quiz-válaszkulcs-szivárgás (korábbi ismert hiba) továbbra is helyesen védett —
  `stripAnswers()` következetesen fut a kadét-oldali válaszokon.

---

## Javasolt következő lépések

Ez a dokumentum állapotfelmérés, nem tartalmaz kódjavítást. Javasolt sorrend külön PR-ekben:
1. A négy 🔴 kritikus pont (WS-log auth, CI-runner hálózat-izoláció, postgres port+jelszó,
   mission-log IDOR) — ezek élesben, azonnal kihasználható, súlyos hozzáférési rések.
2. A 🟠 magas pontok, elsősorban az IDOR-ok (6-8.) és a jelszó-hash log-szivárgás (9.).
3. A 🟡/⚪ pontok a csapat kapacitása szerint, illetve a roadmap-hiányosságok priorizálása
   Norbi döntése alapján.
