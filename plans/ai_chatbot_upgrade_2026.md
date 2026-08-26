# AI chatbot — RAG, streaming, observability fejlesztési terv (2026-08-24)

> **Státusz: TERV, nincs implementáció.** Ez a dokumentum a jóváhagyás előtt álló architekturális
> tervet rögzíti, hogy át tudjuk beszélni, mielőtt bármelyik fázis (lásd lent) ténylegesen
> elkezdődne. Öt önálló, egymástól függetlenül review-olható PR-ra van bontva — egyiket sem
> implementáljuk, amíg ezt a tervet Norbi jóvá nem hagyja.

## Kontextus

Az `ai_chatbot` feature (feature flaggel kapcsolható widget, ld. `/admin/feature-flags`) kódja és
infrastruktúrája teljes és élőben tesztelten működik (backend `ChatController`/`ChatService`,
frontend `ChatWidget.tsx`, egy vékony Python FastAPI wrapper `ai-service/main.py` Ollama köré) —
de a jelenlegi implementáció szándékosan minimális volt (első verzió, "működjön" szinten):

- **Egyetlen, chunking nélküli embedding** csillagrendszerenként (`StarSystemService.generateAndSaveEmbedding`)
  — a missziók tényleges tartalma (`descriptionMarkdown`, `content`, FILL_IN_BLANK `templateText`)
  soha nincs indexelve, csak a misszió-NEVEK kerülnek bele a csillagrendszer flat szövegébe.
- **Egyetlen, blokkoló LLM-hívás** (`ai-service/main.py` `/generate`, `stream: false` hardkódolva) —
  nincs token-streaming, a felhasználó a teljes válaszra vár.
- **Regex-szel kiparsingolt akció** — a modell egy `{"action":"FILL_FORM",...}` JSON-t told bele a
  szabad szöveges válasz végére, amit egy DOTALL regex vág ki (`ChatService.FILL_FORM_PATTERN`) —
  törékeny, nem strukturált-kimenet minta.
- **Nulla observability, nulla teszt** az AI-rétegen (`ChatService`, `AiEmbeddingService`,
  `ai-service/`, `ChatWidget.tsx`/`ChatContext.tsx` egyike sem tesztelt).

**Cél**: ezt a réteget iparági szabvány AI-engineering mintákra fejleszteni (chunkolt RAG, hibrid
retrieval + reranking, valódi token-streaming, strukturált kimenet, eval-vezérelt observability) —
elsősorban azért, hogy Norbi ezt portfólió-darabként be tudja mutatni (LinkedIn, AI engineer
állásinterjú), másodsorban mert ez ténylegesen jobb, robusztusabb architektúra is lesz.

**Fontos keret-feltétel**: az `ollama`/`ai-service` konténer NEM fog futni ezen a megosztott
szerveren (ahol a `legymernok.ujjweb.hu` éles oldal fut) — a fejlesztés ebben a repóban történik
(branch+PR-ekkel), de a tényleges élő teszteléshez Norbi a saját ("nagy") gépén húzza le a branch-et
és futtatja ott a valódi Ollamát. Emiatt minden fázisnak automatikusan, élő LLM nélkül
ellenőrizhetőnek kell lennie (mockolt unit tesztekkel) — a manuális, élő Ollama-s végső ellenőrzés
Norbi feladata és explicit meg van jelölve minden fázisnál.

## Ellenőrzött tények (nem feltételezések — konkrét fájlokból)

- `MissionType` enum (`backend/src/main/java/com/legymernok/backend/model/mission/MissionType.java`):
  `CODING, CIRCUIT_SIMULATION, QUIZ, CONTENT, FILL_IN_BLANK`. (A gyökér `CLAUDE.md` csomagtérkép-
  táblázata elavult, még a régi 3 típust sorolja fel — az enum a hiteles forrás.)
- A CONTENT-misszió szövege a `Mission.content` (TEXT) oszlopban él; a FILL_IN_BLANK sablonszöveg
  egy külön `FillInBlankDefinition.templateText`-ben, 1:1 kapcsolatban a misszióval,
  `FillInBlankDefinitionRepository.findByMissionId(UUID)`-on keresztül elérhető (ez a repository-
  metódus már létezik, nem kell újat írni).
- **Nincs `spring-webflux`** a `backend/pom.xml`-ben — sima Spring MVC/Tomcat. A helyes streaming-
  primitív `SseEmitter`, nem `WebClient`/`Flux`.
- A következő szabad Flyway migráció **`V10`** (`V1`...`V9` léteznek, a legutóbbi
  `V9__create_sectors_table.sql`).
- A `pgcrypto`/`gen_random_uuid()` már aktívan használatban van a `V1__reset_domain_schema.sql`-ban
  és a `V9`-ben is — tehát az extension már engedélyezve van ezen az adatbázisban, a `V10` migráció
  nyugodtan használhatja `DEFAULT gen_random_uuid()`-t, ahogy minden más tábla is teszi.
- A `WebSocketLogAppender` a **ROOT** loggerre van felkötve, semmilyen név/szint-szűrés nélkül
  (`LoggingConfig.java`), egy sima `PatternLayoutEncoder`-rel formáz (`%m`, MDC nélkül), és mindent
  a `/topic/logs`-ra told, amit a meglévő `/admin/logs` oldal fogyaszt. **Azaz: bármilyen új
  `@Slf4j` log-sor bárhol automatikusan megjelenik ott, nulla új bekötéssel.** MDC-mezők viszont
  láthatatlanok maradnának (a pattern nem tartalmaz `%X{...}`-et) — a strukturált mezőket a log
  ÜZENET szövegébe kell tenni, sima `key=value` formában.
- **Nincs beágyazott/teszt Postgres** (se Testcontainers, se H2) a backend teszt-setupban — a V10
  migráció szintaktikai/futtatási helyessége csak egy valódi Postgres ellen ellenőrizhető, ez
  Norbi kézi lépése lesz, nem valami, amit `mvn test` önmagában bizonyít.
- Meglévő teszt-konvenció (`StarSystemServiceTest.java`): JUnit5 + Mockito, közvetlenül a Java-
  szintű határon (`AiEmbeddingService`, `JdbcTemplate`) mockolva, NEM egy fake HTTP szerveren
  keresztül — az új AI-réteg teszteknek is ezt a mintát kell követniük.
- A `RestTemplate` a meglévő HTTP-kliens bean; nincs és nem is kell reaktív kliens — a
  `RestTemplate.execute(uri, method, requestCallback, responseExtractor)` nyers streamelt
  `InputStream`-et ad a NDJSON-olvasó kódnak, új dependency nélkül.

## Tervezési döntések

- **RAG**: a `star_systems.content_embedding` változatlan marad; új **`content_chunks`** tábla
  KIZÁRÓLAG a misszió-szintű tartalomra (chunkolt `descriptionMarkdown`/`content` +
  `FillInBlankDefinition.templateText`). A `ChatService` a két retrieval-útvonal (csillagrendszer
  flat keresés + misszió-chunk hibrid keresés) eredményét egy közös kontextus-blokkba fésüli össze.
  Chunk-méret: **800 karakter / 150 karakter átfedés**, lehetőleg `\n\n` bekezdéshatáron vágva.
- **Hibrid retrieval**: két külön JDBC-lekérdezés (pgvector koszinusz-ANN + Postgres full-text
  `ts_rank`), Java-oldali **Reciprocal Rank Fusion**-nal egyesítve (`1/(60+rank)` listánként,
  szabványos `k=60`) — a fúziós logika önmagában, DB-mock nélkül unit-tesztelhető.
- **Reranking**: egy plusz, nem streamelt Ollama-hívás (`format:"json"`), ami a top ~10 RRF-jelöltet
  0–10 közötti relevancia-pontszámmal látja el; `ObjectMapper`-rel parse-olva (nem regex-szel);
  bármilyen parse-hiba esetén visszaesik a rerank előtti RRF-sorrendre, sosem töri el a kört.
- **Streaming**: az `ai-service` kap egy új `/generate/stream` végpontot, ami Ollama natív NDJSON
  streamjét (`stream:true`) FastAPI `StreamingResponse` + `httpx.AsyncClient.stream()` segítségével
  továbbítja — nincs új Python dependency. A Spring backend ezt az NDJSON-t `RestTemplate.execute()`
  + egy streamelő `ResponseExtractor`-ral olvassa, és SSE-ként adja tovább a böngészőnek egy új
  `GET /api/chat/stream` végponton (`SseEmitter`, háttérszálon egy kis `ExecutorService` bean-nel,
  mivel a streamelt olvasás blokkolja a szálat). A frontend ehhez az egy híváshoz lecseréli az
  axiost egy kézzel írt `fetch()` + `ReadableStream` SSE-parserre (a JWT az `Authorization` fejlécben
  marad, natív `EventSource`-t emiatt nem lehet használni, mert az nem tud egyedi fejlécet küldeni).
