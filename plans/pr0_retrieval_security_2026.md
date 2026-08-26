# PR #0 — Retrieval-biztonság: jogosultság-szűkítés, láthatóság, scope-olt keresés

> **Ez a dokumentum a `plans/ai_chatbot_upgrade_2026.md` öt fázisa ELÉ kerül.** A 2026-08-26-i
> átvizsgálás négy olyan hiányosságot talált, amik mind ugyanabból erednek: a `content_chunks`
> réteg egy **bizalmi határon lóg át**, a tervek viszont végig adat-problémaként kezelték,
> nem hozzáférési kérdésként. Amíg ez nincs rendezve, a PR #1 elindítása azt jelentené, hogy a
> referencia megoldások bekerülnek egy olyan indexbe, amiből a kadét chatel.
>
> A dokumentum három részre oszlik: egy **önálló, azonnal mergelhető PR #0** (jogosultság-
> szűkítés), plusz **kötelező követelmények** a PR #1, #2 és #4 felé, amiket az ő
> implementációjuknak tartalmaznia kell.

## 1. Fenyegetési modell — mi a bizalmi határ

A platform szereplői és az, amit a chatbot kontextusába juttathatnak:

| Szereplő | Mit írhat ma | Bekerül-e a RAG-indexbe |
|---|---|---|
| `ROLE_ADMIN` | mindent | igen |
| `ROLE_CONTENT_CREATOR` | saját csillagrendszer + misszió | igen |
| `ROLE_CADET` | **saját csillagrendszer + misszió** (`starsystem:create`, `mission:create`) | **igen** |

Két különálló probléma következik ebből:

**(A) Kifelé szivárgás — a chatbot kiadja a megoldást.** A `mission-js-template` Forge-repója
tartalmazza a `solution.js`-t a **referencia megoldással**:

```
gitea-templates/mission-js-template/
  starter.js    -> export function add(a, b) { // TODO: Implementáld a megoldásod itt. }
  solution.js   -> export function add(a, b) { return a + b; }     <- ez indexelődne
```

A PR #1 12.4 szakasza kiterjesztés-alapú whitelistet használ (`.py`, `.js`, `.jsx`, `.ts`,
`.tsx`) **fájlnév-kizárás nélkül**, a PR #2 retrieval-je pedig **semmilyen szűrést nem
tartalmaz**. Egy „hogyan oldjam meg ezt a missziót?" kérdés pont ezt a chunkot hozná fel.

**(B) Befelé injektálás — kadét-írta szöveg más felhasználó kontextusában.** A kadét által írt
misszió-leírás indexelődik, majd egy **admin** chatjének kontextusába kerül. A PR #5 után ez
privilégium-eszkalációs csatorna: az injektált utasítás olyan tool-hívásokat válthat ki, amik
az **áldozat JWT-jével** futnak.

**A bizalmi határ, amit ki akarunk alakítani**: az indexbe kizárólag **megbízható szerzők**
tartalma kerülhet, és az indexen belül a **titkos részek** (referencia megoldások) csak annak
láthatók, akinek amúgy is joga van hozzájuk.

---

## 2. PR #0 — Jogosultság-szűkítés (önálló, ELŐBB mergelendő)

### 2.1 A változás

**Norbert döntése (2026-08-26): a kadétok minden létrehozó jogot elveszítenek — kizárólag
missziókat teljesítenek.**

`DataInitializer.java` (jelenlegi 76-86. sor), két sor törlése:

```java
// ROLE_CADET: Alap jogok (Olvasás, Indítás)
Set<Permission> cadetPermissions = new HashSet<>();
cadetPermissions.add(missionRead);
cadetPermissions.add(missionStart);
cadetPermissions.add(quizViewResults);
cadetPermissions.add(starSystemRead);
cadetPermissions.add(groupRead);
cadetPermissions.add(sectorRead);
// TÖRÖLVE 2026-08-26: cadetPermissions.add(starSystemCreate);
// TÖRÖLVE 2026-08-26: cadetPermissions.add(missionCreate);
createRoleIfNotFound("ROLE_CADET", cadetPermissions);
```

### 2.2 Miért NEM kell hozzá Flyway-migráció

A `createRoleIfNotFound()` (`DataInitializer.java:158-167`) **lecseréli** a meglévő szerepkör
jogosultság-halmazát, nem hozzáfűz:

```java
.map(existingRole -> {
     existingRole.setPermissions(permissions);   // <- csere, nem merge
     return roleRepository.save(existingRole);
})
```

