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
│   └── EvalRunResultRepository.java    (ÚJ, JpaRepository — + egy `@Modifying @Query`
│       metódus: `void updateLlmJudgeScore(UUID runId, UUID goldenEntryId, double score)`,
│       mert a `runLlmJudge()` (5.2 szakasz) egy MÁR elmentett `EvalRunResult` sort frissít
│       utólag, nem egy új sort szúr be — a determinisztikus hit-rate-mentés (5.1, 3. lépés)
│       és a judge-pontszám mentése két külön időpontban történik)
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
    hit_rate_at_3  DOUBLE PRECISION,          -- rerank ELŐTT (a fúzió kimenete)
    hit_rate_at_5  DOUBLE PRECISION,          -- rerank ELŐTT
    hit_rate_reranked_at_3 DOUBLE PRECISION,  -- rerank UTÁN — 2026-08-26, ld. PR #2 6.6
    status         VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    llm_judge_used BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT eval_runs_status_check CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE eval_run_results (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id           UUID NOT NULL REFERENCES eval_runs(id) ON DELETE CASCADE,
    golden_entry_id  UUID NOT NULL REFERENCES eval_golden_entries(id) ON DELETE CASCADE,
    hit_at_3         BOOLEAN NOT NULL,        -- rerank ELŐTT
    hit_at_5         BOOLEAN NOT NULL,        -- rerank ELŐTT
    hit_at_3_reranked BOOLEAN NOT NULL,       -- rerank UTÁN — 2026-08-26
    top_result_name  VARCHAR(255),
    latency_ms       INT NOT NULL,
    llm_judge_score  DOUBLE PRECISION,
    CONSTRAINT eval_run_results_unique UNIQUE (run_id, golden_entry_id),
    CONSTRAINT eval_run_results_llm_judge_score_range
        CHECK (llm_judge_score IS NULL OR llm_judge_score BETWEEN 0 AND 10)
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
- `eval_run_results.llm_judge_score` — **ELDÖNTVE Norberttel (2026-08-25)**: az LLM-judge
  KIZÁRÓLAG a retrieveelt (top eredmény) chunkok relevanciáját ítéli meg a kérdéshez képest
  (NEM a teljes generált választ), 0-10 skálán, ugyanazzal a promptmintával, mint a PR #2
  reranking-je. `NULL`, ha az adott futásnál nem volt bekapcsolva a judge
  (`eval_runs.llm_judge_used = false`) vagy ha `llmJudge=false` paraméterrel futott.

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
    @Column(name = "llm_judge_score")
    private Double llmJudgeScore;  // 0-10, NULL ha nem volt bekapcsolva a judge
}
```

(Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor` mindegyiken, a projekt
szokása szerint — a fenti csak a mezőlistát mutatja a tömörség kedvéért.)

## 4.5 `AppConfig` — `evalExecutor` bean (ÚJ, az aszinkron döntés miatt)

Ugyanaz a minta, mint a PR #3 `chatStreamExecutor()` bean-je:

```java
@Bean
public ExecutorService evalExecutor() {
    return Executors.newSingleThreadExecutor();
}
```

**Miért `newSingleThreadExecutor()`, nem `newCachedThreadPool()` (mint a chat-streamnél)**:
egy Eval-futtatás egy admin-akció, nem sok egyidejű felhasználó indítja párhuzamosan (a
PR #3 chat-streamnél viszont sok kadét is chatelhet egyszerre, ott indokolt volt a
cached pool). Egyetlen szál azt is garantálja, hogy **sosem fut két Eval-futás
párhuzamosan** — ha valaki a "Futtatás" gombra kattint, amíg egy korábbi még fut, a
második feladat egyszerűen bekerül a végrehajtási sorba, és csak az első után indul. Ez
egy egyszerű, ingyenes védelem a felesleges erőforrás-versengés ellen, külön kód nélkül.

## 5. `EvalService` — teljes metódustábla