- **Strukturált kimenet**: a régi, szabad szövegbe ágyazott regex-es `FILL_FORM` teljesen lecserélve
  (nem fallback marad — ez az egyetlen hívó, és nincs éles forgalom, amit migrálni kéne). A látható
  válasz természetes szövegként streamel; **miután végzett**, ha az oldal form-kitöltésre alkalmas,
  egy külön, nem streamelt `format:"json"` hívás nyeri ki az akciót, egyetlen záró SSE `action`
  eseményként küldve, mielőtt az emitter lezár. A régi `POST /api/chat` törlésre kerül.
- **Observability**: nincs új pipeline — csak `@Slf4j` log-sorok `key=value` szöveggel minden
  fázishatáron (retrieval, rerank, generálás, akció-kinyerés), amik automatikusan megjelennek a
  meglévő `/admin/logs` nézetben.
- **Eval harness**: admin UI fül (`/admin/eval`), nem önálló szkript — a golden set (kérdés-válasz
  halmaz) adatbázisban tárolva és a UI-n szerkeszthető, a futtatás egy gombnyomás a backendről, ami
  a valódi Java retrieval-service-eket hívja (nem egy külön Python-implementáció), az eredmény
  (hit-rate@k, kérdésenkénti bontás, futás-történet) közvetlenül az oldalon jelenik meg.

## Fázisok — mindegyik önálló, mergelhető PR

### PR #0 — Retrieval-biztonság (ELŐBB mergelendő)

**Teljes terv: [`pr0_retrieval_security_2026.md`](pr0_retrieval_security_2026.md).**

A 2026-08-26-i átvizsgálás négy olyan hiányosságot talált, amik mind ugyanabból erednek: a
`content_chunks` réteg egy **bizalmi határon lóg át**, a tervek viszont adat-problémaként
kezelték, nem hozzáférési kérdésként. Röviden, amit a PR #0 rendez:

- **`ROLE_CADET` elveszíti a `mission:create` és `starsystem:create` jogot** (Norbert döntése,
  2026-08-26: a kadétok kizárólag missziókat teljesítenek). Ezzel megszűnik a keresztfelhasználós
  prompt-injection csatorna: az indexbe innentől csak admin/content-creator tartalom kerül.
  **Migráció nem kell** — a `DataInitializer.createRoleIfNotFound()` lecseréli a jogosultság-
  halmazt, nem hozzáfűzi.
- **`GiteaService.SOLUTION_FILE_PATTERN` kiemelése** egy közös `MissionFilePatterns` osztályba,
  hogy a PR #1 chunkere ugyanazt a mintát használja, ami a kadét-másolatból már ma is kihagyja
  a referencia megoldást. Egy lista, ne kettő.

Ehhez kapcsolódóan a PR #1 kap egy `visibility` oszlopot (lásd a `V10` sémát lent), a PR #2
pedig egy **kötelező** `RetrievalScope` paramétert minden keresési belépési ponton — a
részletek a PR #0 doksijában, mert egy összefüggő tervet alkotnak.

**Prerekvizit Norbertnek**: a meglévő, kadét által írt tartalom leltára és rendezése (PR #0
2.4 szakasz) — **a PR #1 reindexe éles adaton addig nem futtatható**.

### PR #1 — RAG chunking backend

- **Új migráció** `backend/src/main/resources/db/migration/V10__create_content_chunks.sql`:
  ```sql
  CREATE TABLE IF NOT EXISTS content_chunks (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      source_type VARCHAR(32) NOT NULL,
      source_id UUID NOT NULL REFERENCES missions(id) ON DELETE CASCADE,
      file_path VARCHAR(500) NOT NULL DEFAULT '',
      chunk_index INT NOT NULL,
      chunk_text TEXT NOT NULL,
      content_embedding vector(768),
      embedding_model VARCHAR(64) NOT NULL,
      visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
      search_vector tsvector GENERATED ALWAYS AS (to_tsvector('hungarian', chunk_text)) STORED,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      CONSTRAINT content_chunks_source_type_check CHECK (source_type IN ('MISSION', 'MISSION_FILL_IN_BLANK', 'MISSION_CODE_FILE')),
      CONSTRAINT content_chunks_visibility_check CHECK (visibility IN ('PUBLIC', 'AUTHOR_ONLY')),
      CONSTRAINT content_chunks_unique_chunk UNIQUE (source_type, source_id, file_path, chunk_index)
  );
  -- 2026-08-26: SZÁNDÉKOSAN NINCS vektor-index ezen a táblán, ld. az indoklást lentebb.
  CREATE INDEX IF NOT EXISTS idx_content_chunks_search_vector
      ON content_chunks USING gin (search_vector);
  CREATE INDEX IF NOT EXISTS idx_content_chunks_source
      ON content_chunks (source_type, source_id);
  ```
  (A `V2__add_pgvector.sql` index-stílusát követi; a generált `tsvector` oszlop nem igényel
  service-oldali karbantartást.)
  **2026-08-26-i kiegészítés — `source_id` idegen kulcs `ON DELETE CASCADE`-dzsel.** Eredetileg
  a `source_id` egy szabad UUID-oszlop volt, FK nélkül. Ez árva chunkokat hagyott volna: a
  PR #1 hook-pontjai (5. szakasz) csak a létrehozást/módosítást fedik le, a `deleteMission()`-t
  NEM — egy törölt misszió szövege így bennragadt volna az indexben, és a chatbot továbbra is
  felszolgálta volna. Mivel **mindhárom `source_type` ugyanarra a `missions.id`-ra hivatkozik**
  (a `MISSION_FILL_IN_BLANK` is a misszió ID-jához kötött, nem a `FillInBlankDefinition`
  sajátjához — ld. `ContentChunkDto` 3. szakasz), egyetlen FK mindhármat lefedi, és a takarítás
  az adatbázis dolga lesz, nem egy könnyen elfelejthető service-hívásé. **Következmény, amivel
  számolni kell:** ha valaha egy nem-misszió alapú `source_type` kerülne a táblába (pl.
  `STAR_SYSTEM`), ez az FK megakadályozná — akkor vagy külön tábla kell, vagy az FK-t polimorf
  megoldásra kell cserélni (és akkor visszajön a kézi takarítás igénye).
  **2026-08-25-i pontosítás**: a `file_path` oszlop `NOT NULL DEFAULT ''` (nem NULL-abilis) —
  ez tudatos döntés, mert Postgres-ben két `NULL` érték SOSE számít egyenlőnek egy UNIQUE
  constraintben, tehát ha `file_path` NULL-abilis lenne, a `MISSION`/`MISSION_FILL_IN_BLANK`
  típusú (fájlhoz nem köthető) chunkoknál a `content_chunks_unique_chunk` constraint csendben
  KIkapcsolódna (két NULL sose ütközik) — üres string-gyel ez a védelem megmarad mindhárom
  `source_type`-ra. Részletek és a `MISSION_CODE_FILE` típus indoklása:
  [`pr1_rag_chunking_architecture_2026.md`](pr1_rag_chunking_architecture_2026.md) 12. szakasz.
- **Új** `backend/.../dto/rag/ContentChunkDto.java` —
  `record ContentChunkDto(UUID id, String sourceType, UUID sourceId, String filePath, int chunkIndex, String chunkText, double score)`
  (`filePath` üres string a `MISSION`/`MISSION_FILL_IN_BLANK` típusoknál, a tényleges fájl-
  útvonal `MISSION_CODE_FILE`-nál — ld. a pontosítás fent).
