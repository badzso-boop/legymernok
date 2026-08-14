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
   **Fontos árnyalás, hol ér véget a "mobile-first" és hol kezdődik "mobile-usable":**
   - **Kadét-oldali felületek (landing, dashboard, misszió-lejátszás, profil, barátok,
     Star Map)** — ezek a termék *core* felülete, a doomscrolling-helyettesítő élmény. Ezeknél
     a mobile-first nem csak reszponzivitást jelent, hanem hogy a fő interakció (thumb-reach
     akció-sáv, egy oszlopos layout, nagy touch target, teljes képernyős player) **mobilon lesz
     elsőként megtervezve és tesztelve**, a desktop nézet ebből származik.
   - **Admin/content-creation felületek (Mission Editor, QuizBuilder, CodeMissionEditor, Star
     System fa-szerkesztő)** — ezek **reszponzívak és használhatók** mobilon (nem törnek el, nem
     kell vízszintesen scrollozni, a form-ok egy oszlopban rendeződnek), de **nem lesznek mobilra
     optimalizálva elsődlegesen** a mélyebb tartalom-szerkesztésnél. **Ez tudatos, nem hanyagság**
     — a termék motivációja (doomscrolling-helyettesítés) a *tanulói* oldalra vonatkozik, nem a
     tartalom-adminisztrációra. Ezen belül két világosan elváló réteg van:

     **Mobilról is teljes értékűen elvégezhető "alap" admin műveletek** (ez a lista NEM
     kompromisszumos, ugyanolyan jól kell működnie, mint desktopon):
     - Star System / Group / Mission listák böngészése, keresés, szűrés
     - Alapadatok szerkesztése bármely misszió-típusnál (név, leírás, nehézség, sorrend) — ez
       maga a `MissionEditorPage` felső form-ja, ami mindig sima, egy-oszlopos mobil form
     - Sorrend-módosítás a fa-szerkesztőben (`[↑][↓][→][←]` gombok — ezek eleve gombalapúak, nem
       drag-and-drop-ra épülnek, így mobilon ugyanolyan jól működnek, mint desktopon)
     - Feature flag be/kikapcsolása, user lista + role-hozzárendelés, feedback-lista áttekintése
     - CONTENT szöveg gyors módosítása a `MarkdownStudio`-ban (mobilon tab-váltós
       szerkesztés/előnézet mód, nem split-view — de a toolbar és a szerkesztés maga teljes
       értékű)
     - FILL_IN_BLANK blank/opció hozzáadása-szerkesztése (form-alapú lista, nem drag-heavy — a
       "Blank hozzáadása" és opció be/ki jelölés mobilon is kényelmes)
     - `QuizBuilder` alap használata: kérdés/opció hozzáadása, szövegszerkesztés, helyes válasz
       jelölése — a sorrendezés itt is fel/le gombokkal megy, nem drag handle-lel, kifejezetten
       azért, hogy mobilon is működjön

     **Desktop-ajánlott, de mobilon sem törik el, csak szűkebb élmény:**
     - `CodeMissionEditor` (fájlfa + Monaco) admin oldali, sablon-előkészítő használata — egy
       fájl tartalmának gyors módosítása mobilon is megy, de a fájlfa böngészése/rendezgetése
       sok fájlnál kényelmetlenebb egy kis képernyőn
     - Szélesebb admin táblázatok sok oszloppal (pl. role/permission mátrix) — mobilon
       vízszintesen scrollozható vagy leegyszerűsített kártyás nézetre vált, de nem elsődleges
       optimalizálási cél
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
6. **A meglévő i18n (magyar/angol) végig megmarad — nem visszalépés.** A projekt jelenleg is
   teljesen kétnyelvű (`src/i18n/config.ts`, `en`/`hu` `resources`, `useTranslation()` hook
   mindenhol, ld. `frontend/CLAUDE.md` konvenciói). Az újratervezés **egyetlen komponense sem
   kaphat hardkódolt magyar (vagy angol) szöveget** — minden új UI-elem (a `MissionEditorPage`,
   `MarkdownStudio`, `QuizBuilder`, `MissionPlayerShell`, a téma-választó, a streak/barátok/profil
   felületek stb.) új `config.ts` kulcsokat kap **mindkét nyelven egyszerre**, ugyanabban a
   struktúrában, ahogy eddig (ld. `mobile-friendly.md` "i18n kulcsok" szekciója — ugyanez a
   minta folytatódik). Ez különösen fontos, mert több komponens **kiváltja** a régi megfelelőjét
   (pl. `MissionForgePage` → `MissionEditorPage`) — a régi kulcsok csak akkor törölhetők
   `config.ts`-ből, ha az adott komponens ténylegesen megszűnik és semmi más nem hivatkozik rájuk
   (ellenőrizve grep-pel a kulcs nevére), különben "élő" fordítási kulcsok vesznek el észrevétlenül.

