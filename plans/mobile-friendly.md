# Mobile-Friendly Platform — Tervezési Dokumentum

## Vízió

A LégyMérnök.hu célja egy TikTok-helyettesítő tanulási platform, ahol a felhasználó rövid, 3–5 perces sessionökben tanul programozást, matematikát, fizikát és más tudományokat. A platform mobilon is teljesen használható, a tanulás lépésenként épül fel: olvas → gyakorol → csinál → ellenőriz.

---

## Jelenlegi helyzet és hiányosságok

- A meglévő CODING mission Monaco Editort használ, ami mobilon használhatatlan
- Nincs "olvasnivaló" mission típus — a tananyag szöveg nem jelenik meg strukturáltan
- Nincs fill-in-blank interakció
- A CODING mission user oldala nincs kész: nincs fájl létrehozás, törlés, teljes flow hiányzik
- A missziók egymástól független egységek, nem lehet őket logikai csoportba fűzni
- Nincs mobilbarát kódolási alternatíva kezdőknek
- Admin oldal: StarSystemEdit-ből nem lehet közvetlenül missionre navigálni
- Admin oldal: direkt axios hívások az `api/client.ts` helyett, hardkódolt URL-ek

---

## Stage 1 — Tartalom és interakció (új branch, main-ről)

### Mission Group koncepció

A missziók **csoportosíthatók** lesznek. Az admin létrehoz egy Mission Groupot (pl. "JavaScript Változók"), és belerak több al-missziót meghatározott sorrendben. A user szemszögéből ez **egyetlen nagy misszió**, belül lapozható al-lépésekkel. A háttérben minden al-misszió külön entitás.

**Példa felépítés:**
```
[Mission Group] JavaScript Változók
  ├── [CONTENT]       Változók leírása (let, var, const)
  ├── [FILL_IN_BLANK] Egészítsd ki a mondatokat
  └── [QUIZ]          Rövid ellenőrző kvíz
```

---

### Sorrend kezelés szabályai (EGYSÉGESÍTVE)

A Star Systemen belül minden elem (legyen az standalone mission vagy mission group) egy közös `orderIndex` alapján rendeződik.

- **Standalone Mission**: Az `orderIndex` határozza meg a helyét a Star System listájában. `groupId` = NULL.
- **Mission Group**: Az `orderIndex` határozza meg a csoport helyét a listában.
- **Mission a Group-ban**: `groupId` NOT NULL. A sorrendet a csoporton belül a `groupOrder` mező határozza meg. Az `orderIndex` ilyenkor NULL.

**Megjelenítés:**
A backend egyetlen rendezett `items[]` tömbben adja vissza a star system tartalmát — groups és standalone missionök vegyesen, `orderIndex` szerint rendezve, `type: "GROUP" | "MISSION"` discriminatorral. A frontendnek nem kell merge logikát implementálnia.

---

### Új mission típusok

#### CONTENT mission
- Az admin egy nagy markdown textareában írja a tartalmat
- Frontend élő preview-val rendereli a markdownt
- A user csak olvassa — nincs interakció, "Következő" gomb visz tovább
- Képeket, kódblokkokat, táblázatokat is támogat
- **Lehet standalone misszió is** — route: `/play/content/:id`

**Content pagination (hosszú tartalom kezelése):**

A `content` TEXT mező akár több száz sort is tartalmazhat. Egyszerre az egész betöltése pazarló és lassú mobilon. A backend 100 soros oldalakra osztja a tartalmat.

Backend endpoint: `GET /api/missions/{id}/content?page=0&size=100`

```json
// Response 200
{
  "missionId": "uuid",
  "missionName": "Változók leírása",
  "content": "## Bevezetés\n\nA változók...",
  "page": 0,
  "pageSize": 100,
  "totalLines": 247,
  "totalPages": 3,
  "hasNextPage": true,
  "hasPreviousPage": false
}
```

**Backend paginálási logika:**
1. Betölti a teljes `content` TEXT mezőt az adatbázisból
2. `String[] lines = content.split("\n", -1)` — `-1` flaggel az üres sorok megmaradnak
3. `totalPages = (int) Math.ceil((double) lines.length / pageSize)`
4. Adott oldal: `Arrays.copyOfRange(lines, page * pageSize, Math.min((page + 1) * pageSize, lines.length))`
5. Összefűzi: `String.join("\n", slice)` → visszaadja

**Megjegyzés:** MVP korlát — ha egy code block vagy táblázat a 100. sornál vágódik szét, a frontend markdown rendererben törött lehet a blokk. A "Load More" összefűzés után regenerálódik. Stage 2-ban javítható okosabb törési logikával.

**Frontend ContentMissionView logika:**
```typescript
const [loadedContent, setLoadedContent] = useState<string>("");
const [currentPage, setCurrentPage] = useState<number>(0);
const [hasMore, setHasMore] = useState<boolean>(false);
const [loadingMore, setLoadingMore] = useState<boolean>(false);

// Első betöltés: fetchPage(0) → setLoadedContent(resp.content), setHasMore(resp.hasNextPage)
// "Load More" kattintás: fetchPage(currentPage + 1) →
//   setLoadedContent(prev => prev + "\n" + resp.content)
//   setCurrentPage(p => p + 1)
//   setHasMore(resp.hasNextPage)
// A react-markdown az összefűzött loadedContent-et rendereli
// "Load More" gomb csak ha hasMore === true
// "Következő" gomb viselkedése: ld. alább (standalone vs. group mode)
```

**ContentMissionView — standalone vs. group mode:**

A `ContentMissionView` kétféle kontextusban jelenik meg — ezt a komponens egy `onComplete?: () => void` opcionális prop-on keresztül kezeli:

- **Group mode** (`onComplete` prop megadva): a "Következő" gomb megnyomásakor `onComplete()` hívódik → a Group Player elvégzi a `complete-step` API hívást és a következő al-missziót tölti be. A "Következő" gomb **mindig aktív** (az olvasó dönt, nem kell minden oldalt betölteni).

- **Standalone mode** (`onComplete` prop nincs): a komponens `missionId` és `starSystemId` prop-ot kap. A "Következő" gomb megnyomásakor:
  1. `GET /api/star-systems/{starSystemId}/with-missions` — betölti a star system items tömbjét (vagy ez már megvan a navigation state-ben)
  2. Megkeresi az aktuális `missionId`-hez tartozó `orderIndex`-et
  3. Megkeresi a következő standalone mission-t (`orderIndex > aktuális`, `type: "MISSION"`)
  4. Ha van → `navigate("/play/content/{nextMissionId}")` (vagy a típusától függő route-ra)
  5. Ha nincs több elem → **Teljesítés képernyő**: "Megvizsgáltad az összes anyagot ebben a csillagrendszerben!" felirat + "Vissza a csillagrendszerhez" gomb (`navigate("/star-systems/{starSystemId}")`)

  > **MVP egyszerűsítés**: A standalone CONTENT misszión a "következő" navigáció csak akkor müxik ha a star system már be van töltve (pl. a `navigation state`-ben átadva). Ha nem, csak a "Vissza" gomb jelenik meg.

---

#### FILL_IN_BLANK mission

- **Csak group-ban lehet** — standalone FILL_IN_BLANK nem megengedett
- A backend 400-at ad vissza ha a mission-nek nincs `groupId`-ja mentéskor

**Fill-in-blank adatmodellje: saját entitások, nem JSON TEXT**

A fill-in-blank tartalom külön entitásokban tárolódik. Ez biztosítja:
- A `isCorrect` mező **szerkezetileg lehetetlen** user-facing DTO-ba kerülni (nem kézi szűrés, hanem külön DTO osztály)
- Statisztikák lekérdezhetők Stage 2-ben (melyik opciót választják, hibaarány per blank)
- Nincs JSON parse/serialize bonyodalom, nincs race condition szöveg szintű frissítésnél
- A definíció módosítása tranzakcionálisan kezelhető (blank/option entitások törlése + újraírása `@Transactional`-ban)

**Entitások:**
- `FillInBlankDefinition` — a teljes feladat: template szöveg, passThreshold, FK a missionre (OneToOne)
- `FillInBlankBlank` — egy `{kulcs}` helyőrző: key, orderIndex, FK a definícióra
- `FillInBlankOption` — egy opciólehetőség: optionText, **isCorrect** (ez a mező soha nem kerül user DTO-ba), orderIndex, FK a blank-re
- `FillInBlankAttempt` — egy beküldési kísérlet: cadet, mission, score, passed, submittedAt
- `FillInBlankAnswerDetail` — egy blank kiértékelésének részlete: attempt, blank, selectedOption, correct

**Blank jelölő karakter — `[[blank_N]]` szintaxis:**

A `{blank_N}` szintaxis helyett `[[blank_N]]` (dupla szögletes zárójel) kerül alkalmazásra. Indok: ha az admin programozást tanít és kódot ír a szövegbe (pl. JavaScript: `const x = {value: 1}`), a kapcsos zárójel ütközik a blank jelölővel. A dupla szögletes zárójel (`[[...]]`) nem jelenik meg normál programozási kódban sem JavaScript-ben, sem Java-ban, sem Python-ban.

Regex detekcióhoz: `/\[\[(\w+)\]\]/g`

Példa templateText: `"A const változó [[blank_1]] kaphat értéket. A let változó [[blank_2]] kaphat értéket."`

**Admin UI flow:**
1. Admin textarea-ba írja a szöveget. A **"Blank hozzáadása" gomb** a szöveg végéhez fűzi a következő sorszámú `[[blank_N]]` jelölőt (maximum 5 blank engedélyezett) — az admin manuálisan is begépelheti bárhova
2. A frontend folyamatosan figyeli a textarea tartalmát (`onChange`), és minden detektált `[[blank_N]]` mintához automatikusan megjelenik egy szerkesztő panel a textarea alatt:
   - A panel fejlécén a blank neve (pl. `blank_1`)
   - Az opciók bevitele: **automatikus üres input mező** jelenik meg mindig a már kitöltött opciók után — ha az admin beírja és elhagyja (onBlur), az opció mentésre kerül a lokális state-be és megjelenik egy új üres input mező a következőhöz. Ha már 5 opció van, az üres input nem jelenik meg
   - Minden kitöltött opció sorában: a szöveg, pipa checkbox (helyes-e) + törlés ikon (X gomb)
   - A helyes jelölés: egy blank-ben több opció is lehet helyes (pl. `[[blank_2]]` ahol "egyszer" is helyes, "többször" is)
3. Ha az admin **törli a `[[blank_N]]` szöveget** a textarea-ból, az ahhoz tartozó szerkesztő panel és az összes opciója eltűnik — az admin felelőssége, nincs warning dialog
4. Ha az admin **átnevezi a blank kulcsát** (pl. `[[blank_1]]` → `[[blank_a]]`), az új kulcs új üres panelként jelenik meg; az eredeti `blank_1` panel és opcióinak state-je elvész
5. Az egész alján: `passThreshold` mező (0–100, null = nincs küszöb)
6. Mentéskor a frontend **csak azokat a blank-eket küldi el**, amelyek jelenleg detektálhatók a textarea-ban — törölt/átnevezett blank-ek nem kerülnek a request-be

**`POST /api/missions/{missionId}/fill-in-blank` request (admin, új definíció):**
```json
{
  "templateText": "A const változó [[blank_1]] kaphat értéket. A let változó [[blank_2]] kaphat értéket.",
  "passThreshold": 70,
  "blanks": [
    {
      "key": "blank_1",
      "orderIndex": 0,
      "options": [
        { "optionText": "egyszer",  "isCorrect": true,  "orderIndex": 0 },
        { "optionText": "többször", "isCorrect": false, "orderIndex": 1 },
        { "optionText": "soha",     "isCorrect": false, "orderIndex": 2 }
      ]
    },
    {
      "key": "blank_2",
      "orderIndex": 1,
      "options": [
        { "optionText": "egyszer",  "isCorrect": true,  "orderIndex": 0 },
        { "optionText": "többször", "isCorrect": true,  "orderIndex": 1 },
        { "optionText": "soha",     "isCorrect": false, "orderIndex": 2 }
      ]
    }
  ]
}
```

Ha már létezik definíció: `PUT /api/missions/{missionId}/fill-in-blank` — teljes felülírás, törli a meglévő blank/option entitásokat `@Transactional`-ban.

**User oldali UX — Mixed Pool megközelítés:**

A user a szöveget látja `[___]` blank slot-okkal (a `{blank_N}` jelölők helyén), alatta az összes opció minden blank-ből **összekeverve, véletlenszerű sorrendben**, kattintható chip/gomb formátumban.

- **Opció kiválasztása (automatikus cél):** User rákattint egy pool chip-re → az beugrik a szövegben az első üres blank slot-ba (balról jobbra). Az opció eltűnik a pool-ból.
- **Opció kiválasztása (célzott):** User előbb rákattint egy üres blank slot-ra a szövegben (a slot kiemelődik), majd rákattint egy pool chip-re → az a kiemelt slot-ba kerül.
- **Opció visszahelyezése:** User rákattint egy már kitöltött blank slot-ra → az opció visszakerül a pool aljára, a slot ismét üres lesz.
- **Opciók az adatmodellben blank-specifikusak**, de a user felé **közös pool-ként jelennek meg** — a user nem tudja melyik opció melyik blank-hez tartozik. A beküldéskor a backend bármely optionId-t elfogad bármely blank-hez; cross-blank beküldés `correct: false` eredményt kap (nem 400 hibát).
- **Submit:** "Beküldés" gomb aktív ha minden blank ki van töltve. Request: `{ "answers": { "blank_1": "uuid-opt-X", "blank_2": "uuid-opt-Y" } }` — optionId-k bármely blank-ből valók lehetnek.
- **Visszajelzés submit után:** Minden blank slot-on zöld ✓ / piros ✗ megjelenik; helytelen blank-eknél megjelennek a helyes opciók nevei.
- **Újrapróbálás:** Ha `passed: false` (és van `passThreshold`) → "Újra" gomb → blank slot-ok kiürülnek, opciók visszakerülnek a pool-ba véletlenszerű sorrendben.
- Ha nincs küszöb (`passThreshold: null`): mindig `passed: true`.

---

#### QUIZ mission
- Marad a jelenlegi implementáció
- **Refaktor szükséges**: a jelenlegi `QuizPlayer` két részre válik:
  - `QuizPlayerPage` — marad a meglévő standalone route-on (`/play/quiz/:id`)
  - `QuizPlayerComponent` — beilleszthető komponens, `missionId` + `onComplete(result)` callback propokkal
- A Group Player a `QuizPlayerComponent`-et használja

#### CODING mission
- Marad a jelenlegi implementáció (Stage 2-ben lesz teljesen kidolgozva)
- A group rendszerbe berakható

---

### Admin UX — Star System szerkesztő (átdolgozva)