| Metódus | Szignatúra | Tranzakció | Mit csinál |
|---|---|---|---|
| `listGoldenEntries` | `List<EvalGoldenEntryResponse> listGoldenEntries()` | `@Transactional(readOnly = true)` | `evalGoldenEntryRepository.findAll()` → DTO-lista. |
| `createGoldenEntry` | `EvalGoldenEntryResponse createGoldenEntry(CreateGoldenEntryRequest request)` | `@Transactional` | Egyszerű mentés, a `SectorService.createSector()` mintáját követve. |
| `updateGoldenEntry` | `EvalGoldenEntryResponse updateGoldenEntry(UUID id, CreateGoldenEntryRequest request)` | `@Transactional` | `findById` + mezők felülírása + mentés. |
| `deleteGoldenEntry` | `void deleteGoldenEntry(UUID id)` | `@Transactional` | `evalGoldenEntryRepository.deleteById(id)`. |
| `startEvalRun` | `EvalRunResponse startEvalRun(boolean llmJudge)` | `@Transactional` | **ELDÖNTVE (2026-08-25): aszinkron indítás** — létrehozza+elmenti az `EvalRun` sort `RUNNING` státusszal, elindítja a tényleges futást a háttér-executoron, és AZONNAL visszatér (nem várja meg a végét). Ld. 5.1. |
| `runEvalAsync` | `private void runEvalAsync(UUID runId, boolean llmJudge)` | nincs (a háttérszálon fut, saját `@Transactional` blokkokkal entry-nként) | A tényleges hit-rate-számító ciklus — ezt futtatja az `evalExecutor` a `startEvalRun()`-ból elindítva. Ld. 5.1. |
| `listRuns` | `List<EvalRunResponse> listRuns()` | `@Transactional(readOnly = true)` | `evalRunRepository.findAllByOrderByStartedAtDesc()` → DTO-lista (a "korábbi futások" UI-listához, RUNNING/COMPLETED/FAILED állapottal együtt). |
| `getRunDetail` | `EvalRunResponse getRunDetail(UUID runId)` | `@Transactional(readOnly = true)` | Egy run + a hozzá tartozó `EvalRunResult`-ok kérdésenkénti bontásban — **ez a metódus szolgálja ki a frontend polling-ját is**, ld. 7. szakasz. |
| `deleteRun` | `void deleteRun(UUID runId)` | `@Transactional` | **ELDÖNTVE (2026-08-25): teljes CRUD a futás-történethez is.** `evalRunRepository.deleteById(runId)` — az `eval_run_results` sorok a `V11` migráció `ON DELETE CASCADE` FK-ja miatt automatikusan törlődnek vele együtt, nem kell külön törölni őket. Ha egy `RUNNING` státuszú run-t próbálnak törölni (még fut a háttérben), `ResourceConflictException`-t dob — ld. lent. |

**Miért aszinkron már most, nem csak "ha problémává válik"**: Norbert kérésére ez a
professzionálisabb alapból megtervezett megoldás — így a golden-set mérete sosem korlátozza
mesterségesen a rendszert, nincs kockázat egy hosszú futásnál timeoutra, ÉS a UI a futás
ALATT is tud élő visszajelzést mutatni (polling + a meglévő `/admin/logs` élő
`eval_progress` sorai együtt), ami jobb felhasználói élmény, mint egy néma, blokkoló várakozás.

### 5.1 `startEvalRun()` + `runEvalAsync()` — a pontos algoritmus

**`startEvalRun(llmJudge)` — a szinkron, azonnal visszatérő rész:**

```
bemenet: llmJudge (boolean)
kimenet: EvalRunResponse (status="RUNNING")

1. run = EvalRun.builder().startedAt(now()).status("RUNNING").llmJudgeUsed(llmJudge).build()
   run = evalRunRepository.save(run)   // azonnal elmentve, hogy egy összeomlás esetén is
                                          lássuk a DB-ben, hogy elindult egy futás

2. evalExecutor.execute(() -> runEvalAsync(run.getId(), llmJudge))
   # A metódus ITT visszatér — a hívó (EvalController) azonnal 202 Accepted-et küld a
   # RUNNING állapotú EvalRunResponse-szal, NEM várja meg a ciklust.

3. return mapToResponse(run)
```

**`runEvalAsync(runId, llmJudge)` — a háttérszálon futó, tényleges ciklus:**

```
bemenet: runId (UUID), llmJudge (boolean)
kimenet: nincs (a DB-be írja az eredményt, a frontend polling-gal olvassa ki)

1. run = evalRunRepository.findById(runId)   // az executor-szálon ÚJ tranzakcióban
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
   # Ezen a ponton nincs mit "visszaadni" — ez egy háttérszál, a hívó (startEvalRun) már
   # régen visszatért. A frontend a polling (GET /api/admin/eval/runs/{id}) következő
   # körénél fogja látni a frissült status/hitRate mezőket.
```