---

## 3. Theming rendszer — Light / Dark / Space

Az eredeti terv egyetlen, mindig-bekapcsolt "sci-fi csillagos háttér" témát feltételezett. Ez két
szempontból is kevés: (1) nem mindenki akarja a teljes immerzív sci-fi élményt állandóan (pl.
hosszabb CONTENT-olvasásnál zavaró lehet egy mozgó háttér), (2) egy statikus csillag-minta
önmagában valóban "olcsónak" hat — egy modern, prémium-érzetű felülethez ennél többre van
szükség: réteges mélység, finom mozgás, tudatos szín- és fény-nyelv.

**Döntés:** három választható téma, a Settings oldalon állítható, alkalmazásindításkor a mentett
preferenciát (backend `cadets.theme_preference` mező + `localStorage` cache az azonnali
alkalmazáshoz villanás nélkül) tölti be:

| Téma | Kinek | Jelleg |
|---|---|---|
| **Space** (alapértelmezett) | Az igazi termék-élmény, ez adja a márka identitását | Teljes immerzív sci-fi: réteges parallax csillagmező, nebula-gradiensek, glow/HUD elemek |
| **Dark** | Aki a funkcionalitást akarja a teljes sci-fi látvány nélkül (pl. hosszú olvasás, akkumulátor-kímélés) | Sötét, letisztult, a márka szín- és tipográfia-nyelvét megtartja, de statikus háttér, nincs animáció |
| **Light** | Nappali/kültéri használat, elérhetőségi preferencia | Világos alap, ugyanaz a komponens-rendszer, kontraszt-optimalizált |

Mindhárom **ugyanazt a komponens-készletet és layoutot** használja — a téma csak szín-tokeneket és
(Space esetén) egy háttér-réteget cserél, nem külön implementáció.

### 3.1 Token-architektúra

- `theme/tokens.ts` — CSS custom property-alapú token-réteg (`--color-bg-base`,
  `--color-accent-primary`, `--color-accent-secondary`, `--glow-sm/md/lg`, `--radius-*`,
  `--spacing-*`), **témánként külön érték-halmazzal**, hogy a téma-váltás egy `data-theme`
  attribútum cserével azonnal, újratöltés nélkül megtörténjen (MUI `ThemeProvider` ezekből a CSS
  változókból építi a saját palettáját, hogy a Material komponensek és a saját komponensek
  ugyanabból a forrásból színezzenek).
- `theme/typography.ts` — közös minden témában: fejléc `Space Grotesk`, body `Inter`, kód
  `Fira Code`. A tipográfia NEM téma-függő — a márka konzisztenciája a betűkészleten és a
  spacing-en keresztül is érvényesül, nem csak a Space témán.
- `theme/components.ts` — MUI komponens-override-ok egy helyen (`MuiButton`, `MuiCard`,
  `MuiTextField` stb.), a fenti tokenekre hivatkozva — sosem hardkódolt szín/árnyék egy adott
  komponensben.