**Navigation flow:**
1. `/admin/star-systems` → lista
2. Kattint egy star systemre → `/admin/star-systems/:id` — star system adatok + fa struktúra
3. "Mission szerkesztése" → `/admin/missions/:id` — mission edit oldal
4. Mentés / törlés után visszanavigál a star systemre:
   - Ha a mission group-ban van (`mission.groupId != null`): a backend a `MissionResponse`-ban visszaadja a `starSystemId`-t közvetlenül (denormalizálva), a frontend `navigate(/admin/star-systems/${mission.starSystemId})` hívással visszamegy — nem kell a `group.starSystem` láncon végigmenni

**Fa struktúra a star system edit oldalon:**
```
[Star System: JavaScript Alapjai]
  ├── [Group] Változók                          [↑][↓] [szerkeszt] [töröl]
  │     ├── CONTENT: Változók leírása     [↑][↓] [→] [szerkeszt] [töröl]
  │     ├── FILL_IN_BLANK: Kitöltős       [↑][↓] [→] [szerkeszt] [töröl]
  │     └── QUIZ: Ellenőrző kvíz          [↑][↓] [→] [szerkeszt] [töröl]
  │     └── [+ Hozzáadás]
  ├── [Group] String műveletek            [↑][↓] [szerkeszt] [töröl]
  │     └── ...
  ├── CODING: Standalone misszió          [↑][↓] [←] [szerkeszt] [töröl]
  └── [+ Új group létrehozása]
```

**Sorrend kezelés:**
- `[↑][↓]` nyilak: elem fel/le mozgatása (`orderIndex` swap a star systemben; `groupOrder` swap a group-on belül)
- `[→]` gomb group-on belüli misszión: kiveszi → standalone lesz (`groupOrder` null, `orderIndex` beállítva); FILL_IN_BLANK esetén backend `400` → snackbar hiba
- `[←]` gomb standalone misszión: felugró ablak → melyik group-ba kerüljön

**Reorder response — minimális payload:**
A sorrend-csere endpoint **csak az érintett két elem új orderIndex értékét** adja vissza. A backend elvégezte a swap-ot, a frontendnek csak a lokális state-t kell frissítenie:
```json
// PUT /api/mission-groups/{id}/reorder vagy PUT /api/missions/{id}/group-order
{ "updated": [ { "id": "uuid1", "orderIndex": 0 }, { "id": "uuid2", "orderIndex": 1 } ] }
```
Nem kell újratölteni az egész star systemet — a frontend az `updated[]` tömb alapján patcheli a saját state-jét.

**Group törlése:**
- Ha a group-ban FILL_IN_BLANK típusú misszió van: törlés **megtagadva `400 Bad Request`** — az admin a group törlése előtt manuálisan törölje a FILL_IN_BLANK missziót. Frontend snackbar: `"A csoport FILL_IN_BLANK missziót tartalmaz — töröld előbb."`
- CONTENT, QUIZ, CODING missionök standalone missionökké válnak. **OrderIndex kiosztás szabályai** (backend, egy tranzakcióban):
  1. A group `orderIndex` pozíciójától kezdve a missions csoporton belüli `groupOrder` sorrendje szerint kapnak sorszámot: első misszió `= group.orderIndex`, második `= group.orderIndex + 1`, stb.
  2. Minden eddig a group után álló standalone mission és group (amelynek `orderIndex > group.orderIndex`) eltolódik `+N`-nel (ahol N = a standalone-vá vált missziók száma)
  - **Példa:** group `orderIndex: 2`, benne 3 misszió (groupOrder 0, 1, 2). Törlés után: 3 új standalone misszió kapja az `orderIndex` 2, 3, 4 értékeket. Minden elem, amelynek korábban `orderIndex ≥ 3` volt, kap +3-at → az egész rendezett lista konzisztens marad

**Mission hozzáadása group-hoz — conflict kezelés:**
Ha a kiválasztott mission már másik group-ban van, a backend `ResourceConflictException`-t dob (409), amelynek `data` mezője tartalmazza az ütköző group nevét és ID-ját:
```json
{
  "status": 409, "error": "Conflict",
  "message": "A misszió már hozzá van rendelve egy másik csoporthoz",
  "data": { "conflictingGroupId": "uuid", "conflictingGroupName": "String műveletek" }
}
```
A frontend snackbar-ban mutatja: `"Ez a misszió már a 'String műveletek' csoporthoz tartozik."`

---

### User oldal — Star System Detail (átdolgozva)

**Progress-aware betöltés:**

A `StarSystemDetailPage` betöltésekor két párhuzamos kérés indul:
1. `GET /api/star-systems/{id}/with-missions` — items[] tömb
2. A kapott items-ből kinyert összes `GROUP` elem ID-ja → `Promise.all` párhuzamos `GET /api/group-progress/{groupId}` hívások — minden group-ra külön

A progress eredmények egy `Map<groupId, GroupProgressResponse | "NOT_STARTED">` state-ben tárolódnak. A group-on belüli missziókon (FillInBlank, Content, Quiz) nincs külön progress állapot — ezek a Group Player-en belül kezeltek.

**Lista:** A backend `items[]` tömbje alapján, sorrendben, **progress badge-dzsel**:
- `type: "GROUP"` → group neve + missionök száma + progress badge + akciógomb:
  - `NOT_STARTED` (404 a progress GET-nél): "Kezdd el" gomb (kék) → `/play/group/:groupId`
  - `IN_PROGRESS` (`completed: false`): "Folytatás" gomb (sárga) + `"N / M lépés"` indicator (ahol N = `completedMissionIds.length`, M = `totalMissions`)
  - `COMPLETED` (`completed: true`): zöld ✓ badge + "Újra" gomb (szürke) → `/play/group/:groupId`
- `type: "MISSION"` standalone → neve + típus ikon + "Start" gomb (progress tracking nélkül — Stage 2-ban bővíthető)

**Routing:**
- Group indítás/folytatás: `/play/group/:groupId`
- Standalone CONTENT: `/play/content/:missionId`
- Standalone QUIZ: `/play/quiz/:missionId` (meglévő)
- Standalone CODING: Stage 2

**TypeScript kiegészítés (`types/groupProgress.ts`):**
```typescript
type GroupProgressStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED"

// Derived type a megjelenítéshez
interface GroupDisplayProgress {
  status: GroupProgressStatus;
  completedCount: number;   // 0 ha NOT_STARTED
  totalCount: number;       // a group missionjeinek száma (items[].missions.length)
}
```

---

### User oldal — Group Player (`/play/group/:groupId`)

**Első betöltés:**
1. `GET /api/mission-groups/{groupId}/missions` — betölti a group missziólistáját (a `starSystemId` is benne van a response-ban, a back navigációhoz)
2. `GET /api/group-progress/{groupId}` — progress lekérése
   - Ha `404 ResourceNotFoundException`: `POST /api/group-progress/{groupId}/start` → létrehozza a rekordot → újra GET (vagy a POST válasza elég)
   - Ha `409 ResourceConflictException` a POST-nál: GET-tel olvassa be a meglévőt

**Al-misszió léptetés (ID alapú, robusztus):**
- Lépésjelző a tetején (pl. `2 / 4`) — `completedMissionIds.length + 1 / missions.length`
- A Group Player a `nextMissionId` alapján rendeli hozzá a renderelendő komponenst
- "Következő" gomb → `POST /api/group-progress/{groupId}/complete-step` → backend visszaadja a frissített progresst `nextMissionId`-val → frontend lép
- **Robusztusság:** Az ID-alapú progress nem törik ha az admin módosít a csoporton (pl. új misszió a végére), mert a már teljesített lépések UUID-k maradnak érvényesek

**Back navigation:**
- "Vissza" gomb: `navigate(/star-systems/${groupMissions.starSystemId})` — a group missions response tartalmazza a `starSystemId`-t

**CONTENT:** markdown megjelenítés oldalazással (Load More), "Következő" = `complete-step` hívás
**FILL_IN_BLANK:** kitöltés, submit → ha `passed: true` → `complete-step` hívás; ha `passed: false` → hibajelzés, újrapróbálás
**QUIZ:** `QuizPlayerComponent` renderelése, `onComplete` callback → `complete-step` hívás

**Browser Back gomb viselkedése — elvárt és dokumentált:**

Ha a user a böngésző vissza gombjával navigál ki a Group Player-ből:

1. A React komponens unmount-ol, a memóriában lévő state elvész
2. A progress **megmarad az adatbázisban** — a `MissionGroupStepCompletion` rekordok érvényesek
3. Ha a user visszatér a `/play/group/:groupId` route-ra:
   - `GET /group-progress/{groupId}` → visszaadja a legutóbbi állapotot `nextMissionId`-val
   - A Group Player ugyanattól a lépéstől folytatódik ahol abbahagyta — **ez a helyes viselkedés, nem kell "session resume" logika**

4. **FILL_IN_BLANK speciális eset** — a user kitöltötte a feladatot (`passed: true` attempt létrejött az adatbázisban), visszanavigált, majd visszatért:
   - A `FillInBlankAttempt` táblában él a `passed: true` rekord
   - A Group Player visszatéréskor újra a FILL_IN_BLANK lépésnél nyílik meg
   - A `FillInBlankView` betöltéskor lekérdezi az utolsó attempt-et: `GET /api/missions/{missionId}/fill-in-blank/last-attempt`
   - Ha a legutóbbi attempt `passed: true` → megjelenik egy banner: **"Ezt a feladatot már sikeresen teljesítetted."** + "Következő →" gomb (amely meghívja a `complete-step`-et)
   - Ha nincs attempt vagy `passed: false` → a feladatot újra el kell végezni
   - Ez az extra endpoint (`GET .../last-attempt`) **MVP szükséglet**, mert visszanavigálás után különben mindig újra kellene tölteni a feladatot

   > **Backend endpoint:** `GET /api/missions/{missionId}/fill-in-blank/last-attempt` — `mission:start` permission. Response: `{ "passed": boolean, "percentage": number, "submittedAt": string }` vagy 404 ha nincs attempt

---

### Content Creator szerepkör

**Backend role:** `ROLE_CONTENT_CREATOR` — seed-elve a DataInitializerben

**Permissions:**
`starsystem:create`, `starsystem:edit`, `starsystem:read`,
`mission:create`, `mission:edit`, `mission:read`,
`group:create`, `group:edit`, `group:delete`, `group:read`

**Megjegyzés:** A `StarSystem`, `Mission` és `MissionGroup` entitáson van `owner` (Cadet) mező. A content creator saját tartalmait az `/api/star-systems/my-systems`, `/api/missions/my-missions`, `/api/mission-groups/my-groups` endpointokon éri el. Az ownership ellenőrzés a service rétegben történik.

**Frontend:** Admin sidebar permission-aware. Content creator csak "Csillagrendszerek", "Missziók" és "Csoportok" tab-ot lát.

---

## Stage 2 — Fejlettebb funkciók (külön PR, később)

### CODING mission teljes user flow
- Fájl létrehozás, törlés, átnevezés az editorban
- Admin előre beállíthatja a fájlstruktúrát

### Mobile Coding mission (új típus)
- Kártyaalapú kódírás, drag-and-drop mobilon

### Blockly / Vizuális programozás
- Google Blockly integrálása, Scratch-szerű blokkok

### Fill-in-blank statisztikák (admin dashboard)
- Melyik opciót választják leggyakrabban
- Hibaarány per blank, per misszió (FillInBlankAnswerDetail alapján lekérdezhető)

---

## Architekturális terv — Stage 1

### Backend — Entitások

---

#### `MissionGroup`
| Mező | Típus | Leírás |
|---|---|---|
| `id` | UUID | PK |
| `name` | String | Group neve |
| `description` | String (nullable) | Rövid leírás |
| `starSystem` | ManyToOne → StarSystem | FK |
| `owner` | ManyToOne → Cadet | Létrehozó — egyben a `createdBy` (content creator szűréshez) |
| `updatedBy` | ManyToOne → Cadet (nullable) | Utolsó módosítást végző cadet |
| `orderIndex` | int | Sorrend a star systemben (közös a standalone missziókkal) |
| `createdAt` | Instant | `@CreationTimestamp` |
| `updatedAt` | Instant | `@UpdateTimestamp` |

---

#### `Mission` — módosított/új mezők
| Mező | Típus | Leírás |
|---|---|---|
| `group` | ManyToOne → MissionGroup **(nullable)** | Melyik group-hoz tartozik |
| `groupOrder` | Integer **(nullable)** | Sorrend a group-on belül; ha NULL → standalone |
| `orderIndex` | Integer **(nullable, egységesítve)** | Sorrend a star systemben; ha NULL → group-ban van |
| `content` | TEXT **(nullable)** | Markdown szöveg CONTENT típusnál |

> **Megjegyzés:** A `fillInBlankData` TEXT mező **törlendő** — a fill-in-blank adatokat külön entitások tárolják (ld. lent).
> A `MissionResponse` DTO tartalmaz `starSystemId`-t **közvetlenül** (denormalizálva), hogy a navigáció ne igényeljen láncos lekérdezést.

---

#### `FillInBlankDefinition`
| Mező | Típus | Leírás |
|---|---|---|
| `id` | UUID | PK |
| `mission` | OneToOne → Mission | FK, unique |
| `templateText` | TEXT | A szöveg `{kulcs}` jelöléssel |
| `passThreshold` | Integer (nullable) | 0–100, null = nincs küszöb |
| `createdAt` | Instant | `@CreationTimestamp` |
| `updatedAt` | Instant | `@UpdateTimestamp` |

#### `FillInBlankBlank`
| Mező | Típus | Leírás |
|---|---|---|
| `id` | UUID | PK |
| `definition` | ManyToOne → FillInBlankDefinition | FK |
| `blanksKey` | String | A template kulcs (pl. `"blank_1"`) |
| `orderIndex` | int | Megjelenítési sorrend |

#### `FillInBlankOption`
| Mező | Típus | Leírás |
|---|---|---|
| `id` | UUID | PK |
| `blank` | ManyToOne → FillInBlankBlank | FK |
| `optionText` | String | Az opció szövege |
| `isCorrect` | boolean | **Kizárólag ebben az entitásban él. User DTO-ba soha nem kerül.** |
| `orderIndex` | int | Megjelenítési sorrend |

#### `FillInBlankAttempt`
| Mező | Típus | Leírás |
|---|---|---|
| `id` | UUID | PK |
| `cadet` | ManyToOne → Cadet | FK |
| `mission` | ManyToOne → Mission | FK |
| `score` | int | Helyes válaszok száma |
| `maxScore` | int | Összes blank száma |
| `percentage` | int | 0–100 |
| `passed` | boolean | `percentage >= passThreshold` (vagy ha threshold null, mindig true) |
| `submittedAt` | Instant | Beküldés időpontja |

