# LégyMérnök.hu — Frontend Újratervezés és Engagement Funkciók (2026)

## 1. Kiindulási helyzet: a jelenlegi UI egy demó felület

A backend funkcionálisan kész és sokrétű (Mission Group, CONTENT, FILL_IN_BLANK, QUIZ, CODING,
Gitea integráció, RBAC, feature flag-ek), de a frontend eddig kizárólag arra szolgált, hogy ezt a
backendet tesztelni lehessen — **nem egy végleges, végiggondolt terméki felület**. Ennek konkrét,
kódban is látható jelei:

- **Egyazon fogalomnak (misszió létrehozása/szerkesztése) két, teljesen különálló UI-ja van.**
  Az admin `MissionEdit.tsx`-en beállítja a misszió alapadatait, de **QUIZ típusnál egy külön
  gomb** ("Kvíz szerkesztő") a teljesen más dizájnú, nyers Monaco JSON-szerkesztős
  `/forge/:missionId` oldalra navigál át — a kadétoknak szánt Mission Forge oldalra. Ugyanez
  igaz CODING-ra: `MissionEdit.tsx`-ben van egy `MissionFileEditor` tab, DE emellett létezik a
  teljesen külön `MissionForgePage.tsx` is, ami ugyanazt a repót más UI-val szerkeszti.
- **A landing oldal és a bejelentkezés utáni "pilótafülke" minimalista, nem egységes vizuális
  nyelvet használ** — nincs koherens design system, csak Material UI alapértelmezések + pár
  RetroUI.css class.
- **A user-oldali misszió-lejátszó felületek (CONTENT, FILL_IN_BLANK, QUIZ, CODING, Group Player)
  külön-külön lettek megoldva**, saját layout-tal, nem egy közös "player shell" komponensben — ez
  látszik is: van, ami mobilon használható, van, ami nem (pl. a CODING Monaco Editor).
- **Nincs semmilyen engagement/visszatérési mechanika** (streak, barát, napi cél) — pedig a projekt
  eredeti motivációja kifejezetten az volt, hogy a doomscrollingot váltsa ki egy tanulható,
  "magába húzó" alternatívával (ld. [[project_legymernok]] memória, `new_direction_2026.md`,
  `ux_ui_terv.md`).

**A cél ezután:** nem új funkciókat írni a backendbe (az megvan), hanem **egy egységes, mobile-first,
retro-sci-fi hangulatú, Duolingo-szerűen "sticky" terméki felületet** tervezni és megépíteni a
meglévő API-k fölé — plusz néhány, kifejezetten az elköteleződést szolgáló új funkciót (streak,
barátok, saját profil).

---

## 2. Tervezési alapelvek

1. **Mobile-first, de desktop-kompatibilis.** Minden új oldal elsőként mobil viewportra
   (360–430px) tervezve, utána bővítve desktopra — nem fordítva, ahogy eddig.
2. **Egy fogalom, egy szerkesztő.** Egy misszió (bármilyen típusú) létrehozása és szerkesztése
   **egyetlen oldalon** történjen, típusfüggő, beágyazott szerkesztő-panellel — sosem kell külön
   oldalra navigálni "a tényleges tartalom" szerkesztéséhez.
3. **Egy fogalom, egy lejátszó shell.** A user-oldali misszió-lejátszás (CONTENT, FILL_IN_BLANK,
   QUIZ, CODING, és ezek Group Player-en belüli megjelenése) egy közös `MissionPlayerShell`
   layout-ot használjon (fejléc, progress, navigáció egységesen), típusfüggő tartalommal középen.
4. **A retro-sci-fi hangulat megmarad, de letisztultabban.** Nem "összedobott" pixel art +
   Material UI keverék, hanem egy tudatos design system: űr-kék/fekete alap, neon accent színek,
   konzisztens tipográfia (ld. `ux_ui_terv.md` már meglévő irányai), animációk egy helyen
   definiálva (nem ad-hoc, oldalanként újraírva).
5. **Duolingo-mintázat:** napi cél, streak, azonnali vizuális visszajelzés minden teljesítésnél,
   barátok/követés a szociális nyomás miatt, saját profil a láthatóság/büszkeség miatt.

---

## 3. Design system

**Döntés:** Material UI marad az alap (kevesebb átírás, a meglévő komponensek — Monaco Editor
wrapper, admin táblák, form-ok — megmaradnak), de ráépül egy **tudatos MUI theme + token-réteg**:

- `theme/tokens.ts` — színpaletta (mély űr-kék/fekete alap, cián/magenta/zöld neon accentek —
  a `ux_ui_terv.md`-ben már lefektetett irány), tipográfia-skála (Orbitron/Space Grotesk fejléc,
  Inter/Space Grotesk body, Fira Code kód), spacing-skála, border-radius, árnyék/glow stílusok.
- `theme/components.ts` — MUI komponens-override-ok (`MuiButton`, `MuiCard`, `MuiTextField` stb.)
  egy helyen, hogy minden gomb/kártya/input alapból a retro-sci-fi stílust kapja, ne kelljen
  oldalanként `sx` prop-okkal újra megírni.
- `components/shared/` — közös, projekt-specifikus komponensek: `GlowCard`, `NeonButton`,
  `ProgressRing`, `StreakFlame`, `XpBadge`, `StarfieldBackground` (a landing oldal csillag-háttere
  újrafelhasználható komponensként, nem csak a landing oldalra hardkódolva).
- **`framer-motion`** egységesen az átmenetekhez/mikroanimációkhoz (már használatban, de
  konzisztensen kell alkalmazni, nem csak néhol).

---

## 4. Admin oldal — egységes tartalom-szerkesztő

### 4.1 A jelenlegi szétforgácsolt flow megszüntetése

| Ma | Új |
|---|---|
| `MissionEdit.tsx` alapadatok + `/forge/:id` külön oldal a QUIZ JSON-hoz | Egy `MissionEditorPage`, ahol a QUIZ típusnál egy beágyazott **`QuizBuilder`** komponens jelenik meg (kérdés/opció UI, nem nyers JSON) |
| `MissionEdit.tsx` fájl-tab + külön `MissionForgePage.tsx` a CODING repóhoz | Egy beágyazott **`CodeMissionEditor`** komponens (fájlfa + Monaco), ugyanazon az oldalon |
| `ContentEditor` külön tab-on, sima textarea | Beágyazott **`MarkdownStudio`** (ld. 4.2) |
| `FillInBlankEditor` külön tab-on | Marad beágyazva, de a `MarkdownStudio` toolbar-elemeit is megkapja a template szöveg szerkesztéséhez |

**Elv:** a `MissionEditorPage` egyetlen komponens, ami az alapadat-form alatt **közvetlenül**
megjeleníti a típusfüggő tartalom-szerkesztőt (nem tab-váltással eldugva, nem külön route-ra
navigálva) — a szerkesztő és a preview mindig látható, amint a típus ki van választva.

Ez a komponens-réteg **közös a kadét Mission Forge-dzsal is** — a `MissionEditorPage` ugyanazokat
az építőelemeket (`QuizBuilder`, `CodeMissionEditor`, `MarkdownStudio`) használja, csak más
jogosultsági/route kontextusban (`/forge/new`, `/forge/:id` a kadétoknak, `/admin/missions/:id` az
adminnak) — **nem két külön implementáció**, hanem egy komponens-készlet, két belépési ponttal.

### 4.2 `MarkdownStudio` — intuitívabb tartalom-szerkesztő

A jelenlegi `ContentEditor`/`MarkdownEditor` egy sima textarea + preview. Az új verzió:

- **Formázó toolbar** a textarea felett: H1/H2/H3, félkövér, dőlt, lista, számozott lista,
  kódblokk, idézet, link, kép — mindegyik a kurzor/kijelölés köré szúrja be a megfelelő markdown
  szintaxist (pl. kijelölt szöveg + Bold gomb → `**kijelölt szöveg**`).
- **Split view desktopon** (szerkesztő | élő preview egymás mellett), **tab-váltás mobilon**
  (Szerkesztés / Előnézet tab, mert egymás mellett nem fér el).
- **"Blank hozzáadása" gomb** (FILL_IN_BLANK-nál) ugyanebbe a toolbar-ba integrálva, nem külön UI.
- Alapja lehet `@mdxeditor/editor` vagy egy saját, `react-markdown` + kézzel írt toolbar
  kombináció — implementáció közben eldöntendő, de a UX-elvárás (toolbar + élő preview + mobilon
  is használható) nem alku tárgya.

