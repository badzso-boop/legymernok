# AI chatbot — RAG, streaming, observability fejlesztési terv (2026-08-24)

> **Státusz: TERV, nincs implementáció.** Ez a dokumentum a jóváhagyás előtt álló architekturális
> tervet rögzíti, hogy át tudjuk beszélni, mielőtt bármelyik fázis (lásd lent) ténylegesen
> elkezdődne. Négy önálló, egymástól függetlenül review-olható PR-ra van bontva — egyiket sem
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
- **Eval harness**: egy önálló Python szkript az `ai-service/eval/`-ben (közvetlen Postgres-elérés
  `psycopg[binary]`-vel, egy indokolt, csak-eval dependency, kimarad az éles Docker image-ből), ami
  egy kis kézzel írt "golden" kérdés-válasz halmazt futtat végig a retrieval pipeline-on, hit-rate@k-t
  jelent, és egy `report.md`-t ír — önmagában futtatható, csak Postgres+Ollama+ai-service kell hozzá,
  a teljes Spring/React stack nem.

## Fázisok — mindegyik önálló, mergelhető PR

### PR #1 — RAG chunking backend

- **Új migráció** `backend/src/main/resources/db/migration/V10__create_content_chunks.sql`:
  ```sql
  CREATE TABLE IF NOT EXISTS content_chunks (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      source_type VARCHAR(32) NOT NULL,
      source_id UUID NOT NULL,
      chunk_index INT NOT NULL,
      chunk_text TEXT NOT NULL,
      content_embedding vector(768),
      search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple', chunk_text)) STORED,
      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
      CONSTRAINT content_chunks_source_type_check CHECK (source_type IN ('MISSION', 'MISSION_FILL_IN_BLANK')),
      CONSTRAINT content_chunks_unique_chunk UNIQUE (source_type, source_id, chunk_index)
  );
  CREATE INDEX IF NOT EXISTS idx_content_chunks_embedding
      ON content_chunks USING ivfflat (content_embedding vector_cosine_ops) WITH (lists = 10);
  CREATE INDEX IF NOT EXISTS idx_content_chunks_search_vector
      ON content_chunks USING gin (search_vector);
  CREATE INDEX IF NOT EXISTS idx_content_chunks_source
      ON content_chunks (source_type, source_id);
  ```
  (A `V2__add_pgvector.sql` index-stílusát követi; a generált `tsvector` oszlop nem igényel
  service-oldali karbantartást.)
- **Új** `backend/.../dto/rag/ContentChunkDto.java` —
  `record ContentChunkDto(UUID id, String sourceType, UUID sourceId, int chunkIndex, String chunkText, double score)`.
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
    (`ts_rank` + `plainto_tsquery('simple', ?)`), mindkettő `topK*3`-at hoz le, `rrfMerge()`-dzsel
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

### PR #4 — Observability + eval harness + polish

- `key=value`-stílusú `@Slf4j` log-sorok a maradék fázishatárokon (`content_index`,
  `chat_turn_start`/`chat_turn_end` token-számokkal az `OllamaStreamChunk` `evalCount`/
  `promptEvalCount` mezőiből) — nincs új infrastruktúra, egyenesen a `/admin/logs`-ba folyik.
- **Új** `ai-service/eval/run_eval.py` + `ai-service/eval/golden_set.json` (~3 illusztratív tétel
  vázlatosan; a többit Norbi tölti ki a saját, valós seed-adatai alapján) +
  `ai-service/eval/requirements-eval.txt` (`psycopg[binary]`, csak-eval, kimarad az éles image-ből).
  A szkript: minden golden-tételnél embedeli a kérdést, közvetlenül a Postgres ellen lereplikálja a
  vektor+FTS+RRF lekérdezést, hit-rate@k-t számol, opcionálisan LLM-judge-ol `/generate`
  `format:"json"`-nal, `report.json`+`report.md`-t ír. Dokumentált, egyszeri parancsként, nem CI-ba
  kötve.
- A maradék negatív-eset teszt-hiányok pótlása (embed-null / ai-service-elérhetetlen / hibás-JSON)
  az 1-3. fázis új service-jeiben, plusz `ai-service` `test_health.py`/`test_embed.py`/
  `test_generate_stream.py`.

## Függőségek — mindegyik indokolt, semmi spekulatív

| Függőség | Hol | Indoklás |
|---|---|---|
| `pytest` (új `requirements-dev.txt`) | `ai-service/` | Ma nulla teszt létezik; kell a 2-3. fázis Python-változásainak felelős leszállításához élő Ollama nélkül. |
| `psycopg[binary]` (új `requirements-eval.txt`) | `ai-service/eval/` | Az eval szkriptnek közvetlen Postgres-elérés kell, hogy a Spring backend nélkül lereplikálja a hibrid retrievalt; elkülönítve az éles image-től. |
| **Semmi** a backendhez | — | `SseEmitter` (spring-boot-starter-web), `RestTemplate` (már bean), `ObjectMapper` (már bean) mindent lefed — nincs `spring-webflux`, nincs reaktív lib. |
| **Semmi** az ai-service streaminghez | — | `StreamingResponse` (fastapi, már megvan) + `httpx.AsyncClient.stream()` (httpx, már megvan) lefedi az NDJSON-továbbítást — `sse-starlette` nem kell. |
| **Semmi** a frontendhez | — | A kézzel írt `fetch()`+`ReadableStream` SSE-parse (~20 sor) ezen a léptéken nem indokol külön libet. |

## Nyitott kérdések / Norbi feladatai a végén

1. A `golden_set.json` eval-tételeit valós, élő seed-adatokkal (star system/mission nevek) kell
   kitölteni — ezt csak Norbi tudja értelmesen megcsinálni.
2. A `docker-compose.yml` `ollama` service `/mnt/g/Projects/AI Models:/gguf:ro` volume-mountja egy
   Windows/WSL-stílusú útvonal — a saját gépén a tényleges GGUF-modell-mappára kell igazítani,
   mielőtt elindítja az `ollama`/`ai-service` konténereket.
3. A `SseEmitter` 120 másodperces timeoutja egy becslés — érdemes a saját modell tényleges
   sebességéhez hangolni élő teszt után.
4. Ez a terv NEM tartalmazza a multi-provider absztrakciót, a guardrails/prompt-injection védelmet,
   és egy teljes teszt-lefedettséget — ezeket Norbi kifejezetten később-priorizálta, egy jövőbeli
   ötödik fázis/PR tárgya lehet, ha még mélyebbre akar menni.

## Ellenőrzés / verifikáció összefoglalva

- **Itt, élő Ollama nélkül is ellenőrizhető, fázisonként**: `mvn test` (új Mockito-alapú service
  tesztek), `cd ai-service && pip install -r requirements.txt -r requirements-dev.txt && pytest`,
  `npm test` (Vitest az új frontend stream-parserhez + widget teszthez), és a V10 migráció
  szintaktikai átolvasása (nincs beágyazott teszt-DB, ami ezt automatikusan bizonyítaná).
- **Csak Norbi, miután lehúzta a branch-et a saját gépére, élő Ollamával**: V10 alkalmazása egy
  valódi Postgres ellen + a `reindex-content` végpont ellenőrzése; hogy a retrieval-minőség/
  reranking ténylegesen jobb választ ad-e; hogy a valódi token-streaming helyesen jelenik-e meg a
  widgetben; az `ai-service/eval/run_eval.py` futtatása a kitöltött golden set-tel, és a
  `report.md` screenshotolása/megosztása (ez lehet a LinkedIn-poszt konkrét "bizonyítéka").