#### `FillInBlankAnswerDetail`
| Mező | Típus | Leírás |
|---|---|---|
| `id` | UUID | PK |
| `attempt` | ManyToOne → FillInBlankAttempt | FK |
| `blank` | ManyToOne → FillInBlankBlank | FK |
| `selectedOption` | ManyToOne → FillInBlankOption **(nullable)** | Amit a user választott (null ha üresen hagyta) |
| `correct` | boolean | A választott opció helyes volt-e |

---

#### `MissionGroupProgress`
| Mező | Típus | Leírás |
|---|---|---|
| `id` | UUID | PK |
| `cadet` | ManyToOne → Cadet | FK |
| `group` | ManyToOne → MissionGroup | FK |
| `nextMissionId` | UUID (nullable) | A következő elvégzendő misszió ID-ja; null ha a group teljesítve |
| `completed` | boolean | Az egész group teljesítve |
| `startedAt` | Instant | Első megnyitás |
| `lastUpdatedAt` | Instant | Utolsó lépés időpontja |
| `completedAt` | Instant (nullable) | Teljesítés időpontja |

> **Unique constraint:** `(cadet_id, group_id)` — egy usernek egy group-ra csak egy progress rekord lehet

#### `MissionGroupStepCompletion` (join tábla a completedMissionIds helyett)
| Mező | Típus | Leírás |
|---|---|---|
| `id` | UUID | PK |
| `progress` | ManyToOne → MissionGroupProgress | FK |
| `mission` | ManyToOne → Mission | FK |
| `completedAt` | Instant | Lépés teljesítésének időpontja |

> **Unique constraint:** `(progress_id, mission_id)` — idempotens: kétszeri "Következő" kattintás sem hoz létre duplikált rekordot, az adatbázis szinten ki van zárva.

**Miért join tábla a JSON TEXT helyett:**
- **Race condition eliminálva:** két párhuzamos `complete-step` request `INSERT INTO ... ON CONFLICT DO NOTHING` — az egyik sikeres, a másik némán eldobódik, adat nem vész el és nem duplikálódik
- **Statisztika Stage 2-ben:** `SELECT mission_id, COUNT(*) FROM step_completions GROUP BY mission_id` — megmondja melyik lépésnél hagyják abba legtöbben
- **Rendezés törésállósága:** ha az admin átrendezi a group missionjeit, a UUID-alapú step completion rekordok érvényesek maradnak
- **`nextMissionId` kiszámítása:** a service betölti a group misszióit groupOrder szerint, az első amelynek id-ja NEM szerepel a step_completions-ben — ez az aktuális lépés

---

### Backend — Új MissionType értékek
- `CONTENT`
- `FILL_IN_BLANK`

---

### Backend — Permission rendszer (group:* kategória)

Az eddigi `mission:*` kategória nem fedi le a `MissionGroup` entitást — az önálló entitás, önálló permission kategóriát kap.

**Új permissionök (DataInitializerben seed-elni):**
| Permission | Leírás |
|---|---|
| `group:create` | MissionGroup létrehozása |
| `group:edit` | MissionGroup szerkesztése, sorrend módosítása, mission hozzáadás/eltávolítás |
| `group:delete` | MissionGroup törlése |
| `group:read` | MissionGroup és tartalmának olvasása |

**Hozzárendelés szerepkörökhöz (DataInitializer):**
| Role | group permissionök |
|---|---|
| `ROLE_ADMIN` | `group:create`, `group:edit`, `group:delete`, `group:read` |
| `ROLE_CONTENT_CREATOR` | `group:create`, `group:edit`, `group:delete`, `group:read` (saját group-jain, service szintű ownership check) |
| `ROLE_CADET` | `group:read` |

**Kontroller annotáció minta:**
```java
@PreAuthorize("hasAuthority('group:create')")
@PreAuthorize("hasAuthority('group:edit')")
@PreAuthorize("hasAuthority('group:delete')")
@PreAuthorize("hasAuthority('group:read')")
```

**Frontend sidebar permission check:**
```typescript
// AdminLayout sidebar renderelési feltétel
const canManageGroups = permissions.includes("group:create") || permissions.includes("group:read");
// Ha true → "Csoportok" menüpont megjelenik
```

---

### DB Migration stratégia (Flyway)

A projekt jelenleg `spring.jpa.hibernate.ddl-auto=update`-et (vagy `create-drop`-ot) használhat. Ez fejlesztésre elfogadható, de production-ban nem biztonságos: `NOT NULL → nullable` oszlopváltozás nem végrehajtható automatikusan meglévő adatokon, tábla törlés sincs automatikusan, és a schema divergencia nyomon nem követhető.

A Stage 1 változtatások érintik a meglévő sémát (pl. `orderInSystem` → `orderIndex` nullable, `fillInBlankData` eltávolítása), ezért Flyway bevezetése szükséges.

**Bevezetés lépései:**

**1. `pom.xml` dependency:**
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```
Spring Boot automatikusan lefuttatja Flyway-t induláskor ha `flyway-core` a classpath-on van.

**2. `application.properties` módosítás:**
```properties
# Flyway bevezetése után ddl-auto=validate: Hibernate ellenőrzi de nem módosítja a sémát
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

**3. Migration fájlok helye:**
```
backend/src/main/resources/db/migration/
├── V1__baseline.sql          ← meglévő séma (manuálisan leírva, ha az adatbázis már tartalmaz adatot)
└── V2__stage1_mobile.sql     ← Stage 1 változtatások
```

**V1 (baseline) kezelési stratégiák:**
- Ha a fejlesztői adatbázis **docker compose-zal minden indulásnál újraépül** (nincs perzisztens volume): V1 nem szükséges, a Hibernate `create-drop` elegendő fejlesztésre, Flyway csak production-ban fut → `spring.flyway.baseline-on-migrate=true` a production properties-ben
- Ha **perzisztens fejlesztői adatbázis** van: V1__baseline.sql létrehozása szükséges a meglévő táblák leírásával, `flyway baseline` parancs futtatása egyszer, ezután Flyway trackeli a változásokat

**V2__stage1_mobile.sql tartalma (mintavázlat):**
```sql
-- orderInSystem átnevezése orderIndex-re és nullable-vé tétel
ALTER TABLE missions RENAME COLUMN order_in_system TO order_index;
ALTER TABLE missions ALTER COLUMN order_index DROP NOT NULL;

-- fillInBlankData törlése (új entitások váltják fel)
ALTER TABLE missions DROP COLUMN IF EXISTS fill_in_blank_data;

-- group FK és groupOrder hozzáadása
ALTER TABLE missions ADD COLUMN group_id UUID REFERENCES mission_groups(id);
ALTER TABLE missions ADD COLUMN group_order INTEGER;
ALTER TABLE missions ADD COLUMN content TEXT;

-- MissionGroup tábla
CREATE TABLE mission_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    star_system_id UUID NOT NULL REFERENCES star_systems(id),
    owner_id UUID NOT NULL REFERENCES cadets(id),
    updated_by_id UUID REFERENCES cadets(id),
    order_index INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Fill-in-blank entitások
CREATE TABLE fill_in_blank_definitions ( ... );
CREATE TABLE fill_in_blank_blanks ( ... );
CREATE TABLE fill_in_blank_options ( ... );
CREATE TABLE fill_in_blank_attempts ( ... );
CREATE TABLE fill_in_blank_answer_details ( ... );

-- Group progress entitások
CREATE TABLE mission_group_progresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cadet_id UUID NOT NULL REFERENCES cadets(id),
    group_id UUID NOT NULL REFERENCES mission_groups(id),
    next_mission_id UUID REFERENCES missions(id),
    completed BOOLEAN NOT NULL DEFAULT false,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    completed_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (cadet_id, group_id)
);

CREATE TABLE mission_group_step_completions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    progress_id UUID NOT NULL REFERENCES mission_group_progresses(id),
    mission_id UUID NOT NULL REFERENCES missions(id),
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (progress_id, mission_id)
);
```

---

### Backend — `GET /api/auth/me` bővítés (generikus profil endpoint)

A jelenlegi `/api/auth/me` csak `username` és `roles` mezőket ad vissza. A permission-aware frontend sidebar és jövőbeli feature gating miatt a válasz bővítendő.

**`GET /api/auth/me`** — Bearer token szükséges
```json
// Response 200 — UserProfileResponse (ÚJ DTO)
{
  "id": "uuid",
  "username": "badzso",
  "email": "norbert@example.com",
  "fullName": "Ujj Norbert",
  "avatarUrl": null,
  "roles": ["ROLE_ADMIN"],
  "permissions": [
    "mission:read", "mission:create", "mission:edit", "mission:delete",
    "group:create", "group:edit", "group:delete", "group:read",
    "starsystem:read", "starsystem:create",
    "user:read", "role:read",
    "logs:read"
  ]
}
```

**Backend DTO — `UserProfileResponse.java`:**
```java
@Data
@Builder
public class UserProfileResponse {
    private UUID id;
    private String username;
    private String email;
    private String fullName;
    private String avatarUrl;
    private List<String> roles;
    private List<String> permissions;  // flat lista: minden role összes permissionje, deduplikálva
}
```

**Backend service logika** (`AuthService.getMe()`):
```java
public UserProfileResponse getMe(String username) {
    Cadet cadet = cadetRepository.findByUsername(username).orElseThrow(...);
    Set<String> permissions = cadet.getRoles().stream()
        .flatMap(role -> role.getPermissions().stream())
        .map(Permission::getName)
        .collect(Collectors.toSet());  // Set → deduplikálás automatikus
    
    return UserProfileResponse.builder()
        .id(cadet.getId())
        .username(cadet.getUsername())
        .email(cadet.getEmail())
        .fullName(cadet.getFullName())
        .avatarUrl(cadet.getAvatarUrl())
        .roles(cadet.getRoles().stream().map(Role::getName).toList())
        .permissions(new ArrayList<>(permissions))
        .build();
}
```

**Frontend — `types/auth.ts` módosítás:**
```typescript
export interface User {
  id: string;
  username: string;
  email?: string;
  fullName?: string;
  avatarUrl?: string | null;
  roles: string[];
  permissions: string[];  // ← ÚJ, /auth/me-ből töltődik fel
  exp?: number;
}
```

**Frontend — `AuthContext.tsx` módosítás (a setState blokkban):**
```typescript
setState(prev => ({
  ...prev,
  user: {
    ...prev.user!,
    id: response.data.id,
    username: response.data.username,
    email: response.data.email,
    fullName: response.data.fullName,
    avatarUrl: response.data.avatarUrl,
    roles: response.data.roles,
    permissions: response.data.permissions,  // ← ÚJ
  },
  isLoading: false,
}));
```

**Generikus frontend használat — `hasPermission` minden feature gating-hez:**
```typescript
// AuthContextType-ban:
hasPermission: (permission: string) => boolean
// Implementáció:
const hasPermission = (p: string) => state.user?.permissions.includes(p) ?? false

// Használat a komponensekben:
const { hasPermission } = useAuth()
if (hasPermission("group:create")) { ... }
if (hasPermission("mission:edit")) { ... }
// Soha ne role-check: user?.roles.includes("ROLE_ADMIN") — ezt CSAK auth guard-okhoz használjuk
```

> **Elv:** UI elemek láthatóságát mindig `hasPermission()` dönti el, nem role. Így a `ROLE_CONTENT_CREATOR` automatikusan a saját permission-set alapján látja a megfelelő menüpontokat, és ha jövőben egy új role-t hozunk létre, nem kell a frontend kódot módosítani.

---

### Backend — Összes új endpoint + DTO-k

---

#### MissionGroup CRUD

**`POST /api/mission-groups`** — `group:create`
```json
// Request
{ "name": "JavaScript Változók", "description": "...", "starSystemId": "uuid", "orderIndex": 1 }

// Response 201
{ "id": "uuid", "name": "JavaScript Változók", "description": "...", "starSystemId": "uuid",
  "orderIndex": 1, "missions": [], "createdAt": "...", "updatedAt": "...",
  "ownerId": "uuid", "updatedById": null }
```

**`GET /api/mission-groups/{id}`** — `group:read`
```json
// Response 200
{ "id": "uuid", "name": "...", "starSystemId": "uuid", "orderIndex": 1,
  "createdAt": "...", "updatedAt": "...", "ownerId": "uuid", "updatedById": "uuid",
  "missions": [
    { "id": "uuid", "name": "...", "missionType": "CONTENT",       "groupOrder": 0 },
    { "id": "uuid", "name": "...", "missionType": "FILL_IN_BLANK", "groupOrder": 1 }
  ]
}
```

**`PUT /api/mission-groups/{id}`** — `group:edit`
```json
// Request
{ "name": "Új név", "description": "..." }
// Response 200 — MissionGroupResponse (mint fent, updatedById frissítve)
```

**`DELETE /api/mission-groups/{id}`** — `group:delete`
```
// Response 204 — CONTENT/QUIZ/CODING missionök standalone-ná válnak
// Response 400 — ha FILL_IN_BLANK misszió van:
{ "error": "GROUP_HAS_FILL_IN_BLANK", "message": "Előbb mozgasd vagy töröld a fill-in-blank missiont" }
```

**`POST /api/mission-groups/{id}/missions`** — `group:edit` — mission hozzáadása
```json
// Request
{ "missionId": "uuid", "groupOrder": 2 }
// Response 200 — MissionGroupResponse
// Response 409 (ResourceConflictException) — ha a mission már másik group-ban van:
{ "status": 409, "error": "Conflict",
  "message": "A misszió már hozzá van rendelve egy másik csoporthoz",
  "data": { "conflictingGroupId": "uuid", "conflictingGroupName": "String műveletek" } }
```

**`DELETE /api/mission-groups/{id}/missions/{missionId}`** — `group:edit` — mission eltávolítása (standalone lesz)
```
// Response 204 — mission orderIndex értéket kap, groupOrder null lesz, group FK null lesz
// Response 400 — ha FILL_IN_BLANK:
{ "error": "FILL_IN_BLANK_REQUIRES_GROUP", "message": "A fill-in-blank misszió nem lehet standalone" }
```

**`PUT /api/mission-groups/{id}/reorder`** — `group:edit` — group sorrendje a star systemben
```json
// Request: { "direction": "up" }  // vagy "down"
// Response 200 — csak az érintett két elem:
{ "updated": [ { "id": "uuid1", "orderIndex": 0 }, { "id": "uuid2", "orderIndex": 1 } ] }
```

**`PUT /api/missions/{id}/group-order`** — `group:edit` — misszió sorrendje group-on belül
```json
// Request: { "direction": "up" }  // vagy "down"
// Response 200 — csak az érintett két elem:
{ "updated": [ { "id": "uuid1", "groupOrder": 0 }, { "id": "uuid2", "groupOrder": 1 } ] }
```