### 4.3 `QuizBuilder` — a jelenlegi nyers JSON-szerkesztés helyett

Egy tényleges form-alapú UI: kérdések listája, mindegyikhez szöveg + pontszám + opciók
(szöveg + helyes/helytelen checkbox), drag-handle a sorrendhez, "+ Kérdés hozzáadása" /
"+ Opció hozzáadása" gombok. A `quiz.json` struktúra (ld. `backend/CLAUDE.md`) változatlan marad —
csak a szerkesztő UI vált nyers Monaco-ról form-ra. Mentéskor a builder állítja össze a JSON-t és
ugyanazon a `/forge/{missionId}/save` endpointon küldi, mint eddig.

### 4.4 Star System fa-szerkesztő — megmarad, finomodik

A `mobile-friendly.md`-ben már megtervezett fa-struktúra (Star System → Group → Mission,
`[↑][↓][→][←]` gombokkal) koncepcionálisan jó és marad — csak vizuálisan kap egy retro-sci-fi
átdolgozást (a jelenlegi Material UI lista helyett kártyás, ikonos, drag-and-drop-ra előkészített
fa nézet).

---

## 5. Landing oldal és dashboard

### 5.1 Landing (nem authentikált)

A jelenlegi `HeroSection`/`FeaturesSection`/`AboutSection`/`FaqSection` szerkezet marad
(tartalmilag jó bontás), de vizuálisan újratervezve:

- **`StarfieldBackground`** — közös, paraméterezhető csillag-háttér komponens (canvas vagy CSS
  particle), amit a landing ÉS a bejelentkezés utáni dashboard ÉS a Star Map is használ közös
  vizuális szálként (ma a `SpaceStationCanvas.tsx` csak a landingen él, izoláltan).
- A hero szekció kap egy karakter-animációt (a `new_direction_2026.md`-ben már megtervezett
  "barátságos robot" — ha még nincs asset, egyszerű SVG/Lottie placeholder-rel indulhat).
- CTA-k, kártyák a fenti design system komponenseivel (`GlowCard`, `NeonButton`).

### 5.2 Dashboard (authentikált "pilótafülke")

Jelenleg minimális. Új felépítés, Duolingo-inspirált információs hierarchiával felülről lefelé:

1. **Streak + napi cél sáv** (ld. 7.1) — a legfelső, legszembetűnőbb elem.
2. **"Folytasd onnan, ahol abbahagytad"** kártya — az utolsó aktív Star System/Group/Mission.
3. **Star Map előnézet** — a felfedezett rendszerek mini-térképe, "Térkép megnyitása" CTA-val.
4. **Barátok aktivitása** (ld. 7.2) — kompakt lista, ki mit teljesített mostanában.

---

## 6. User-oldali misszió-lejátszás — egységes, mobil-first shell

### 6.1 `MissionPlayerShell`

Egy közös layout-komponens minden lejátszási módhoz (standalone CONTENT/QUIZ, Group Player belső
lépései, CODING):

- Fejléc: vissza-gomb, misszió/csoport neve, progress indikátor (`x / y lépés`, vagy CODING esetén
  "mentve" állapot).
- Középső terület: a típusfüggő tartalom (ma is megvan komponensenként, csak közös keret nélkül).
- Alsó akció-sáv: "Következő" / "Beküldés" / "Mentés" — mindig ugyanabban a pozícióban, mobilon a
  képernyő aljához rögzítve (thumb-reach zóna), nem a tartalom végén elszórva.

Ez az egy komponens váltja ki a jelenlegi `ContentMissionPage`, `GroupPlayerPage`,
`QuizPlayerPage`, `CodingMissionPage` egymástól független layout-jait — mindegyik csak a
*középső tartalmat* adja a shell-nek.

### 6.2 CODING misszió mobilon

A Monaco Editor marad (döntés: nem váltunk CodeMirror-ra — kevesebb migrációs kockázat, a
desktop UX nem sérül), de köré:

- **Teljes képernyős szerkesztő mód mobilon** — a fájlfa és a diagnosztikai panel alapból
  összecsukva, bottom-sheet-ként előhúzható, hogy a szerkesztő maga kapja a maximális helyet.
- **Virtuális billentyűzet feletti gyorsgombok sáv** (zárójelek, tab, indentálás) — mobil kódolás
  legnagyobb súrlódási pontja jelenleg ez, nem maga a Monaco.