**Miért kell külön tranzakció a `runEvalAsync()`-ban**: mivel ez egy MÁSIK szálon fut (az
`evalExecutor`-on, nem a HTTP-kérést kiszolgáló szálon), a Spring `@Transactional`
szál-lokális tranzakció-kezelése miatt **nem örökölheti** a `startEvalRun()` tranzakcióját —
saját, önálló tranzakciós blokkokra van szükség (pl. entry-nkénti `@Transactional`
mentés, ahogy a fenti pszeudokód `evalRunResultRepository.save(...)` hívásai is
implikálják) — ez ugyanaz a minta, mint a PR #3 `chatStreamExecutor`-on futó
`streamChat()`-je, ami szintén nem az eredeti HTTP-kérés tranzakcióján belül dolgozik.

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

**2026-08-26-i javítás — a `RetrievalResult` típus definiálva lett.** A fenti pszeudokód egy
`List<RetrievalResult>` típusra és egy `result.sourceName` mezőre hivatkozott, **amik sehol
nem voltak definiálva** — a `ContentChunkDto`-ban ugyanis csak `sourceId` (UUID) volt, névből
semmi, tehát a `top_result_name VARCHAR(255)` oszlopot nem lett volna miből feltölteni.

A hiányzó típus a PR #2-ben lett pótolva: **`RetrievedItem`**
(`dto/rag/RetrievedItem.java`, ld. `pr2_hybrid_retrieval_architecture_2026.md` 6.5 szakasz) —
egy közös rekord, amit MINDKÉT retrieval-ág termel (csillagrendszer flat keresés és
misszió-chunk hibrid keresés), és amit a `ChatService` kontextus-építése ÉS az itteni
`matchesAny()` egyaránt fogyaszt:

```java
public record RetrievedItem(
    String sourceType, UUID sourceId, String sourceName,
    String filePath, String text, double score
) {}
```

Ez a fenti pszeudokódot változtatás nélkül működőképessé teszi (`result.sourceType`,
`result.sourceName`, `result.text` mind létező mezők), és a `top_result_name` értéke
`results.get(0).sourceName()`. A `sourceName` a `missions` táblából jön JOIN-nal — ezt a
2026-08-26-i `source_id REFERENCES missions(id)` FK teszi garantáltan lehetségessé.

**Egy apró, de fontos igazítás a `matchesAny()`-ben**: a `result.chunkText` helyett
`result.text()` a mező neve (a `RetrievedItem` a csillagrendszer-leírást is ugyanebben a
mezőben hordozza, ezért nem `chunkText` a neve).

### 5.1.5 `runRetrievalFor()` MEGSZŰNIK — a közös pipeline hívása (2026-08-26, ELDÖNTVE)

A fenti pszeudokód `runRetrievalFor(entry)`-je **az elvárt forrástípus alapján választott
retrieval-ágat** (`STAR_SYSTEM`-nél `searchByEmbedding()`, egyébként
`retrieveMissionChunks()`). Élesben viszont a `ChatService` mindkét ágat lefuttatja — az eval
tehát egy olyan rendszert mért volna, ami nem létezik, és szerkezetileg képtelen lett volna
elkapni a „rossz ág nyer" és a „a két ág együtt ad rossz sorrendet" hibaosztályokat.