**`PUT /api/missions/{id}/reorder`** — `mission:edit` — standalone misszió sorrendje a star systemben
```json
// Request: { "direction": "up" }  // vagy "down"
// Response 200 — csak az érintett két elem (mindkét standalone mission vagy group):
{ "updated": [ { "id": "uuid1", "orderIndex": 0 }, { "id": "uuid2", "orderIndex": 1 } ] }
// Response 400 — ha a mission group-ban van (groupId != null)
```
> **Megjegyzés:** Ez az endpoint a star systemen belüli standalone missziók és group-ok közötti sorrend kezeléséhez szükséges. Az orderIndex-ek a star systemben lévő összes elem (group + standalone) között értelmezendők. A swap logika: megkeresi a szomszédos elemet (group vagy standalone mission) a `orderIndex ± 1` pozícióban, majd felcseréli a két elem orderIndex értékét.

**`GET /api/mission-groups/my-groups`** — `group:read` — saját group-ok (content creator)
```json
// Response 200
[{ "id": "uuid", "name": "...", "starSystemId": "uuid", "orderIndex": 0, "missions": [...] }]
```

---

#### Fill-in-blank (admin)

**`POST /api/missions/{missionId}/fill-in-blank`** — `mission:edit` — definíció létrehozása
```
// Request: ld. fentebb (blanks tömb, templateText, passThreshold)
// Response 201 — FillInBlankAdminResponse (isCorrect benne van)
// Response 400 — ha a mission nem FILL_IN_BLANK típus
// Response 409 — ha már létezik definíció a missionhöz (használd PUT-ot)
```

**`PUT /api/missions/{missionId}/fill-in-blank`** — `mission:edit` — teljes felülírás
```
// Request: azonos a POST-tal
// Response 200 — FillInBlankAdminResponse
// Logika: @Transactional — törli az összes meglévő FillInBlankBlank + FillInBlankOption entitást,
//         majd újraírja az egészet a kért adatokkal
```

**`GET /api/missions/{missionId}/fill-in-blank/admin`** — `mission:edit` — admin nézet
```json
// Response 200
{
  "id": "uuid-def", "missionId": "uuid", "templateText": "...", "passThreshold": 70,
  "blanks": [
    { "id": "uuid-blank-1", "key": "blank_1", "orderIndex": 0,
      "options": [
        { "id": "uuid-opt-1", "optionText": "egyszer",  "isCorrect": true,  "orderIndex": 0 },
        { "id": "uuid-opt-2", "optionText": "többször", "isCorrect": false, "orderIndex": 1 }
      ]
    }
  ]
}
// Response 404 — ha nincs definíció
```

---

#### Fill-in-blank (user)

**`GET /api/missions/{missionId}/fill-in-blank`** — `mission:read` — user nézet
```json
// Response 200 — isCorrect NÉLKÜL
{
  "missionId": "uuid", "templateText": "...", "passThreshold": 70,
  "blanks": [
    { "id": "uuid-blank-1", "key": "blank_1", "orderIndex": 0,
      "options": [
        { "id": "uuid-opt-1", "optionText": "egyszer",  "orderIndex": 0 },
        { "id": "uuid-opt-2", "optionText": "többször", "orderIndex": 1 }
      ]
    }
  ]
}
```

**`POST /api/missions/{missionId}/submit-fill-blank`** — `mission:start`
```json
// Request — optionId-kat küld, nem szövegeket (manipuláció ellen)
{
  "answers": {
    "blank_1": "uuid-opt-1",
    "blank_2": "uuid-opt-4"
  }
}

// Response 200
{
  "attemptId": "uuid",
  "score": 1, "maxScore": 2, "percentage": 50,
  "passed": false, "passThreshold": 70,
  "results": [
    { "blankKey": "blank_1", "correct": true,  "selectedOptionText": "egyszer",   "correctOptionTexts": ["egyszer"] },
    { "blankKey": "blank_2", "correct": false, "selectedOptionText": "egyszer",   "correctOptionTexts": ["egyszer", "többször"] }
  ]
}

// Response 400 — ha a mission nem FILL_IN_BLANK típus
// Response 400 — ha optionId egyáltalán nem létezik az adatbázisban (teljesen ismeretlen UUID)
// FONTOS: cross-blank beküldés (pl. blank_2 opciója kerül blank_1-hez) NEM 400, hanem correct: false
```

---

#### Group Progress (user)

**`GET /api/group-progress/{groupId}`** — `mission:start` — progress lekérése
```json
// Response 200 — ha létezik progress rekord
{
  "groupId": "uuid",
  "nextMissionId": "uuid-mission-1",
  "completedMissionIds": ["uuid-mission-0"],
  "completed": false,
  "startedAt": "2026-04-09T10:00:00Z",
  "lastUpdatedAt": "2026-04-09T10:05:00Z",
  "completedAt": null,
  "totalMissions": 3
}

// Response 404 (ResourceNotFoundException) — ha nincs progress rekord
// Frontend reagálás: POST /api/group-progress/{groupId}/start
```

**`POST /api/group-progress/{groupId}/start`** — `mission:start` — progress rekord létrehozása
```json
// Request — üres body
// Response 201 — GroupProgressResponse (nextMissionId = az első misszió id-ja, completedMissionIds=[])
// Response 409 (ResourceConflictException) — ha már létezik progress rekord
// Frontend reagálás 409-re: GET-tel olvassa be a meglévőt
```

**`POST /api/group-progress/{groupId}/complete-step`** — `mission:start`
```json
// Request — üres body

// Backend logika:
// 1. Betölti a progress rekordot (404 ha nincs)
// 2. Meghatározza az aktuális missziót a nextMissionId alapján
// 3. Ha missionType == FILL_IN_BLANK:
//      Ellenőrzi: van-e FillInBlankAttempt ahol cadet=current AND mission=current AND passed=true
//      Ha nincs → 400 Bad Request
// 4. INSERT INTO mission_group_step_completions (progress_id, mission_id) ON CONFLICT DO NOTHING
// 5. Kiszámolja az új nextMissionId-t: group missionjei groupOrder szerint,
//    az első amelynek id-ja NEM szerepel a step_completions-ben
// 6. Ha nincs ilyen → completed=true, completedAt=now(), nextMissionId=null
// 7. Menti és visszaadja a frissített progress rekordot

// Response 200 — GroupProgressResponse frissített állapottal
// Response 400 — { "error": "FILL_IN_BLANK_NOT_PASSED",
//                   "message": "Ezt a feladatot még nem teljesítetted sikeresen." }
```

---

#### Content pagination

**`GET /api/missions/{id}/content`** — `mission:read`

Query paraméterek: `page` (default: 0), `size` (default: 100, max: 500)
```json
// Response 200
{
  "missionId": "uuid",
  "missionName": "Változók leírása",
  "content": "## Bevezetés\n\nA változók...",
  "page": 0,
  "pageSize": 100,
  "totalLines": 247,
  "totalPages": 3,
  "hasNextPage": true,
  "hasPreviousPage": false
}
// Response 400 — ha a mission nem CONTENT típus
// Response 404 — ha a mission nem létezik
```

---

#### Star System with-missions bővítés

**`GET /api/star-systems/{id}/with-missions`** — `starsystem:read` — **rendezett items tömb**
```json
// Response 200
{
  "id": "uuid",
  "name": "JavaScript Alapjai",
  "description": "...",
  "iconUrl": "...",
  "items": [
    {
      "type": "GROUP",
      "id": "uuid-group-1",
      "name": "Változók",
      "orderIndex": 0,
      "missions": [
        { "id": "uuid", "name": "Változók leírása",  "missionType": "CONTENT",       "groupOrder": 0 },
        { "id": "uuid", "name": "Kitöltős feladat",  "missionType": "FILL_IN_BLANK", "groupOrder": 1 },
        { "id": "uuid", "name": "Kvíz",              "missionType": "QUIZ",          "groupOrder": 2 }
      ]
    },
    {
      "type": "MISSION",
      "id": "uuid-mission-5",
      "name": "Standalone CODING",
      "missionType": "CODING",
      "difficulty": "EASY",
      "orderIndex": 1
    }
  ]
}
```
A backend az összes group és standalone mission `orderIndex` alapján rendezve adja vissza. A frontend TypeScript-ben discriminated union-nal (`type: "GROUP" | "MISSION"`) kezeli.

**Backend DTO osztályok (Jackson polimorfizmus):**

```java
// Alap interface — Jackson tudja szétválasztani type mező alapján
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = StarSystemGroupItemDTO.class,   name = "GROUP"),
    @JsonSubTypes.Type(value = StarSystemMissionItemDTO.class, name = "MISSION")
})
public abstract class StarSystemItemDTO {
    public abstract String getId();
    public abstract String getName();
    public abstract int getOrderIndex();
}

@Data @Builder
public class StarSystemGroupItemDTO extends StarSystemItemDTO {
    private String id;
    private String name;
    private int orderIndex;
    private List<MissionInGroupResponse> missions;
    // type = "GROUP" — Jackson automatikusan beírja
}

@Data @Builder
public class StarSystemMissionItemDTO extends StarSystemItemDTO {
    private String id;
    private String name;
    private int orderIndex;
    private String missionType;
    private String difficulty;
    // type = "MISSION"
}

@Data @Builder
public class StarSystemDetailResponse {
    private String id;
    private String name;
    private String description;
    private String iconUrl;
    private List<StarSystemItemDTO> items;  // vegyes lista, rendezve orderIndex szerint
}
```

**Backend service metódus (`StarSystemService.getWithMissions`):**

```java
public StarSystemDetailResponse getWithMissions(UUID starSystemId) {
    StarSystem ss = starSystemRepository.findById(starSystemId)
        .orElseThrow(() -> new ResourceNotFoundException("StarSystem", "id", starSystemId));

    // 1. Összes csoport a star systemhez (eagerly fetch-eli a missions listát)
    List<MissionGroup> groups = missionGroupRepository
        .findByStarSystemIdOrderByOrderIndex(starSystemId);

    // 2. Standalone missziók (groupId IS NULL), rendezve
    List<Mission> standalone = missionRepository
        .findByStarSystemIdAndGroupIsNullOrderByOrderIndex(starSystemId);

    // 3. Merge + rendezés
    List<StarSystemItemDTO> items = new ArrayList<>();
    for (MissionGroup g : groups) {
        List<MissionInGroupResponse> mInGroup = g.getMissions().stream()
            .sorted(Comparator.comparingInt(Mission::getGroupOrder))
            .map(MissionInGroupResponse::from)
            .toList();
        items.add(StarSystemGroupItemDTO.builder()
            .id(g.getId().toString())
            .name(g.getName())
            .orderIndex(g.getOrderIndex())
            .missions(mInGroup)
            .build());
    }
    for (Mission m : standalone) {
        items.add(StarSystemMissionItemDTO.builder()
            .id(m.getId().toString())
            .name(m.getName())
            .orderIndex(m.getOrderIndex())
            .missionType(m.getMissionType().name())
            .difficulty(m.getDifficulty().name())
            .build());
    }
    items.sort(Comparator.comparingInt(StarSystemItemDTO::getOrderIndex));

    return StarSystemDetailResponse.builder()
        .id(ss.getId().toString())
        .name(ss.getName())
        .description(ss.getDescription())
        .iconUrl(ss.getIconUrl())
        .items(items)
        .build();
}
```

**Repository metódusok szükségesek:**
```java
// MissionGroupRepository:
List<MissionGroup> findByStarSystemIdOrderByOrderIndex(UUID starSystemId);

// MissionRepository (már létezhet részben, de ez az új sor):
List<Mission> findByStarSystemIdAndGroupIsNullOrderByOrderIndex(UUID starSystemId);
```

> **N+1 elkerülése:** A `findByStarSystemId...` query-nél a `MissionGroup.missions` lista `@OneToMany(fetch = LAZY)` → a service metódusban `JOIN FETCH` szükséges vagy `@EntityGraph`. Javasolt: `@EntityGraph(attributePaths = {"missions"})` a repository metóduson, így egy queryben jön le az összes csoport és a bennük lévő missziók.

---

#### User-accessible Group endpoint

**`GET /api/mission-groups/{id}/missions`** — `group:read`
```json
// Response 200
{
  "groupId": "uuid",
  "groupName": "Változók",
  "starSystemId": "uuid",
  "missions": [
    { "id": "uuid", "name": "Változók leírása",  "missionType": "CONTENT",       "groupOrder": 0 },
    { "id": "uuid", "name": "Kitöltős feladat",  "missionType": "FILL_IN_BLANK", "groupOrder": 1 },
    { "id": "uuid", "name": "Kvíz",              "missionType": "QUIZ",          "groupOrder": 2 }
  ]
}
// fillInBlankData NEM szerepel itt — Group Player külön GET /fill-in-blank hívással tölti be
```

---

### Frontend — TypeScript típusok

**`types/mission.ts` bővítése:**
```typescript
type MissionType = "CODING" | "CIRCUIT_SIMULATION" | "QUIZ" | "CONTENT" | "FILL_IN_BLANK"

interface MissionResponse {
  // ... meglévő mezők ...
  starSystemId: string          // denormalizálva — közvetlen navigációhoz
  groupId?: string | null
  groupOrder?: number | null
  orderIndex?: number | null
  content?: string | null
}

// Fill-in-blank user DTO (isCorrect NÉLKÜL)
interface FillInBlankOptionUser { id: string; optionText: string; orderIndex: number }
interface FillInBlankBlankUser  { id: string; key: string; orderIndex: number; options: FillInBlankOptionUser[] }
interface FillInBlankUserResponse {
  missionId: string; templateText: string; passThreshold: number | null
  blanks: FillInBlankBlankUser[]
}

// Fill-in-blank admin DTO (isCorrect benne van)
interface FillInBlankOptionAdmin { id?: string; optionText: string; isCorrect: boolean; orderIndex: number }
interface FillInBlankBlankAdmin  { id?: string; key: string; orderIndex: number; options: FillInBlankOptionAdmin[] }
interface FillInBlankAdminResponse {
  id: string; missionId: string; templateText: string; passThreshold: number | null
  blanks: FillInBlankBlankAdmin[]
}
interface SaveFillInBlankRequest {
  templateText: string; passThreshold: number | null; blanks: FillInBlankBlankAdmin[]
}

// Fill-in-blank submit
interface FillInBlankSubmitRequest { answers: Record<string, string> } // blankKey → optionId
interface FillInBlankResultDetail {
  blankKey: string; correct: boolean
  selectedOptionText: string; correctOptionTexts: string[]
}
interface FillInBlankSubmitResponse {
  attemptId: string; score: number; maxScore: number; percentage: number
  passed: boolean; passThreshold: number | null; results: FillInBlankResultDetail[]
}

// Content pagination
interface ContentPageResponse {
  missionId: string; missionName: string; content: string
  page: number; pageSize: number; totalLines: number; totalPages: number
  hasNextPage: boolean; hasPreviousPage: boolean
}
```

