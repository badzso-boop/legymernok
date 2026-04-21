# Captain's Log: LégyMérnök.hu Fejlesztési Napló

Ez a dokumentum a LégyMérnök.hu projekt fejlesztésének történetét örökíti meg, űrhajós napló stílusban.

---

## 🎯 Bejegyzés #18: A Kvíz-Reaktor és a Kohó Teljessé Vétele (Quiz System & Mission Forge Complete)

**Stardate:** 2026.03.18
**Status:** Teljes Kapacitás — Küldetésrendszer Élesítve

A `mission-forge` ág befutott a főáramlatba. Az eddigi legkomplexebb modul — a Kvíz-Reaktor — teljessé vált: a kadétok mostantól valódi, interaktív kvízeket tölthetnek ki, a szerver pontosan értékeli a válaszaikat, és az eredmény azonnal rögzítésre kerül. A biztonsági protokollok gondoskodnak arról, hogy a helyes válaszok titokban maradjanak — a szerver oldal osztályozza, amit a kliens nem láthat. A `isMulti` flag elegánsan jelzi, hogy egy kérdésnél egy vagy több helyes válasz létezik-e, és a frontend ennek megfelelően vált radio gomb és checkbox között. A Küldetés-Kohó szerkesztőfelülete is megkapta a végső összekötő elemeket: az oktatók a teljes munkafolyamatot — a létrehozástól a Gitea verifikációig — a saját Forge-felületeiken keresztül kezelhetik. A session-menedzsment is bekerült: ha egy kvíz tartalma frissül, az oktató egyetlen gombnyomásra ki tudja törölni az összes aktív sessiont, és a kadét automatikusan újrakezdi a friss verzióval.

- **Technikai részletek:**
  - **Quiz Backend:** `QuizSession`, `QuizDefinition`, `MissionResult` entitások. `QuizService`: session indítás, szinkronizáció, beküldés és pontozás. Szerver oldali `stripAnswers()` biztonsági metódus — a helyes válaszok (`isCorrect`) soha nem hagyják el a backendet; helyette az `isMulti` flag kerül kiszámításra és elküldésre.
  - **Quiz Frontend:** `QuizPlayer` komponens időzítővel, navigációval, automatikus checkbox/radio váltással (`isMulti` alapján). `QuizEditor` a kérdésbank szerkesztéséhez, vizuális MULTI/SINGLE_SELECTION jelölővel. `QuizPlayerPage` session-helyreállítással — 404-es hiba esetén a TanStack Query cache resetelése és automatikus újraindítás.
  - **Session Menedzsment:** `DELETE /{missionId}/sessions` endpoint — az oktatók és adminok kényszerrel resetelhetik az összes aktív sessiont, ha a kvíz tartalma megváltozott. Saját `clearAllSessions` validációval (csak a misszió tulajdonosa vagy `mission:edit_any` jogkörű felhasználó törölhet).
  - **Mission Verification:** `MissionVerificationController` — Gitea Action callbackok fogadása HMAC-SHA256 aláírás-ellenőrzéssel, `MissionResult` rögzítése, `VerificationStatus` státuszgép.
  - **GiteaService 3.0:** Masszív bővítés — forge-alapú repókezelés, `createForgeRepo`, fájlfeltöltés, `CreateForgeMissionRequest` és `CreateMissionInitialRequest` kétlépéses folyamatok.
  - **Retro UI:** `RetroButton` komponens bevezetése az egységes retro-esztétikáért. `MissionTable` és `StarSystemTable` kiszervezése újrafelhasználható komponensekbe (admin listák és kadét nézet közös alapon).
  - **i18n:** Magyar és angol szótár masszív bővítése — forge, quiz, timer, session-kezelés, hibaüzenetek mind lokalizálva.
  - **Testing:** `QuizServiceTest` (5 egységteszt a `clearAllSessions`-re), `QuizControllerSecurityTest` (13 biztonsági teszt — minden végpont jogosultsági mátrixa ellenőrizve). `MissionServiceTest` masszív kibővítése a forge-flow-val. Frontend: `QuizPlayer.test.tsx`, `QuizPlayerPage.test.tsx`, `OptionRow.test.tsx`, `QuestionCard.test.tsx`. Cypress E2E: `cadet_forge_init`, `cadet_forge_editor`, `cadet_my_forge`, `cadet_quiz_player` — a teljes kadét kvíz- és forge-workflow automatizáltan tesztelve.