### 3.2 A "Space" téma kidolgozása — miért nem lesz "olcsó" hatású

A cél nem egy PNG csillag-tapéta, hanem **réteges mélység + finom, ambient mozgás + fény-nyelv**,
a mai prémium UI-trendek (Linear, Arc Browser, Stripe gradiens-hátterek) mintájára, sci-fi
színvilágba öntve:

1. **Réteges parallax csillagmező** (nem egy statikus kép): 3 réteg, eltérő sebességgel/mérettel —
   távoli réteg (apró, halvány pontok, alig látható mozgás), középső réteg (közepes csillagok,
   lassú, folyamatos "twinkle" — opacity-pulzálás, nem pozíció-animáció, hogy ne legyen zavaró),
   közeli réteg (néhány nagyobb, halvány fényudvaros csillag, nagyon lassú driftel). CSS
   transform + `requestAnimationFrame` throttle-lel, **canvas helyett DOM/SVG réteg** — kevesebb
   akkumulátor-terhelés mobilon, és `prefers-reduced-motion`-nál egyetlen kapcsolóval teljesen
   kikapcsolható (statikus csillagmezőre esik vissza).
2. **Nebula-gradiens foltok a háttérben** — nagy, elmosott (`filter: blur()`), lassan pozíciót
   váltó radiális gradiens-foltok (indigo → magenta → cián, alacsony opacitással), NEM éles
   csillag-pontok, hanem szín-mélység a háttérnek — ez adja a "drága" érzetet a lapos fekete
   helyett. A bázisszín is változik: nem tiszta fekete (`#000`), hanem mély indigo-fekete
   (`#05040F`-hez közeli), ami melegebb, kevésbé "üres" hatású.
3. **Alkalmi "hulló csillag"** — ritkán (30–90 másodpercenként, randomizált késleltetéssel), egy
   rövid, halvány csóva-animáció fut át a képernyőn — `aria-hidden`, dekoratív, és
   `prefers-reduced-motion`-nál teljesen kikapcsolva.
4. **Glassmorphism HUD-panelek** — a kártyák (`GlowCard`) nem tömör Material-kártyák, hanem
   félig-áttetsző, `backdrop-filter: blur()` panelek finom, halványan izzó szegéllyel — mintha egy
   űrhajó kijelzőjén lenne az adott panel, nem "rálapozva" a háttérre.
5. **Konzisztens glow-nyelv interakcióknál** — fókusz/hover/aktív állapotok egységes,
   token-vezérelt glow-val (`--glow-accent`), nem oldalanként újra kitalált `box-shadow` értékek.
6. **Mobil teljesítmény-védelem** — a rétegek száma és a csillag-darabszám kisebb viewport-on
   automatikusan csökken (pl. mobil: 1-2 réteg, kevesebb elem), hogy alacsonyabb kategóriás
   telefonokon se legyen frame-drop vagy érezhető akkumulátor-terhelés.

A `StarfieldBackground`/`NebulaLayer` komponensek **paraméterezhetők** (réteg-szám, intenzitás),
így a landing oldal kaphat egy teltebb, "hero" verziót, a dashboard/player felületek egy
visszafogottabb, kevésbé figyelemelvonó verziót — ugyanabból a komponensből.

### 3.3 Dark és Light téma

Nem "Space téma mínusz animáció" egyszerű levágás, hanem saját, tudatos szín-döntés:

- **Dark:** a Space téma alap-paletta-családjából indul (indigo-fekete alap, ugyanazok az accent
  színek), de **statikus** háttér (nincs parallax/nebula-animáció), a glassmorphism helyett sima,
  enyhén emelt felületű kártyák (`elevation`, nem `backdrop-blur`) — letisztultabb, kevésbé
  figyelemelvonó, de vizuálisan még mindig egyértelműen "ugyanaz a márka".