**A megoldás a PR #2 6.6 szakaszában született meg**: mostantól egyetlen, közös
`RetrievalPipeline.retrieve(query, scope)` létezik, ami három rangsorolt listát (vektoros
chunk-keresés, full-text chunk-keresés, csillagrendszer-keresés) fésül egyetlen RRF-be. Ezt
hívja a `ChatService` ÉS az `EvalService` is — ez adja meg ténylegesen azt a garanciát, amit
ez a doksi eddig csak ígért („nem kell egy második, lereplikált verziót karbantartani").

```
runEvalAsync ciklusa, a módosított rész:

    preRerank  = retrievalPipeline.retrieve(entry.getQuery(), RetrievalScope.forEval())
    postRerank = rerankingService.rerank(entry.getQuery(), preRerank, RERANK_KEEP_TOP)

    hit3         = matchesAny(preRerank.subList(0, min(3, preRerank.size())), entry)
    hit5         = matchesAny(preRerank, entry)                      // TOP_K = 5
    hit3Reranked = matchesAny(postRerank, entry)                     // RERANK_KEEP_TOP = 3
```

**Miért két mérési pont**: `RERANK_KEEP_TOP = 3`, tehát a rerank kimenete 3 elem — ott a
`hit@5` matematikailag azonos a `hit@3`-mal, két oszlopot töltöttünk volna ugyanazzal az
adattal. A rerank ELŐTTI listán mérve a `@3`/`@5` a retrieval minőségét mutatja, a rerank
UTÁNI `hit@3` pedig a végeredményt. **A kettő különbsége az egyetlen mód arra, hogy
megmutasd, a reranking ténylegesen javít-e** — enélkül egy indokolatlan extra LLM-hívás
marad, ami a mért latencia mellett (ld. `ai_chatbot_upgrade_2026.md` „Lokális futtatás")
külön is súlyos kérdés.

A `V11` séma ehhez kapta a `eval_runs.hit_rate_reranked_at_3` és a
`eval_run_results.hit_at_3_reranked` oszlopot (2. szakasz), a UI eredmény-táblázata pedig
mindkét értéket mutassa egymás mellett.

**Az `expected_source_type` szerepe megváltozik**: már nem ágválasztó (nincs mit választani),
csak egy elvárás, amit a `matchesAny()` ellenőriz. Érdemes megengedni egy `ANY` értéket is
azokra a kérdésekre, ahol bármelyik forrástípus elfogadható találat — ehhez a
`eval_golden_entries_source_type_check` CHECK-constraintet ki kell egészíteni `'ANY'`-vel.

### 5.2 Az LLM-judge lépés — **ELDÖNTVE Norberttel (2026-08-25)**

A fő terv csak ennyit mondott: *"Opcionális LLM-judge lépés (checkbox a UI-n) —
újrahasznosítja a PR #2 `AiServiceClient.generateJson()`-t, nincs hozzá új
infrastruktúra."* Norbert pontosította: **kizárólag a retrieveelt (top) chunkok
relevanciáját ítéli meg a kérdéshez képest**, 0-10 skálán — NEM a teljes generált választ
(ahhoz egy "ideális válasz" referencia-mező kellene a golden setben, ami most nincs
megtervezve, külön kör lenne).

```
runLlmJudge(entry, results):
    if results.isEmpty():
        return   # nincs mit megítélni, a llm_judge_score NULL marad ennél a sornál

    topResult = results.get(0)   # csak a #1 találatot ítéli meg — ugyanaz a lépték,
                                  # mint a hit@3/hit@5 (a "legjobbnak vélt" eredmény minőségét
                                  # nézzük, nem az összes visszaadott chunkot egyenként)

    prompt = """
    Kérdés: "{entry.getQuery()}"
    Talált szövegrészlet: "{topResult.getChunkText()}"

    0-10 skálán, mennyire releváns ez a szövegrészlet a kérdés megválaszolásához?
    Csak egy JSON objektumot adj vissza: {"score": <szám 0 és 10 között>}
    """
    result = aiServiceClient.generateJson(prompt, null)   # ugyanaz a hívás-forma, mint a
                                                            # PR #2 RerankingService-ében
    if not result.success():
        log.warn("LLM-judge hívás sikertelen, entry={}, score NULL marad", entry.getId())
        return

    score = parseScoreOrNull(result.raw())   # ObjectMapper, hibatűrő parse — sikertelen
                                              # parse esetén is NULL marad, nem dob kivételt
    if score != null:
        evalRunResultRepository.updateLlmJudgeScore(run.getId(), entry.getId(), score)
```

**Fontos, indoklással**: a judge-hívás **entry-nkénti, szinkron, plusz LLM-hívás** — ha
`llmJudge=true`, a teljes `runEvalAsync()` futásideje kb. **megduplázódik** (minden golden
entry-nél egy plusz Ollama-hívás a retrieval mellett). Mivel ez a PR #4 aszinkron
tervezése miatt már amúgy sem blokkolja a felhasználót (ld. 5.1 szakasz), ez a
megduplázódás elfogadhatóbb, mint egy szinkron világban lenne — de a checkbox/opcionális
jelleg megmarad, mert a hit-rate@3/@5 önmagában is egy teljesen működő, gyors mérőszám, a
judge csak egy mélyebb, drágább kiegészítés.

## 6. `EvalController` — végpontok

| Metódus | Path | Permission | Visszatérés |
|---|---|---|---|
| `GET` | `/api/admin/eval/golden-set` | `eval:read` | `List<EvalGoldenEntryResponse>` |
| `POST` | `/api/admin/eval/golden-set` | `eval:write` | `EvalGoldenEntryResponse` (201) |
| `PUT` | `/api/admin/eval/golden-set/{id}` | `eval:write` | `EvalGoldenEntryResponse` |
| `DELETE` | `/api/admin/eval/golden-set/{id}` | `eval:write` | 204 |
| `POST` | `/api/admin/eval/run?llmJudge=true\|false` | `eval:write` | **202 Accepted**, `EvalRunResponse` (`status="RUNNING"`) — **ELDÖNTVE (2026-08-25): aszinkron**, NEM várja meg a futás végét (ld. 5.1) |
| `GET` | `/api/admin/eval/runs` | `eval:read` | `List<EvalRunResponse>` |
| `GET` | `/api/admin/eval/runs/{id}` | `eval:read` | `EvalRunResponse` (részletes, `results` beágyazva) — **ezt hívja a frontend polling-ban**, ld. 7. szakasz |
| `DELETE` | `/api/admin/eval/runs/{id}` | `eval:write` | 204 — **ÚJ (2026-08-25, Norbert kérésére: "határozottan törölhetők legyen, teljes CRUD")**. `409 Conflict` (`ResourceConflictException`), ha a run még `RUNNING` státuszú — ld. 6.1. |

A `SectorController` mintáját követve — `@Valid @RequestBody` a mutáló végpontokon,
`ResponseEntity` minden metódusból, `@RequestParam(defaultValue = "false") boolean llmJudge`
a run végponton.

**Fontos, hogy `eval:write` kell a futtatáshoz, NEM csak `eval:read`** — a fő terv ezt nem
mondja ki explicit, de logikusan következik abból, hogy a futtatás egy AI-hívásokkal járó,
erőforrás-igényes (és `llmJudge=true` esetén pénzbe/API-kvótába kerülő, ha valaha nem lokális
Ollama-t használnának) művelet, nem egy sima olvasás — ugyanaz a minta, mint a
`sector:write` a `reorder`-nél (ami is egy "cselekvés", nem CRUD a szó szoros értelmében).

### 6.1 `deleteRun()` — miért véd a `RUNNING` státusz ellen

```java
@Transactional
public void deleteRun(UUID runId) {
    EvalRun run = evalRunRepository.findById(runId)
            .orElseThrow(() -> new ResourceNotFoundException("EvalRun", "id", runId));
    if ("RUNNING".equals(run.getStatus())) {
        throw new ResourceConflictException("EvalRun", "status",
                "Cannot delete a run that is still in progress.");
    }
    evalRunRepository.deleteById(runId);
}
```

Ha egy `RUNNING` futást törölnénk, miközben a `runEvalAsync()` még a háttérszálon dolgozik
rajta, a háttérszál a törölt `run_id`-re próbálna `EvalRunResult` sorokat menteni — ez vagy
egy FK-violation hibát dobna (mert a szülő `eval_runs` sor már nincs meg), vagy (ha a
CASCADE valahogy mégis lefutna közben) csendben elveszne a futás eredménye anélkül, hogy
bárki észrevenné. A `409 Conflict` egyértelmű visszajelzés az adminnak: "várd meg, amíg
végez, utána törölheted" — a frontend ez alapján a `RUNNING` sorok törlés-gombját
`disabled`-nek jelenítheti meg a "Korábbi futások" listában (ld. 7. szakasz).

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
│   ├── "Futtatás" gomb → `evalApi.runEval(llmJudge)` — **ELDÖNTVE (2026-08-25): aszinkron
│   │   indítás + polling**, NEM egy szinkron, blokkoló hívás. A gomb megnyomása AZONNAL
│   │   visszakapja a `RUNNING` státuszú run-t (202 Accepted), a UI ettől kezdve 2
│   │   másodpercenként lekérdezi `evalApi.getRunDetail(runId)`-t, amíg a `status` el nem
│   │   éri a `COMPLETED`/`FAILED`-et — ld. 7.2 szakasz a pontos polling-hook-ra.
│   ├── Futás közben: "Futtatás folyamatban…" jelzés + spinner + egy link/megjegyzés,
│   │   hogy a soronkénti haladás élőben követhető az `/admin/logs` oldalon
│   │   (`eval_progress`-sorok, ld. 8. szakasz) — ez a "professzionálisabb" UX-nek pont a
│   │   lényege: nem néma várakozás, hanem élő visszajelzés két csatornán (polling + logok)
│   └── Hiba esetén (`status="FAILED"` VAGY a `POST /run` hívás maga hibázik) Snackbar/Alert
│       (a `FeatureFlagList` `toggleError` mintája)
├── Eredmény-összefoglaló kártya (a legutóbbi BEFEJEZETT futás után jelenik meg, a polling
│   automatikusan lecseréli a "folyamatban" jelzést, amint `status != RUNNING`)
│   ├── hit-rate@3, hit-rate@5 (nagy szám + %, pl. Card + Typography variant="h3")
│   └── Kérdésenkénti bontás táblázat (query, hit@3 ✓/✗, hit@5 ✓/✗, topResultName, latencyMs)
└── "Korábbi futások" szekció
    ├── Select/lista a `evalApi.getRuns()` eredményéből (dátum + hit-rate@3/@5 + státusz badge)
    ├── Kiválasztásra betölti az adott run részleteit (`evalApi.getRunDetail(id)`) ugyanabba
    │   az eredmény-táblázatba, mint a legutóbbi futás
    └── **ÚJ (2026-08-25): törlés-ikon soronként** → `evalApi.deleteRun(id)`, megerősítő
        dialógussal (a `SectorList` törlés-mintáját követve). `RUNNING` státuszú soroknál az
        ikon `disabled`, tooltippel ("Futás közben nem törölhető") — ld. 6.1 szakasz indoklása.
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
    // A visszakapott EvalRunResponse.status itt még "RUNNING" — a hívó felelőssége,
    // hogy elindítsa a pollingot (ld. 7.2 usePollingEvalRun hook).
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
  deleteRun: async (id: string): Promise<void> => {
    // 409-et dob (interceptorban/hívóban kezelendő), ha a run még RUNNING —
    // a UI ezt Snackbar-ral jelzi, ld. 6.1 szakasz.
    await apiClient.delete(`/admin/eval/runs/${id}`);
  },
};
```

### 7.2 `usePollingEvalRun()` — a polling-hook (ÚJ, 2026-08-25-i aszinkron döntés miatt)

```typescript
function usePollingEvalRun(runId: string | null, intervalMs = 2000) {
  const [run, setRun] = useState<EvalRunResponse | null>(null);

  useEffect(() => {
    if (!runId) return;

    let cancelled = false;
    const poll = async () => {
      const result = await evalApi.getRunDetail(runId);
      if (cancelled) return;
      setRun(result);
      if (result.status === "RUNNING") {
        setTimeout(poll, intervalMs);
      }
    };
    poll();

    return () => { cancelled = true; };   // unmount/runId-váltás esetén leáll a lánc
  }, [runId, intervalMs]);

  return run;
}
```

**Miért `setTimeout`-lánc, nem `setInterval`**: ha egy `getRunDetail()` hívás lassabb lenne,
mint az `intervalMs`, egy `setInterval` egymást átfedő kéréseket indítana — a `setTimeout`-
lánc mindig megvárja az ELŐZŐ hívás válaszát, mielőtt a következőt ütemezné, tehát sosem
fut párhuzamosan két polling-kérés ugyanarra a run-ra.

**Használat az `EvalPage.tsx`-ben**: a "Futtatás" gomb `onClick`-je elmenti a
`evalApi.runEval(llmJudge)` válaszából kapott `id`-t egy `activeRunId` state-be, ez a hook
pedig automatikusan pollingol, amíg a `status` `RUNNING` — a komponens ebből az `run` értékből
dönti el, spinnert vagy eredmény-táblázatot mutat-e.

### 7.3 Hook-pontok — routing és navigáció

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
| `eval_progress` | Ez a PR, `EvalService.runEvalAsync()` (a háttérszálon, ld. 5.1) | `eval_progress query="{}" hit@3={} hit@5={} latency_ms={}` |
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
        -ExecutorService evalExecutor
        +listGoldenEntries() List~EvalGoldenEntryResponse~
        +createGoldenEntry(CreateGoldenEntryRequest) EvalGoldenEntryResponse
        +updateGoldenEntry(UUID, CreateGoldenEntryRequest) EvalGoldenEntryResponse
        +deleteGoldenEntry(UUID) void
        +startEvalRun(boolean llmJudge) EvalRunResponse
        -runEvalAsync(UUID runId, boolean llmJudge) void
        -runLlmJudge(EvalGoldenEntry, List~RetrievalResult~) void
        +listRuns() List~EvalRunResponse~
        +getRunDetail(UUID) EvalRunResponse
        +deleteRun(UUID) void
        -matchesAny(List~RetrievalResult~, EvalGoldenEntry) boolean
        -runRetrievalFor(EvalGoldenEntry) List~RetrievalResult~
    }

    class EvalController {
        -EvalService evalService
        +getGoldenSet() ResponseEntity
        +createGoldenEntry(CreateGoldenEntryRequest) ResponseEntity
        +updateGoldenEntry(UUID, CreateGoldenEntryRequest) ResponseEntity
        +deleteGoldenEntry(UUID) ResponseEntity
        +startEvalRun(boolean) ResponseEntity~202~
        +getRuns() ResponseEntity
        +getRunDetail(UUID) ResponseEntity
        +deleteRun(UUID) ResponseEntity
    }

    class AppConfig {
        +evalExecutor() ExecutorService
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
    EvalService ..> "HybridRetrievalService (PR #2)" : runEvalAsync() hívja
    EvalService ..> "StarSystemService (meglévő)" : runEvalAsync() hívja
    EvalService --> AppConfig : evalExecutor bean
```

## 10. Sequence diagram — Admin lefuttatja az Eval-t (aszinkron + polling, 2026-08-25)

```mermaid
sequenceDiagram
    actor Admin
    participant EP as EvalPage (frontend)
    participant EC as EvalController
    participant ES as EvalService
    participant EX as evalExecutor (háttérszál)
    participant GER as EvalGoldenEntryRepository
    participant RS as retrieval (Hybrid/StarSystem)
    participant DB as Postgres (eval_run_results)
    participant WS as WebSocketLogAppender

    Admin->>EP: "Futtatás" gomb (llmJudge checkbox állapota)
    EP->>EC: POST /api/admin/eval/run?llmJudge=false
    EC->>ES: startEvalRun(false)
    ES->>DB: run = new EvalRun(status=RUNNING); save
    ES->>EX: execute(() -> runEvalAsync(run.id, false))
    ES-->>EC: EvalRunResponse (status=RUNNING)
    EC-->>EP: 202 Accepted
    EP->>EP: activeRunId = response.id — usePollingEvalRun() elindul

    par háttérszálon fut, a HTTP-választól függetlenül
        EX->>ES: runEvalAsync(runId, false)
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
    and a frontend párhuzamosan pollingol
        loop 2 másodpercenként, amíg status=RUNNING
            EP->>EC: GET /api/admin/eval/runs/{activeRunId}
            EC->>ES: getRunDetail(activeRunId)
            ES->>DB: findById + results
            DB-->>ES: EvalRun (status még RUNNING)
            ES-->>EC: EvalRunResponse
            EC-->>EP: 200 OK (status=RUNNING)
        end
        Note over EP: a háttérfutás időközben COMPLETED-re vált a DB-ben
        EP->>EC: GET /api/admin/eval/runs/{activeRunId} (következő poll)
        EC-->>EP: 200 OK (status=COMPLETED, hitRateAt3/At5 kitöltve)
        EP->>EP: polling leáll, eredmény-összefoglaló kártya + táblázat megjelenítése
    end
```

## 11. Tesztterv

| Teszteset | Osztály | Mit ellenőriz |
|---|---|---|
| `createGoldenEntry_savesCorrectly` | `EvalServiceTest` | Alap CRUD, a `SectorServiceTest`-hez hasonló mintával |
| `startEvalRun_returnsImmediatelyWithRunningStatus` | `EvalServiceTest` | Mockolt `evalExecutor` (`doNothing()` a `execute()`-on) → a visszaadott `EvalRunResponse.status == "RUNNING"`, és a teszt bizonyítja, hogy a `runEvalAsync()` NEM hívódott meg szinkron a `startEvalRun()` szálán |
| `runEvalAsync_allHit_hitRateIsOne` | `EvalServiceTest` | Mockolt retrieval mindig egyezik → a mentett `EvalRun.hitRateAt3 == 1.0`, `status == "COMPLETED"` |
| `runEvalAsync_noHits_hitRateIsZero` | `EvalServiceTest` | Mockolt retrieval sosem egyezik → `hitRateAt3 == 0.0`, de a run státusza `COMPLETED` marad (a 0% is egy érvényes eredmény, nem hiba) |
| `runEvalAsync_emptyGoldenSet_returnsZeroWithoutError` | `EvalServiceTest` | 0 golden entry → `hitRateAt3/At5 == 0.0`, nem `NaN`/`ArithmeticException` (a nullával-osztás elkerülése expliciten tesztelve) |
| `runEvalAsync_retrievalThrows_marksRunFailed` | `EvalServiceTest` | A retrieval-hívás kivételt dob → a run `status=FAILED`-del mentődik, a már addig megszerzett `EvalRunResult` sorok megmaradnak |
| `matchesAny_typeMismatch_returnsFalse` | `EvalServiceTest` | Egyező név/kulcsszó, de eltérő `sourceType` → nem számít találatnak |
| `matchesAny_caseInsensitive` | `EvalServiceTest` | Kis/nagybetű-eltérés a névben/kulcsszóban nem befolyásolja az egyezést |
| `EvalControllerSecurityTest` | — | `eval:read`/`eval:write` helyesen elválasztva (olvasás vs. mutáló+futtató végpontok), a meglévő `SectorControllerSecurityTest`/`FeatureFlagControllerSecurityTest` mintája szerint |
| `EvalControllerTest_runReturns202` | — | A `POST /api/admin/eval/run` végpont ténylegesen `202 Accepted`-et ad vissza, nem `200 OK`-t (a REST-szemantika helyessége — aszinkron, még-nem-kész erőforrás létrehozása) |
| `deleteRun_completedRun_deletesSuccessfully` | `EvalServiceTest` | `COMPLETED`/`FAILED` státuszú run törölhető, a hozzá tartozó `EvalRunResult` sorok is eltűnnek (CASCADE) |
| `deleteRun_runningRun_throwsConflict` | `EvalServiceTest` | `RUNNING` státuszú run törlési kísérlete `ResourceConflictException`-t dob, a sor a DB-ben megmarad |
| `deleteRun_notFound_throwsNotFound` | `EvalServiceTest` | Ismeretlen `runId` → `ResourceNotFoundException` |
| `usePollingEvalRun.test.ts` | frontend | Mockolt `evalApi.getRunDetail()`, ami első hívásra `RUNNING`-ot, másodikra `COMPLETED`-et ad → a hook pontosan 2 hívást indít, a második után leáll (nem pollingol tovább) |
| `EvalPage.test.tsx` | frontend | Golden set CRUD-interakció (mockolt `evalApi`), "Futtatás" gomb → azonnal "folyamatban" jelzés (NEM várja meg a végét) → polling-gal frissül → eredmény-kártya megjelenik, korábbi futás kiválasztása betölti a régi eredményt |

**Kézi ellenőrzés (Norbi, itt nem elvégezhető)**: golden set feltöltése valós kérdésekkel,
"Futtatás" élő Ollamával — ellenőrizd, hogy a gomb megnyomása UTÁN AZONNAL (nem a teljes
futás végén) visszakapod a "folyamatban" állapotot, hogy a polling ténylegesen 2
másodpercenként frissül, és hogy a `/admin/logs` párhuzamosan mutatja-e élőben a
soronkénti `eval_progress` sorokat, ahogy a futás a háttérben halad.

## 12. Nyitott kérdések Norbertnek

1. ~~Az LLM-judge lépés pontos viselkedése~~ — **ELDÖNTVE (2026-08-25)**: kizárólag a
   retrieveelt (top) chunkok relevanciáját ítéli meg a kérdéshez képest (NEM a teljes
   generált választ), 0-10 skálán, `llm_judge_score DOUBLE PRECISION` oszlop az
   `eval_run_results`-ban (NULL, ha nem volt bekapcsolva). Részletek: 2. és 5.2 szakasz.
2. ~~A golden-set méretének felső korlátja~~ — **ELDÖNTVE (2026-08-25): aszinkron
   indítás + polling, MOST rögtön, nem "majd ha problémává válik".** Norbert kifejezett
   kérésére a `startEvalRun()`/`runEvalAsync()` szétválasztás (5.1 szakasz), a `202
   Accepted` válasz (6. szakasz), az `evalExecutor` bean (4.5 szakasz) és a frontend
   `usePollingEvalRun()` hook (7.2 szakasz) mind bekerült ebbe a körbe — nincs explicit
   felső korlát a golden set méretére, mert a szinkron-blokkolás kockázata ezzel a
   tervezéssel eleve megszűnt.
3. ~~A korábbi futások törölhetők-e~~ — **ELDÖNTVE (2026-08-25): igen, teljes CRUD.**
   `DELETE /api/admin/eval/runs/{id}` (`eval:write`), `RUNNING` státuszú run-t nem lehet
   törölni (`409 Conflict`, ld. 6.1 szakasz), a frontend törlés-ikonnal + megerősítő
   dialógussal a "Korábbi futások" listában (7. szakasz).
4. ~~`STAR_SYSTEM` mint `expected_source_type`~~ — **JÓVÁHAGYVA (2026-08-25).** Marad a
   4-elemű CHECK-constraint (`STAR_SYSTEM`, `MISSION`, `MISSION_FILL_IN_BLANK`,
   `MISSION_CODE_FILE`), hogy a golden set mindkét retrieval-útvonalat (régi
   csillagrendszer-keresés + PR #1-es misszió-chunk keresés) le tudja fedni.

**Ezzel a PR #4 összes nyitott kérdése lezárva (2026-08-25).**