---

## 🛠️ Bejegyzés #17: A Küldetés-Kohó Alapkövei (Mission Forge - Initial Phase)

**Stardate:** 2026.02.24
**Status:** Fejlesztés Alatt (Beta)

Elkezdtük a projekt eddigi legambiciózusabb moduljának, a Küldetés-Kohónak (Mission Forge) az építését. Ez a felület lehetővé teszi, hogy a kadétok ne csak megoldják, hanem maguk is alkossák a küldetéseket.
Integráltuk a Monaco Editor-t a böngészőbe, kiépítettük a valós idejű kommunikációt a Gitea Action-ök és a frontend között, és lefektettük az automatizált küldetés-verifikáció alapjait.

- **Technikai részletek:**
  - **Mission Forge UI:** Monaco Editor integráció (`@monaco-editor/react`) és egy komplex konfigurációs panel a küldetések paraméterezéséhez.
  - **Real-time Comms:** WebSocket kapcsolat kiépítése a Gitea események (tesztfutások) és a frontend között az azonnali visszajelzéshez.
  - **Gitea Backend:** Automatikus repository inicializálás és fájlmásolás template-ek alapján (JavaScript/Python támogatás).
  - **Workflow:** Fájlok mentése Giteába -> Gitea Action automatikus futtatás -> Callback a backendnek -> Státuszfrissítés a UI-on.

---

## 🛡️ Bejegyzés #16: Pajzsellenőrzés és Automatizált Karbantartás (CI/CD & Testing)

**Stardate:** 2026.02.18
**Status:** Dokkrendszer Optimalizálva

A biztonság és stabilitás jegyében megerősítettük az automatizált ellenőrző folyamatainkat. A GitHub Actions pipeline-unkat finomhangoltuk, szétválasztva az egységteszteket és a kontroller teszteket a
gyorsabb visszajelzés érdekében. A backend tesztlefedettsége jelentősen javult, különös tekintettel a jogosultságkezelésre és a küldetéslogikára. Beüzemeltük a Gitea Runner infrastruktúrát is, ami a jövőbel
automatizált kódellenőrzések alapköve.

- **Technikai részletek:**
  - **CI Pipeline:** `ci.yml` frissítése, párhuzamos tesztfuttatás és szétválasztott riportolás.
  - **Backend Testing:** Controller tesztek implementálása a Roles, Permissions és Missions modulokhoz (közel 100%-os lefedettség a kritikus utakon).
  - **Gitea Runner:** Saját runner konfiguráció (`runner-config.yaml`) és Docker integráció a tesztkörnyezetek izolálásához.
  - **E2E Fixes:** Cypress tesztek stabilizálása és a tesztadat-generálás javítása.

---

## 🌌 Bejegyzés #15: A Vizuális Motor Frissítése (Landing, Map & Control Panel)

**Stardate:** 2026.02.10
**Status:** Navigációs Rendszer Élesítve

Az űrhajó külső és belső megjelenése jelentős ráncfelvarráson esett át. Elindítottuk az új Landing Page-et, ahol egy interaktív űrállomás fogadja az érkezőket. A Galaxis Térkép (Star Map) segítségével most
már vizuálisan navigálhatunk a csillagrendszerek között, a Parancsnoki Panel (Control Panel) pedig készen áll a robotok irányítására. A mobil-első (PWA) szemlélet jegyében minden felületet érintésbaráttá és
reszponzívvá tettünk.

- **Technikai részletek:**
  - **Frontend Landing:** `SpaceStationCanvas` (Three.js/React Three Fiber) és animált bemutatkozó felület.
  - **Star Map:** Dinamikus, gráf-alapú navigáció a csillagrendszerek között (`StarMapCanvas`).
  - **Control Panel:** Irányítókonzol a robotparancsok és a küldetés-állapot vizualizációjához.
  - **Star System Detail:** Új információs oldalak a rendszerek és küldetések részletes adataihoz.
  - **Styling:** Tailwind CSS és Material UI v6/v7 szinkronizálása a modern, "űrhajós" esztétikáért.