- **Light:** világos alap (nem tiszta fehér, enyhén hűvös szürkésfehér a szemkímélés miatt), az
  accent-színek (cián/magenta) sötétebb, kontrasztosabb változatban (WCAG AA kontraszt-arány
  ellenőrizve szöveg/háttér párokra), a márka-elemek (logó, ikonok) light-variánsban.

### 3.4 Közös komponensek (`components/shared/`)

`GlowCard`, `NeonButton`, `ProgressRing`, `StreakFlame`, `XpBadge`, `StarfieldBackground`,
`NebulaLayer` — mind a token-rendszerből színeznek, egyik téma sincs beléjük hardkódolva.
**`framer-motion`** egységesen az átmenetekhez/mikroanimációkhoz (már használatban, de
konzisztensen kell alkalmazni, nem csak néhol).

### 3.5 Backend — téma-preferencia perzisztálása

- `Cadet` entitás bővítés: `themePreference VARCHAR(10) DEFAULT 'SPACE'` (`SPACE`/`DARK`/`LIGHT`).
- `PUT /api/auth/me/theme` — egyszerű, saját-magára szűkített frissítés (nincs szükség admin
  jogosultságra, mindenki csak a saját preferenciáját írja).
- A Settings oldalon egy 3-állású választó (kártyás preview-val, nem sima dropdown-nal — a user
  lássa is, mit választ).

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

### 4.5 Meglévő admin felületek, amik megmaradnak — csak jól látható helyen

Van néhány admin funkció, ami **funkcionálisan ma is teljes értékű**, csak a jelenlegi
kaotikus/hosszú sidebar-listában könnyen elvész, és emiatt nem is triviális megtalálni:

- **Feature Flag kezelés** (`/admin/feature-flags`, `FeatureFlagList.tsx`) — táblázatos nézet,
  soronként valódi `Switch`-csel be/ki kapcsolható flag, optimista UI-frissítéssel. Backend oldal
  (`FeatureFlagController`/`Service`) teljes CRUD-dal már kész. **Ehhez a redesign-ban nincs
  funkcionális teendő** — csak az egységesített admin navigációban (ld. 4.4-hez hasonlóan
  kártyás/csoportosított menü, nem egy hosszú, differenciálatlan lista) kap egyértelmű, könnyen
  megtalálható helyet, hogy ne kelljen "elveszni" benne, mint ma.
- Hasonlóan megtartandó, csak navigációban jobban rendszerezendő: user/role/permission kezelés,
  admin logok (WebSocket real-time nézet) — ezek se kapnak új funkciót ebben a körben, csak új
  helyet az egységes admin navigációs struktúrában.

---

## 5. Landing oldal és dashboard

### 5.1 Landing (nem authentikált)

A jelenlegi `HeroSection`/`FeaturesSection`/`AboutSection`/`FaqSection` szerkezet marad
(tartalmilag jó bontás), de vizuálisan újratervezve:

- A landing mindig a **Space téma "hero" intenzitású** `StarfieldBackground`/`NebulaLayer`
  párosát kapja (ld. 3.2) — nem regisztrált látogatónak ez a téma-választó nem is jelenik meg
  még, ez a márka bemutatkozó élménye. A mai `SpaceStationCanvas.tsx` izolált, csak-landing
  megoldását váltja ki a közös, paraméterezett komponens.
- A hero szekció kap egy karakter-animációt (a `new_direction_2026.md`-ben már megtervezett
  "barátságos robot" — ha még nincs asset, egyszerű SVG/Lottie placeholder-rel indulhat).
- CTA-k, kártyák a fenti design system komponenseivel (`GlowCard`, `NeonButton`).

### 5.2 Dashboard (authentikált "pilótafülke")

Jelenleg minimális. Új felépítés, Duolingo-inspirált információs hierarchiával felülről lefelé:

1. **Streak + napi cél sáv** (ld. 7.1) — a legfelső, legszembetűnőbb elem.
2. **"Folytasd onnan, ahol abbahagytad"** kártya — az utolsó aktív Star System/Group/Mission.
3. **Star Map előnézet** — a felfedezett rendszerek mini-térképe, "Térkép megnyitása" CTA-val,
   ugyanabból a komponensből, mint a 5.3-ban leírt teljes Star Map (kicsinyítve, nem interaktív).
