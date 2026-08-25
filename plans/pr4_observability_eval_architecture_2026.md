# PR #4 — Observability + admin Eval fül: implementációs architektúra-terv

> Ez a dokumentum a `plans/ai_chatbot_upgrade_2026.md` PR #4 szakaszát bontja le
> osztály/metódus-szintre, a `pr1_rag_chunking_architecture_2026.md`-vel azonos
> mélységben és formátumban. Csak terv, nincs implementáció.

## 1. Új komponensek — csomag-elhelyezés

A projekt admin-CRUD mintáját (`model` → `repository` → `service` → `web` → DTO-k,
JPA-entitásokkal, nem nyers JDBC-vel — ellentétben a PR #1 `ContentChunkingService`-ével,
mert itt NINCS pgvector-oszlop, ami a `StarSystemService`-mintát indokolná) követve, a
legközelebbi élő analógia a `sector`/`featureflag` modul (teljes CRUD + permission-pár):

```
backend/src/main/java/com/legymernok/backend/
├── model/eval/
│   ├── EvalGoldenEntry.java         (ÚJ, @Entity)
│   ├── EvalRun.java                 (ÚJ, @Entity)
│   └── EvalRunResult.java           (ÚJ, @Entity)
├── repository/eval/
│   ├── EvalGoldenEntryRepository.java  (ÚJ, JpaRepository)
│   ├── EvalRunRepository.java          (ÚJ, JpaRepository)
│   └── EvalRunResultRepository.java    (ÚJ, JpaRepository)
├── dto/eval/
│   ├── CreateGoldenEntryRequest.java
│   ├── EvalGoldenEntryResponse.java
│   ├── EvalRunResponse.java
│   └── EvalRunResultResponse.java
├── service/eval/
│   └── EvalService.java             (ÚJ, @Service)
└── web/eval/
    └── EvalController.java          (ÚJ, @RestController)

frontend/src/
├── pages/admin/eval/
│   └── EvalPage.tsx                 (ÚJ)
├── types/eval.ts                    (ÚJ — a `types/featureFlag.ts` mintáját követve)
├── api/client.ts                    (MÓDOSUL — új `evalApi` blokk)
├── router/index.tsx                 (MÓDOSUL — új `/admin/eval` route)
└── layouts/AdminLayout.tsx          (MÓDOSUL — új sidebar-menüpont)
```

## 2. Új Flyway migráció — `V11__create_eval_tables.sql`