---

## 📡 Bejegyzés #14: A Fekete Doboz Élesítése (System Logs & WebSocket)

**Stardate:** 2026.01.24
**Status:** Valós Idejű Monitorozás Aktív

A Parancsnoki Híd (Admin Dashboard) újabb kritikus rendszerrel bővült. Mostantól nem repülünk vakon: a hajó minden rezdülését, minden rendszerüzenetet és hibajelzést valós időben látunk a központi kijelzőn.
Kiépítettük a neurális hálózatot (WebSocket) a gépház és a parancsnoki pult között, így az üzenetek késleltetés nélkül érkeznek. A Fekete Doboz (LogList) nem csak rögzít, de színezett, szűrhető és azonnal
olvasható formában tálalja az eseményeket a mérnököknek.

- **Technikai részletek:**
  - **Backend WebSocket:** `WebSocketConfig` és STOMP protokoll beüzemelése.
  - **Custom Log Appender:** Egyedi `WebSocketLogAppender` írása, ami a Logback eseményeket közvetlenül a `/topic/logs` csatornára továbbítja.
  - **Frontend Console:** Új `LogList.tsx` komponens, ami feliratkozik a WebSocket csatornára és terminál-szerű nézetben megjeleníti a bejövő logokat.
  - **Security:** WebSocket végpontok védelme (csak ADMIN számára elérhető).
  - **Refaktor:** `GlobalExceptionHandler` és a Service osztályok tisztítása a jobb logolás érdekében.

---

## 🛰️ Bejegyzés #13: A Műszerfal Teljes Aktiválása (DataGrid & Final Admin UI)

**Stardate:** 2026.01.21
**Status:** Adminisztrációs Modul 100%

A mai napon a parancsnoki híd összes monitorát a legmodernebb holografikus technológiára (MUI X DataGrid) cseréltük. Mostantól nem csak listázzuk a flottát, hanem villámgyorsan szűrhetünk, rendezhetünk és csoportosíthatunk minden adatot – legyen szó Kadétokról, Csillagrendszerekről vagy Küldetésekről. A Szerepkör-kezelő terminál is teljes üzemmódba kapcsolt: a Szenátus (Admin) most már vizuális felületen, checkboxokkal konfigurálhatja a jogosultsági mátrixot.

A biztonsági protokollokat is szinkronizáltuk az űrhajó minden szegletével: a frontend és a backend között egy azonnali adatkapcsolat (Auth /me endpoint) garantálja, hogy minden jogkörváltozás azonnal életbe lépjen. A szimulációs droidjaink (Vitest & Cypress) is megkapták a frissített navigációs adatokat, így minden teszt zölden világít.

- **Technikai részletek:**
  - **UI Modernizáció:** Minden listaoldal (`UserList`, `StarSystemList`, `MissionList`, `RoleList`) átállítása `DataGrid`-re szűréssel és rendezéssel.
  - **Role Management UI:** `RoleList` és `RoleEdit` implementálása (MUI checkbox grid a permissionöknek).
  - **Frontend Auth Sync:** `AuthContext` frissítése a `/api/auth/me` hívással az azonnali jogosultság-ellenőrzéshez.
  - **Standardizálás:** Egységesített MUI v6/v7 komponensstruktúra és típusbiztos importok.
  - **Testing:** Teljeskörű Cypress E2E lefedettség a szerepkörökhöz (`admin_roles.cy.ts`) és javított autentikációs mockolás minden tesztben.

---

## 🏛️ Bejegyzés #12: A Galaktikus Szenátus Felépítése (RBAC & Permissions)

**Stardate:** 2026.01.20
**Status:** Jogosultsági Rendszer Aktiválva