4. **Barátok aktivitása** (ld. 7.2) — kompakt lista, ki mit teljesített mostanában.

### 5.3 Star Map (csillagtérkép) — teljes újragondolás

A jelenlegi `StarMapCanvas.tsx` egy kézzel írt Canvas 2D rendering, ami konkrétan **nem illeszkedik
sem a design system-hez, sem a mobile-first elvhez**:

- **Vizuálisan zöld "radar/mátrix" stílus** (`VT323` monospace font, zöld szkennelő-vonal,
  koncentrikus radar-körök) — teljesen más nyelvet beszél, mint a többi felület tervezett
  cián/magenta neon sci-fi világa (`ux_ui_terv.md`). Ez egy izolált, sosem összehangolt dizájn-
  sziget.
- **A rendszerek pozíciója `hash(rendszer-id)` alapú pszeudo-random** — nincs mögötte valódi
  struktúra vagy kapcsolat (bár a `new_direction_2026.md` eredetileg gráf-éleket és
  előfeltétel-viszonyokat tervezett a rendszerek közé).
- **Kizárólag egér-eseményekkel működik** (`onMouseMove`/`onClick`) — nincs touch/pinch/pan
  kezelés. Mobilon ez azt jelenti, hogy egy tap találhat egy csillagot (a `onClick` browser-szinten
  fut touch-on is), de a hover-alapú név/koordináta-felfedés és a nagyítás/pásztázás **egyáltalán
  nem működik** — pont a legfontosabb, mobil-only felhasználói rétegnél törik el legjobban.
- **A csillag színe mindig ugyanaz** (`#0f0`, statikus) — nincs vizuális állapot-visszajelzés
  (folyamatban / teljesítve / még el sem kezdett), pedig ez az adat már létezik
  (`CadetMission`/`MissionGroupProgress` rekordok) — az `ux_ui_terv.md` eredeti terve
  ("Villog = Aktuális, Szürke = Zárt, Zöld = Kész") sosem lett ténylegesen bekötve.

**Az új megközelítés:**

1. **A kézzel írt Canvas lecserélése egy dedikált, touch-first gráf-vizualizációs könyvtárra** —
   `react-flow` javasolt (ezt már a `new_direction_2026.md` is megcélozta korábban): natívan van
   pan/zoom/pinch támogatása, node-click/tap kezelés, jól tesztelt mobilon is. Ez önmagában
   megoldja a touch-interakció hiányát, kézzel írt gesztus-kezelés nélkül.
2. **A csillag node-ok a Space-téma design nyelvét kapják** (`GlowCard`/glow-effektek, cián/magenta
   paletta) a zöld radar-stílus helyett — vizuálisan egységes a landing/dashboard/player
   felületekkel, nem egy elszigetelt "más app" érzés.
3. **Valós állapot-alapú node-színezés**: szürke = még nem kezdett, pulzáló cián/glow = aktuális/
   folytatható, zöld pipa-jelvény = teljesítve — a meglévő progress-adatokból számolva (nincs
   szükség új backend endpointra, a `with-missions`/progress lekérdezések már megvannak).
4. **Pozicionálás MVP-ben marad egyszerű, determinisztikus elrendezés** (radiális vagy grid-layout
   az id alapján, hasonlóan a mostanihoz, csak `react-flow` node-koordinátaként) — a valódi
   "előfeltétel-gráf" (melyik rendszer nyit meg melyiket, gráf-élekkel összekötve) egy külön,
   **Stage 2 backend-munka** lenne (`StarSystem.prerequisiteId` mező bevezetése + zárolási logika)
   — ld. 8. szekció, tudatosan NEM ebben a körben.