- **Új** `backend/.../service/rag/ContentChunkingService.java` — pure JDBC (a `StarSystemService`
  meglévő pgvector-mintáját követve, nincs JPA entitás ehhez a táblához):
  - `List<String> chunkText(String text)` — pure function, 800/150 sliding window, `\n\n` törésre
    törekedve.
  - `void reindexMission(UUID missionId)` (`@Transactional`) — betölti a `descriptionMarkdown`+
    `content`-et, törli a meglévő `MISSION` chunkokat, chunkol+embedel+beszúr. **Embed-hiba esetén
    csak warningot logol, a régi chunkok érintetlenül maradnak** (ugyanaz a graceful-degradation
    minta, mint a `StarSystemService.generateAndSaveEmbedding`-ben — sose maradjon egy misszió
    indexe csendben üres egy átmeneti ai-service-kiesés miatt).
  - `void reindexFillInBlankOnly(UUID missionId)` — ugyanez `MISSION_FILL_IN_BLANK` forrástípusra.
  - `void deleteChunks(String sourceType, UUID sourceId)`, `int reindexAllMissions()` (a
    `StarSystemService.reindexAllStarSystems()`-t tükrözi).
- **Bekötés**: `MissionService.createMission()`/`updateMission()`/`updateMissionContent()` és
  `FillInBlankService.saveDefinition()` — mentés után hívja a megfelelő reindex-metódust,
  szinkron, a request-szálban (ugyanaz a minta/indoklás, mint a meglévő csillagrendszer-embedding
  triggernél).
- **Bővítés**: `SearchController`-ben `POST /api/admin/reindex-content`
  (`@PreAuthorize("hasAuthority('starsystem:edit_any')")`, a meglévő reindex-permissiont
  újrahasznosítva), ami `reindexAllMissions()`-t hív.
- **Tesztek**: `ContentChunkingServiceTest.java` (Mockito, a `StarSystemServiceTest` mintáját
  követve) — `chunkText()` határeset/átfedés/bekezdéstörés esetek mock nélkül; `reindexMission()`
  ellenőrzi a törlés-majd-beszúrás sorrendet és az embed-hibánál való graceful degradation-t.
- **Kézi ellenőrzés (Norbi, itt nem elvégezhető)**: V10 alkalmazása egy valódi Postgres ellen, az új
  reindex végpont hívása, `SELECT count(*) FROM content_chunks;`.

### PR #2 — Hibrid retrieval + reranking

- **Új** `backend/.../service/ai/AiServiceClient.java` — az új ai-service hívás-formák egy helyre
  gyűjtése (az `AiEmbeddingService`-t érintetlenül hagyjuk, hogy a már működő kódot ne kockáztassuk):
  `JsonGenerateResult generateJson(String prompt, String systemPrompt)` — `{prompt, system_prompt,
  format:"json"}` POST a `/generate`-re.
- **`ai-service/main.py`**: `GenerateRequest` kap egy `format: str | None = None` mezőt; a
  `/generate` handler beleteszi Ollama payloadjába, ha meg van adva. Nincs új dependency.
- **Új** `backend/.../service/rag/HybridRetrievalService.java`:
  - `List<ContentChunkDto> retrieveMissionChunks(String query, int topK)` — embedeli a kérdést,
    lefuttatja a `vectorSearch()`-öt (koszinusz-ANN a `content_chunks`-on) és a `fullTextSearch()`-öt
    (`ts_rank` + `plainto_tsquery('hungarian', ?)`), mindkettő `topK*3`-at hoz le, `rrfMerge()`-dzsel
    egyesítve.
  - `static List<ContentChunkDto> rrfMerge(List<ContentChunkDto> a, List<ContentChunkDto> b, int topK)`
    — pure function, `k=60`, chunk-id szerinti dedup, `1/(60+rank)` összegzés, csökkenő rendezés —
    ez az elsődleges unit-teszt célpont (nincs szükség semmilyen mockra).
- **Új** `backend/.../service/rag/RerankingService.java` — `rerank(query, candidates, keepTop)`:
  egy `AiServiceClient.generateJson()` hívás, ami `{"<index>": <0-10 pontszám>}`-ot kér vissza,
  `ObjectMapper`-rel parse-olva; bármilyen parse-hiba warningot logol és visszaadja a rerank előtti
  sorrendet.