A mai napon befejeztük a biztonsági protokollok legmagasabb szintjének implementálását. A rendszer most már nem csak egyszerű parancsnokokat és kadétokat ismer, hanem egy teljeskörű, finomhangolt jogosultsági rendszert (Role-Based Access Control). Minden zsilip, minden konzol és minden adatbázis-hozzáférés mostantól szigorúan ellenőrzött Engedélyekhez (Permissions) kötött. Sőt, kifejlesztettünk egy azonnali neurális kapcsolatot (Stateful Auth Check), így ha a Szenátus (Admin) visszavon egy jogot, az a másodperc töredéke alatt érvénybe lép, nem kell megvárni a műszakváltást (Logout).

A Küldetés-tervező modul is intelligensebb lett: a rendszer automatikusan rendezi a sorokat (Smart Insert/Delete), így sosem marad üres hely a küldetések láncolatában.

- **Technikai részletek:**
  - **RBAC Core:** `RoleService`, `RoleController` és DTO-k implementálása.
  - **Permission Logic:** `@EnableMethodSecurity` és `@PreAuthorize` annotációk minden végponton.
  - **Immediate Auth:** `JwtAuthenticationFilter` átírása DB-alapú ellenőrzésre (`UserDetailsService`), plusz `/api/auth/me` végpont a frontend szinkronizációhoz.
  - **Mission Logic:** Smart Insert (eltolás) és Smart Delete (visszahúzás) a `MissionService`-ben.
  - **Frontend:** `MissionList` és `MissionEdit` (DataGrid, Form validáció).
  - **Testing:** Teljes backend lefedettség (`RoleServiceTest`, `MissionServiceTest`), és javított E2E tesztek (`admin_missions.cy.ts`).

---

## 🎨 Bejegyzés #11: A Műszerfal Újrafényezése és a Védelmi Rendszerek Kalibrálása (Admin UI & Testing)

**Stardate:** 2025.12.31
**Status:** Műveleti Terület Biztosítva

Az év utolsó napján jelentős fejlesztéseket hajtottunk végre a parancsnoki hídon. A vezérlőpult (Frontend Admin) most már teljes pompájában ragyog: a tisztek kényelmesen kezelhetik a Csillagrendszereket és a Kadétállományt. A fedélzeti számítógép nyelvtanfolyamon is részt vett, így mostantól folyékonyan beszéli a Galaktikus Közös (Angol) és az Anyanyelvi (Magyar) nyelvet is. A szimulációs droidjainkat (Vitest & Cypress) is megjavítottuk, miután egy rejtélyes időhurok ("Maximum update depth exceeded") és egy elavult protokoll (Cypress v4) majdnem megbénította a tesztelést.

- **Technikai részletek:**
  - **Admin UI:** Teljes `StarSystem` és `User` CRUD felület React-ban (Material UI).
  - **i18n:** Kétnyelvűség (HU/EN) bevezetése a teljes admin felületen (`react-i18next`).
  - **Testing (Unit):** `Vitest` tesztek javítása (`MemoryRouter` használata, `AuthContext` mockolása). A `UserEdit.tsx` végtelen ciklusának javítása a `useEffect` függőségek optimalizálásával.
  - **Testing (E2E):** `Cypress` konfiguráció modernizálása (v13+), típusdefiníciók helyreállítása (`tsconfig.json`), és a `npm audit fix` által okozott verzió-downgrade korrigálása.
  - **Security:** JWT token kezelés javítása a tesztekben (Role array vs string).

---

## 🚀 Bejegyzés #10: A Nagy Klónozás (Start Mission Protocol)

**Stardate:** 2025.12.16
**Status:** Küldetés Indítva

A kadétok felkészültek. Kidolgoztuk a protokollt, amivel egyetlen gombnyomásra átadjuk nekik a tudást. A "Start Mission" parancs kiadásakor a rendszer a háttérben azonnal reagál: az Adminisztrátori Tudástárból (Template Repo) kivonatolja a publikus adatokat, és egy védett, privát csatornán átmásolja a kadét személyes munkaállomására (Student Repo). A rendszer intelligens ("Smart Copy"), így a titkos megoldókulcsok az oktatóknál maradnak. A kadétok azonnal írási jogot kapnak a saját repójukhoz.