**`types/missionGroup.ts` (új fájl):**
```typescript
interface MissionGroupResponse {
  id: string; name: string; description?: string
  starSystemId: string; orderIndex: number
  ownerId: string; updatedById: string | null
  createdAt: string; updatedAt: string
  missions: MissionInGroupResponse[]
}
interface MissionInGroupResponse { id: string; name: string; missionType: MissionType; groupOrder: number }
interface CreateMissionGroupRequest { name: string; description?: string; starSystemId: string; orderIndex: number }
interface GroupMissionsResponse { groupId: string; groupName: string; starSystemId: string; missions: MissionInGroupResponse[] }
interface ReorderUpdatedItem { id: string; orderIndex?: number; groupOrder?: number }
interface ReorderResponse { updated: ReorderUpdatedItem[] }
```

**`types/groupProgress.ts` (új fájl):**
```typescript
interface GroupProgressResponse {
  groupId: string; nextMissionId: string | null
  completedMissionIds: string[]; completed: boolean
  startedAt: string; lastUpdatedAt: string; completedAt: string | null; totalMissions: number
}
```

**`types/starSystem.ts` bővítése:**
```typescript
interface StarSystemGroupItem {
  type: "GROUP"; id: string; name: string; orderIndex: number; missions: MissionInGroupResponse[]
}
interface StarSystemMissionItem {
  type: "MISSION"; id: string; name: string; missionType: MissionType; difficulty: string; orderIndex: number
}
type StarSystemItem = StarSystemGroupItem | StarSystemMissionItem
interface StarSystemDetailResponse {
  id: string; name: string; description: string; iconUrl?: string; items: StarSystemItem[]
}
```

---

### Frontend — Fájlstruktúra (Stage 1 változások)

**Új fájlok:**
```
src/
├── types/
│   ├── missionGroup.ts          # MissionGroupResponse, GroupMissionsResponse, stb.
│   └── groupProgress.ts         # GroupProgressResponse, GroupDisplayProgress, stb.
├── components/
│   ├── admin/
│   │   ├── MarkdownEditor.tsx   # Textarea + live react-markdown preview
│   │   └── FillInBlankEditor.tsx # Blank detektor + opció editor
│   └── play/
│       ├── ContentPlayer.tsx    # Content lejátszó logika (missionId + onComplete prop)
│       └── FillInBlankView.tsx  # Mixed pool kitöltős feladat
└── pages/
    └── play/
        ├── ContentMissionView.tsx  # Route wrapper: /play/content/:missionId
        └── MissionGroupPlayer.tsx  # Route wrapper: /play/group/:groupId
```

**Módosítandó fájlok:**
```
src/types/auth.ts                    # + permissions: string[] a User-ben
src/types/mission.ts                 # + CONTENT/FILL_IN_BLANK, + starSystemId stb.
src/types/starSystem.ts              # Teljes csere: StarSystemDetailResponse + items[]
src/context/AuthContext.tsx          # + permissions kinyerése /auth/me-ből + hasPermission()
src/layouts/AdminLayout.tsx          # permission-aware menuItems
src/router/index.tsx                 # + 2 új play route
src/api/client.ts                    # + groupApi, groupProgressApi, fillInBlankApi modulok
src/pages/admin/star-system/StarSystemEdit.tsx    # Teljes újraírás: fa struktúra
src/pages/admin/missions/MissionEdit.tsx          # + MarkdownEditor/FillInBlankEditor
src/pages/star-system-detail/StarSystemDetailPage.tsx  # Teljes újraírás: items[] + progress
src/components/forge/quiz/QuizPlayerComponent.tsx # Új komponens (API logika nélküli QuizPlayer page)
src/pages/mission-forge/QuizPlayerPage.tsx        # Vékony wrapper → QuizPlayerComponent
src/pages/admin/star-system/StarSystemList.tsx    # URL cleanup
src/pages/admin/missions/MissionList.tsx          # URL cleanup
```

---

## i18n kulcsok — teljes lista (Stage 1 új kulcsok)

A meglévő `src/i18n/config.ts` fájlban mindkét language objektumba (`en.translation`, `hu.translation`) be kell szúrni az alábbi kulcsokat. A `{{placeholder}}` szintaxis az i18next interpoláció.

```typescript
// HU fordítások (EN párjuk alább)
admin: {
  // Meglévő admin kulcsok megmaradnak, ezek az újak:
  groups: "Csoportok",
  group: {
    createGroup: "Csoport létrehozása",
    groupName: "Csoport neve",
    description: "Leírás (opcionális)",
    noGroups: "Nincsenek csoportok ebben a csillagrendszerben",
    addMissionToGroup: "Misszió hozzáadása a csoporthoz",
    selectGroup: "Válassz csoportot",
    moveToGroup: "Csoportba mozgatás",
    noGroupsForMove: "Nincs csoport — hozz létre egyet először",
    deleteConfirm: "Biztosan törlöd ezt a csoportot? A benne lévő missziók standalone-ná válnak.",
    deleteError_fillInBlank: "A csoport FILL_IN_BLANK missziót tartalmaz — töröld előbb",
    conflictError: "Ez a misszió már a '{{name}}' csoporthoz tartozik",
    removeFillInBlankError: "FILL_IN_BLANK misszió nem lehet standalone",
    reorderError: "Nem sikerült átrendezni",
    saveSuccess: "Csoport elmentve",
    deleteSuccess: "Csoport törölve",
  },
  mission: {
    // Meglévő mission kulcsok megmaradnak, ezek az újak:
    addBlank: "[[Blank]] hozzáadása",
    passThreshold: "Sikerességi küszöb (%)",
    passThresholdHelp: "0–100 között. Üresen hagyva mindig sikeresnek számít.",
    noThreshold: "Nincs küszöb",
    contentPreview: "Tartalom előnézete",
    markdownContent: "Markdown tartalom",
    fillInBlankContent: "Fill-in-blank tartalom",
    saveFirstForFillInBlank: "Mentsd el a missziót, majd visszatérve szerkesztheted a fill-in-blank tartalmat.",
    blankLabel: "{{key}} opcióit:",
    optionCorrect: "Helyes",
    optionPlaceholder: "Opció szövege...",
    maxOptionsReached: "Maximum 5 opció érhető el",
  },
  starSystem: {
    // Meglévő star system kulcsok megmaradnak, ezek az újak:
    items: "{{count}} elem",
    reorderSuccess: "Sorrend frissítve",
    groupCreated: "Csoport létrehozva",
    missionMoved: "Misszió átmozgatva",
  },
},
play: {
  content: {
    loadMore: "Több betöltése",
    next: "Következő →",
    back: "← Vissza",
    systemComplete: "Megvizsgáltad az összes anyagot ebben a csillagrendszerben!",
    backToSystem: "Vissza a csillagrendszerhez",
  },
  fillInBlank: {
    submit: "Beküldés",
    retry: "Újra",
    alreadyPassed: "Ezt a feladatot már sikeresen teljesítetted.",
    nextStep: "Következő lépés →",
    score: "Eredmény: {{score}} / {{max}} ({{pct}}%)",
    passed: "Sikeres!",
    failed: "Nem sikerült. Próbáld újra!",
    correctAnswers: "Helyes válaszok:",
    fillAllBlanks: "Töltsd ki az összes mezőt a beküldéshez",
    optionPool: "Válassz az alábbi lehetőségek közül:",
  },
  group: {
    step: "{{current}} / {{total}} lépés",
    back: "← Vissza",
    completed: "Csoport teljesítve!",
    backToSystem: "Vissza a csillagrendszerhez",
    loadError: "Nem sikerült betölteni a feladatot.",
    stepError: "Nem sikerült továbblépni. Próbáld újra.",
  },
},
starSystem: {
  // Meglévő kulcsok megmaradnak, ezek az újak:
  startGroup: "Kezdd el",
  continueGroup: "Folytatás",
  replayGroup: "Újra",
  groupCompleted: "Teljesítve",
  missions: "{{count}} misszió",
  progress: "{{done}} / {{total}} lépés",
  loadError: "Nem sikerült betölteni a csillagrendszert.",
},
```

```typescript
// EN fordítások (teljes egyezés HU struktúrával, más szövegekkel)
admin: {
  groups: "Groups",
  group: {
    createGroup: "Create Group",
    groupName: "Group name",
    description: "Description (optional)",
    noGroups: "No groups in this star system",
    addMissionToGroup: "Add mission to group",
    selectGroup: "Select a group",
    moveToGroup: "Move to group",
    noGroupsForMove: "No groups — create one first",
    deleteConfirm: "Delete this group? Missions inside will become standalone.",
    deleteError_fillInBlank: "Group contains a FILL_IN_BLANK mission — delete it first",
    conflictError: "This mission already belongs to '{{name}}'",
    removeFillInBlankError: "FILL_IN_BLANK mission cannot be standalone",
    reorderError: "Failed to reorder",
    saveSuccess: "Group saved",
    deleteSuccess: "Group deleted",
  },
  mission: {
    addBlank: "Add [[Blank]]",
    passThreshold: "Pass threshold (%)",
    passThresholdHelp: "0–100. Leave empty to always pass.",
    noThreshold: "No threshold",
    contentPreview: "Content preview",
    markdownContent: "Markdown content",
    fillInBlankContent: "Fill-in-blank content",
    saveFirstForFillInBlank: "Save the mission first, then you can edit the fill-in-blank content.",
    blankLabel: "Options for {{key}}:",
    optionCorrect: "Correct",
    optionPlaceholder: "Option text...",
    maxOptionsReached: "Maximum 5 options allowed",
  },
  starSystem: {
    items: "{{count}} item(s)",
    reorderSuccess: "Order updated",
    groupCreated: "Group created",
    missionMoved: "Mission moved",
  },
},
play: {
  content: {
    loadMore: "Load more",
    next: "Next →",
    back: "← Back",
    systemComplete: "You've explored all content in this star system!",
    backToSystem: "Back to star system",
  },
  fillInBlank: {
    submit: "Submit",
    retry: "Try again",
    alreadyPassed: "You've already completed this task successfully.",
    nextStep: "Next step →",
    score: "Score: {{score}} / {{max}} ({{pct}}%)",
    passed: "Passed!",
    failed: "Not passed. Try again!",
    correctAnswers: "Correct answers:",
    fillAllBlanks: "Fill in all blanks to submit",
    optionPool: "Choose from the options below:",
  },
  group: {
    step: "Step {{current}} / {{total}}",
    back: "← Back",
    completed: "Group completed!",
    backToSystem: "Back to star system",
    loadError: "Failed to load the task.",
    stepError: "Failed to advance. Please try again.",
  },
},
starSystem: {
  startGroup: "Start",
  continueGroup: "Continue",
  replayGroup: "Replay",
  groupCompleted: "Completed",
  missions: "{{count}} mission(s)",
  progress: "{{done}} / {{total}} steps",
  loadError: "Failed to load star system.",
},
```

---

## Megvalósítási lépések (Stage 1)

### Backend

0. **`GET /api/auth/me` bővítése** — `UserProfileResponse` DTO létrehozása (`id`, `username`, `email`, `fullName`, `avatarUrl`, `roles`, `permissions`); `AuthService.getMe()` metódus: cadet role-jaiból flat permission lista összegyűjtése (Set → deduplikáció); `AuthController.getMe()` visszaad `UserProfileResponse`-t a régi response helyett. **Ez az első lépés** mert a frontend sidebar az első indulástól fogva elvárja a `permissions` mezőt.

1. **Flyway bevezetése** — `flyway-core` + `flyway-database-postgresql` dependency, `V1__baseline.sql` (ha szükséges), `application.properties` módosítás (`ddl-auto=validate`, `flyway.enabled=true`)
2. **`group:*` permission seed** — `DataInitializer.java`: 4 új permission létrehozása, ROLE_ADMIN és ROLE_CONTENT_CREATOR hozzárendelés, ROLE_CADET `group:read` kap
3. **`ROLE_CONTENT_CREATOR` role seed** — `DataInitializer.java`: új role, összes szükséges `starsystem:*`, `mission:*`, `group:*` permission hozzárendelése
4. **`MissionGroup` entitás + repository** — `owner`, `updatedBy`, `orderIndex`, `createdAt`, `updatedAt` mezőkkel; `MissionGroupRepository` (findByStarSystemId, findByOwnerId, findByStarSystemIdOrderByOrderIndex)
5. **`Mission` entitás módosítás** — `group` FK (nullable), `groupOrder` (nullable), `orderIndex` (nullable, átnevezve `orderInSystem`-ről), `content` (TEXT nullable); `fillInBlankData` mező törlése; Flyway: `V2__mission_group_fields.sql`
6. **`MissionType` enum bővítése** — `CONTENT`, `FILL_IN_BLANK` értékek hozzáadása
7. **Fill-in-blank entitások + repositoryek** — `FillInBlankDefinition`, `FillInBlankBlank`, `FillInBlankOption`, `FillInBlankAttempt`, `FillInBlankAnswerDetail`; Flyway: `V3__fill_in_blank_entities.sql`
8. **`MissionGroupProgress` + `MissionGroupStepCompletion` entitások + repositoryek** — unique constraintek; `findByCadetIdAndGroupId`; `existsByProgressIdAndMissionId`; Flyway: `V4__group_progress.sql`
9. **MissionGroup CRUD service + controller** — CRUD, reorder (swap), mission hozzáadás (conflict check), eltávolítás (FILL_IN_BLANK blokk), my-groups, ownership check, `group:*` `@PreAuthorize`; **standalone mission reorder** (`PUT /api/missions/{id}/reorder`) ugyanitt vagy külön `MissionController`-ban: megkeresi a szomszédos elemet a `orderIndex ± 1` pozícióban (group vagy standalone), felcseréli az orderIndex értékeket, visszaadja a `ReorderResponse`-t
10. **Fill-in-blank service + controller (admin)** — POST (új definíció, 409 ha létezik), PUT (`@Transactional` teljes replace), GET admin nézet (`FillInBlankAdminResponse` — isCorrect benne)
11. **Fill-in-blank service + controller (user)** — GET user nézet (`FillInBlankUserResponse` — isCorrect nélkül), `submit-fill-blank` (optionId alapú kiértékelés — cross-blank optionId nem 400, hanem `correct: false`; `FillInBlankAttempt` + `FillInBlankAnswerDetail` mentés), `GET .../last-attempt` (legutóbbi attempt passed/percentage/submittedAt — 404 ha nincs; a Group Player visszanavigáláshoz szükséges)
12. **Group Progress service + controller** — GET (404 ha nincs), POST start (201, 409 ha van), POST complete-step (FILL_IN_BLANK validáció, `MissionGroupStepCompletion` INSERT, `nextMissionId` kiszámítása, completed flag)
13. **Content pagination endpoint** — `GET /api/missions/{id}/content?page&size`, soros szeletelés, `ContentPageResponse` DTO
14. **`StarSystemController.getWithMissions` refaktor** — új `StarSystemDetailResponse` DTO, rendezett `items[]` tömb (groups + standalone missions vegyesen, `orderIndex` szerint) — `GroupItem` / `MissionItem` wrapper osztályokkal
15. **`MissionResponse` DTO bővítése** — `starSystemId` (denormalizálva), `groupId`, `groupOrder`, `orderIndex` (nullable)

