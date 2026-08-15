# Mission Forge Feature Design Document

## I. Core Koncepció

A Mission Forge egy felhasználói felület, amely lehetővé teszi a kadétok számára, hogy saját egyedi missziókat és (a jövőben) csillagrendszereket hozzanak létre. Ezáltal aktív résztvevőivé válnak a játék
világának formálásában és a programozási tudásukat valós projektekben kamatoztathatják. A létrehozott kódokat Gitea repository-kban tároljuk, automatizált teszteléssel (Gitea Actions) validáljuk, és a usern
valós Gitea portfóliót építünk.

## II. User Flow (Felhasználói élmény)

1.  **Navigáció:** A felhasználó belép a Mission Forge oldalra.
2.  **Inicializálás (Bal oldali konfigurációs panel):**
    - **Választó:** Ki kell választani, hogy "New Star System" vagy "New Mission" készül. (Kezdetben csak a "New Mission" lesz implementálva).
    - **Új Küldetés létrehozása esetén:**
      - Felhasználó kiválasztja saját csillagrendszerét egy legördülő menüből (adatok a `GET /api/star-systems/my-systems` hívás alapján).
      - Megadja a misszió nevét, leírását, nehézségét, típusát, sorrendjét (űrlap mezők).
      - Programozási nyelvet választ (`JavaScript` vagy `Python`).
    - **"Initialize Mission" (vagy "Create Mission") gomb:** Ezt megnyomva történik az első API hívás a backend felé.
3.  **Editor Fázis (Jobb oldali Monaco Editor):**
    _ Miután a backend sikeresen inicializálta a missziót és a Gitea repót, a frontend betölti a Monaco Editorba a template fájlokat (`solution.js`/`.py`, `solution.test.js`/`.py`, `README.md`) a Gitea
    repóból (`GET /api/missions/{missionId}/forge/files`).
    _ A felhasználó szerkeszti a fájlokat. \* **"Save" gomb:** Elmenti a módosított fájlokat a Giteába (`POST /api/missions/{missionId}/forge/save`).
4.  **Tesztelés és Visszajelzés:**
    - Minden "Save" után a Gitea Action automatikusan futtatja a teszteket a user repójában.
    - A Gitea Action visszajelez a Backendnek a tesztek eredményéről (`POST /api/mission-verification/{missionId}/callback`).
    - A Frontend a misszió `verificationStatus` mezője alapján visszajelzést ad a felhasználónak (pl. `PENDING`, `SUCCESS`, `FAILED`).

## III. Frontend Tervezés és Implementáció

1.  **Függőségek:**
    - `@monaco-editor/react`: A kódeditor komponenshez.
    - `framer-motion`: (Már telepítve) Animációkhoz.
2.  **Fő Komponens:** `MissionForgePage.tsx`
    - **Elrendezés:** Teljes képernyős, `RetroUI.css` stílusokat használva egy fémes, csavaros kerettel és terminál hatású háttérrel.
    - **Két oszlopos felosztás (`Material UI Grid`):**
      - **Bal oldal (vékonyabb, kb. 1/3 szélesség): `ForgeConfigPanel.tsx`**
        - Felül: Választó (Star System / Mission).
        - Alatta: Form a kiválasztott entitás létrehozásához.
          - `New Mission` form elemei: `starSystemId` (dropdown), `name`, `descriptionMarkdown`, `missionType`, `difficulty`, `orderInSystem` inputok.
          - Nyelvválasztó (`JavaScript` / `Python`).
          - "Initialize Mission" gomb.
      - **Jobb oldal (szélesebb, kb. 2/3 szélesség): `ForgeEditor.tsx`**
        - **Monaco Editor:** Kódszerkesztő.
        - **Fájlkezelés:** Tabok vagy legördülő menü a `solution.*`, `solution.test.*`, `README.md` fájlok közötti váltáshoz.
        - **Gombok:** "Save" gomb a módosítások Giteába küldéséhez.
        - **Státusz Kijelzés:** A `Mission` `verificationStatus` (DRAFT, PENDING, SUCCESS, FAILED) és az utolsó tesztfutás eredményének (ha van) megjelenítése.
3.  **API Integráció:**
    - `POST /api/missions/forge/initialize`: A `ForgeConfigPanel` hívja meg a misszió inicializálására.
    - `GET /api/missions/{missionId}/forge/files`: A `ForgeEditor` hívja meg a fájlok betöltésére.
    - `POST /api/missions/{missionId}/forge/save`: A `ForgeEditor` hívja meg a fájlok mentésére.
    - `GET /api/star-systems/my-systems`: A `ForgeConfigPanel` hívja meg a felhasználó saját csillagrendszereinek listázására.
4.  **Lokalizáció (i18n):** Minden felhasználói felület elem lefordítható.

## IV. Backend Tervezés és Implementáció