- **Technikai részletek:**
  - `POST /api/missions/{id}/start` végpont implementálása.
  - `CadetMission` entitás és kapcsolótábla létrehozása (User <-> Mission).
  - Logika: Template tartalom olvasása -> Új repo létrehozása -> Fájlok másolása -> Collaborator hozzáadása.

---

## 🔐 Bejegyzés #9: Galaktikus Hierarchia (Dinamikus RBAC)

**Stardate:** 2025.12.16
**Status:** Jogosultsági Mátrix Élesítve

A parancsnoki lánc túl merev volt. Lecseréltük az egyszerű rangokat egy dinamikus jogosultsági mátrixra. Mostantól nem csak 'Kadét' vagy 'Admin' létezik, hanem finomhangolt engedélyek (Permissions) határozzák meg, ki melyik zsilipet nyithatja ki. Az adatbázisban rögzítettük a szerepkörök és jogok bonyolult hálózatát, a rendszer induláskor automatikusan kalibrálja az alapvető hozzáféréseket a Parancsnokság, az Oktatók és a Kadétok számára.

- **Technikai részletek:**
  - Dinamikus Role-Based Access Control (RBAC) implementálása.
  - `Role` és `Permission` entitások és kapcsolótáblák létrehozása.
  - `DataInitializer` a kezdő jogosultságkészlet feltöltéséhez.
  - `Cadet` entitás frissítése: több szerepkör támogatása és dinamikus Authority generálás.

---

## 🛠️ Bejegyzés #8: A Kódraktár Teljes Kontrollja (GiteaService 2.0)

**Stardate:** 2025.12.16
**Status:** Eszköztár Bővítve

A mérnökcsapat jelentette: a Gitea kommunikációs modulunk elérte a maximális kapacitását. Mostantól nem csak felhasználókat tudunk létrehozni, hanem a teljes infrastruktúrát menedzseljük. Képesek vagyunk tárolókat (Repository) létrehozni, fájlokat feltölteni, tartalmat olvasni, és szükség esetén mindent nyomtalanul eltüntetni (Delete User & Repo). A kaszkádolt törlési mechanizmus gondoskodik róla, hogy ha egy kadét elhagyja a fedélzetet, a digitális lábnyoma is törlődjön.

- **Technikai részletek:**
  - `GiteaService` bővítése: `deleteGiteaUser`, `deleteRepository`, `getRepoContents`, `getFileContent`, `addCollaborator`.
  - `CadetService` bővítése: `deleteCadet` (kaszkádolt törlés: DB + Gitea).
  - Repository kezelés automatizálása.

---

## 🗺️ Bejegyzés #7: A Térkép Aktiválása (Swagger UI)

**Stardate:** 2025.12.15
**Status:** Sikeres Küldetés

A hajó rendszerei bonyolulttá váltak. Szükségünk volt egy térképre, hogy eligazodjunk a végpontok (API Endpoints) labirintusában. Aktiváltuk a **Swagger UI** modult. Kezdeti inkompatibilitási turbulenciák (`NoSuchMethodError`) léptek fel a régi navigációs szoftver (`springdoc 2.3.0`) and az új hajtómű (`Spring Boot 3.4+`) között, de egy verziófrissítéssel (`2.6.0`) stabilizáltuk a rendszert. Most már minden tiszt tisztán látja a hajó összes funkcióját egy interaktív felületen.

- **Technikai részletek:**
  - `springdoc-openapi` integráció.
  - Security Config finomhangolása a publikus dokumentációhoz.
  - Verziókonfliktus elhárítása.

---

## 🛰️ Bejegyzés #6: Mission Control Automatizáció

**Stardate:** 2025.12.15
**Status:** Rendszer Élesítve

A Parancsnokság (Admin) számára lehetővé tettük, hogy ne csak manuálisan adminisztráljanak. Megépítettük az automatizált csatornát a Backend és a Kódraktár (Gitea) között. Mostantól, ha egy tiszt új küldetést (Mission) definiál, a rendszer a háttérben automatikusan létrehozza a hozzá tartozó tárolót és feltölti a kezdőcsomagot. A manuális munka a múlté.