Tehát a következő induláskor a `roles_permissions` sorok maguktól eltűnnek. **Ez fontos
tulajdonság, amit érdemes tudni**: a `DataInitializer` deklaratív, a Java-kód a hiteles forrás
a szerepkör-jogokra — egy kézzel a DB-be szúrt jogosultság is elveszne induláskor.

### 2.3 Nem töri el a kadét-folyamatot — ellenőrizve

A kadét-út végpontjai és a hozzájuk tartozó jogok (a tényleges `@PreAuthorize`
annotációkból kigyűjtve):

| Funkció | Permission | Marad? |
|---|---|---|
| Misszió-lista, misszió megnyitása | `mission:read` | igen |
| Misszió indítása, saját munkarepó fájljai | `mission:start` | igen |
| Kvíz indítás/beküldés | `mission:start` | igen |
| Kvíz-eredmények | `quiz:view_results` | igen |
| Fill-in-blank megoldás/beküldés | `mission:start`, `mission:read` | igen |
| Csillagrendszer/szektor böngészése | `starsystem:read`, `sector:read` | igen |
| Misszió-csoportok | `group:read` | igen |
| **Misszió létrehozása (Forge)** | `mission:create` | **NEM** |
| **Csillagrendszer létrehozása** | `starsystem:create` | **NEM** |

A teljes Forge-felület a `mission:create`/`mission:edit` mögött van, tehát a kadét számára
egyben bezárul — nem kell külön letiltani.

### 2.4 A meglévő, kadét által írt tartalom — külön kezelendő

**A jogelvétel visszamenőleg nem takarít.** Ami már a DB-ben van, ott marad, és a PR #1 után
indexelődne is. Leltár:

```sql
SELECT c.username, count(DISTINCT ss.id) AS csillagrendszer, count(DISTINCT m.id) AS misszio
FROM cadets c
JOIN cadet_roles cr ON cr.cadet_id = c.id
JOIN roles r        ON r.id = cr.role_id AND r.name = 'ROLE_CADET'
LEFT JOIN star_systems ss ON ss.owner_id = c.id
LEFT JOIN missions m      ON m.owner_id  = c.id
GROUP BY c.username
HAVING count(ss.id) > 0 OR count(m.id) > 0;
```

**Norbert feladata**: lefuttatni az éles adatbázison, és eldönteni soronként, mi legyen
(törlés / átadás egy admin tulajdonába / meghagyás). **Amíg ez nincs meg, a PR #1 reindexe
nem futtatható éles adaton** — mert pont az a tartalom kerülne be, ami elől a jogelvétel
véd.

### 2.4.1 Frontend — a létrehozó gombok elrejtése

A „+ Új csillagrendszer" / „+ Új misszió" gombokat is el kell rejteni a kadét elől. Enélkül a
gomb látszik, a hívás pedig 403-mal elhasal — rossz UX, és úgy néz ki, mintha hibás lenne az
oldal.