1.  **Gitea Integráció Stratégia ("Admin-Owned, User-Collaborator"):**
    - Minden user-generált Gitea repository az **admin felhasználó** tulajdonában marad.
    - A felhasználó `write` joggal `collaborator`-ként lesz hozzáadva a repository-jához.
    - Ez egyszerűsíti a CI/CD secrets kezelését és az admin kontrollját.

2.  **Data Modellek és DTO-k (`backend/src/main/java/...`):**
    - **`Cadet`:** `getAuthorities()` a részletes jogosultságkezeléshez.
    - **`Permission` & `Role`:** RBAC rendszerhez.
    - **`Mission`:**
      - `owner: Cadet` mező.
      - `verificationStatus: VerificationStatus` mező (`DRAFT`, `PENDING`, `SUCCESS`, `FAILED`, `REVIEW_NEEDED`).
      - `templateRepositoryUrl`: Az admin által birtokolt user-specifikus Gitea repó URL-je.
    - **`StarSystem`:** `owner: Cadet` mező.
    - **`CreateMissionInitialRequest`:** DTO a misszió inicializálásához.
    - **`MissionForgeContentRequest`:** DTO a fájlok tartalmának mentéséhez.
    - **`MissionResponse`:** Bővült `ownerId`, `ownerUsername`, `verificationStatus` mezőkkel.

3.  **Service Réteg (`backend/src/main/java/com/legymernok/backend/service/mission/MissionService.java`):**
    - **`initializeForgeMission(CreateMissionInitialRequest request)`:** Inicializálja a missziót (DB rekord, Gitea repó létrehozása template alapján, user hozzáadása kollaborátorként).
    - **`saveForgeMissionContent(MissionForgeContentRequest request)`:** Menti a user által szerkesztett fájlokat a Gitea repóba, frissíti a misszió `verificationStatus`-át `PENDING`-re.
    - **`getMissionFiles(UUID missionId)`:** Lekéri egy misszióhoz tartozó Gitea repó fájljainak tartalmát.
    - **`startMission(UUID missionId, String username)`:** Módosítva: az admin által birtokolt **eredeti misszió repójából** másolja a tartalmat a Cadet saját (admin-owned) repójába.
    - **`deleteMission(UUID id)`:** Törli a missziót és a Gitea repóját (admin alól).
    - **`updateMissionVerificationStatus(UUID missionId, VerificationStatus newStatus)`:** Frissíti a misszió státuszát (Gitea Action callback).

4.  **Controller Réteg (`backend/src/main/java/com/legymernok/backend/web/mission/MissionController.java`):**
    - **`POST /api/missions/forge/initialize`:** Végpont a misszió inicializálására.
    - **`POST /api/missions/{missionId}/forge/save`:** Végpont a fájlok mentésére.
    - **`GET /api/missions/{missionId}/forge/files`:** Végpont a fájlok lekérésére.
    - A régi `POST /api/missions` végpont eltávolítva.

5.  **Gitea Integráció (`backend/src/main/java/com/legymernok/backend/integration/GiteaService.java`):**
    _ **Konfiguráció:** Injektálja a JS és Python template repók tulajdonosát és nevét az `application.properties`-ből.
    _ **`createMissionRepository(String missionIdString, String templateLanguage, Cadet user)`:**
    _ Létrehoz egy üres repót az admin alatt.
    _ Kiválasztja a megfelelő template repót.
    _ Meghívja a `copyRepositoryContents()`-t (rekurzív fájlmásolás a template-ből az új repóba).
    _ Meghívja az `addCollaborator()`-t (user hozzáadása write joggal).
    _ **`uploadFile(String repoOwner, String repoName, String filePath, String content)`:** Egységesített metódus fájlok feltöltésére/frissítésére (létrehozás/módosítás).
    _ **`copyRepositoryContents(String sourceOwner, String sourceRepoName, String targetOwner, String targetRepoName)`:** Rekurzív másoló metódus fájlok és mappák másolására. \* **Egyéb metódusok:** `createGiteaUser`, `deleteGiteaUser`, `createEmptyRepository`, `deleteRepository`, `getRepository`, `getRepoContents`, `getFileContent`, `addCollaborator` (mind rugalmasabb,
    `owner` paraméterrel, ahol indokolt).

6.  **CI/CD Gitea Actions:**
    _ **Template Repókban:** Minden template repó (JS és Python) tartalmaz egy `.gitea/workflows/ci.yml` fájlt.
    _ **Tartalom:** Checkout, Node/Python setup, install dependencies, run tests, `determine status` (`SUCCESS`/`FAILED`), `send status to backend webhook` (`POST
/api/mission-verification/{missionId}/callback`).
    _ **`MISSION_ID`:** A Gitea repository neve maga a `Mission UUID`.
    _ **Secret:** `MISSION_VERIFICATION_SECRET` a Gitea `Secrets`-ben beállítva.

7.  **`MissionVerificationController`:** Fogadja a Gitea Actions callback-eket és frissíti a `Mission` `verificationStatus`-át.
