# LégyMérnök.hu - Fejlesztési Terv (Master Plan)

Ez a dokumentum a projekt véglegesített fejlesztési ütemtervét és technikai mérföldköveit tartalmazza, összefésülve a kezdeti víziót a megvalósult Gitea-alapú architektúrával.

## 1. Technológiai Stack
*   **Backend:** Java 17, Spring Boot 3.x (Web, Data JPA, Security)
*   **Adatbázis:** PostgreSQL 16
*   **Kód Tárolás:** Gitea (Self-hosted Git Server) - *Központi elem a kódok kezelésére.*
*   **Frontend:** React (Vite, TypeScript), Tailwind CSS / Bootstrap
*   **Containerization:** Docker, Docker Compose
*   **CI/CD:** GitHub Actions (tervezett)

---

## 2. Architektúra és Biztonsági Modell

A rendszer központi eleme a **Gitea**, amely kezeli mind az oktatóanyagokat (Template Repók), mind a diákok megoldásait.

*   **Admin Template Repo:** Privát repository, amely tartalmazza a feladat vázát (publikus) és a megoldásokat/teszteket (rejtett).
*   **Student Repo:** Privát repository, amely a "Start Mission" pillanatában jön létre. Tartalmazza a vázat, de **nem** tartalmazza a megoldásokat.
*   **Runner Service:** Izolált Docker környezet, amely a kiértékeléskor egyesíti a Student Repo kódját az Admin Repo rejtett tesztjeivel.

---

## 3. Fejlesztési Ütemterv (Mérföldkövek)

### Mérföldkő 0: Alapok és Infrastruktúra (KÉSZ)
*Cél: A stabil fejlesztői környezet felállítása.*
*   [x] **Projekt Inicializálás:** Monorepo (`backend`, `frontend`), `docker-compose.yml`.
*   [x] **Adatbázis:** PostgreSQL és Gitea konténerek beüzemelése, inicializálása.
*   [x] **Backend Alapok:**
    *   Rétegzett architektúra (Controller, Service, Repository, Model).
    *   `GiteaService`: Felhasználó létrehozása Gitea API-n keresztül.
    *   Biztonság: Spring Security, BCrypt, JWT Token alapú autentikáció.
    *   Felhasználókezelés (Regisztráció, Login, Gitea szinkron).
*   [x] **Adatmodell v1:** `StarSystem` (Kurzus) és `Mission` (Lecke) entitások és CRUD API.
*   [x] **Frontend Alapok:** React + Vite + TypeScript scaffold.

### Mérföldkő 1: Tartalomkezelés és Adminisztráció (FOLYAMATBAN)
*Cél: Az oktatók (Adminok) képesek legyenek kurzusokat és feladatokat létrehozni egy kényelmes felületen.*

#### 1.1. Admin UI (Frontend)
*   [ ] **Bejelentkezés:** Login oldal, JWT token kezelése.
*   [ ] **Kurzus Kezelő:** Star System-ek listázása, létrehozása, szerkesztése.
*   [ ] **Feladat Szerkesztő (Mission Editor):**
    *   Űrlap a feladat adatainak.
    *   **Monaco Editor Integráció:** Beépített kódszerkesztő, ahol az admin megírja a sablon kódot.

#### 1.2. Backend Automatizáció (Gitea API)
*   [ ] **Repo Automatizáció:** A `GiteaService` bővítése, hogy API-n keresztül hozzon létre **privát** repository-kat az Admin felhasználó alatt.
*   [ ] **Fájl Feltöltés:** A `GiteaService` bővítése fájlok (kód, leírás) feltöltésével.
*   [ ] **Mission Service:** A `createMission` végpont átalakítása: URL helyett kódot vár, és a háttérben végzi el a repo létrehozást és feltöltést.

### Mérföldkő 2: A Tanulói Élmény (Start Mission)
*Cél: A diákok elkezdhetik a feladatokat, saját repót kapnak, és kódolhatnak.*

#### 2.1. Start Mission Folyamat (Backend)
*   [ ] **Template Klónozás (Smart Copy):** Amikor a diák elindít egy feladatot:
    1.  A rendszer Admin joggal olvassa a feladat **Admin Template Repóját**.
    2.  Kiszűri a titkos fájlokat (megoldások, rejtett tesztek).
    3.  Létrehoz egy **új, privát repót a Diáknak** Giteán.
    4.  Feltölti a szűrt kezdőcsomagot (vázkód) a diák repójába.
*   [ ] **Adatbázis:** A `cadet_missions` táblában rögzítjük a diák repójának URL-jét.

#### 2.2. Kódolás (Frontend)
*   [ ] **Kurzus Böngésző:** Diák felület a kurzusok és leckék listázására.
*   [ ] **Webes IDE:** A diák a böngészőben szerkesztheti a saját Gitea repójának fájljait (Monaco Editor + Gitea API commit).

### Mérföldkő 3: Tesztelés és Értékelés (Runner)
*Cél: A beküldött kódok automatikus futtatása és ellenőrzése.*

*   [ ] **Docker Runner Service:** Egy különálló Spring Boot (vagy Go/Python) szolgáltatás, ami Docker API-t használ.
*   [ ] **Tesztelési Logika:**
    1.  Runner letölti a Diák kódját.
    2.  Runner letölti a Rejtett Teszteket az Admin Template Repóból.
    3.  Összefésüli és lefuttatja őket egy ideiglenes konténerben.
    4.  Visszaküldi az eredményt (Pass/Fail + Logok).
*   [ ] **Biztonság:** Erőforrás-limitek (CPU, RAM), Timeoutek, Hálózati izoláció.

### Mérföldkő 4: Áramkör Szimulátor
*Cél: Elektronikai feladatok támogatása.*

*   [ ] **Könyvtár:** `circuit-simulator-js` (vagy hasonló) integrálása React komponensként.
*   [ ] **Integráció:** Az áramkör leírófájljának (JSON) tárolása a Gitea repóban, verziókövetése.

### Mérföldkő 5: Közösségi Funkciók (Social AI)
*   [ ] AI Tutor integráció.
*   [ ] Ranglisták, XP rendszer.

---

## 4. DevOps és CI/CD

*   **GitHub Actions:**
    *   Build & Test (Backend + Frontend).
    *   Docker Image Build & Push.
    *   Deploy (SSH + Docker Compose).