- **Technikai részletek:**
  - `GiteaService` bővítése: `createRepository`, `createFile` API hívások.
  - `MissionService` refaktorálás: Template fájlok fogadása és feltöltése.
  - `CreateMissionRequest` DTO módosítása.

---

## 🛡️ Bejegyzés #5: Védelmi Pajzsok és Identitás (Auth & Security)

**Stardate:** 2025.12.14
**Status:** Pajzsok 100%-on

A hajó biztonsága elsődleges. Beüzemeltük a **Spring Security** védelmi rendszert. Minden kadét és tiszt mostantól egyedi azonosítót és titkosított belépési kódot (BCrypt) kap. A kommunikációt **JWT (JSON Web Token)** alapú igazolványokkal biztosítottuk, így a rendszerünk állapota megmarad (Stateless), de a biztonság garantált. A Gitea identitásokat szinkronizáltuk a központi adatbázissal.

- **Technikai részletek:**
  - `SecurityConfig` és `JwtAuthenticationFilter` implementálása.
  - Jelszó hash-elés (`PasswordEncoder`).
  - Role-based authorization (ADMIN vs CADET).
  - Custom Exception Handling (`UserNotFound`, `BadCredentials`).

---

## 📦 Bejegyzés #4: A Kódraktár Integrációja

**Stardate:** 2025.11.30
**Status:** Kapcsolat Stabil

Sikeresen felvettük a kapcsolatot a külső Kódraktárral (Gitea). A hajó mostantól képes önállóan kommunikálni a raktárral, felhasználókat létrehozni és törölni. Ez a lépés elengedhetetlen volt ahhoz, hogy minden kadétnak saját, privát munkaterülete legyen a jövőben.

- **Technikai részletek:**
  - `GiteaService` létrehozása (RestClient).
  - API kommunikáció implementálása (User CRUD).
  - `application.properties` konfiguráció.

---

## 🏗️ Bejegyzés #3: A Hajótest Felépítése (Backend & DB)

**Stardate:** 2025.11.29
**Status:** Szerkezet Stabil

Lefektettük az alapokat. A hajtómű (Spring Boot Backend) és az üzemanyagtartály (PostgreSQL Adatbázis) a helyére került. Megterveztük a belső tereket (Adatbázis Séma): Csillagrendszerek (Kurzusok) és Küldetések (Leckék) tárolására alkalmas rekeszeket hoztunk létre.

- **Technikai részletek:**
  - Spring Boot projekt scaffold.
  - PostgreSQL kapcsolat (`spring-boot-starter-data-jpa`).
  - Liquibase/Flyway helyett `ddl-auto` (fejlesztői mód).
  - Entitások (`Cadet`, `StarSystem`, `Mission`) létrehozása.

---

## 🐳 Bejegyzés #2: Konténerizáció (Docker Setup)

**Stardate:** 2025.11.29
**Status:** Környezet Izolálva

Hogy a hajó bárhol bevethető legyen, az egész rendszert konténerekbe zártuk. A `docker-compose` vezérlőpult segítségével egyetlen paranccsal indítható a teljes flotta: Adatbázis, Backend, Frontend és Gitea. A hálózati kommunikáció a konténerek között biztosított.

- **Technikai részletek:**
  - `Dockerfile`-ok írása (Backend: Multi-stage build, Frontend: Node+Nginx).
  - `docker-compose.yml` összeállítása.
  - Hálózati izoláció és Volume-ok konfigurálása.

---

## 📜 Bejegyzés #1: A Terv (Genesis)

**Stardate:** 2025.11.29
**Status:** Projekt Indulása

Megszületett a vízió. Egy rendszer, ahol a jövő mérnökei játékos formában, valós eszközökkel tanulhatnak. A tervrajzok (`terv.md`, `api_spec.md`) elkészültek, az irány kijelölve. A cél: A csillagok.

- **Technikai részletek:**
  - Projekt struktúra kialakítása.
  - Dokumentációk (Terv, API specifikáció, DB séma) megírása.
  - Git repository inicializálása.