5. **Dashboard preview** (5.2, 3. pont) ugyanebből a komponensből jön, csak kicsinyítve és
   nem-interaktív módban paraméterezve — nem külön implementáció.

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

- **Star System előfeltétel-gráf** (5.3) — a rendszerek közötti valódi "melyik nyit meg melyiket"
  kapcsolat és zárolási logika (`StarSystem.prerequisiteId` + backend jogosultsági logika) külön
  backend-munka; ebben a körben a Star Map csak vizuálisan/interakciósan újul meg, a pozíciók
  MVP-ben továbbra is egyszerű, determinisztikus elrendezésből jönnek.
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

1. **Téma-rendszer és design system alapok** — `theme/tokens.ts` mindhárom témával (Space/Dark/
   Light), `theme/components.ts`, közös `components/shared/` komponensek (`StarfieldBackground`,
   `NebulaLayer`, `GlowCard`, `NeonButton`, `StreakFlame`, `ProgressRing`), téma-váltó logika
   (`data-theme` attribútum + `localStorage` + backend perzisztálás). Ez az alap, minden más erre
   épül.
2. **Backend: téma-preferencia + streak + follow + profil endpointok** — a fenti 3.5, 7.1–7.3
   szerint, egy közös Flyway migrációval (`cadets` tábla bővítés: `theme_preference`,
   `current_streak`, `longest_streak`, `last_activity_date` + `follows` tábla), párhuzamosan
   futtatható a frontend munkával.
3. **`MissionPlayerShell`** + a 4 meglévő lejátszó-oldal átalakítása, hogy csak a tartalmat adják.
4. **`MarkdownStudio`, `QuizBuilder`, `CodeMissionEditor`** komponensek + **`MissionEditorPage`**
   egyesítése (ez váltja ki a `MissionForgePage`/`MissionEdit` kettősséget).
5. **Landing + Dashboard újratervezés** a design systemre + streak/barátok kártyákra építve.
6. **Profil oldal + felhasználó-kereső.**
7. **Mobil CODING UX finomítás** (bottom-sheet fájlfa, gyorsgomb-sáv).
8. **Star System fa-szerkesztő vizuális átdolgozása.**
9. **Star Map lecserélése `react-flow`-alapú, touch-first vizualizációra** (5.3) — a Canvas 2D
   render megszűnik, node-színezés valós progress-adatból.
10. Végigtesztelés mobil viewporton (360px, 390px, 430px) minden érintett oldalon + meglévő
    Cypress/Vitest tesztek frissítése az átalakított komponensekre.

**Minden fenti lépés keresztmetsző követelménye (nem külön lépés, hanem folyamatos elvárás):**
minden új szöveg azonnal bekerül `config.ts`-be **mindkét nyelven** (`en`/`hu`), a komponens
`useTranslation()`-t használ, sosem hardkódolt stringet — ahogy ma is elvárt konvenció
(`frontend/CLAUDE.md`). Amikor egy régi komponens (pl. `MissionForgePage`) ténylegesen megszűnik
és lecserélődik, az utolsó lépés a hozzá tartozó, máshol nem hivatkozott `config.ts` kulcsok
törlése — nem hagyjuk se élő kódot fordítás nélkül, se holt fordítási kulcsokat a fájlban.

---

## 10. Nyitott kérdések (implementáció közben eldöntendő, nem blokkolja a tervet)

- `MarkdownStudio` alapja: kész könyvtár (`@mdxeditor/editor`) vs. saját toolbar + `react-markdown`
  — implementáció közben, a konkrét toolbar-igények alapján dől el.
- Karakter/robot maszkot (a `new_direction_2026.md` "barátságos robot" narrátora) — kész
  asset/illustrátor hiányában induljon-e egyszerű SVG/emoji-szintű placeholder-rel, vagy várjunk
  vizuális asset-re — ez terméki döntés, nem technikai.
- Streak "freeze"/emlékeztető push-notifikáció — Stage 2, nem blokkolja az MVP streak-et.