A `backend/src/main/resources/db/migration/` legutóbbi fájlja jelenleg `V9__create_sectors_table.sql`
(`V10`-et a PR #1 terve már lefoglalta a `content_chunks` táblának) — ez a PR tehát **`V11`**.

```sql
-- =============================================================================
-- V11: Eval harness a RAG-retrieval minőségének méréséhez
--
-- Admin által szerkeszthető golden set (kérdés → elvárt találat), és a
-- lefuttatott eval-körök eredménye, hogy idővel követhető legyen a
-- retrieval-minőség trendje (javult/romlott-e egy változtatás után).
-- =============================================================================

CREATE TABLE eval_golden_entries (
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    query                           TEXT NOT NULL,
    expected_source_type            VARCHAR(32) NOT NULL,
    expected_source_name_contains   VARCHAR(255),
    expected_keywords               TEXT[] NOT NULL DEFAULT '{}',
    created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT eval_golden_entries_source_type_check
        CHECK (expected_source_type IN ('STAR_SYSTEM', 'MISSION', 'MISSION_FILL_IN_BLANK', 'MISSION_CODE_FILE'))
);

CREATE TABLE eval_runs (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    started_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at    TIMESTAMPTZ,
    hit_rate_at_3  DOUBLE PRECISION,
    hit_rate_at_5  DOUBLE PRECISION,
    status         VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    llm_judge_used BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT eval_runs_status_check CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE eval_run_results (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id           UUID NOT NULL REFERENCES eval_runs(id) ON DELETE CASCADE,
    golden_entry_id  UUID NOT NULL REFERENCES eval_golden_entries(id) ON DELETE CASCADE,
    hit_at_3         BOOLEAN NOT NULL,
    hit_at_5         BOOLEAN NOT NULL,
    top_result_name  VARCHAR(255),
    latency_ms       INT NOT NULL,
    CONSTRAINT eval_run_results_unique UNIQUE (run_id, golden_entry_id)
);

CREATE INDEX idx_eval_run_results_run_id ON eval_run_results (run_id);
```

**Döntések, amiket a fő terv nem specifikált, itt pótolva:**
- `expected_source_type` egy 4-elemű CHECK-constraint (`STAR_SYSTEM` a régi, csillagrendszer-
  szintű flat embeddingre, a másik három a PR #1 `content_chunks.source_type` értékeire) — ez
  fedi le mindkét retrieval-útvonalat (a régi csillagrendszer-keresés ÉS az új misszió-chunk
  hibrid keresés, ahogy a `ChatService.chat()` PR #2-ben mindkettőt egy közös kontextusba fésüli).
- `eval_runs.status` — a fő terv nem említi, de kell egy mező, ami jelzi, ha egy futás menet
  közben elhasal (pl. a `runEval()` egy váratlan kivételbe fut) — enélkül egy félbeszakadt
  futás egy örökre `finished_at IS NULL` sort hagyna maga után, amit a UI nem tudna
  egyértelműen "sikertelen"-ként megjeleníteni.
- `eval_runs.llm_judge_used` — hogy a futás-történet listájában látszódjon, melyik futás
  használt LLM-judge-ot (mert ahogy a 6. szakaszban részletezem, ez befolyásolhatja az
  eredmény összehasonlíthatóságát két futás között).
- `eval_run_results_unique` — egy golden entry egy run-on belül csak egyszer szerepelhet
  (véd egy esetleges hibás, kétszer-lefuttatott ciklus ellen).

## 3. Permission-seed (`DataInitializer.java`)

A `feature_flag:*`/`sector:*` pontos mintáját követve (`DataInitializer.java` jelenlegi
55-72. sor környéke):

```java
// Eval jogok (RAG-retrieval minőség-mérés)
Permission evalRead  = createPermissionIfNotFound("eval:read",  "Eval golden set és futás-történet megtekintése");
Permission evalWrite = createPermissionIfNotFound("eval:write", "Eval golden set szerkesztése és futtatása");
```

Csak `ROLE_ADMIN`-hoz rendelve (a fő terv szerint) — a `ROLE_CADET` permission-halmazába
NEM kerül bele, ellentétben pl. a `sector:read`-del, amit a kadétok is megkapnak olvasásra.

## 4. JPA-entitások

```java
// model/eval/EvalGoldenEntry.java
@Entity
@Table(name = "eval_golden_entries")
public class EvalGoldenEntry {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(columnDefinition = "TEXT", nullable = false)
    private String query;
    @Column(name = "expected_source_type", nullable = false)
    private String expectedSourceType;
    @Column(name = "expected_source_name_contains")
    private String expectedSourceNameContains;
    @Column(name = "expected_keywords")
    private List<String> expectedKeywords;  // Postgres TEXT[] — Hibernate 6 natívan támogatja
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    // @PrePersist/@PreUpdate: ugyanaz a minta, mint a FeatureFlag entitásban
}

// model/eval/EvalRun.java
@Entity
@Table(name = "eval_runs")
public class EvalRun {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;
    @Column(name = "finished_at")
    private Instant finishedAt;
    @Column(name = "hit_rate_at_3")
    private Double hitRateAt3;
    @Column(name = "hit_rate_at_5")
    private Double hitRateAt5;
    @Column(nullable = false)
    private String status;  // RUNNING | COMPLETED | FAILED
    @Column(name = "llm_judge_used", nullable = false)
    private boolean llmJudgeUsed;
}

// model/eval/EvalRunResult.java
@Entity
@Table(name = "eval_run_results")
public class EvalRunResult {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "run_id", nullable = false)
    private EvalRun run;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "golden_entry_id", nullable = false)
    private EvalGoldenEntry goldenEntry;
    @Column(name = "hit_at_3", nullable = false)
    private boolean hitAt3;
    @Column(name = "hit_at_5", nullable = false)
    private boolean hitAt5;
    @Column(name = "top_result_name")
    private String topResultName;
    @Column(name = "latency_ms", nullable = false)
    private int latencyMs;
}
```

(Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor` mindegyiken, a projekt
szokása szerint — a fenti csak a mezőlistát mutatja a tömörség kedvéért.)

## 5. `EvalService` — teljes metódustábla

| Metódus | Szignatúra | Tranzakció | Mit csinál |
|---|---|---|---|
| `listGoldenEntries` | `List<EvalGoldenEntryResponse> listGoldenEntries()` | `@Transactional(readOnly = true)` | `evalGoldenEntryRepository.findAll()` → DTO-lista. |
| `createGoldenEntry` | `EvalGoldenEntryResponse createGoldenEntry(CreateGoldenEntryRequest request)` | `@Transactional` | Egyszerű mentés, a `SectorService.createSector()` mintáját követve. |
| `updateGoldenEntry` | `EvalGoldenEntryResponse updateGoldenEntry(UUID id, CreateGoldenEntryRequest request)` | `@Transactional` | `findById` + mezők felülírása + mentés. |
| `deleteGoldenEntry` | `void deleteGoldenEntry(UUID id)` | `@Transactional` | `evalGoldenEntryRepository.deleteById(id)`. |
| `runEval` | `EvalRunResponse runEval(boolean llmJudge)` | `@Transactional` | Ld. 5.1 — a fő logika. |
| `listRuns` | `List<EvalRunResponse> listRuns()` | `@Transactional(readOnly = true)` | `evalRunRepository.findAllByOrderByStartedAtDesc()` → DTO-lista (a "korábbi futások" UI-listához). |
| `getRunDetail` | `EvalRunResponse getRunDetail(UUID runId)` | `@Transactional(readOnly = true)` | Egy run + a hozzá tartozó `EvalRunResult`-ok kérdésenkénti bontásban. |

### 5.1 `runEval()` — a pontos algoritmus

```
bemenet: llmJudge (boolean)
kimenet: EvalRunResponse

1. run = EvalRun.builder().startedAt(now()).status("RUNNING").llmJudgeUsed(llmJudge).build()
   run = evalRunRepository.save(run)   // azonnal elmentve, hogy egy összeomlás esetén is
                                          lássuk a DB-ben, hogy elindult egy futás

2. entries = evalGoldenEntryRepository.findAll()
   hits3 = 0; hits5 = 0

3. try:
     for entry in entries:
         startTime = System.currentTimeMillis()

         # A tényleges retrieval-hívás — a PR #2 architektúrájára hivatkozva:
         # STAR_SYSTEM esetén StarSystemService.searchByEmbedding(), egyébként
         # HybridRetrievalService.retrieveMissionChunks() (PR #2-ben specifikálva)
         results = runRetrievalFor(entry)   // top 5, rangsorolt lista

         latencyMs = System.currentTimeMillis() - startTime

         hit3 = matchesAny(results.subList(0, min(3, results.size())), entry)
         hit5 = matchesAny(results, entry)
         if hit3: hits3++
         if hit5: hits5++

         topResultName = results.isEmpty() ? null : results.get(0).getSourceName()

         log.info("eval_progress query=\"{}\" hit@3={} hit@5={} latency_ms={}",
                   entry.getQuery(), hit3, hit5, latencyMs)
         # ^ ez a soronkénti, /admin/logs-ba folyó progress-log — lásd 8. szakasz

         evalRunResultRepository.save(EvalRunResult(run, entry, hit3, hit5, topResultName, latencyMs))

         if llmJudge:
             # Ld. 5.2 — KÜLÖN, opcionális lépés, NEM helyettesíti a fenti determinisztikus
             # hit-rate-számítást, hanem kiegészíti
             runLlmJudge(entry, results)

     run.setFinishedAt(now())
     run.setHitRateAt3(entries.isEmpty() ? 0.0 : (double) hits3 / entries.size())
     run.setHitRateAt5(entries.isEmpty() ? 0.0 : (double) hits5 / entries.size())
     run.setStatus("COMPLETED")

   catch (Exception e):
     run.setFinishedAt(now())
     run.setStatus("FAILED")
     log.error("Eval run {} failed: {}", run.getId(), e.getMessage())
     # a run FAILED állapotban, RÉSZLEGES eredményekkel (amik addig elmentődtek) marad a DB-ben
     # — nem dobjuk el a már megszerzett részleges adatot

4. evalRunRepository.save(run)
5. return mapToResponse(run)
```

**`matchesAny(results, entry)` — a determinisztikus egyezés-ellenőrzés pontos definíciója**
(ezt a fő terv nem specifikálta karakter-szinten, itt pótolva, mert a hit-rate-számítás
enélkül nem implementálható egyértelműen):

```
matchesAny(results, entry):
    for result in results:
        ha result.sourceType != entry.expectedSourceType: continue
        nameMatches = entry.expectedSourceNameContains == null
                      OR result.sourceName.toLowerCase().contains(entry.expectedSourceNameContains.toLowerCase())
        keywordMatches = entry.expectedKeywords.isEmpty()
                      OR entry.expectedKeywords.any(kw -> result.chunkText.toLowerCase().contains(kw.toLowerCase()))
        ha nameMatches AND keywordMatches: return true
    return false
```

(Case-insensitive, részszó-egyezés — ugyanaz a "ne legyen túl szigorú" filozófia, mint a
PR #2 hibrid retrieval-jénél; egy pontos szó-egyezés túl törékeny lenne egy embedding-alapú
rendszer eval-jéhez, ahol a cél a "megtalálta-e a releváns forrást", nem a szó szerinti
egyezés.)

### 5.2 Az LLM-judge lépés — **NYITOTT KÉRDÉS, nem specifikálható a fő tervből**

A fő terv csak ennyit mond: *"Opcionális LLM-judge lépés (checkbox a UI-n) — újrahasznosítja
a PR #2 `AiServiceClient.generateJson()`-t, nincs hozzá új infrastruktúra."* — ez NEM elég
információ ahhoz, hogy a `runLlmJudge()` metódust pontosan megtervezzem, és a hiányzó
részletek **DB-séma-kérdést** is felvetnek (ld. lent). Nem találgatok tovább, ld. a 9.
szakasz nyitott kérdéseit.

## 6. `EvalController` — végpontok

| Metódus | Path | Permission | Visszatérés |
|---|---|---|---|
| `GET` | `/api/admin/eval/golden-set` | `eval:read` | `List<EvalGoldenEntryResponse>` |
| `POST` | `/api/admin/eval/golden-set` | `eval:write` | `EvalGoldenEntryResponse` (201) |
| `PUT` | `/api/admin/eval/golden-set/{id}` | `eval:write` | `EvalGoldenEntryResponse` |
| `DELETE` | `/api/admin/eval/golden-set/{id}` | `eval:write` | 204 |
| `POST` | `/api/admin/eval/run?llmJudge=true\|false` | `eval:write` | `EvalRunResponse` |
| `GET` | `/api/admin/eval/runs` | `eval:read` | `List<EvalRunResponse>` |
| `GET` | `/api/admin/eval/runs/{id}` | `eval:read` | `EvalRunResponse` (részletes, `results` beágyazva) |

A `SectorController` mintáját követve — `@Valid @RequestBody` a mutáló végpontokon,
`ResponseEntity` minden metódusból, `@RequestParam(defaultValue = "false") boolean llmJudge`
a run végponton.

**Fontos, hogy `eval:write` kell a futtatáshoz, NEM csak `eval:read`** — a fő terv ezt nem
mondja ki explicit, de logikusan következik abból, hogy a futtatás egy AI-hívásokkal járó,
erőforrás-igényes (és `llmJudge=true` esetén pénzbe/API-kvótába kerülő, ha valaha nem lokális
Ollama-t használnának) művelet, nem egy sima olvasás — ugyanaz a minta, mint a
`sector:write` a `reorder`-nél (ami is egy "cselekvés", nem CRUD a szó szoros értelmében).

## 7. Frontend — `EvalPage.tsx`

A `FeatureFlagList.tsx` (226 sor) szerkezetét követve, de a `SectorList`-ből átvéve a
teljes CRUD-dialógus mintát (mert itt — ellentétben a feature flag egyszerű toggle-jével —
létrehozás/törlés is kell):

```
EvalPage.tsx
├── Golden set szekció
│   ├── MUI DataGrid — oszlopok: query, expectedSourceType, expectedSourceNameContains,
│   │   expectedKeywords (chip-lista), actions (szerkesztés/törlés ikon)
│   ├── "+ Új golden entry" gomb → Dialog (query/type/name-contains/keywords mezők,
│   │   a keywords egy MUI Autocomplete "freeSolo" chip-inputként, vesszővel/Enterrel
│   │   felvehető kulcsszavak — ugyanaz a minta, ha van már ilyen valahol a projektben,
│   │   pl. a Mission tag-kezelésénél; ha nincs, sima vesszővel elválasztott TextField
│   │   is elfogadható MVP-nek)
│   └── Szerkesztés/törlés dialógus — a `FeatureFlagList` szerkesztő-dialógusának mintája
├── "Futtatás" gomb-sáv
│   ├── Checkbox: "LLM-judge is fusson" (a `llmJudge` paraméterhez)
│   ├── "Futtatás" gomb → `evalApi.runEval(llmJudge)`, `loading` állapot spinnerrel
│   │   (a futás szinkron, néhány másodperces — a gomb `disabled` + spinner amíg fut)
│   └── Hiba esetén Snackbar/Alert (a `FeatureFlagList` `toggleError` mintája)
├── Eredmény-összefoglaló kártya (a legutóbbi futás után jelenik meg)
│   ├── hit-rate@3, hit-rate@5 (nagy szám + %, pl. Card + Typography variant="h3")
│   └── Kérdésenkénti bontás táblázat (query, hit@3 ✓/✗, hit@5 ✓/✗, topResultName, latencyMs)
└── "Korábbi futások" szekció
    ├── Select/lista a `evalApi.getRuns()` eredményéből (dátum + hit-rate@3/@5 + státusz badge)
    └── Kiválasztásra betölti az adott run részleteit (`evalApi.getRunDetail(id)`) ugyanabba
        az eredmény-táblázatba, mint a legutóbbi futás
```

### 7.1 `evalApi` — `client.ts` bővítés

A `sectorApi`/`featureFlagApi` pontos mintáját követve:

```typescript
export const evalApi = {
  getGoldenSet: async (): Promise<EvalGoldenEntryResponse[]> => {
    const response = await apiClient.get<EvalGoldenEntryResponse[]>("/admin/eval/golden-set");
    return response.data;
  },
  createGoldenEntry: async (data: CreateGoldenEntryRequest): Promise<EvalGoldenEntryResponse> => {
    const response = await apiClient.post<EvalGoldenEntryResponse>("/admin/eval/golden-set", data);
    return response.data;
  },
  updateGoldenEntry: async (id: string, data: CreateGoldenEntryRequest): Promise<EvalGoldenEntryResponse> => {
    const response = await apiClient.put<EvalGoldenEntryResponse>(`/admin/eval/golden-set/${id}`, data);
    return response.data;
  },
  deleteGoldenEntry: async (id: string): Promise<void> => {
    await apiClient.delete(`/admin/eval/golden-set/${id}`);
  },
  runEval: async (llmJudge: boolean): Promise<EvalRunResponse> => {
    const response = await apiClient.post<EvalRunResponse>(`/admin/eval/run?llmJudge=${llmJudge}`);
    return response.data;
  },
  getRuns: async (): Promise<EvalRunResponse[]> => {
    const response = await apiClient.get<EvalRunResponse[]>("/admin/eval/runs");
    return response.data;
  },
  getRunDetail: async (id: string): Promise<EvalRunResponse> => {
    const response = await apiClient.get<EvalRunResponse>(`/admin/eval/runs/${id}`);
    return response.data;
  },
};
```

### 7.2 Hook-pontok — routing és navigáció

| Fájl | Hova kerül a hívás |
|---|---|
| `frontend/src/router/index.tsx:376` környéke (a `feature-flags` route mellé) | `{ path: "eval", element: <EvalPage /> }` az `/admin` gyerek-route-jai közé |
| `frontend/src/layouts/AdminLayout.tsx:43` környéke (a `featureFlags` menüpont mellé) | `{ text: "eval", icon: <AssessmentIcon />, path: "/admin/eval" }` a menüItems tömbbe |

## 8. Observability — a log-formátum pontos szintaxisa

A fő terv szerint minden fázishatáron `@Slf4j` `key=value`-stílusú log-sor, ami a meglévő
`WebSocketLogAppender`-en (`config/WebSocketLogAppender.java`) keresztül **automatikusan**
eljut a `/admin/logs`-ba — ehhez **semmilyen kódmódosítás nem kell** az appenderben/configban
(a ROOT loggerre van kötve, minden `@Slf4j` sor átmegy rajta), CSAK a log-hívásokat kell a
megfelelő helyekre beszúrni a PR #1/#2/#3 service-jeibe:

| Log-esemény | Hol (PR) | Pontos formátum |
|---|---|---|
| `eval_progress` | Ez a PR, `EvalService.runEval()` | `eval_progress query="{}" hit@3={} hit@5={} latency_ms={}` |
| `content_index` | PR #1, `ContentChunkingService` | `content_index mission_id={} chunks={} status={success\|failed}` (a fő terv csak a nevet adja meg, a mezőlistát itt egészítem ki a PR #1 doksi tartalma alapján — ha a `pr1_rag_chunking_architecture_2026.md` idővel máshogy nevezi, azt a doksit kell mérvadónak tekinteni) |
| `chat_retrieval` | PR #2 | *(a `pr2_...md` architektúra-doksira hivatkozik, ha az elkészült — ott kell a pontos mezőlistát megadni, itt nem duplikálom)* |
| `chat_rerank` | PR #2 | *(ua.)* |
| `chat_turn_start`/`chat_turn_end` | PR #3 | *(a `pr3_...md`-re hivatkozik — a fő terv szerint az `OllamaStreamChunk` `evalCount`/`promptEvalCount` mezőiből számolt token-számokkal)* |

**Fontos korlát, amit a `LoggingConfig.java`-ból derítettünk ki**: a `PatternLayoutEncoder`
mintája (`"%d{...} %5p --- [%15.15t] %-40.40logger{39} : %m%n"`) **kizárólag a nyers
üzenetet (`%m`) formázza**, MDC-mezőt (`%X{...}`) NEM tartalmaz — tehát a strukturált
adatokat **kötelezően a log-üzenet SZÖVEGÉBE** kell tenni (`key=value` formában, ahogy fent),
NEM `MDC.put()`-tal, mert az néma maradna a `/admin/logs` nézeten.

## 9. Class diagram

```mermaid
classDiagram
    class EvalService {
        -EvalGoldenEntryRepository goldenEntryRepository
        -EvalRunRepository runRepository
        -EvalRunResultRepository runResultRepository
        -HybridRetrievalService hybridRetrievalService
        -StarSystemService starSystemService
        -AiServiceClient aiServiceClient
        +listGoldenEntries() List~EvalGoldenEntryResponse~
        +createGoldenEntry(CreateGoldenEntryRequest) EvalGoldenEntryResponse
        +updateGoldenEntry(UUID, CreateGoldenEntryRequest) EvalGoldenEntryResponse
        +deleteGoldenEntry(UUID) void
        +runEval(boolean llmJudge) EvalRunResponse
        +listRuns() List~EvalRunResponse~
        +getRunDetail(UUID) EvalRunResponse
        -matchesAny(List~RetrievalResult~, EvalGoldenEntry) boolean
        -runRetrievalFor(EvalGoldenEntry) List~RetrievalResult~
    }

    class EvalController {
        -EvalService evalService
        +getGoldenSet() ResponseEntity
        +createGoldenEntry(CreateGoldenEntryRequest) ResponseEntity
        +updateGoldenEntry(UUID, CreateGoldenEntryRequest) ResponseEntity
        +deleteGoldenEntry(UUID) ResponseEntity
        +runEval(boolean) ResponseEntity
        +getRuns() ResponseEntity
        +getRunDetail(UUID) ResponseEntity
    }

    class EvalGoldenEntry {
        <<Entity>>
        +UUID id
        +String query
        +String expectedSourceType
        +String expectedSourceNameContains
        +List~String~ expectedKeywords
    }

    class EvalRun {
        <<Entity>>
        +UUID id
        +Instant startedAt
        +Instant finishedAt
        +Double hitRateAt3
        +Double hitRateAt5
        +String status
        +boolean llmJudgeUsed
    }

    class EvalRunResult {
        <<Entity>>
        +UUID id
        +boolean hitAt3
        +boolean hitAt5
        +String topResultName
        +int latencyMs
    }

    EvalController --> EvalService
    EvalService --> EvalGoldenEntry
    EvalService --> EvalRun
    EvalService --> EvalRunResult
    EvalRunResult --> EvalRun : run_id FK
    EvalRunResult --> EvalGoldenEntry : golden_entry_id FK
    EvalService ..> "HybridRetrievalService (PR #2)" : runEval() hívja
    EvalService ..> "StarSystemService (meglévő)" : runEval() hívja
```

## 10. Sequence diagram — Admin lefuttatja az Eval-t

```mermaid
sequenceDiagram
    actor Admin
    participant EP as EvalPage (frontend)
    participant EC as EvalController
    participant ES as EvalService
    participant GER as EvalGoldenEntryRepository
    participant RS as retrieval (Hybrid/StarSystem)
    participant DB as Postgres (eval_run_results)
    participant WS as WebSocketLogAppender

    Admin->>EP: "Futtatás" gomb (llmJudge checkbox állapota)
    EP->>EC: POST /api/admin/eval/run?llmJudge=false
    EC->>ES: runEval(false)
    ES->>ES: run = new EvalRun(status=RUNNING); save
    ES->>GER: findAll()
    GER-->>ES: List~EvalGoldenEntry~
    loop minden golden entry-re
        ES->>RS: runRetrievalFor(entry)
        RS-->>ES: List~RetrievalResult~ (top 5)
        ES->>ES: matchesAny() → hit@3, hit@5
        ES->>WS: log.info("eval_progress ...") — élőben megy a /admin/logs-ba
        ES->>DB: save(EvalRunResult)
    end
    ES->>ES: run.hitRateAt3/At5 számítás, status=COMPLETED
    ES->>DB: save(run)
    ES-->>EC: EvalRunResponse
    EC-->>EP: 200 OK
    EP->>EP: eredmény-összefoglaló kártya + táblázat megjelenítése
```

## 11. Tesztterv

| Teszteset | Osztály | Mit ellenőriz |
|---|---|---|
| `createGoldenEntry_savesCorrectly` | `EvalServiceTest` | Alap CRUD, a `SectorServiceTest`-hez hasonló mintával |
| `runEval_allHit_hitRateIsOne` | `EvalServiceTest` | Mockolt retrieval mindig egyezik → `hitRateAt3 == 1.0` |
| `runEval_noHits_hitRateIsZero` | `EvalServiceTest` | Mockolt retrieval sosem egyezik → `hitRateAt3 == 0.0`, de a run státusza `COMPLETED` marad (a 0% is egy érvényes eredmény, nem hiba) |
| `runEval_emptyGoldenSet_returnsZeroWithoutError` | `EvalServiceTest` | 0 golden entry → `hitRateAt3/At5 == 0.0`, nem `NaN`/`ArithmeticException` (a nullával-osztás elkerülése expliciten tesztelve) |
| `runEval_retrievalThrows_marksRunFailed` | `EvalServiceTest` | A retrieval-hívás kivételt dob → a run `status=FAILED`-del mentődik, a már addig megszerzett `EvalRunResult` sorok megmaradnak |
| `matchesAny_typeMismatch_returnsFalse` | `EvalServiceTest` | Egyező név/kulcsszó, de eltérő `sourceType` → nem számít találatnak |
| `matchesAny_caseInsensitive` | `EvalServiceTest` | Kis/nagybetű-eltérés a névben/kulcsszóban nem befolyásolja az egyezést |
| `EvalControllerSecurityTest` | — | `eval:read`/`eval:write` helyesen elválasztva (olvasás vs. mutáló+futtató végpontok), a meglévő `SectorControllerSecurityTest`/`FeatureFlagControllerSecurityTest` mintája szerint |
| `EvalPage.test.tsx` | frontend | Golden set CRUD-interakció (mockolt `evalApi`), "Futtatás" gomb → loading state → eredmény-kártya megjelenik, korábbi futás kiválasztása betölti a régi eredményt |

**Kézi ellenőrzés (Norbi, itt nem elvégezhető)**: golden set feltöltése valós kérdésekkel,
"Futtatás" élő Ollamával, és hogy a `/admin/logs` ténylegesen mutatja-e élőben a soronkénti
`eval_progress` sorokat, ahogy a futás halad.

## 12. Nyitott kérdések Norbertnek

1. **Az LLM-judge lépés pontos viselkedése tisztázatlan** (ld. 5.2 szakasz) — a fő terv csak
   annyit mond, hogy "újrahasznosítja a `generateJson()`-t", de nem specifikálja: (a) MIT
   ítél meg az LLM — a retrieveelt chunkok relevanciáját a kérdéshez képest, vagy egy teljes
   generált válasz minőségét? (b) milyen skálán (0-10 pontszám, mint a rerankingnél, vagy
   igen/nem)? (c) **hova mentődik az eredménye** — a jelenlegi `eval_run_results` séma
   (`hit_at_3`/`hit_at_5`/`top_result_name`/`latency_ms`) NEM tartalmaz erre mezőt, tehát
   vagy egy új oszlopot kell hozzáadni (pl. `llm_judge_score FLOAT`), vagy külön táblát
   kell nyitni. Ezt nem tudom kitalálni a fő tervből — kérlek pontosítsd, mielőtt a `V11`
   migráció végleges lesz.
2. **A golden-set méretének felső korlátja** — a fő terv szerint "15-20 golden entry-nél ez
   néhány másodperc, nem indokol async/polling bonyodalmat", és a `runEval()` terve ennek
   megfelelően **szinkron, blokkoló** HTTP-hívás. Ha a golden set idővel jelentősen nő (pl.
   100+ tétel), ez percekig blokkolhatja a kérést, és Spring/böngésző-oldali timeoutba
   futhat. Szeretnél-e egy explicit felső korlátot a golden set méretére (pl. a UI ne
   engedjen 50 fölé menni), vagy ez most nem probléma, és majd ha azzá válik, külön
   foglalkozunk vele (async+polling-ra átalakítás)?
3. **A korábbi futások (`eval_runs`) törölhetők-e, és ha igen, ki törölheti** — a fő terv
   nem említ törlési útvonalat a futás-történethez. Idővel ez a tábla korlátlanul nőhet.
   Kell-e egy `DELETE /api/admin/eval/runs/{id}` végpont, vagy egyelőre nem szükséges
   (a futás-történet amúgy is kis méretű, nem project-kritikus adat)?
4. **`STAR_SYSTEM` mint `expected_source_type`** — én vezettem be ezt az értéket a CHECK-
   constraint-be (a fő terv nem sorolja fel explicit az `expected_source_type` lehetséges
   értékeit), mert enélkül a golden set nem tudna a RÉGI, csillagrendszer-szintű flat
   embedding retrieval-útvonalra vonatkozó teszteket tartalmazni, csak a PR #1-es
   misszió-chunk retrieval-re. Egyetértesz ezzel a bővítéssel?