### Frontend — Admin

---

**16. TypeScript típusok + API client bővítés**

**`src/types/auth.ts` módosítás:**
```typescript
export interface User {
  username: string;
  roles: string[];
  permissions: string[];  // ← ÚJ: backend /auth/me-ből jön
  exp?: number;
}
```

**`src/context/AuthContext.tsx` módosítás:**
- A `/api/auth/me` response-ban a backend mostantól `permissions: string[]` mezőt is küld
- A setState-ben: `permissions: response.data.permissions || []`
- Új context függvény: `hasPermission: (p: string) => boolean` → `state.user?.permissions.includes(p) ?? false`
- `AuthContextType`-ban: `hasPermission: (permission: string) => boolean`

**`src/types/mission.ts` módosítás:**
```typescript
// MissionType bővítés
type MissionType = "CODING" | "CIRCUIT_SIMULATION" | "QUIZ" | "CONTENT" | "FILL_IN_BLANK"

// MissionResponse bővítés (meglévő mezők megmaradnak, ezek az újak):
interface MissionResponse {
  // ... meglévő mezők ...
  starSystemId: string          // denormalizálva a backendtől
  groupId: string | null
  groupOrder: number | null
  orderIndex: number | null     // orderInSystem helyett (régi mező eldobva)
  content: string | null
}
```

**`src/types/starSystem.ts` teljes csere:**
- Régi `StarSystemWithMissionsResponse` (lapos `missions[]`) helyett a terv TypeScript szekciójában definiált `StarSystemDetailResponse` (items[] discriminated union) kerül

**`src/types/missionGroup.ts`** és **`src/types/groupProgress.ts`**: az "Architekturális terv → TypeScript típusok" szekcióban definiált interfészek alapján, változtatás nélkül

**`src/api/client.ts` bővítés — új API modulok hozzáadása:**
```typescript
// Meglévő modul-exportok mellé:

export const groupApi = {
  getByStarSystem: (starSystemId: string) =>
    apiClient.get<MissionGroupResponse[]>(`/mission-groups?starSystemId=${starSystemId}`),
  create: (data: CreateMissionGroupRequest) =>
    apiClient.post<MissionGroupResponse>('/mission-groups', data),
  update: (id: string, data: { name: string; description?: string }) =>
    apiClient.put<MissionGroupResponse>(`/mission-groups/${id}`, data),
  delete: (id: string) =>
    apiClient.delete(`/mission-groups/${id}`),
  addMission: (groupId: string, missionId: string, groupOrder: number) =>
    apiClient.post<MissionGroupResponse>(`/mission-groups/${groupId}/missions`, { missionId, groupOrder }),
  removeMission: (groupId: string, missionId: string) =>
    apiClient.delete(`/mission-groups/${groupId}/missions/${missionId}`),
  reorder: (groupId: string, direction: "up" | "down") =>
    apiClient.put<ReorderResponse>(`/mission-groups/${groupId}/reorder`, { direction }),
  getMissions: (groupId: string) =>
    apiClient.get<GroupMissionsResponse>(`/mission-groups/${groupId}/missions`),
};

export const missionReorderApi = {
  reorderStandalone: (missionId: string, direction: "up" | "down") =>
    apiClient.put<ReorderResponse>(`/missions/${missionId}/reorder`, { direction }),
  reorderInGroup: (missionId: string, direction: "up" | "down") =>
    apiClient.put<ReorderResponse>(`/missions/${missionId}/group-order`, { direction }),
};

export const groupProgressApi = {
  get: (groupId: string) =>
    apiClient.get<GroupProgressResponse>(`/group-progress/${groupId}`),
  start: (groupId: string) =>
    apiClient.post<GroupProgressResponse>(`/group-progress/${groupId}/start`, {}),
  completeStep: (groupId: string) =>
    apiClient.post<GroupProgressResponse>(`/group-progress/${groupId}/complete-step`, {}),
};

export const fillInBlankApi = {
  getUserView: (missionId: string) =>
    apiClient.get<FillInBlankUserResponse>(`/missions/${missionId}/fill-in-blank`),
  getAdminView: (missionId: string) =>
    apiClient.get<FillInBlankAdminResponse>(`/missions/${missionId}/fill-in-blank/admin`),
  create: (missionId: string, data: SaveFillInBlankRequest) =>
    apiClient.post<FillInBlankAdminResponse>(`/missions/${missionId}/fill-in-blank`, data),
  update: (missionId: string, data: SaveFillInBlankRequest) =>
    apiClient.put<FillInBlankAdminResponse>(`/missions/${missionId}/fill-in-blank`, data),
  submit: (missionId: string, data: FillInBlankSubmitRequest) =>
    apiClient.post<FillInBlankSubmitResponse>(`/missions/${missionId}/submit-fill-blank`, data),
  getLastAttempt: (missionId: string) =>
    apiClient.get<{ passed: boolean; percentage: number; submittedAt: string }>(`/missions/${missionId}/fill-in-blank/last-attempt`),
};

export const contentApi = {
  getPage: (missionId: string, page: number, size = 100) =>
    apiClient.get<ContentPageResponse>(`/missions/${missionId}/content?page=${page}&size=${size}`),
};
```

---

**17. `AuthContext.tsx` + `AdminLayout.tsx` — permission-aware sidebar**

**`AdminLayout.tsx` módosítás:**

```typescript
// Régi: const menuItems = [...] statikus tömb

// Új: dinamikusan a permission alapján
const { user, hasPermission } = useAuth();
const isAdmin = user?.roles.includes("ROLE_ADMIN") ?? false;
const canManageGroups = hasPermission("group:create") || hasPermission("group:read");

const menuItems = [
  { text: "users",       icon: <PeopleIcon />,     path: "/admin/users",        show: isAdmin },
  { text: "starSystems", icon: <SchoolIcon />,      path: "/admin/star-systems", show: true },
  { text: "missions",    icon: <AssignmentIcon />,  path: "/admin/missions",     show: true },
  { text: "groups",      icon: <FolderIcon />,      path: "/admin/groups",       show: canManageGroups },
  { text: "roles",       icon: <SecurityIcon />,    path: "/admin/roles",        show: isAdmin },
  { text: "permissions", icon: <PermissionIcon />,  path: "/admin/permissions",  show: isAdmin },
  { text: "logsTitle",   icon: <TerminalIcon />,    path: "/admin/logs",         show: isAdmin },
].filter(item => item.show);
```

Import hozzáadás: `Folder as FolderIcon` from `@mui/icons-material`
i18n kulcs hozzáadás (`config.ts`): `"groups": "Csoportok"` (HU) / `"Groups"` (EN)

> **Megjegyzés:** A "Csoportok" admin oldal (`/admin/groups`) egy egyszerű lista a saját group-okról — Stage 1 MVP-ben elegendő ha az admin a StarSystemEdit-ből kezeli a group-okat, ez a menüpont opcionálisan elmaradhat; a `canManageGroups` flag akkor is szükséges a jövőbeli bővíthetőséghez.

---

**18. `StarSystemList.tsx` + `MissionList.tsx` + `StarSystemEdit.tsx` URL cleanup**

Mindhárom fájlban:
- `const API_URL = "http://localhost:8080/api"` → törlés
- `import axios from "axios"` → törlés (ahol csak URL miatt volt)
- `import apiClient from "../../../api/client"` hozzáadás
- `axios.get(${API_URL}/...)` → `apiClient.get(/...)`
- `axios.post(${API_URL}/...)` → `apiClient.post(/...)`
- `axios.put(${API_URL}/...)` → `apiClient.put(/...)`
- `axios.delete(${API_URL}/...)` → `apiClient.delete(/...)`
- Headers `{ Authorization: Bearer ${token} }` → törlés (az apiClient interceptora kezeli)

> `MissionEdit.tsx`-ben az `axios` közvetlen import marad a jelenlegi patternnek megfelelően — a cleanup ráér Stage 2-ben.

---

**19. `MissionEdit.tsx` bővítés + `MarkdownEditor.tsx` + `FillInBlankEditor.tsx` létrehozása**

**`MissionEdit.tsx` változások:**

```typescript
// 1. MISSION_TYPES bővítés
const MISSION_TYPES = ["CODING", "CIRCUIT_SIMULATION", "QUIZ", "CONTENT", "FILL_IN_BLANK"];

// 2. mission state bővítés
const [mission, setMission] = useState({
  name: "",
  descriptionMarkdown: "",
  difficulty: "EASY",
  missionType: "CODING",
  orderIndex: 1,           // orderInSystem → orderIndex
  starSystemId: starSystemIdFromQuery || "",
  content: "",             // ← ÚJ: CONTENT típushoz
  // starSystemId, groupId, groupOrder szerver által jön vissza, nem szerkeszthetők
});

// 3. Back navigáció — szerkesztés oldalon mentés/törlés után:
const handleBack = () => {
  if (mission.starSystemId) {
    navigate(`/admin/star-systems/${mission.starSystemId}`);
  } else {
    navigate(-1);
  }
};
// Ugyanezt a navigate-t hívja a ← gomb is (ne navigate(-1))

// 4. Save payload bővítés
const payload = {
  name: mission.name,
  descriptionMarkdown: mission.descriptionMarkdown,
  difficulty: mission.difficulty,
  missionType: mission.missionType,
  orderIndex: mission.orderIndex,
  starSystemId: mission.starSystemId,
  ...(mission.missionType === "CONTENT" && { content: mission.content }),
};

// 5. Típusfüggő editor — a Form alján, a Save gomb előtt:
{mission.missionType === "CONTENT" && (
  <MarkdownEditor
    value={mission.content || ""}
    onChange={(val) => setMission(prev => ({ ...prev, content: val }))}
  />
)}
{mission.missionType === "FILL_IN_BLANK" && !isNew && (
  <FillInBlankEditor missionId={id!} />
)}
{mission.missionType === "FILL_IN_BLANK" && isNew && (
  <Alert severity="info">
    Mentsd el a missziót, majd visszatérve szerkesztheted a fill-in-blank tartalmat.
  </Alert>
)}
```

---

**`src/components/admin/MarkdownEditor.tsx` — ÚJ fájl:**

```typescript
interface MarkdownEditorProps {
  value: string;
  onChange: (value: string) => void;
}
```

Layout: `Grid container spacing={2}`:
- Bal (xs=12, md=6): `<TextField multiline minRows={15} fullWidth value={value} onChange={e => onChange(e.target.value)} label="Markdown tartalom" />`
- Jobb (xs=12, md=6): Paper alap, "Előnézet" felirat, `<ReactMarkdown>{value}</ReactMarkdown>`

Import szükséges: `react-markdown` — ha nincs: `npm install react-markdown`

---

**`src/components/admin/FillInBlankEditor.tsx` — ÚJ fájl:**

```typescript
interface FillInBlankEditorProps {
  missionId: string;
}

// Lokális state típus
interface BlankEditorState {
  key: string;   // pl. "blank_1"
  options: Array<{ tempId: string; optionText: string; isCorrect: boolean }>
}
```

State:
```typescript
const [templateText, setTemplateText] = useState("")
const [blanks, setBlanks] = useState<BlankEditorState[]>([])
const [passThreshold, setPassThreshold] = useState<number | null>(null)
const [saving, setSaving] = useState(false)
const [hasDefinition, setHasDefinition] = useState(false)  // POST vs PUT dönt
const [saveError, setSaveError] = useState<string | null>(null)
```

Logika:
- **Mount:** `fillInBlankApi.getAdminView(missionId)` → 200: populate state + `setHasDefinition(true)`; 404: üres state
- **templateText onChange:** regex `/\{(\w+)\}/g` → kinyeri az összes blank kulcsot → frissíti a `blanks` state-et (megtartja a meglévő opciók state-jét az azonos kulcsú blank-eknél, új kulcshoz üres options tömböt ad, törölt kulcsú blank-et eltávolítja)
- **"Blank hozzáadása" gomb:** `setTemplateText(prev => prev + " {blank_" + (blanks.length + 1) + "}")`
- **Option input (auto-extend):** minden blank-nél az utolsó kitöltött opció után `TextFiled` jelenik meg (üres, onBlur-ra ha nem üres → opció hozzáadódik + következő üres megjelenik). Max 5 opció/blank
- **Save:** `SaveFillInBlankRequest` összeállítása → `fillInBlankApi.create` (ha !hasDefinition) vagy `fillInBlankApi.update` (ha hasDefinition)

---

**20. `StarSystemEdit.tsx` — teljes újraírás**

**State:**
```typescript
// Star system metaadatok (szerkeszthető mezők)
const [meta, setMeta] = useState({ name: "", description: "", iconUrl: "" })
// A fa (groups + standalone missions rendezve)
const [items, setItems] = useState<StarSystemItem[]>([])
// Loading / saving / error
const [loading, setLoading] = useState(!isNew)
const [saving, setSaving] = useState(false)
const [error, setError] = useState<string | null>(null)
// Snackbar (success / error / warning üzenetek)
const [snackbar, setSnackbar] = useState<{ open: boolean; msg: string; severity: "success"|"error"|"warning" }>({ open: false, msg: "", severity: "success" })
// Group létrehozás dialog
const [createGroupOpen, setCreateGroupOpen] = useState(false)
const [newGroupName, setNewGroupName] = useState("")
const [creatingGroup, setCreatingGroup] = useState(false)
// Mission group-ba mozgatás dialog
const [moveDialog, setMoveDialog] = useState<{ missionId: string } | null>(null)
// Mission group-hoz adás: célcsoport kiválasztás
const targetGroups = items.filter((item): item is StarSystemGroupItem => item.type === "GROUP")
```

**Betöltés:** `GET /api/star-systems/{id}/with-missions` → `setMeta({name, description, iconUrl})` + `setItems(response.items)`

**Reorder state patch helper:**
```typescript
const applyReorder = (updated: ReorderUpdatedItem[], field: "orderIndex" | "groupOrder", groupId?: string) => {
  setItems(prev => {
    const newItems = [...prev]
    // orderIndex reorder: group vagy standalone mission a top-level listában
    if (field === "orderIndex") {
      return newItems.map(item => {
        const match = updated.find(u => u.id === item.id)
        return match ? { ...item, orderIndex: match.orderIndex! } : item
      }).sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0))
    }
    // groupOrder reorder: group-on belüli mission
    if (field === "groupOrder" && groupId) {
      return newItems.map(item => {
        if (item.type !== "GROUP" || item.id !== groupId) return item
        const newMissions = item.missions.map(m => {
          const match = updated.find(u => u.id === m.id)
          return match ? { ...m, groupOrder: match.groupOrder! } : m
        }).sort((a, b) => a.groupOrder - b.groupOrder)
        return { ...item, missions: newMissions }
      })
    }
    return newItems
  })
}
```