- A "Mentés" / "Diagnosztika futtatása" gombok a `MissionPlayerShell` alsó akció-sávjában, nem a
  fájlfa fölött elrejtve.

### 6.3 Star System Detail nézet

A `mobile-friendly.md`-ben tervezett progress-badge-es lista (Group/standalone Mission státusszal)
tartalmilag jó, csak vizuálisan igazodik az új design system-hez, és a kártyák mobilon egy
oszlopban, nagy touch targettel jelennek meg.

---

## 7. Új engagement funkciók

### 7.1 Streak (napi sorozat)

**Backend:**
- `Cadet` entitás bővítése: `currentStreak INT DEFAULT 0`, `longestStreak INT DEFAULT 0`,
  `lastActivityDate DATE`.
- Egy countable "aktivitás" = bármilyen teljesített lépés (Group step complete, standalone
  CONTENT elolvasva/"Következő", QUIZ beküldés, FILL_IN_BLANK sikeres beküldés, CODING sikeres
  verifikáció). Ezekhez a meglévő endpointokhoz (`complete-step`, quiz submit, fill-in-blank
  submit, mission-verification callback) egy közös `StreakService.recordActivity(cadetId)` hívás
  kerül.
- `recordActivity` logika: ha `lastActivityDate == ma` → nincs teendő. Ha `== tegnap` →
  `currentStreak++`. Ha korábbi vagy null → `currentStreak = 1`. `longestStreak = max(longestStreak,
  currentStreak)`. `lastActivityDate = ma`.
- **Nincs külön ütemezett job** — a streak-törés (ha valaki kihagy egy napot) nem aktívan
  detektálva "éjfélkor törik", hanem lustán: a következő aktivitáskor derül ki, hogy a
  `lastActivityDate` nem tegnapi, és akkor nullázódik 1-re. A frontend-en megjelenő "jelenlegi
  streak" mindig ezt az utoljára számolt értéket mutatja — MVP-ben elfogadható egyszerűsítés,
  Stage 2-ben jöhet "streak freeze"/emlékeztető push.
- `GET /api/auth/me` válasz bővül `currentStreak`, `longestStreak` mezőkkel (már úgyis minden
  oldal lekéri ezt bejelentkezve).

**Frontend:**
- `StreakFlame` komponens a dashboard tetején és a `MissionPlayerShell` fejlécében — lángikon +
  szám, teljesítéskor rövid "streak +1" mikroanimáció.

### 7.2 Barátok / követés

**Döntés a mechanikára:** a user kifejezetten Duolingo-analógiát adott referenciaként — a Duolingo
**egyirányú követést** használ (nincs elfogadás, mint egy Twitter follow), ez egyszerűbb és jobban
illik a "szociális nyomás, ki hol tart" motivációhoz, mint egy kétirányú barát-kérés/elfogadás
flow. **Javaslat: egyirányú `Follow` reláció**, nem a Wrenchly-stílusú kétirányú
`FriendRequest`/PENDING-ACCEPTED modell.

**Backend:**
- Új tábla: `follows (follower_id UUID, followee_id UUID, created_at TIMESTAMPTZ, PRIMARY KEY
  (follower_id, followee_id))` — mindkét irányú FK `cadets(id)`-re.
- `FollowService`: `follow(followerId, followeeId)`, `unfollow(...)`,
  `getFollowing(cadetId)`, `getFollowers(cadetId)`.
- `GET /api/cadets/search?username=...` — username-alapú keresés (a `cadets.username` már unique
  és indexelt).
- `POST /api/cadets/{id}/follow` / `DELETE /api/cadets/{id}/follow` — `mission:start`-tal azonos
  szintű, bejelentkezett-user permission (nincs szükség új finomszemcsés permission-re, mert
  minden bejelentkezett kadét követhet bárkit).

**Frontend:**
- Felhasználó-kereső (profil oldalon vagy külön "Flotta" oldalon).
- Dashboard "Barátok aktivitása" kártya (ld. 5.2) — kiket követek, mit teljesítettek mostanában
  (egyszerű, legutóbbi N aktivitás lekérdezés a követett cadet-ek `MissionGroupStepCompletion`/
  `FillInBlankAttempt`/`MissionResult` rekordjaiból, időrendben).

### 7.3 Saját profil oldal