- **`ChatService.chat()`** (ebben a PR-ban még a régi szinkron metódus): a meglévő csillagrendszer-
  keresés után hozzáadja a hibrid+rerank hívást, bővíti a `buildContextLines()`-t egy "Releváns
  misszió-részletek" blokkal. Itt kerül be a `chat_retrieval`/`chat_rerank` strukturált log-sor is
  (a PR #4 observability-munkájának egy része szinte ingyen bejön itt).
- **Tesztek**: `HybridRetrievalServiceTest` (a `JdbcTemplate` kétféle mockolása + alapos,
  táblázatos `rrfMerge()` esetek), `RerankingServiceTest` (érvényes/hibás/részleges JSON esetek),
  `AiServiceClientTest` (mockolt `RestTemplate`), `ai-service/tests/test_generate_format.py` (új,
  `httpx.MockTransport`, nincs új prod dependency — `pytest` egy új
  `ai-service/requirements-dev.txt`-be kerül).

### PR #3 — Streaming + strukturált kimenet (frontend-et is érinti)

- **`ai-service/main.py`**: új `POST /generate/stream` — `StreamingResponse` egy async generátor
  köré, ami Ollama nyers NDJSON sorait `httpx.AsyncClient.stream()`-mel továbbítja. Nincs új
  dependency (`sse-starlette` nem kell — az ai-service→backend úton nyers NDJSON marad, a Spring
  backend végzi az NDJSON→SSE fordítást a böngésző felé).
- **`AiServiceClient`**: `streamGenerate(prompt, contextLines, systemPrompt,
  Consumer<OllamaStreamChunk> onChunk, Runnable onDone, Consumer<Exception> onError)` —
  `RestTemplate.execute()` + egy soronként olvasó streamelő `ResponseExtractor`;
  `record OllamaStreamChunk(String response, boolean done, Long evalCount, Long evalDuration, Long promptEvalCount)`.
- **`AppConfig.java`**: `@Bean ExecutorService chatStreamExecutor()`
  (`Executors.newCachedThreadPool()` — ezen a léptéken bőven elég).
- **`ChatController`**: a `POST /api/chat` helyett `GET /api/chat/stream?message=...&context=<url-
  encoded JSON>` (`produces = TEXT_EVENT_STREAM_VALUE`, `@PreAuthorize("isAuthenticated()")`,
  `SseEmitter`-t ad vissza). GET, mert ez a szokásos SSE-végpont-forma, és a `fetch()` GET simán
  tud `Authorization` fejlécet küldeni (natív `EventSource`-t úgysem használunk, szóval annak
  GET-only/fejléc-nélküli korlátja itt nem releváns).
- **`ChatService`**: `chat()` → `streamChat(message, context, username)`, ami `SseEmitter`-t ad
  vissza, a teljes kört (retrieval → rerank → `streamGenerate` → feltételes `extractAction()`) az
  új executoron futtatva. Az `extractAction()` teljesen lecseréli a `parseAction()`/
  `removeActionJson()`/`FILL_FORM_PATTERN`-t — egy dedikált `format:"json"` hívás, csak akkor fut,
  ha `ctx.pageType()` a `FORM_FILLABLE_PAGE_TYPES`-ban van (`STAR_SYSTEM_CREATE/EDIT`,
  `MISSION_CREATE/EDIT`, változatlan a mai szabályhoz képest), egy záró SSE `action` eseményként
  küldve `emitter.complete()` előtt.
- **`frontend/src/api/client.ts`**: `chatApi.send()` törölve; új `streamChat(message, context,
  onToken, onAction, onError)` — egy kis kézzel írt `fetch()` + `ReadableStream` SSE-frame-parser
  (nincs új dependency).
- **`ChatWidget.tsx`**: `handleSend()` egy üres placeholder AI-üzenetet told be, a streamelt
  tokeneket funkcionális `setMessages` update-tel fűzi hozzá, az `onAction` callback hívja a
  `triggerFill()`-t, változatlanul.
- **`ChatContext.tsx`**: nem változik.
- **Tesztek**: `ChatServiceTest` (új — mockolt `AiServiceClient`/`HybridRetrievalService`/
  `RerankingService`/`StarSystemService`, ellenőrzi az SSE-esemény-sorrendet + hogy nem-form-
  kitölthető oldalon a `generateJson` sosem hívódik), bővített `AiServiceClientTest` (kész, több
  soros NDJSON `InputStream`), frontend `client.stream.test.ts` (mockolt `fetch`, fix SSE-frame-ek
  parse-olása), `ChatWidget.test.tsx` (mockolt `streamChat`, fokozatos renderelés + `triggerFill`
  hívás ellenőrzése), `ChatControllerSecurityTest` (401 JWT nélkül az új végponton).
- **Kézi ellenőrzés (kizárólag Norbi)**: valódi token-streaming élő Ollama ellen, SSE-stabilitás,
  hogy a 120s `SseEmitter`-timeout elég-e a saját modellje sebességéhez.

### PR #4 — Observability (log-pipeline) + admin Eval fül + polish

**Observability — változatlan, Norbi jóváhagyta**: `key=value`-stílusú `@Slf4j` log-sorok a
fázishatárokon (`content_index`, `chat_retrieval`, `chat_rerank`, `chat_turn_start`/
`chat_turn_end` token-számokkal az `OllamaStreamChunk` `evalCount`/`promptEvalCount` mezőiből) —
nincs új infrastruktúra, egyenesen a meglévő `/admin/logs`-ba folyik.

**Eval — újratervezve Norbi kérésére**: a korábbi verzióban ez egy önálló, kézzel indított Python
szkript lett volna (`ai-service/eval/run_eval.py`, saját `golden_set.json` fájllal). Norbi ehelyett
egy **admin UI fület** kér: a golden set szerkeszthető legyen az oldalon (nem egy fájlban kell
piszkálni), az eval futtatható legyen egy gombnyomással, és az eredmény is ott jelenjen meg —
ne kelljen terminálba menni egy jelentés-fájl elolvasásához. Ez egyben **egyszerűsítés** is: mivel
az eval-logika (embed + hibrid vektor/FTS keresés + RRF + rerank) most a Spring backendben él (PR
#1/#2), a Java-oldali eval-futtató **közvetlenül a valódi, éles service-eket hívja**
(`HybridRetrievalService`, `RerankingService`, `StarSystemService`) — nem kell egy második,
Pythonban külön lereplikált verziót karbantartani, ami idővel elcsúszhatna a valóditól. Emiatt a
korábban tervezett `psycopg[binary]`/`ai-service/eval/` Python-szkript **törlődik a tervből**.

**Backend — új adatmodell**: a golden set adatbázisban él, nem fájlban.
- **Új migráció** (a következő szabad `V` szám a PR #1 `V10`-e után) — két tábla:
  - `eval_golden_entries` (`id UUID`, `query TEXT`, `expected_source_type VARCHAR`,
    `expected_source_name_contains VARCHAR`, `expected_keywords TEXT[]`, `created_at`, `updated_at`)
  - `eval_runs` (`id UUID`, `started_at`, `finished_at`, `hit_rate_at_3 FLOAT`,
    `hit_rate_at_5 FLOAT`, `status VARCHAR`) + `eval_run_results` (`run_id FK`,
    `golden_entry_id FK`, `hit_at_3 BOOLEAN`, `hit_at_5 BOOLEAN`, `top_result_name VARCHAR`,
    `latency_ms INT`) — így nem csak az utolsó futás eredménye látszik, hanem **idővel követhető a
    trend** (javult/romlott-e a retrieval minőség az utolsó változtatás óta — ez egy valódi,
    interjún elmondható "eval regression tracking" minta).
- **Új permission-pár** `eval:read`/`eval:write` (`DataInitializer.java`-ban, a `feature_flag:*`/
  `sector:*` mintáját követve), csak `ROLE_ADMIN`-hoz rendelve.
- **Új** `service/eval/EvalService.java`:
  - CRUD a golden set fölött (`listGoldenEntries`, `createGoldenEntry`, `updateGoldenEntry`,
    `deleteGoldenEntry`).
  - `runEval()` — végigmegy az összes golden entry-n, mindegyikre lefuttatja a valódi retrieval-
    pipeline-t, számolja a hit-rate@3/@5-öt, **közben soronként logol** (`eval_progress
    query="..." hit@3=true latency_ms=210`) — így a futás élőben, kérdésenként követhető a meglévő
    `/admin/logs` nézetben, pont úgy, ahogy a chat-observability is oda folyik (ugyanaz a pipeline,
    kétszeresen hasznosítva). A futás szinkron (blokkolja a kérést, amíg végez — 15-20 golden
    entry-nél ez néhány másodperc, nem indokol async/polling bonyodalmat), a végén elmenti az
    `eval_runs`/`eval_run_results` sorokat és visszaadja az összefoglalót.
  - Opcionális LLM-judge lépés (checkbox a UI-n) — újrahasznosítja a PR #2 `AiServiceClient.generateJson()`-t, nincs hozzá új infrastruktúra.
- **Új** `web/eval/EvalController.java` — `GET/POST/PUT/DELETE /api/admin/eval/golden-set`,
  `POST /api/admin/eval/run` (`?llmJudge=true|false`), `GET /api/admin/eval/runs` (futtatás-
  történet), mind `@PreAuthorize("hasAuthority('eval:read')")`/`'eval:write'`.

**Frontend — új admin oldal, a meglévő mintát követve**: a `SectorList`/`FeatureFlagList` egy-
oldalas DataGrid+dialog mintáját másoljuk (ezek is minimális adatmodellt kezelnek, mint most a
golden set), nem a kétoldalas `StarSystemList`/`Edit` mintát.
- **Új** `frontend/src/pages/admin/eval/EvalPage.tsx` — egy `MUI DataGrid` a golden set sorokkal
  (query, elvárt forrás, kulcsszavak) + hozzáadás/szerkesztés/törlés dialógus; egy **"Futtatás"**
  gomb, ami `POST /api/admin/eval/run`-t hív, betöltés közben spinner, siker után egy
  összefoglaló-kártya (hit-rate@3, hit-rate@5) + egy táblázat kérdésenkénti bontásban (talált-e,
  mit talált, mennyi ideig tartott); egy kis "korábbi futások" lista/select a trend követéséhez.
- **Új** `evalApi` modul a `client.ts`-ben (golden set CRUD + `runEval`/`getRuns`).
- Új route `/admin/eval` (`router/index.tsx`), nav-link az `AdminLayout.tsx` admin sidebarjába.

**Tesztek**: `EvalServiceTest.java` (Mockito, mockolt `HybridRetrievalService`/
`RerankingService`/`StarSystemService` — hit-rate@k számítás helyessége ismert bemenetekre),
`EvalControllerSecurityTest.java` (a meglévő minta szerint), frontend `EvalPage.test.tsx`
(mockolt API-hívások, CRUD-interakció + futtatás-eredmény megjelenítés).
- A maradék negatív-eset teszt-hiányok pótlása (embed-null / ai-service-elérhetetlen / hibás-JSON)
  az 1-3. fázis új service-jeiben, plusz `ai-service` `test_health.py`/`test_embed.py`/
  `test_generate_stream.py`.

**Kézi ellenőrzés (Norbi)**: a golden set feltöltése valós kérdésekkel az admin UI-n keresztül,
a "Futtatás" gomb kipróbálása élő Ollamával, és hogy a `/admin/logs` tényleg mutatja-e élőben a
soronkénti eval-progresszust.

### PR #5 — MCP-szerver + tool-calling agent-hurok

**Miért ide kerül, és mi a döntés mögötte**: a PR #3-ig a chatbot csak *beszél* — kontextust kap,
válaszol, legfeljebb egy űrlapot javasol kitölteni. Ez a fázis valódi *cselekvőképességet* ad neki:
saját, hívható eszközöket (tools), amiket a modell maga dönt el, mikor és hogyan használjon egy
beszélgetésen belül. Ez a Model Context Protocol (MCP) — ugyanaz a minta, amit az `ai-os` projekt
`mcp_server.py`-ja már megépített (`propose_file_patch`, `trigger_sandbox_validation` stb.) — csak
itt egy másik LLM-kliens (Ollama) mögé kötve.

**Fontos, tudatos döntés: valódi MCP-szerver, nem csak Ollama-specifikus glue-kód.** Ollama saját
function-calling API-ja (`/api/chat`, `tools` mező) NEM ugyanaz, mint az MCP — egy külön,
JSON-RPC-alapú protokoll. Írhatnánk egy egyszerű Python dispatch-táblát is (név → függvény), ami
csak Ollamának szólna — de ehelyett egy **valódi `mcp` Python SDK-val épített MCP-szervert**
teszünk (ugyanazt a csomagot használva, mint az `ai-os`), amit egy vékony híd köt Ollama saját
tool-calling hurkához. Ennek ára van (egy plusz protokoll-réteg egy olyan helyzetben, ahol
egyelőre csak egy kliens — Ollama — beszélne vele), de a nyereség: (1) a tool-implementációk
később bármilyen más MCP-kompatibilis kliensből (Claude Desktop, Claude Code) is elérhetők
lennének, nem csak ebből a chatbotból; (2) konzisztens, névvel megnevezhető minta a két projekt
(`ai-os`, `legymernok`) között — erős, konkrét interjú-sztori.

**2026-08-25-i pontosítás — transport és elhelyezés.** Az `ai-os` `mcp_server.py`-ja **stdio-
transporttal** fut: a kliens (az `ai-os` saját `task_runner.py`-ja) egy adott taszk-futtatáshoz
**alfolyamatként indítja el**, egy konkrét sandbox-mappához (`ToolContext`) kötve — nem egy
folyamatosan futó, hálózaton elérhető szolgáltatás. Ez a minta itt NEM alkalmazható közvetlenül:
a legymernok-nál a kliens (Spring backend) és a szerver (Python) **más nyelv, más konténer** —
egy Java-folyamat nem tud egy másik Docker-konténerben lévő Python-alfolyamat stdin/stdout-jához
hozzáférni. Emiatt a legymernok MCP-szervere **HTTP/SSE-transporttal** fut (az `mcp` SDK ezt is
támogatja stdio mellett), **saját, önálló konténerben** (`mcp-server`, ld. lent) — folyamatosan,
nem taszkonként újraindítva. Az `ai-os`-t ez a döntés **nem érinti**, marad a jelenlegi,
stdio-alfolyamatos, taszkonkénti mintája — a két projekt MCP-szervere tudatosan **külön marad**
(más bizalmi szint: az `ai-os` toolai fájlt módosítanak/kódot futtatnak, a legymernok toolai
egy hitelesített végfelhasználó JWT-jével, a Spring `@PreAuthorize`-on át korlátozott REST
hívások), nem egy közös, megosztott MCP-hubba kerülnek.

**Auth-kérdés, ami itt élesebb, mint az `ai-os`-nál**: az `ai-os` egyfelhasználós CLI-eszköz, a
`legymernok` viszont többfelhasználós, RBAC-védett alkalmazás. **A tool-implementációk sose
hívjanak közvetlenül adatbázist/service-réteget** — mindegyik a meglévő, hitelesített Spring REST
API-t hívja, **a beszélgető felhasználó saját JWT-jével** továbbítva. Ez azt jelenti, hogy a
meglévő `@PreAuthorize`/owner-check logika (pl. `mission:create`, `StarSystem` tulajdon-ellenőrzés)
automatikusan, ingyen érvényesül a tool-hívásokon is — nem kell újraírni Pythonban semmilyen
jogosultság-logikát, és egy nem-admin kadét egy ügyes prompttal sem tud admin-műveletet kicsikarni.

**Mutáló vs. olvasó toolok — eltérő biztonsági szint**: az olvasó toolok (`search_platform_content`,
`get_cadet_progress`) side-effect-mentesek, ezeket a modell szabadon, automatikusan hívhatja. A
**mutáló** `create_mission_draft` viszont — ugyanúgy, mint a FILL_FORM minta — **javaslatot** ad,
nem hajtja végre automatikusan: a tool eredménye egy "ide egy javasolt vázlat, egy gombnyomással
hozd létre" UI-akció, nem egy csendben lezajlott létrehozás. Ez védelem prompt-injection ellen is
(pl. ha egy admin által írt, de manipulált misszió-leírás a retrieveelt kontextusba kerül, és
megpróbálná "meggyőzni" a modellt egy nem kért létrehozásra).

**Az induló 4 tool**:

| Tool | Típus | Mit hív | Megjegyzés |
|---|---|---|---|
| `search_platform_content` | olvasó, auto | a PR #2 hibrid retrieval-je (`GET /api/search/hybrid`, új végpont) | a modell explicit dönt a keresésről, nem minden üzenetnél fut automatikusan, mint most |
| `get_cadet_progress` | olvasó, auto | `GET /api/users/{id}/progress` — **ÚJ végpont, ezzel a PR-ral együtt megírva** (2026-08-25-i felfedezés: nem létezett meglévő admin végpont erre, ld. `pr5_mcp_toolcalling_architecture_2026.md` 6.1) | csak akkor sikeres, ha a hívó JWT-je admin — a backend dobja a 403-at, ha nem |
| `create_mission_draft` | **mutáló, javaslat** | `POST /api/missions/forge/initialize` | a tool NEM hívja meg automatikusan — SSE `action` eseményként (`type: PROPOSE_MISSION`) megy a frontendnek, ami egy megerősítő UI-t mutat |
| `navigate_to` | UI-side-effect, nem "igazi" MCP-adat-tool | — | ez technikailag nem egy adatot visszaadó tool, hanem egy UI-vezérlő jel; a FILL_FORM/PROPOSE_MISSION mintájára egy külön SSE `action` eseményként (`type: NAVIGATE`) modellezve, nem a klasszikus MCP tool-return-value séma szerint |

**Architektúra**:

```
ChatWidget → ChatService.streamChat() → Ollama tool-calling hurok (max N iteráció)
                                              │
                                    tool_call esemény esetén
                                              ▼
                                    AiServiceClient.callTool(name, args, userJwt)
                                              │
                                              ▼  HTTP (belső Docker-hálózat: legymernok-net)
                                    MCP kliens ⇄ MCP szerver — ÖNÁLLÓ KONTÉNER (mcp-server, mcp SDK,
                                    HTTP/SSE transport, folyamatosan fut, nem taszkonként indul)
                                              │
                                              ▼
                                    tool implementáció → Spring REST API hívás a userJwt-vel
```

- **Új top-level mappa és konténer: `mcp-server/`** (NEM az `ai-service/` alá kerül — külön build-
  kontextus, külön image, külön életciklus, mert ez egy állandóan futó daemon, nem egy Ollama-
  wrapper). Fájlok: `mcp-server/main.py` (`mcp` SDK-val épített szerver, HTTP/SSE transport, a 4
  tool JSON-schema definíciójával + implementációjával — mindegyik egy `httpx` hívás a Spring
  backend felé, a kapott JWT-t `Authorization` fejlécként továbbítva), `mcp-server/requirements.txt`
  (`mcp`, `httpx`, a HTTP/SSE transporthoz szükséges ASGI-szerver, pl. `uvicorn`),
  `mcp-server/Dockerfile` (a meglévő `ai-service/Dockerfile` mintáját követve).
- **`docker-compose.yml` bővítés** — új service, a meglévő `ai-service`/`ollama` blokkok mintáját
  követve:
  ```yaml
  mcp-server:
    build:
      context: ./mcp-server
      dockerfile: Dockerfile
    container_name: legymernok-mcp-server
    restart: always
    # 2026-08-26: NINCS ports: blokk — ld. pr5 7. szakasz (CLAUDE.md biztonsági lista).
    environment:
      BACKEND_URL: http://backend:8080
    depends_on:
      - backend
    networks:
      - legymernok-net
  ```
  Nincs `depends_on: ollama` — a tool-implementációk sosem hívják közvetlenül az Ollamát, csak a
  Spring backendet; az Ollama-hívás iránya fordított (Java → Ollama, a tool-calling hurokban).
- **`AiServiceClient` bővítés**: `chatWithTools(messages, tools, userJwt)` — Ollama natív
  `/api/chat` (nem `/api/generate`) hívása `tools` mezővel; ha a válasz `tool_calls`-t tartalmaz,
  a hurok HTTP-n hívja a megfelelő toolt a `mcp-server` konténeren (`http://mcp-server:8082`), az
  eredményt visszateszi az üzenet-listába `role:"tool"`-ként, és újra hívja Ollamát — max ~10
  iterációig (ugyanaz a védőháló, mint az `ai-os` `max_tool_iterations`-e).
- **`ChatService.streamChat()` bővítés**: a mostani egy-kör (retrieval → generate → extractAction)
  helyett egy tool-hurok, ami közben is streameli a látható szöveges részeket, a `PROPOSE_MISSION`/
  `NAVIGATE` akciókat pedig ugyanúgy záró SSE-eseményként küldi, mint a FILL_FORM-ot.

**Tesztek**: `mcp-server/` toolonként (`pytest`, mockolt `httpx` a Spring hívásokhoz — érvényes
JWT-vel sikeres hívás, érvénytelen/hiányzó jogosultsággal 403 helyesen propagálódik, plusz egy
alap health-check teszt, hogy a HTTP/SSE szerver ténylegesen elindul); a Java-oldali tool-hurok
mockolt `AiServiceClient.chatWithTools()`-szal (több körös tool_call szekvenciák, max
iteráció-korlát tesztelése, `create_mission_draft` sosem hív automatikusan mutáló végpontot).

**Kézi ellenőrzés (Norbi)**: `docker compose up mcp-server --build -d` a saját gépén (a többi
konténerrel — `backend`, `ollama`, `ai-service` — együtt, mind ugyanazon a `legymernok-net`
hálózaton), majd valódi többkörös tool-használat élő Ollamával (kell egy function-calling-képes
modell, pl. `qwen2.5`/`llama3.1` — nem minden kis GGUF-modell támogatja jól a natív tool-callinget,
ezt érdemes lesz kipróbálni a saját gépén elérhető modellekkel).

## Függőségek — mindegyik indokolt, semmi spekulatív

| Függőség | Hol | Indoklás |
|---|---|---|
| `pytest` (új `requirements-dev.txt`) | `ai-service/` | Ma nulla teszt létezik; kell a 2-3. fázis Python-változásainak felelős leszállításához élő Ollama nélkül. |
| `mcp[cli]>=2.0,<3`, `httpx`, `uvicorn` (új `mcp-server/requirements.txt`) | `mcp-server/` (önálló konténer, NEM az `ai-service/` alá) | Ugyanaz a Python SDK verzió-sáv, amit az `ai-os` projekt is használ, de itt Streamable HTTP-transporttal (`MCPServer.streamable_http_app()`), mert a kliens (Spring backend) és a szerver külön konténerben fut — valódi, szabványos, folyamatosan futó MCP-szerver a 4 tool-hoz, nem csak Ollama-specifikus glue-kód, és nem az `ai-service` életciklusához kötve (PR #5). |
| `io.modelcontextprotocol.sdk:mcp` (+ `mcp-bom` verzió-kezeléshez, backend `pom.xml`) | `service/ai/McpToolLoopService.java` | **ÚJ, 2026-08-25-i doksi-kutatással feltárva** — a hivatalos, Spring AI-jal együttműködésben fejlesztett Java MCP kliens SDK. Ezt kell használni a `mcp-server`-rel való kommunikációhoz a helyett a kézzel írt REST-hívás helyett, amit a terv korábban tévesen feltételezett (az MCP Streamable HTTP valójában JSON-RPC 2.0 egyetlen `/mcp` végponton, nem REST tool-onként) — ld. `pr5_mcp_toolcalling_architecture_2026.md` 3.2 szakasz. |
| `org.mozilla:rhino:1.7.15.1` (backend `pom.xml`) | `service/rag/strategy/JsMethodSplitter.java` | Pure-Java JS/TS-parser a CODING-misszió kódfájlok metódus-szintű chunkolásához (PR #1, ld. [`pr1_rag_chunking_architecture_2026.md`](pr1_rag_chunking_architecture_2026.md) 12.6) — 2026-08-25-i spike-kal ténylegesen kipróbálva a valódi `mission-js-template` fájlokon, nem elméleti választás. Nincs natív/subprocess igény. |
| `org.testcontainers:postgresql` + `junit-jupiter` (test scope, backend `pom.xml`) | `src/test/java/.../db/` | **ELDÖNTVE (2026-08-26)**: két új migráció, öt tábla, generált `tsvector` oszlop, FK-cascade és a projekt legösszetettebb nyers SQL-jei kerülnek be — eddig ez a réteg teljesen automatikus ellenőrzés nélkül maradt volna ("Norbi kézzel megnézi"). A `pgvector/pgvector:pg16` image-dzsel (ugyanaz, mint élesben) a Flyway-migrációk lefutása ÉS a nyers lekérdezések valódi Postgres ellen tesztelhetők. Csak `test` scope, a prod-artefaktumot nem érinti. |
| **Semmi más** a backendhez | — | `SseEmitter` (spring-boot-starter-web), `RestTemplate` (már bean), `ObjectMapper` (már bean) mindent lefed — nincs `spring-webflux`, nincs reaktív lib. |
| **Semmi** az ai-service streaminghez | — | `StreamingResponse` (fastapi, már megvan) + `httpx.AsyncClient.stream()` (httpx, már megvan) lefedi az NDJSON-továbbítást — `sse-starlette` nem kell. |
| **Semmi** a frontendhez | — | A kézzel írt `fetch()`+`ReadableStream` SSE-parse (~20 sor) ezen a léptéken nem indokol külön libet. |
| **Semmi** az evalhoz | — | Az admin UI-alapú eval (PR #4, újratervezve) a meglévő Java retrieval-service-eket hívja közvetlenül — nincs külön Python-implementáció, tehát a korábban tervezett `psycopg[binary]` sem kell. |

## Vektoros keresés — index-stratégia és az `ORDER BY` javítása (2026-08-26)

### A hiba, ami ma is él

A `StarSystemService.searchByEmbedding()` (és a PR #2 terve, ami 1:1 lemásolta) így rendez:

```sql
1 - (content_embedding <=> ?::vector) AS similarity
...
ORDER BY similarity DESC
```

A pgvector a vektor-indexet **kizárólag** `ORDER BY <oszlop> <=> <vektor>` (nyers távolság,
növekvő) alakra tudja használni. Egy származtatott kifejezésre (`1 - (...)`) csökkenően
rendezve **nincs index**, minden lekérdezés teljes tábla-scan + rendezés.

**Ez a ma élő rendszert is érinti**: a `V2__add_pgvector.sql` `idx_star_system_embedding`
ivfflat indexe emiatt soha nem használódott — ráadásul üres táblán épült, ami önmagában is
használhatatlanná tenné. A jelenlegi szemantikus keresés tehát ma is egzakt scannel megy,
csak ez 1-2 csillagrendszernél észrevehetetlen.

### Javítás (kötelező, minden vektoros lekérdezésre)

```sql
SELECT id, ..., 1 - (content_embedding <=> ?::vector) AS score
FROM content_chunks
ORDER BY content_embedding <=> ?::vector      -- nyers távolság, ASC
LIMIT ?
```

A `1 - (...)` kifejezés maradhat a SELECT-listában (a pontszám kiírásához kell, a tervezőt
nem befolyásolja) — csak a rendezésből kell kivenni. A vektor-paraméter így kétszer megy be.
A `WHERE content_embedding IS NOT NULL` feltétel elhagyható: a pgvector amúgy sem indexel
NULL-t, a predikátum csak felesleges.

**Ez a javítás akkor is kell, ha most nem teszünk indexet** — enélkül egy később hozzáadott
index sem lépne életbe, és a hiba csendben megmaradna.

### Index-stratégia — ELDÖNTVE (2026-08-26): egyelőre NINCS vektor-index

Három lehetőséget mérlegeltünk:

| | Mit ad | Mit kér cserébe |
|---|---|---|
| **A) Nincs index** | Egzakt találat, nulla hangolás, nulla meglepetés | Lineáris scan |
| **B) HNSW** | Üresen is épül (nincs tanítóadat-igény), jó recall | Több memória, lassabb insert, `ef_search` hangolás |
| **C) ivfflat** | Kis memória | Csak adat UTÁN építhető, `lists`/`probes` hangolás, növekedéskor újraépítés |

**Norbert döntése: A.** A várható lépték néhány ezer chunk (nagyságrendileg 200 misszió ×
~10 chunk), ahol az egzakt keresés milliszekundumokban mérhető — az ANN-nek gyakorlatilag
nincs mit megnyernie. Cserébe viszont hozna egy nehezen debugolható hibaosztályt: **hibrid
keresésnél az ANN recall-hibái összeadódnak az RRF-ben**, és utólag nem lehet megmondani,
hogy egy chunk azért nem jött fel, mert rossz az embedding, vagy mert az index nem találta
meg. Az egzakt keresés ezt a bizonytalanságot teljesen kiveszi a rendszerből.

**Ha valaha mégis index kell** (a mérés indokolja), akkor **HNSW, nem ivfflat** — az ivfflat
üres táblás építése pontosan az a csapda, amibe ez a terv eredetileg beleszaladt volna, és a
`lists`/`probes` hangolás ezen a léptéken tiszta ráfizetés.

### Külön, ettől független teendő

A `StarSystemService.searchByEmbedding()` `ORDER BY`-ja és a `V2` üresen épült ivfflat indexe
a **jelenlegi éles kódot** érinti, nem ezt a fejlesztést. Érdemes külön, pár soros PR-ban
javítani, függetlenül attól, hogy ez az öt fázis mikor indul.

## Embedding-hívás — task-prefixek és modell-verziózás (2026-08-26)

### A hiba

Az `AiEmbeddingService.embed(String text)` egyetlen metódus, amit a kód **dokumentum-
beágyazásra és kérdés-beágyazásra egyaránt** használ, nyers szöveggel. A jelenlegi
`EMBED_MODEL` (`nomic-embed-text`, v1.5) viszont **task-prefixekkel van tanítva**: a
dokumentumokat `search_document: ` , a lekérdezéseket `search_query: ` prefixszel várja. A
prefixek nélkül a kérdés- és a dokumentum-vektorok nem ugyanabba az altérbe esnek, és a
koszinusz-hasonlóság mérhetően romlik — ez egy csendes minőségromlás, ami semmilyen hibát
nem dob, csak rosszabb találatokat ad.

Ez a hiba a **jelenlegi, élő** csillagrendszer-keresést is érinti, nem csak a tervezett
chunk-retrievalt.

### A javítás

`AiEmbeddingService` két explicit metódust kap a mai egy helyett:

```java
public float[] embedDocument(String text)   // documentPrefix + text
public float[] embedQuery(String text)      // queryPrefix + text
```

A régi, kétértelmű `embed(String)` **megszűnik** — nem marad meg deprecated alakban sem,
mert pont az a hibaforrás, hogy hívás helyén nem derül ki, melyik oldalról van szó. Minden
hívási helyet át kell nézni és a megfelelőre cserélni:

| Hívási hely | Melyik |
|---|---|
| `StarSystemService.generateAndSaveEmbedding()` | `embedDocument` |
| `ContentChunkingService` minden reindex-ága (PR #1) | `embedDocument` |
| `ChatService` szemantikus keresés | `embedQuery` |
| `HybridRetrievalService.retrieveMissionChunks()` (PR #2) | `embedQuery` |
| `EvalService.runRetrievalFor()` (PR #4) | `embedQuery` |

**A prefix konfigurálható, nem beégetett** — mert modell-specifikus. Egy nem-nomic modellre
váltva a prefix nem javít, hanem ront:

```properties
ai.embed.document-prefix=${AI_EMBED_DOCUMENT_PREFIX:search_document: }
ai.embed.query-prefix=${AI_EMBED_QUERY_PREFIX:search_query: }
```

Üres értékre állítva a viselkedés a mostani (prefix nélküli) — tehát egy modellváltás nem
igényel kódmódosítást, csak env-változót.

### Kötelező következmény: teljes újraindexelés

A prefix megváltoztatja a vektorteret, tehát **a már tárolt embeddingek érvénytelenné
válnak** — nem hibásak szintaktikailag, csak egy másik tér pontjai, és a hasonlóságuk az új
kérdés-vektorokhoz értelmetlen. A javítás bevezetésekor **kötelező** lefuttatni mindkét
reindexet:

```
POST /api/admin/reindex-star-systems
POST /api/admin/reindex-content          (PR #1 hozza be)
```

Ez nem opcionális karbantartás — nélküle a keresés rosszabb lesz, mint a javítás előtt volt,
mert a két oldal biztosan eltérő prefixeltségű lenne.

### Kapcsolódó, még nyitott hiányosság: nincs modell-verzió az adatban

A `content_embedding vector(768)` dimenzió be van égetve a sémába, és **sehol nincs
eltárolva, melyik modellel/prefixszel készült egy adott vektor**. Emiatt egy modellváltás
(vagy ez a prefix-javítás) csendben inkonzisztens állapotot hagy, amíg valaki kézzel le nem
futtatja a reindexet — semmi nem jelzi, hogy ez megtörtént-e.

**ELDÖNTVE (2026-08-26): bekerül ebbe a körbe.** Egy `embedding_model VARCHAR(64) NOT NULL`
oszlop a `content_chunks`-on (a `V10` migrációban) és a `star_systems`-en (egy külön, egysoros
`V12` migrációban, mert az a tábla már létezik). Indexeléskor az aktuális `EMBED_MODEL`
értéke kerül bele.

Mit old meg:
- A retrieval **figyelmeztethet** (`log.warn`), ha a tárolt érték eltér az aktuális
  `EMBED_MODEL`-től — vagyis ha valaki elfelejtette a reindexet egy modellváltás után.
- A reindex **célzottan** csak az elavult sorokat érintheti, nem kell mindent újraszámolni.
- A prefix-javítás bevezetésekor egyértelműen látszik a DB-ből, mely sorok készültek már az
  új eljárással.

**A `star_systems` `V12` migrációja**: az oszlopot `DEFAULT 'unknown'`-nal kell felvenni,
mert a meglévő sorokról nem tudjuk, mivel készültek — és pont ez a helyes állapot: az
`'unknown'` érték jelzi, hogy azok a vektorok reindexre várnak.

## Lokális futtatás — mért teljesítmény és a timeout-lánc (2026-08-26)

**Ez egy tudott, elfogadott limitáció, nem hiba** — a tervezésnél számolni kell vele.

A fenti "Fontos keret-feltétel" szerint az élő LLM-tesztelés Norbi saját gépén történik. Az
első teljes lokális felállás során kimértük, mennyibe kerül ez ténylegesen — a számok
lényegesen rosszabbak, mint amit a tervek implicit feltételeztek.

### Hardver-adottság

| | |
|---|---|
| Inferencia | **CPU-only** (`ollama` log: `inference compute: id=cpu library=cpu`) |
| RAM | 15,6 GiB |
| GPU | NVIDIA GTX 1050, **2 GB VRAM** — egy 11–13B modellhez használhatatlan |

A `docker-compose.yml` `ollama` service-e nem is kér GPU-t (nincs
`deploy.resources.reservations.devices`), és a 2 GB VRAM mellett ennek nem is lenne értelme.
**A modellméret a sebesség egyetlen érdemi gombja.**

### Mért értékek

| Modell | Méret betöltve | Capabilities | Sebesség / válaszidő |
|---|---|---|---|
| `gemma4-coding:q8` (11.9B, Q8_0) | 14 GB | `completion, tools, **thinking**` | **1,44 token/mp** |
| `llama2-13b:q4` (13.0B, Q4_0) | 7,4 GB | `completion` | **~1 perc 50 mp** egy válaszra, betöltéssel együtt |

A `gemma4-coding` **thinking modell**: a tényleges válasz előtt több száz tokent generál
gondolkodásként. 1,44 token/mp mellett ez önmagában több perc, és a nyers gondolatmenet a
`/api/generate` `response` mezőjébe is beszivároghat — a widgetben a kadét ezt látná.

A meglévő lokális modellek közül **egyedül a `llama2-13b:q4` nem gondolkodik**, ezért lett ez
beállítva `CHAT_MODEL`-nek. Cserébe 2023-as modell, gyenge magyar nyelvtudással.

**Következtetés:** ezen a gépen egy 11B+ modell CPU-n nem alkalmas interaktív chatre. Ha
használható válaszidő kell, egy 3B körüli modell (`qwen2.5:3b`, `llama3.2:3b`, ~2 GB) a reális
választás — nagyságrendileg 7-8-szoros sebesség. A `thinking` capability az `ollama show
<modell>` kimenetéből mindig ellenőrizhető; a `deepseek-r1` / `qwen3` / `qwq` / `gpt-oss`
család mind gondolkodó, ezek ugyanebbe a problémába futnak.

### A timeout-lánc — minden rétegnek konfigurálhatónak kell lennie

A válaszidő ismeretében ez nem elméleti kérdés: **a lánc leggyengébb láncszeme dönt**, hiába
nagy az összes többi.

| Réteg | Jelenlegi timeout | Konfigurálható? |
|---|---|---|
| böngésző → frontend nginx `/api/` | **60 mp** (nginx default, `frontend/nginx.conf`-ban nincs felülírva) | **NEM** ← ez vág el elsőként |
| frontend nginx → backend | ugyanaz a 60 mp | **NEM** |
| backend → ai-service (`RestTemplate`) | nincs (`AppConfig.restTemplate()` = `new RestTemplate()`, végtelen) | nem releváns |
| ai-service → ollama (`httpx`) | 600 mp (`ai-service/main.py`) | **NEM** (hardkódolt) |
| PR #3 `SseEmitter` | 300 mp | **IGEN** (`chat.stream.timeout-ms` / `CHAT_STREAM_TIMEOUT_MS`) |

**Ezt a PR #3 terve nem fedi le.** Ott az `SseEmitter` timeoutja lett 300 000 ms-re emelve és
env-változóval felülírhatóvá téve — ez helyes, de **önmagában nem elég**: az nginx a `/api/`
blokkban 60 másodperc után elvágja a kapcsolatot, jóval a Java-oldali 300 mp előtt. A
felhasználó ilyenkor a widgetben csak annyit lát, hogy *"hiba történt a válasz lekérésekor"*,
miközben az ollama még dolgozik, és a kérés utóbb sikeresen be is fejeződik. Ez a hibakép
2026-08-26-án ténylegesen előfordult, és először modell-hibának tűnt.

**Elvárás a streaming (PR #3) implementációjához:**

1. A `frontend/nginx.conf` `/api/` blokkjában legyen explicit `proxy_read_timeout` (és
   `proxy_send_timeout`), a `CHAT_STREAM_TIMEOUT_MS`-hez igazítva vagy afölött.
   Streamelésnél a `proxy_buffering off;` is kell, különben az nginx a teljes választ
   kipuffereli, és a token-streaming értelmét veszti.
2. Az `ai-service/main.py` `httpx` timeoutja (`600`) is env-változóból jöjjön, ne hardkódolva.
3. A rétegek relációja legyen tudatos: **nginx ≥ SseEmitter ≥ ai-service→ollama**, különben a
   külső réteg vágja el a belsőt, és a hibaüzenet félrevezet.

A streaming amúgy pont ezt a problémát enyhíti a legjobban: az első token pár másodpercen
belül megérkezik, tehát a kapcsolat aktív marad, és a felhasználó sem ül üres képernyő előtt
két percig. De az nginx `proxy_read_timeout` **az utolsó adatcsomag óta** eltelt időt méri,
szóval streameléssel is kell a beállítás — csak jóval kisebb értékkel is elég lenne.

### Hatás a fejlesztésre

Ezzel számolni kell az ütemezésnél: minden élő, végponttól végpontig tartó kézi ellenőrzés
körönként **percekbe** kerül, nem másodpercekbe. Ez erősíti a tervek meglévő döntését, hogy
minden fázisnak mockolt unit tesztekkel, élő LLM nélkül is ellenőrizhetőnek kell lennie — az
élő Ollamás verifikáció maradjon ritka, kötegelt végső ellenőrzés.

## Nyitott kérdések / Norbi feladatai a végén

1. A golden set tételeit valós, élő seed-adatokkal (star system/mission nevek) kell kitölteni az
   admin `/admin/eval` oldalon — ezt csak Norbi tudja értelmesen megcsinálni, de legalább nem egy
   fájlt kell szerkesztenie hozzá, hanem egy UI-t.
2. A `docker-compose.yml` `ollama` service `/mnt/g/Projects/AI Models:/gguf:ro` volume-mountja egy
   Windows/WSL-stílusú útvonal — a saját gépén a tényleges GGUF-modell-mappára kell igazítani,
   mielőtt elindítja az `ollama`/`ai-service` konténereket.
3. A `SseEmitter` 120 másodperces timeoutja egy becslés — érdemes a saját modell tényleges
   sebességéhez hangolni élő teszt után.
4. Ez a terv NEM tartalmazza a multi-provider absztrakciót és egy teljes teszt-lefedettséget —
   ezeket Norbi kifejezetten később-priorizálta, egy jövőbeli PR tárgya lehet, ha még mélyebbre
   akar menni. **A guardrails/prompt-injection kérdés viszont a PR #5 (tool-calling) miatt már
   most releváns lett** — a jelenlegi terv erre a válasz a "mutáló tool = javaslat, nem
   auto-végrehajtás" minta + a meglévő `@PreAuthorize`/owner-check újrahasznosítása minden
   tool-hívásnál, de egy alaposabb, dedikált guardrails-átvilágítás (pl. mennyire lehet a
   retrieveelt kontextuson keresztül manipulálni a modellt) továbbra sincs ebben a körben — ha ez
   fontos, érdemes külön téma legyen, mielőtt a PR #5 élesedik.
5. A `qwen2.5`/`llama3.1` méretű, function-calling-képes modellek nagyobb erőforrás-igényűek, mint
   a jelenlegi `gemma3:8b-q4_K_M` default — Norbinak érdemes lesz ellenőriznie, hogy a saját gépén
   elérhető modellek közül melyik támogatja jól a natív Ollama tool-callinget, mielőtt a PR #5-öt
   élesben tesztelné.
6. **Az `ai-os` MCP-szervere ebben a körben szándékosan nem változik** — marad a jelenlegi,
   taszkonként alfolyamatként induló, stdio-transportos mintája. Nincs közös, megosztott
   MCP-hub a két projekt között (2026-08-25-i döntés, ld. PR #5 fenti pontosítása) — ha ez
   valaha mégis felmerülne, az egy külön, önállóan átgondolandó téma lenne (az `ai-os` toolai
   fájlt módosítanak/kódot futtatnak, más bizalmi szint, mint a legymernok JWT-hitelesített
   toolai).

## Ellenőrzés / verifikáció összefoglalva

- **Itt, élő Ollama nélkül is ellenőrizhető, fázisonként**: `mvn test` (új Mockito-alapú service
  tesztek **és — 2026-08-26 óta — Testcontainers-alapú DB-tesztek**),
  `cd ai-service && pip install -r requirements.txt -r requirements-dev.txt && pytest`,
  `npm test` (Vitest az új frontend stream-parserhez + widget teszthez).

  **A Testcontainers ezt a korábbi mondatot váltja le**: *"a V10 migráció szintaktikai
  átolvasása (nincs beágyazott teszt-DB, ami ezt automatikusan bizonyítaná)"*. Mostantól van.
  Amit a DB-tesztek lefednek, és amit korábban SEMMI nem bizonyított automatikusan:

  | Amit tesztel | Melyik PR |
  |---|---|
  | Minden Flyway-migráció lefut egy tiszta `pgvector/pgvector:pg16`-on, `V1`-től a legutolsóig | mind |
  | A `vector` és `pgcrypto` extension tényleg elérhető (nem `postgres:16` image) | PR #1 |
  | `content_chunks` FK `ON DELETE CASCADE` — misszió törlése után 0 chunk marad | PR #1 |
  | `content_chunks_unique_chunk` UNIQUE tényleg fog (üres `file_path`-tal is) | PR #1 |
  | A `to_tsvector('hungarian', ...)` generált oszlop tényleg szótövez (`függvényt` → `függvény`) | PR #1 |
  | A vektoros lekérdezés `ORDER BY emb <=> ?` alakja lefut és helyes sorrendet ad | PR #2 |
  | A full-text lekérdezés `plainto_tsquery('hungarian', ?)`-vel talál ragozott alakra | PR #2 |
  | A `JOIN missions` tényleg kitölti a `source_name`-et | PR #2 |
  | `eval_runs`/`eval_run_results` CHECK-constraintjei és a CASCADE | PR #4 |
  | Az `expected_keywords TEXT[]` JPA-mapping `ddl-auto=validate` mellett elindul | PR #4 |

  Ez a lista pont azokat a hibaosztályokat fedi, amiket a projekt korábban élesben szedett
  össze (`order_in_system`→`order_index`, `template_repository_url` NOT NULL, `mission_type`
  CHECK — ld. gyökér `CLAUDE.md`).
- **Csak Norbi, miután lehúzta a branch-et a saját gépére, élő Ollamával**: V10 alkalmazása egy
  valódi Postgres ellen + a `reindex-content` végpont ellenőrzése; hogy a retrieval-minőség/
  reranking ténylegesen jobb választ ad-e; hogy a valódi token-streaming helyesen jelenik-e meg a
  widgetben; a golden set feltöltése + a "Futtatás" gomb kipróbálása az `/admin/eval` oldalon, és
  az eredmény-táblázat screenshotolása/megosztása (ez lehet a LinkedIn-poszt konkrét
  "bizonyítéka").
- **Kifejezetten kiemelve (2026-08-25, Norbi kérésére) — `extractAction()` prompt-minőség
  (PR #3)**: a `plans/pr3_streaming_architecture_2026.md` 8.3 szakaszában megtervezett,
  `format:"json"` alapú akció-kinyerő prompt egy első tervezet, NEM tesztelt élő modellel —
  élő Ollamával mindenképp ki kell próbálni, hogy tényleg jó minőségű, parse-olható JSON-t
  ad-e vissza (pl. egy `STAR_SYSTEM_CREATE` oldalon egy olyan üzenetre, ami form-kitöltést
  kér), mielőtt a PR #3 készre jelentődik.