**Action handlerek (mind axios hívással, loading state + snackbar):**
```typescript
// ↑↓ group a star systemben
const handleReorderGroup = async (groupId: string, direction: "up" | "down") => {
  const res = await groupApi.reorder(groupId, direction)
  applyReorder(res.data.updated, "orderIndex")
}

// ↑↓ standalone mission a star systemben
const handleReorderMission = async (missionId: string, direction: "up" | "down") => {
  const res = await missionReorderApi.reorderStandalone(missionId, direction)
  applyReorder(res.data.updated, "orderIndex")
}

// ↑↓ misszió group-on belül
const handleReorderInGroup = async (missionId: string, groupId: string, direction: "up" | "down") => {
  const res = await missionReorderApi.reorderInGroup(missionId, direction)
  applyReorder(res.data.updated, "groupOrder", groupId)
}

// → misszió kivétele group-ból (standalone lesz)
const handleRemoveFromGroup = async (missionId: string, groupId: string) => {
  // Ha FILL_IN_BLANK: backend 400-at ad → snackbar warning
  try {
    await groupApi.removeMission(groupId, missionId)
    // state: misszió eltávolítása a group missions tömbből, hozzáadása a top-level items-hez
    // (legegyszerűbb: teljes újratöltés)
    await reloadItems()
  } catch (err: any) {
    if (err.response?.status === 400) {
      setSnackbar({ open: true, msg: "FILL_IN_BLANK misszió nem lehet standalone", severity: "warning" })
    }
  }
}

// ← standalone misszió group-ba mozgatása (moveDialog alapján)
const handleMoveToGroup = async (missionId: string, groupId: string) => {
  try {
    await groupApi.addMission(groupId, missionId, 999) // groupOrder végére kerül
    setMoveDialog(null)
    await reloadItems()
  } catch (err: any) {
    if (err.response?.status === 409) {
      const name = err.response.data.data?.conflictingGroupName ?? "másik csoport"
      setSnackbar({ open: true, msg: `Ez a misszió már a '${name}' csoporthoz tartozik`, severity: "warning" })
    }
  }
}

// Group törlése
const handleDeleteGroup = async (groupId: string) => {
  // FILL_IN_BLANK ellenőrzés: ha van ilyen misszió a group-ban, snackbar warning
  const group = items.find(i => i.type === "GROUP" && i.id === groupId) as StarSystemGroupItem
  if (group?.missions.some(m => m.missionType === "FILL_IN_BLANK")) {
    setSnackbar({ open: true, msg: "A csoport FILL_IN_BLANK missziót tartalmaz — töröld előbb", severity: "warning" })
    return
  }
  try {
    await groupApi.delete(groupId)
    await reloadItems()
  } catch (err: any) {
    if (err.response?.status === 400) {
      setSnackbar({ open: true, msg: err.response.data.message, severity: "error" })
    }
  }
}

// Segédfüggvény: teljes újratöltés reorderektől eltérő műveletek után
const reloadItems = async () => {
  const res = await apiClient.get<StarSystemDetailResponse>(`/star-systems/${id}/with-missions`)
  setItems(res.data.items)
}
```

**Render — fa struktúra:**
```tsx
{items.map((item, idx) => (
  item.type === "GROUP" ? (
    <GroupRow key={item.id}
      group={item}
      isFirst={idx === 0}
      isLast={idx === items.length - 1}
      onReorderUp={() => handleReorderGroup(item.id, "up")}
      onReorderDown={() => handleReorderGroup(item.id, "down")}
      onEdit={() => navigate(`/admin/missions/group/${item.id}`)}  // ha lesz group edit oldal
      onDelete={() => handleDeleteGroup(item.id)}
      onAddMission={() => navigate(`/admin/missions/new?groupId=${item.id}`)}
      onMissionReorderUp={(mId) => handleReorderInGroup(mId, item.id, "up")}
      onMissionReorderDown={(mId) => handleReorderInGroup(mId, item.id, "down")}
      onMissionEdit={(mId) => navigate(`/admin/missions/${mId}`)}
      onMissionRemove={(mId) => handleRemoveFromGroup(mId, item.id)}
    />
  ) : (
    <StandaloneMissionRow key={item.id}
      mission={item}
      isFirst={idx === 0}
      isLast={idx === items.length - 1}
      onReorderUp={() => handleReorderMission(item.id, "up")}
      onReorderDown={() => handleReorderMission(item.id, "down")}
      onEdit={() => navigate(`/admin/missions/${item.id}`)}
      onMoveToGroup={() => setMoveDialog({ missionId: item.id })}
    />
  )
))}
```

**`GroupRow` és `StandaloneMissionRow` — WordPress-szerű fa UI**

Minden listaelem egy MUI `Paper` div. A group-on belüli missziók `paddingLeft: 32px` behúzással jelennek meg — vizuálisan egyértelmű a hierarchia.

**`GroupRow` render (egy group a listában):**
```
┌─────────────────────────────────────────────────────────────┐
│ [▶] JavaScript Változók                [↑][↓] [szerkeszt] [töröl] │  ← group fejléc sor
│                                                             │
│   ┌────────────────────────────────────────────────────┐   │
│   │ CONTENT  Változók leírása    [↑][↓] [→] [szerkeszt]│   │  ← behúzott mission sor
│   └────────────────────────────────────────────────────┘   │
│   ┌────────────────────────────────────────────────────┐   │
│   │ FILL_IN_BLANK  Kitöltős  [↑][↓] [→⚠] [szerkeszt] │   │  ← FILL_IN_BLANK: [→] piros !
│   └────────────────────────────────────────────────────┘   │
│   [+ Misszió hozzáadása a csoporthoz]                       │
└─────────────────────────────────────────────────────────────┘
```

A `[→]` gomb (kivétel a group-ból → standalone lesz):
- CONTENT, QUIZ, CODING esetén: normál gomb, kattintásra `handleRemoveFromGroup` hívódik
- FILL_IN_BLANK esetén: a `[→]` gomb **helyett** `[→⚠]` piros Tooltip: *"FILL_IN_BLANK misszió nem lehet standalone"* — kattintásra snackbar warning, nem navigál

A group fejléc `[töröl]` gomb: ha a group-ban FILL_IN_BLANK van, a gombra kattintva snackbar warning jelenik meg (nem nyílik meg törlés dialog). Ha nincs FILL_IN_BLANK, megerősítő dialog nyílik.

**`StandaloneMissionRow` render (standalone mission):**
```
┌──────────────────────────────────────────────────────────────┐
│ CODING  Standalone feladat         [↑][↓] [←] [szerkeszt]   │
└──────────────────────────────────────────────────────────────┘
```

A `[←]` gomb (group-ba mozgatás): megnyitja a `moveDialog`-ot — `Select` dropdown a rendelkezésre álló group-okkal (`targetGroups` derived state). Ha nincs group a star systemben, a gomb disabled: Tooltip: *"Nincs csoport — hozz létre egyet először"*.

**Ikonok és vizuális jelölők:**
- Mission típus chip/badge: `CONTENT` → kék, `QUIZ` → lila, `FILL_IN_BLANK` → narancs, `CODING` → zöld, `CIRCUIT_SIMULATION` → szürke
- `[↑]` disabled ha az elem az első, `[↓]` disabled ha az utolsó (group-on belül a groupOrder alapján, top-level az orderIndex alapján)
- Gombok jobb oldalon: `IconButton` komponensek (`ArrowUpward`, `ArrowDownward`, `ArrowForward`/`ArrowBack`, `Edit`, `Delete`)

### Frontend — User oldal

---

21. **`QuizPlayer` refaktor** → `QuizPlayerPage` + `QuizPlayerComponent`

   **Jelenlegi állapot elemzése:**
   - `QuizPlayer.tsx` (`src/components/forge/quiz/QuizPlayer.tsx`) — **tisztán prezentációs**: kapja a `data: QuizDefinition` prop-ot, kezeli a timer-t, navigációt, válasz kiválasztást, hívja az `onSubmit` callback-et
   - `QuizPlayerPage.tsx` (`src/pages/mission-forge/QuizPlayerPage.tsx`) — **container**: API hívások (`quizApi.startQuiz`, `quizApi.submitQuiz`), loading/error state, eredmény megjelenítés, 409 session kezelés

   **A refaktor célja:** Olyan `QuizPlayerComponent` létrehozása, amelyet a `MissionGroupPlayer` közvetlenül használhat `missionId` + `onComplete` prop-okkal — az eredménymegjelenítés nélkül.

   ---

   **Létrehozandó: `src/components/quiz/QuizPlayerComponent.tsx`**

   ```typescript
   interface QuizPlayerComponentProps {
     missionId: string;
     onComplete: (result: MissionResult) => void;
   }
   ```

   Ez a komponens veszi át a `QuizPlayerPage` API logikáját:
   1. `useQuery(["quiz", missionId], () => quizApi.startQuiz(missionId))` — betölti a `QuizDefinition`-t; loading + error state megjelenítés ugyanúgy mint jelenleg a `QuizPlayerPage`-ben
   2. `useMutation(quizApi.submitQuiz)`:
      - `onSuccess(data)`: `onComplete(data)` hívása — **a komponens nem jeleníti meg az eredményt**, a szülő kezeli
      - `onError 409`: `onComplete(err.response.data.data)` — a régi eredmény átadása a szülőnek
      - `onError 404` (session expired): `queryClient.resetQueries(["quiz", missionId])` — újratölti a kvízt, majd újra megjeleníti a `QuizPlayer`-t (a user újra játssza)
   3. A `QuizPlayer` prezentációs komponenst rendereli `data={quiz}` és `onSubmit={submit}` prop-okkal
   4. **Nem tartalmaz** eredmény megjelenítőt — az `onComplete` callback hívódik és a szülő dönt mit mutat

   ---

   **Módosítandó: `QuizPlayerPage.tsx`**

   ```tsx
   const QuizPlayerPage: React.FC = () => {
     const { missionId } = useParams<{ missionId: string }>();
     const navigate = useNavigate();
     const [result, setResult] = useState<MissionResult | null>(null);

     if (result) {
       // Jelenlegi MISSION_ACCOMPLISHED UI marad itt
       return <QuizResultDisplay result={result} onBack={() => navigate(-1)} />;
     }

     return (
       <Box sx={{ width: "100vw", height: "100vh", bgcolor: "#000" }}>
         <QuizPlayerComponent
           missionId={missionId!}
           onComplete={(r) => setResult(r)}
         />
       </Box>
     );
   };
   ```

   Az eredmény megjelenítő logika (MISSION_ACCOMPLISHED panel, score, navigate -1 gomb) **marad a `QuizPlayerPage`-ben** — kinyerhető `QuizResultDisplay` saját komponensbe az olvashatóságért, de ez nem kötelező az MVP-hez.

   ---

   **`MissionGroupPlayer` használat:**

   ```tsx
   // A Group Player al-misszió renderelő switch-jében:
   case "QUIZ":
     return (
       <QuizPlayerComponent
         missionId={currentMission.id}
         onComplete={() => handleCompleteStep()}
         // A group context-ben az eredményt NEM mutatjuk — completeStep azonnal lép
       />
     );
   ```

   A `handleCompleteStep` a Group Player-ben lévő függvény, amely meghívja a `POST /complete-step` endpoint-ot és frissíti a progress state-et.

   ---

   **Tesztelési következmények:**
   - A meglévő `QuizPlayer.test.tsx` **nem változik** (csak prezentációs, nincs API)
   - A meglévő `QuizPlayerPage.test.tsx` **nem változik lényegesen** — az API mock-ok ugyanúgy működnek, most a `QuizPlayerComponent`-en keresztül
   - Új: `QuizPlayerComponent.test.tsx` — API loading/error/submit/409 tesztek (korábban a `QuizPlayerPage.test.tsx`-ből kerülnek ide)
---

22. **`ContentPlayer.tsx` + `ContentMissionView.tsx` + route**

> **Dependency:** `npm install react-markdown` szükséges a `MarkdownEditor` és a `ContentPlayer` komponensekhez. Futtasd a frontend könyvtárban mielőtt elkezded implementálni ezt a lépést.

**`src/components/play/ContentPlayer.tsx` — ÚJ fájl:**

```typescript
interface ContentPlayerProps {
  missionId: string;
  onComplete?: () => void;   // undefined = standalone mód (navigáció self-managed)
  starSystemId?: string;     // standalone módban szükséges a "Következő" navigációhoz
}
```

State:
```typescript
const [loadedContent, setLoadedContent] = useState("")
const [currentPage, setCurrentPage] = useState(0)
const [hasMore, setHasMore] = useState(false)
const [loadingMore, setLoadingMore] = useState(false)
const [loading, setLoading] = useState(true)
const [missionName, setMissionName] = useState("")
```

Logika:
- Mount: `contentApi.getPage(missionId, 0)` → `setLoadedContent(resp.content)`, `setMissionName(resp.missionName)`, `setHasMore(resp.hasNextPage)`
- "Load More" gomb (csak ha `hasMore`): `contentApi.getPage(missionId, currentPage + 1)` → `setLoadedContent(prev => prev + "\n" + resp.content)`, oldal ++ , `setHasMore`
- "Következő" gomb:
  - Ha `onComplete` prop van (group mód): `onComplete()` hívás
  - Ha nincs (standalone mód): ld. ContentMissionView standalone navigáció alább

Render:
```tsx
<Box sx={{ maxWidth: "800px", mx: "auto", p: 2 }}>
  <Typography variant="h5">{missionName}</Typography>
  <ReactMarkdown>{loadedContent}</ReactMarkdown>
  {hasMore && <Button onClick={handleLoadMore} disabled={loadingMore}>Load More</Button>}
  <Button variant="contained" onClick={handleNext}>Következő →</Button>
</Box>
```

---

**`src/pages/play/ContentMissionView.tsx` — ÚJ fájl (route wrapper):**

```typescript
// Route: /play/content/:missionId
// Navigation state-ből (StarSystemDetailPage-től kapott): { starSystemId, nextItem: { id, missionType } | null }
```

State:
```typescript
const { missionId } = useParams<{ missionId: string }>()
const navigate = useNavigate()
const location = useLocation()
const navState = location.state as { starSystemId?: string; nextItem?: { id: string; missionType: MissionType } | null } | null
```