**Backend:**
- `GET /api/cadets/{id}/profile` — publikus (bejelentkezve bárki más profilját is megnézheti):
  `username`, `fullName`, `avatarUrl`, `currentStreak`, `longestStreak`, `totalCompletedMissions`,
  `totalCompletedGroups`, `followerCount`, `followingCount`, `memberSince`.
- A számított mezők (`totalCompletedMissions` stb.) egyszerű aggregáló query-k a meglévő
  `cadet_missions`/`mission_group_progress` táblákon — nem kell hozzá új tábla.

**Frontend:**
- `ProfilePage` — avatar, alapadatok, streak, statisztika-kártyák, "Kövess" gomb (ha nem saját
  profil), badge-ek helye (Stage 2-ben bővíthető, MVP-ben elég a számok).
- Saját profil szerkesztése (avatar, fullName, username) — ez a Settings oldalon már részben
  létezik, csak összekötve a ProfilePage-dzsel.

---

## 8. Nem ebben a körben (tudatosan kizárva)

- **XP/pontszám-rendszer és jelvények** — a `gamification_roadmap.md`-ben tervezett `reward_xp`
  mechanika nem része ennek a körnek; a streak és a barát-rendszer önmagában is jelentős
  engagement-javulást hoz, az XP-rendszer egy jól elkülöníthető, külön feature.
- **Squad/csapat rendszer** (`gamification_roadmap.md` Fázis 2) — külön, nagyobb terv.
- **`subscription_box_pivot.md`** fizikai termék iránya — ez egy más léptékű üzleti döntés, nem
  frontend munka, nincs átfedés ezzel a tervvel.
- **Blockly/vizuális programozás, Mobile Coding kártya-alapú mód** (`mobile-friendly.md` Stage 2) —
  a CODING mobil-UX javítása (ld. 6.2) ebben a körben elég; a teljes alternatív interakciós mód
  külön terv.
- **AI keresés (`ai_embedding.md`), Social AI (`social_ai.md`)** — függetlenek ettől a körtől.

---

## 9. Megvalósítási sorrend (egy PR-on belül, checklist-szerűen)

A user döntése alapján ez **egyetlen nagy PR**-ban valósul meg (nem külön fázis-PR-ok), de a
munka belső sorrendje logikailag így épül egymásra:

1. **Design system alapok** — `theme/tokens.ts`, `theme/components.ts`, közös `components/shared/`
   komponensek (`StarfieldBackground`, `GlowCard`, `NeonButton`, `StreakFlame`, `ProgressRing`).
2. **Backend: streak + follow + profil endpointok** — a fenti 7.1–7.3 szerint, Flyway migrációval
   (`cadets` tábla bővítés + `follows` tábla), párhuzamosan futtatható a frontend design system
   munkával.
3. **`MissionPlayerShell`** + a 4 meglévő lejátszó-oldal átalakítása, hogy csak a tartalmat adják.
4. **`MarkdownStudio`, `QuizBuilder`, `CodeMissionEditor`** komponensek + **`MissionEditorPage`**
   egyesítése (ez váltja ki a `MissionForgePage`/`MissionEdit` kettősséget).
5. **Landing + Dashboard újratervezés** a design systemre + streak/barátok kártyákra építve.
6. **Profil oldal + felhasználó-kereső.**
7. **Mobil CODING UX finomítás** (bottom-sheet fájlfa, gyorsgomb-sáv).
8. **Star System fa-szerkesztő vizuális átdolgozása.**
9. Végigtesztelés mobil viewporton (360px, 390px, 430px) minden érintett oldalon + meglévő
   Cypress/Vitest tesztek frissítése az átalakított komponensekre.

---

## 10. Nyitott kérdések (implementáció közben eldöntendő, nem blokkolja a tervet)

- `MarkdownStudio` alapja: kész könyvtár (`@mdxeditor/editor`) vs. saját toolbar + `react-markdown`
  — implementáció közben, a konkrét toolbar-igények alapján dől el.
- Karakter/robot maszkot (a `new_direction_2026.md` "barátságos robot" narrátora) — kész
  asset/illustrátor hiányában induljon-e egyszerű SVG/emoji-szintű placeholder-rel, vagy várjunk
  vizuális asset-re — ez terméki döntés, nem technikai.
- Streak "freeze"/emlékeztető push-notifikáció — Stage 2, nem blokkolja az MVP streak-et.
