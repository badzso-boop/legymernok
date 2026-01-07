# LégyMérnök.hu - Fejlesztési Ütemterv (Véglegesített)

Ez a dokumentum a projekt hivatalos ütemterve, amely ötvözi a stabil Adminisztrációs felületet a gamifikált Játékos élménnyel (PWA).

## Fejlesztési Stratégia
*   **Backend:** Java Spring Boot (Monolitikus mag, Gitea integrációval).
*   **Admin UI:** React Web (Desktop fókusz) - Tartalomkezelés, User management.
*   **Player UI:** React PWA (Mobile fókusz) - Játékos felület, inventory, squadok.

---

## Fázis 1: Admin Dashboard & Core Stabilizálás (PRIORITÁS)
*Cél: A jelenlegi backend és frontend admin funkcióinak készre jelentése. Az adminisztrátor képes legyen teljeskörűen menedzselni a rendszert.*

### 1.1 Felhasználó Kezelés és Jogosultságok
*   [x] **Role Management:** Admin felületen Role (USER, ADMIN) módosítása (Backend & Frontend KÉSZ).
*   [ ] **Real-time Role Update:** Ha az admin jogot ad, a felhasználónak ne kelljen kijelentkeznie. (Megoldás: `/auth/refresh` endpoint vagy proaktív token frissítés a frontend oldalon).
*   [x] **User Edit:** Profil adatok szerkesztése admin által (KÉSZ).

### 1.2 Tartalomkezelés (CMS)
*   [x] **StarSystem CRUD:** Csillagrendszerek létrehozása, szerkesztése képpel, leírással (KÉSZ).
*   [ ] **Mission Editor (Frontend HIÁNYZIK):**
    *   **Új Oldal:** `MissionEdit.tsx` létrehozása.
    *   **Funkciók:** Monaco Editor integráció a template fájlok (Java/Python kód, README) szerkesztéséhez.
    *   **API Hívás:** A `CreateMissionRequest` összeállítása a frontendről (Map<String, String> templateFiles).
*   [x] **Mission Backend Logic:** A `MissionService` már kezeli a Gitea repo létrehozást és fájlfeltöltést (KÉSZ).
*   [x] **Gitea Integration:** A `GiteaService` RestClient alapú implementációja működik (KÉSZ).

---

## Fázis 2: Game Backend & Adatbázis Bővítés
*Cél: Az adatbázis és a backend felkészítése a játékmechanikákra (Inventory, Squads, Game Logic).*

### 2.1 Új Adatmodellek (PostgreSQL)
*   [ ] **Inventory System:** `Item`, `Inventory`, `UserItem` táblák (pl. CPU, Memória modulok, Skinek).
*   [ ] **Squad System:** `Squad`, `SquadMember` táblák (Csapatnév, logó, tagok, rangok).
*   [ ] **Mission Logic:** A `Mission` tábla bővítése a megoldás feltételeivel (pl. `required_commands`, `max_lines`, `reward_xp`).

### 2.2 Game Service Logic
*   [ ] **Inventory Service:** Tárgyak hozzáadása, elvétele, "felszerelése".
*   [ ] **Squad Service:** Meghívás, csatlakozás, kilépés, csapat statisztikák.

---

## Fázis 3: Player Flow & Game Logic (Funkcionális Prototípus)
*Cél: A játékos felület (PWA) logikai vázának elkészítése, látványos animációk nélkül.*

### 3.1 Player UI (Frontend)
*   [ ] **Game Layout:** Külön nézet a sima "User" role-al rendelkezőknek.
    *   Bottom Navigation: Missions, Squad, Inventory, Profile.
*   [ ] **Mission Selector:** A feloldott StarSystem-ek és Mission-ök listázása.

### 3.2 The "Command Deck" (Logic)
*   [ ] **Input Interface:** A parancs gombok (Walk, Grab, stb.) működése.
*   [ ] **Translator Service (Backend):**
    *   Bemenet: JSON parancslista (pl. `[{cmd: "WALK", val: 2}]`).
    *   Feldolgozás: Java/Python kód generálása a template alapján.
    *   Kimenet: Commit a Gitea User Repóba.

---

## Fázis 4: Polish & Visuals (Élmény)
*Cél: A "száraz" logika felöltöztetése.*

*   [ ] **2D Grid megjelenítés:** Sprite-ok, mozgás animáció.
*   [ ] **Visual Feedback:** Sikeres/Sikertelen tesztfutás vizuális jelzése.
*   [ ] **Sound & Haptics:** Hangeffektek, rezgés mobilon.