Standalone navigáció (`handleNext`):
```typescript
const handleNext = () => {
  if (navState?.nextItem) {
    // Navigál a következő mission-re típus alapján
    const { id, missionType } = navState.nextItem
    if (missionType === "CONTENT") navigate(`/play/content/${id}`, { state: { starSystemId: navState.starSystemId, nextItem: null } })
    else if (missionType === "QUIZ") navigate(`/play/quiz/${id}`)
    else if (missionType === "GROUP") navigate(`/play/group/${id}`)
  } else {
    // Nincs következő → teljesítési képernyő
    navigate(`/star-systems/${navState?.starSystemId}`, {
      state: { completionMessage: "Megvizsgáltad az összes anyagot!" }
    })
  }
}
```

Render:
```tsx
return <ContentPlayer missionId={missionId!} onComplete={undefined} starSystemId={navState?.starSystemId} />
// A ContentPlayer handleNext-je a fenti függvényre callback-el visszahív — ehhez a ContentPlayer egy `onStandaloneNext` prop-ot kap
```

> **Egyszerűbb alternatíva MVP-re:** A ContentMissionView nem kapja meg a `nextItem`-t navigation state-ben, csak `starSystemId`-t. A "Következő" gomb mindig visszanavigál a star systemre. A true "next mission" navigáció Stage 2-re halasztható.

**Router bővítés (`router/index.tsx`):**
```typescript
{ path: "play/content/:missionId", element: <ContentMissionView /> },
```

---

23. **`FillInBlankView.tsx` — ÚJ fájl**

Fájl: `src/components/play/FillInBlankView.tsx`

```typescript
interface FillInBlankViewProps {
  missionId: string;
  onComplete: () => void;
}
```

State:
```typescript
const [definition, setDefinition] = useState<FillInBlankUserResponse | null>(null)
const [loading, setLoading] = useState(true)
// Pool: minden opció az összes blank-ből összekeverve
const [pool, setPool] = useState<Array<FillInBlankOptionUser & { blankKey: string }>>([])
// Slots: blankKey → optionId | null
const [slots, setSlots] = useState<Record<string, string | null>>({})
// Kiemelt blank slot (célzott kitöltéshez)
const [activeSlot, setActiveSlot] = useState<string | null>(null)
// Eredmény submit után
const [result, setResult] = useState<FillInBlankSubmitResponse | null>(null)
const [submitting, setSubmitting] = useState(false)
// Ha már van passed attempt (back button recovery)
const [alreadyPassed, setAlreadyPassed] = useState(false)
```

Betöltés (mount, párhuzamosan):
```typescript
const [defResp, lastAttemptResp] = await Promise.allSettled([
  fillInBlankApi.getUserView(missionId),
  fillInBlankApi.getLastAttempt(missionId),
])
// lastAttempt: ha fulfilled és passed → setAlreadyPassed(true)
// def: setDefinition, initializeSlotsAndPool
```

Pool inicializálás:
```typescript
const initPool = (def: FillInBlankUserResponse) => {
  const allOptions = def.blanks.flatMap(blank =>
    blank.options.map(opt => ({ ...opt, blankKey: blank.key }))
  )
  // Fisher-Yates shuffle
  for (let i = allOptions.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [allOptions[i], allOptions[j]] = [allOptions[j], allOptions[i]]
  }
  setPool(allOptions)
  setSlots(Object.fromEntries(def.blanks.map(b => [b.key, null])))
}
```

Interakció logika:
```typescript
const handlePoolClick = (optId: string) => {
  if (activeSlot) {
    // Célzott: az activeSlot-ba kerül
    placeOption(activeSlot, optId)
    setActiveSlot(null)
  } else {
    // Automatikus: első üres slot
    const firstEmpty = definition!.blanks
      .sort((a, b) => a.orderIndex - b.orderIndex)
      .find(b => slots[b.key] === null)
    if (firstEmpty) placeOption(firstEmpty.key, optId)
  }
}

const placeOption = (blankKey: string, optId: string) => {
  const prevOptId = slots[blankKey]
  setSlots(prev => ({ ...prev, [blankKey]: optId }))
  setPool(prev => {
    const withoutNew = prev.filter(o => o.id !== optId)
    if (prevOptId) {
      // Az előző opció visszakerül a pool végére
      const prev2 = definition!.blanks.flatMap(b => b.options).find(o => o.id === prevOptId)
      if (prev2) return [...withoutNew, { ...prev2, blankKey: definition!.blanks.find(b => b.options.some(o => o.id === prevOptId))!.key }]
    }
    return withoutNew
  })
}

const handleSlotClick = (blankKey: string) => {
  if (slots[blankKey]) {
    // Visszahelyez a pool-ba
    const optId = slots[blankKey]!
    setSlots(prev => ({ ...prev, [blankKey]: null }))
    const opt = definition!.blanks.flatMap(b => b.options).find(o => o.id === optId)!
    const bk = definition!.blanks.find(b => b.options.some(o => o.id === optId))!.key
    setPool(prev => [...prev, { ...opt, blankKey: bk }])
    setActiveSlot(null)
  } else {
    setActiveSlot(prev => prev === blankKey ? null : blankKey)
  }
}
```

Submit:
```typescript
const handleSubmit = async () => {
  setSubmitting(true)
  try {
    const res = await fillInBlankApi.submit(missionId, {
      answers: Object.fromEntries(
        Object.entries(slots).filter(([, v]) => v !== null) as [string, string][]
      )
    })
    setResult(res.data)
    if (res.data.passed) {
      // auto complete-step-et a szülő (Group Player) hívja az onComplete-n keresztül
      onComplete()
    }
  } finally {
    setSubmitting(false)
  }
}
```

**Render struktúra:**

Ha `alreadyPassed`: Banner: "Ezt a feladatot már sikeresen teljesítetted." + "Következő →" gomb (`onComplete()`)

Ha `result` és `!result.passed`: visszajelzés + "Újra" gomb (→ `setResult(null)`, `initPool(definition!)`, `setSlots(...)`)

Ha `result` és `result.passed`: ez az ág nem jelenik meg (onComplete már hívva volt)

Fő render: a `definition.templateText` alapján renderelt szöveg inline blank slot-okkal, alatta pool chip-ek.

**A templateText renderelése — `[[blank_N]]` szintaxis, inline BlankSlot:**

A templateText feldarabolása: szöveg részek és blank slot-ok váltakozva.

Regex: `/\[\[(\w+)\]\]/g` — minden `[[kulcsnev]]` mintát megtalál, a match[1] a kulcs neve (pl. `blank_1`).

Algoritmus: a regex matchek között lévő szöveg `type: "text"` részként, a matchek `type: "blank"` részként kerülnek egy `parts[]` tömbbe. A `parts[]` React elemekre képezve: szöveg → `<span>`, blank → `<BlankSlot blankKey={...} />`.

`BlankSlot` renderelési állapotok (inline `<Box component="span">`):
- **Üres, nem aktív:** aláhúzott téglaszerű box, `"___"` placeholder, szürke szegély, `cursor: pointer`
- **Üres, aktív** (user rákattintott, várja az opciót): kék szegély + kék háttér kiemelés
- **Kitöltött:** az opció szövege belül, sárga/warning háttér — kattintásra visszakerül a pool-ba
- **Kitöltött, result megjelenítés után:** zöld (`correct: true`) vagy piros (`correct: false`) + ikon

---

24. **`MissionGroupPlayer.tsx` + route — ÚJ fájl**

Fájl: `src/pages/play/MissionGroupPlayer.tsx`

```typescript
// Route: /play/group/:groupId
```

State:
```typescript
const { groupId } = useParams<{ groupId: string }>()
const navigate = useNavigate()
const [groupMissions, setGroupMissions] = useState<GroupMissionsResponse | null>(null)
const [progress, setProgress] = useState<GroupProgressResponse | null>(null)
const [loading, setLoading] = useState(true)
const [completing, setCompleting] = useState(false)
const [error, setError] = useState<string | null>(null)
```

Derived (useMemo):
```typescript
const currentMission = useMemo<MissionInGroupResponse | null>(() => {
  if (!groupMissions || !progress) return null
  if (progress.completed) return null
  return groupMissions.missions.find(m => m.id === progress.nextMissionId) ?? null
}, [groupMissions, progress])

const stepNumber = useMemo(() => {
  if (!progress) return 1
  return (progress.completedMissionIds?.length ?? 0) + 1
}, [progress])
```

Betöltési szekvencia (mount):
```typescript
useEffect(() => {
  const load = async () => {
    try {
      // 1. Group missions betöltése
      const missionsResp = await groupProgressApi.get... // wait, ez groupApi
      const [missionsResp] = await Promise.all([
        groupApi.getMissions(groupId!)
      ])
      setGroupMissions(missionsResp.data)

      // 2. Progress betöltése
      let prog: GroupProgressResponse
      try {
        const progResp = await groupProgressApi.get(groupId!)
        prog = progResp.data
      } catch (err: any) {
        if (err.response?.status === 404) {
          // Nincs progress → létrehozás
          try {
            const startResp = await groupProgressApi.start(groupId!)
            prog = startResp.data
          } catch (startErr: any) {
            if (startErr.response?.status === 409) {
              // Versenyhelyzet: valaki már elindította → GET újra
              const retryResp = await groupProgressApi.get(groupId!)
              prog = retryResp.data
            } else throw startErr
          }
        } else throw err
      }
      setProgress(prog)
    } catch (e) {
      setError("Nem sikerült betölteni a feladatot.")
    } finally {
      setLoading(false)
    }
  }
  load()
}, [groupId])
```

`handleCompleteStep`:
```typescript
const handleCompleteStep = async () => {
  if (!groupId || completing) return
  setCompleting(true)
  try {
    const res = await groupProgressApi.completeStep(groupId)
    setProgress(res.data)
  } catch (err: any) {
    // 400 FILL_IN_BLANK_NOT_PASSED: nem jelenítünk meg snackbárt, mert ez nem kéne megtörténjen
    // (a FillInBlankView csak passed=true esetén hívja)
    console.error("complete-step failed", err)
  } finally {
    setCompleting(false)
  }
}
```

Render:
```tsx
// Header: lépésjelző + vissza gomb
<Box sx={{ display: "flex", justifyContent: "space-between", p: 2 }}>
  <Button onClick={() => navigate(`/star-systems/${groupMissions.starSystemId}`)}>← Vissza</Button>
  <Typography>{groupMissions.groupName} — {stepNumber} / {groupMissions.missions.length} lépés</Typography>
</Box>

// Tartalom: al-misszió típus alapján
{progress.completed ? (
  <CompletionScreen
    groupName={groupMissions.groupName}
    onBack={() => navigate(`/star-systems/${groupMissions.starSystemId}`)}
  />
) : currentMission ? (
  <>
    {currentMission.missionType === "CONTENT" && (
      <ContentPlayer missionId={currentMission.id} onComplete={handleCompleteStep} />
    )}
    {currentMission.missionType === "FILL_IN_BLANK" && (
      <FillInBlankView missionId={currentMission.id} onComplete={handleCompleteStep} />
    )}
    {currentMission.missionType === "QUIZ" && (
      <QuizPlayerComponent missionId={currentMission.id} onComplete={handleCompleteStep} />
    )}
  </>
) : null}
```

`CompletionScreen`: egyszerű komponens — group neve + "Group teljesítve!" üzenet + "Vissza a csillagrendszerhez" gomb.

**Router bővítés (`router/index.tsx`):**
```typescript
{ path: "play/group/:groupId", element: <MissionGroupPlayer /> },
```

---

25. **`StarSystemDetailPage.tsx` — teljes újraírás**

Fájl: `src/pages/star-system-detail/StarSystemDetailPage.tsx`

State:
```typescript
const { id } = useParams<{ id: string }>()
const navigate = useNavigate()
const [data, setData] = useState<StarSystemDetailResponse | null>(null)
const [progressMap, setProgressMap] = useState<Map<string, GroupDisplayProgress>>(new Map())
const [loading, setLoading] = useState(true)
const [error, setError] = useState<string | null>(null)
```

Betöltés:
```typescript
useEffect(() => {
  const load = async () => {
    try {
      const resp = await apiClient.get<StarSystemDetailResponse>(`/star-systems/${id}/with-missions`)
      setData(resp.data)

      // Párhuzamos progress betöltés minden group-ra
      const groupItems = resp.data.items.filter((i): i is StarSystemGroupItem => i.type === "GROUP")
      const progressResults = await Promise.allSettled(
        groupItems.map(g => groupProgressApi.get(g.id))
      )

      const map = new Map<string, GroupDisplayProgress>()
      groupItems.forEach((group, idx) => {
        const result = progressResults[idx]
        if (result.status === "fulfilled") {
          const p = result.value.data
          map.set(group.id, {
            status: p.completed ? "COMPLETED" : "IN_PROGRESS",
            completedCount: p.completedMissionIds?.length ?? 0,
            totalCount: group.missions.length,
          })
        } else {
          // 404 = NOT_STARTED
          map.set(group.id, { status: "NOT_STARTED", completedCount: 0, totalCount: group.missions.length })
        }
      })
      setProgressMap(map)
    } catch {
      setError("Nem sikerült betölteni a csillagrendszert.")
    } finally {
      setLoading(false)
    }
  }
  load()
}, [id])
```

Render (megtartja a retro UI keretet — csak a misszió lista belseje változik):
```tsx
// Régi: data.missions.map(mission => ...)
// Új:
{data.items.map((item) => (
  item.type === "GROUP" ? (
    <GroupCard
      key={item.id}
      group={item}
      progress={progressMap.get(item.id) ?? { status: "NOT_STARTED", completedCount: 0, totalCount: item.missions.length }}
      onStart={() => navigate(`/play/group/${item.id}`)}
    />
  ) : (
    <MissionCard
      key={item.id}
      mission={item}
      onStart={() => {
        if (item.missionType === "QUIZ") navigate(`/play/quiz/${item.id}`)
        else if (item.missionType === "CONTENT") navigate(`/play/content/${item.id}`, { state: { starSystemId: id } })
        else navigate(`/play/quiz/${item.id}`) // fallback
      }}
    />
  )
))}
```

`GroupCard` render:
- Group neve + "(N misszió)"
- Badge/chip alapján progress state:
  - `NOT_STARTED`: kék "Kezdd el" gomb
  - `IN_PROGRESS`: sárga "Folytatás" gomb + `"{completedCount}/{totalCount}"` szöveg
  - `COMPLETED`: zöld ✓ + szürke "Újra" gomb

Régi `data.missions.length` count → `data.items.length` (items include both groups and standalone missions)

### Lezárás

26. **Teljes flow teszt:** admin létrehoz star system-et → group-ot CONTENT + FILL_IN_BLANK + QUIZ missionnel → user megnyitja → Group Player indul → CONTENT olvas (Load More teszt hosszú tartalommal) → FILL_IN_BLANK kitölt (nem sikerül → újra → sikerül) → QUIZ elvégez → group teljesítve → visszatér a star systemre → újra belép → progress megmarad → folytatás a helyes lépéstől