**Amivel meg lehet csinálni (ellenőrizve a kódban)**: az `AuthContext` már ma is kínál egy
`hasRole(role: string)` függvényt, és a JWT `roles` tömbje **a flattened permissionöket is
tartalmazza**, nem csak a szerepkör-neveket — ezt a `AuthContext.tsx` saját kommentje is
kimondja (125-127. sor: *„A backend már 'flattened' permissionöket is küldhet role-ként"*).
Egy `hasRole("mission:create")` hívás tehát ma is helyesen működne.

**Amit viszont NEM szabad feltételezni**: hogy erre már van bevett minta. Nincs — a jelenlegi
használat kizárólag szerepkör-nevekre megy (`hasRole("ROLE_ADMIN")` a `MainLayout.tsx:61`-ben,
a `UserList.tsx`-ben és a `ProtectedRoute`-ban), és **nem létezik `usePermission` hook** a
`frontend/src/hooks/` alatt. Ez a PR #0 tehát vagy közvetlenül `hasRole("mission:create")`-et
hív a két érintett helyen (kevesebb munka), vagy bevezet egy vékony `usePermission()` wrappert
a szándék olvashatóbbá tételéért (a `hasRole` név félrevezető egy permission-ellenőrzésnél).
**Javaslat: a wrapper** — a különbség két fájl, cserébe a hívási helyeken egyértelmű lesz,
hogy permissionről és nem szerepkörről van szó.

### 2.5 Amit a jogelvétel megold és amit nem

**Megoldja**: az (B) injektálási csatornát — az indexbe innentől kizárólag admin/content-creator
tartalom kerül, tehát nincs keresztfelhasználós prompt injection kadétról adminra.

**NEM oldja meg**:
- A már meglévő kadét-tartalmat (2.4).
- Az (A) megoldás-szivárgást — az teljesen független, és a 3-4. szakasz kezeli.
- A `ChatContextDto.formFields`-et, ami továbbra is felhasználói szöveg a kontextusban. Ez
  **saját magára hat** (a felhasználó a saját chatjét befolyásolja), amit az RBAC amúgy is
  korlátoz — elfogadott maradék kockázat, nem indokol külön munkát.
- Az admin/content-creator közti bizalmi különbséget: a `ROLE_CONTENT_CREATOR` tartalma is
  bekerül az admin kontextusába. Ez tudatosan elfogadott — a content creator a platform
  szerkesztője, nem külső szereplő.

---

## 3. Követelmény a PR #1 felé — a `visibility` oszlop és az indexelési szabály

### 3.1 Séma-kiegészítés

A `V10__create_content_chunks.sql` táblája egy új oszlopot kap:

```sql
visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC',
...
CONSTRAINT content_chunks_visibility_check CHECK (visibility IN ('PUBLIC', 'AUTHOR_ONLY'))
```

**A láthatóság INDEXELÉSKOR dől el, nem lekérdezéskor.** Ez tudatos: a szabály egy helyen,
az indexelő ágban él, és ha később új „titkos" tartalomtípus jön, csak ott kell bővíteni — a
retrieval oldalán semmi nem változik.

### 3.2 Az indexelési szabály

```
MISSION_CODE_FILE chunk, aminek a file_path-ja illeszkedik SOLUTION_FILE_PATTERN-re
    -> visibility = 'AUTHOR_ONLY'
minden más
    -> visibility = 'PUBLIC'
```

### 3.3 A minta ÚJRAHASZNOSÍTVA, nem újraírva

**Ne találjunk ki második listát.** A kódban már létezik a mérvadó válasz arra, hogy mit nem
láthat egy kadét — pontosan az a fájlhalmaz, amit a kadét-másolat nem kap meg
(`GiteaService.transformForCadetCopy`):

```java
// GiteaService.java:245
private static final Pattern SOLUTION_FILE_PATTERN =
        Pattern.compile("^solution\\.(js|ts|py)$", Pattern.CASE_INSENSITIVE);
```

**Teendő**: ez a konstans (a `STARTER_FILE_PATTERN`-nel együtt) kikerül egy közös helyre —
`service/mission/MissionFilePatterns.java` —, és onnan használja a `GiteaService` ÉS az új
`CodeFileChunker` is. Tiszta áthelyezés, viselkedés-változás nélkül, tehát a
`transformForCadetCopy` meglévő tesztjei változatlanul futnak.

**Miért ez a helyes megoldás**: ha valaha bővül a minta (pl. `solution.java` egy Java-sablonnal),
egy helyen bővül. Két külön lista előbb-utóbb szétcsúszik, és a szétcsúszás **csendes** — a
kadét-másolat helyes maradna, miközben az index elkezdené kiadni a megoldást.

### 3.4 Amit SOHA nem szabad indexelni

Ez a lista azért van itt, mert a `CodeFileChunker` whitelistjének egy jövőbeli, jószándékú
bővítése némán nyitná meg őket:

| Mi | Hol van | Miért veszélyes |
|---|---|---|
| `quiz.json` | a QUIZ-missziók Forge-repójában | **minden kvízkérdés helyes válasza** benne van (`"isCorrect": true`) — a `.json` kiterjesztés felvétele a whitelistbe egy csapásra kiadná az összes kvíz megoldását |
| `FillInBlankOption.correct` | DB, `fill_in_blank_options` tábla | a kihagyások helyes opciói. A PR #1 **kizárólag a `templateText`-et** indexeli (ami csak `{{k1}}` helyőrzőket tartalmaz) — az opciók sose kerüljenek be |
| kadét-munkarepók | Gitea, `cadet-<user>-<missionId>` | más kadétok megoldásai; a PR #1 12.2 scope-döntése ezt már kizárja (ld. 3.5) |

**A `CodeFileChunker` whitelistje szándékosan whitelist, nem blacklist** — új kiterjesztés
felvétele legyen tudatos döntés, aminek a felvevője elolvassa ezt a táblázatot.

### 3.5 A PR #1 12.2 scope-döntése megerősítve

A PR #1 12.2 szakasza kérdésként tette fel: *„Kérlek erősítsd meg, hogy ez a scope helyes"* —
tudniillik hogy **kizárólag az admin Forge-repóját** indexeljük, a kadétok egyéni beadásait
nem. **Norbert megerősítette (2026-08-26): igen, csak az admin Forge-repója.**

Ez egyben biztonsági döntés is, nem csak méretezési: a kadét-repók indexelése minden kadét
megoldását bevinné egy közös keresőtérbe.

---

## 4. Követelmény a PR #2 felé — `RetrievalScope` és a kötelező szűrés

### 4.1 A típus

```java
package com.legymernok.backend.dto.rag;

/**
 * Meghatározza, hogy egy retrieval-hívás milyen láthatóságú chunkokat érhet el.
 * KÖTELEZŐ paraméter minden keresési belépési ponton — ld. 4.2, miért nincs
 * paraméter nélküli overload.
 */
public record RetrievalScope(boolean canSeeAllAuthorContent, UUID cadetId) {

    /** Kadét: kizárólag PUBLIC chunkok. */
    public static RetrievalScope forCadet(UUID cadetId) {
        return new RetrievalScope(false, cadetId);
    }

    /** Admin (mission:edit_any): minden chunk, tulajdonostól függetlenül. */
    public static RetrievalScope forAdmin(UUID cadetId) {
        return new RetrievalScope(true, cadetId);
    }
}
```

A `cadetId` akkor is kell, ha `canSeeAllAuthorContent == false`: egy **content creator** a
SAJÁT misszióinak megoldását láthatja (azt ő írta), másét nem. Ezt a `missions.owner_id`
oszlop adja, amit a 2026-08-26-i FK óta amúgy is JOIN-olunk a `source_name` miatt.

### 4.2 Kötelező paraméter — nincs kényelmi overload

```java
// NINCS ilyen. Sose legyen:
List<RetrievedItem> retrieve(String query, int topK);

// CSAK ilyen van:
List<RetrievedItem> retrieve(String query, int topK, RetrievalScope scope);
```

**Ez a szakasz legfontosabb mondata.** Ha van paraméter nélküli változat, valaki — jövőbeli
funkció, teszt, sietős javítás — azt fogja hívni, és a szűrés csendben kimarad. A típus-
rendszer olcsóbban véd, mint egy code review.

Ugyanez vonatkozik a `vectorSearch()`/`fullTextSearch()` privát metódusokra is: mindkettő
kapja meg a scope-ot, ne a hívó fűzze hozzá utólag a predikátumot.

### 4.3 A SQL-predikátum

Mindkét keresési ág `WHERE`-jébe bekerül, ugyanabban a formában:

```sql
FROM content_chunks cc
JOIN missions m ON m.id = cc.source_id
WHERE (cc.visibility = 'PUBLIC' OR ? = TRUE OR m.owner_id = ?)
```

paraméterek: `scope.canSeeAllAuthorContent()`, `scope.cadetId()`.

Ez adja a három kívánt viselkedést egyetlen predikátummal:

| Szereplő | `canSeeAllAuthorContent` | Mit lát |
|---|---|---|
| kadét | `false` | csak `PUBLIC` |
| content creator | `false` | `PUBLIC` + a **saját** misszióinak `AUTHOR_ONLY` chunkjai |
| admin | `true` | mindent |

### 4.4 A scope levezetése — a hitelesített userből, sosem a hívótól

```java
// ChatService
RetrievalScope scope = hasAuthority(currentUser, "mission:edit_any")
        ? RetrievalScope.forAdmin(currentUser.getId())
        : RetrievalScope.forCadet(currentUser.getId());
```

**Permission-alapú, nem szerepkör-név alapú** — a projekt RBAC-je permissionökkel dolgozik, a
szerepkör-nevekre hivatkozás törékeny lenne (ld. `ROLE_CONTENT_CREATOR`, ami már ma is
létezik).

### 4.5 A PR #5 tool-rétege: a scope SOSEM utazhat a dróton

A `search_platform_content` tool a felhasználó JWT-jével hívja a
`GET /api/search/hybrid`-et — **a backend a hitelesített userből vezeti le a scope-ot**. Ha a
scope kérés-paraméter lenne, az MCP-szerveren keresztül hamisítható lenne (az a szerver
szándékosan nem validál JWT-t, csak továbbít — ld. PR #5 6. szakasz).

Ugyanez az elv: **a `/api/search/hybrid` végpont sem fogadhat semmilyen scope/visibility
query-paramétert.**

---

## 5. Követelmény a PR #4 felé — az eval scope-ja

Az `EvalService.runEvalAsync()` a közös pipeline-t hívja (PR #2 6.6), tehát neki is át kell
adnia egy scope-ot. **A választás: `RetrievalScope.forCadet(...)`.**

Indoklás: az eval azt méri, amit a **termék** csinál a felhasználók túlnyomó többségének. Ha
admin scope-pal futna, olyan találatokra is adhatna pontot, amiket egy kadét sosem kapna meg —
és pont a megoldás-chunkok lennének a legrelevánsabbnak tűnő találatok egy „hogyan oldjam meg"
kérdésre. Az eval így hamis biztonságérzetet adna.

**Következmény**: az `eval_golden_entries` kérdései sem hivatkozhatnak `AUTHOR_ONLY`
tartalomra elvárt találatként — az mindig 0 hit-rate-et adna. Ezt a golden set feltöltésekor
(Norbert feladata) érdemes fejben tartani.

---

## 6. Tesztterv

A szűrés az a fajta biztonsági kontroll, ami **csendben romlik el** — egy hibás refaktor után
minden zöld marad, csak a chatbot elkezdi kiadni a megoldásokat. Ezért itt a teszt nem
formalitás.

| Teszteset | Osztály | Mit ellenőriz |
|---|---|---|
| `cadetScope_neverReturnsAuthorOnlyChunks` | `RetrievalSecurityIT` (Testcontainers) | Beszúrunk `PUBLIC` és `AUTHOR_ONLY` chunkot ugyanahhoz a misszióhoz, `forCadet()` scope-pal keresünk → az `AUTHOR_ONLY` **egyszer sem** jelenik meg, sem a vektoros, sem a full-text ágban |
| `ownerScope_seesOwnAuthorOnlyChunksButNotOthers` | `RetrievalSecurityIT` | Két misszió, két különböző `owner_id`, mindkettőnek `AUTHOR_ONLY` chunkja → a tulajdonos csak a sajátját kapja vissza |
| `adminScope_seesEverything` | `RetrievalSecurityIT` | `forAdmin()` → mindkét misszió `AUTHOR_ONLY` chunkja visszajön |
| `solutionFileIsIndexedAsAuthorOnly` | `CodeFileChunkerTest` / `ContentChunkingServiceTest` | Egy `solution.js` fájlból származó chunk `visibility='AUTHOR_ONLY'`-val kerül be, a `starter.js`-ből származó `PUBLIC`-kal |
| `solutionPatternIsSharedWithGiteaService` | `MissionFilePatternsTest` | A `CodeFileChunker` és a `transformForCadetCopy` **ugyanazt a konstansot** használja (regressziós teszt a 3.3 döntésre — ne lehessen két listává szétcsúsztatni) |
| `chatService_derivesScopeFromPermissionNotRole` | `ChatServiceTest` | `mission:edit_any` jogú user → `forAdmin`, enélkül → `forCadet`; a szerepkör NEVE nem befolyásolja |
| `searchHybridEndpoint_ignoresClientSuppliedScope` | `SearchControllerSecurityTest` | Egy `?visibility=AUTHOR_ONLY` vagy hasonló query-paraméter **semmilyen hatással nincs** az eredményre (4.5 döntés) |
| `cadetRoleHasNoCreatePermissions` | `DataInitializerTest` | Indulás után a `ROLE_CADET` jogosultság-halmaza nem tartalmaz `mission:create`/`starsystem:create` elemet |

---

## 7. Kézi ellenőrzés (Norbert)

1. **A jogelvétel után**: kadétként bejelentkezve nem érhető el a Forge, és a
   „+ Új csillagrendszer"/„+ Új misszió" gombok nem látszanak. Egy misszió teljes végigvitele
   (CONTENT → FILL_IN_BLANK → QUIZ → CODING) továbbra is működik.
2. **A 2.4 leltár-lekérdezés** lefuttatása az éles DB-n, és döntés a meglévő kadét-tartalomról
   — **a PR #1 reindexe előtt**.
3. **A szivárgás tényleges próbája**: kadétként megkérdezni a chatbotot, hogy „hogyan oldjam
   meg az `osszead` függvényes missziót?" — a válasz nem tartalmazhatja a referencia
   megoldást. Ugyanez adminként: ott meg kell jelennie.

---

## 8. Sorrend

```
PR #0  jogosultság-szűkítés + MissionFilePatterns kiemelése   <- ELŐBB
  |
  +-- 2.4 leltár és a meglévő kadét-tartalom rendezése (Norbert)
  |
PR #1  content_chunks + visibility oszlop + indexelési szabály
  |
PR #2  RetrievalScope + kötelező szűrés a lekérdezésekben
  |
PR #3, #4, #5  változatlan sorrendben
```

A PR #0 önmagában is értelmes és mergelhető (egy jogosultság-szűkítés + egy tiszta
konstans-áthelyezés), nem kell megvárnia semmit.
