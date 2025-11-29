# LégyMérnök.hu - Fejlesztési Terv

Ez a dokumentum a `legymernok.hu` projekt fejlesztési tervét vázolja fel, beleértve a technológiai stacket, az architektúrát és a fejlesztési ütemtervet.

## 1. Célkitűzés

Egy online platform létrehozása, amely projekt- és küldetés-alapú oktatási rendszerben tanítja meg a mérnöki (kezdetben szoftverfejlesztői) szakma alapjait és haladóbb koncepcióit. A platform biztonságos, interaktív környezetet biztosít a kódoláshoz és később az elektronikai áramkörök szimulációjához.

## 2. Technológiai Stack (Tech Stack)

| Komponens | Technológia | Indoklás |
|---|---|---|
| **Backend** | Spring Boot (Java 17+), Maven | Robusztus, skálázható, nagyvállalati környezetben elterjedt. A te választásod. |
| **Frontend** | React (Vite-tal) | Modern, komponens-alapú, gyors fejlesztést tesz lehetővé. Hatalmas közösség és ökoszisztéma. |
| **Adatbázis** | PostgreSQL | Megbízható, nyílt forráskódú, relációs adatbázis, JSON támogatással. Jól kezeli a komplex lekérdezéseket. |
| **Backend Tesztelés** | JUnit 5, Mockito | Java világban de-facto standard tesztelési keretrendszerek. |
| **Frontend Tesztelés** | Jest, React Testing Library (Unit), Cypress (E2E) | Modern, iparági standard eszközök a frontend minőségbiztosításához. |
| **Konténerizáció** | Docker, Docker Compose | Egységes fejlesztői és deployment környezetet biztosít. |
| **CI/CD** | GitHub Actions | Könnyen integrálható GitHub repóval, automatizálja a tesztelést és a deploymentet. |
| **Kód Futtatás** | Dedikált Docker-alapú sandbox | Biztonságos környezetet nyújt a felhasználói kód futtatásához. |

## 3. Architektúra

A rendszer mikroszerviz-szerűen, de egy monorepóban (egy közös git repository) lesz fejlesztve a kezdeti egyszerűség kedvéért. Az egyes fő komponensek külön Docker konténerekben futnak.

- **`legymernok-backend`**: Spring Boot alkalmazás, amely a REST API-t, az üzleti logikát és a felhasználókezelést tartalmazza.
- **`legymernok-frontend`**: React alkalmazás, amely a felhasználói felületet biztosítja.
- **`legymernok-db`**: PostgreSQL adatbázis konténer.
- **`legymernok-runner`**: (Későbbi fázisban) Egy különálló szolgáltatás, ami a felhasználók által beküldött kód biztonságos futtatásáért felelős.

A helyi fejlesztést egy `docker-compose.yml` fájl fogja támogatni, amely egyetlen paranccsal elindítja a teljes környezetet.

## 4. Fejlesztési Ütemterv (Mérföldkövek)

### Mérföldkő 0: Alapok és Infrastruktúra
*Cél: A fejlesztői és CI/CD környezet felállítása.*

1.  **Git repository inicializálása** (monorepo struktúrával: `backend`, `frontend` mappák).
2.  **`docker-compose.yml` létrehozása** a `backend`, `frontend` és `db` szervizekkel.
3.  **"Hello World" Spring Boot alkalmazás** létrehozása Mavennel, Dockerfile-lal.
4.  **"Hello World" React alkalmazás** létrehozása Vite-tal, Dockerfile-lal.
5.  **GitHub Actions pipeline (CI) beállítása**:
    -   Trigger: `push` a `main` és `develop` branchekre.
    -   Job 1: Backend buildelése és tesztek futtatása (`mvn clean install`).
    -   Job 2: Frontend buildelése és tesztek futtatása (`npm install && npm test`).
6.  **Alapvető adatbázis séma** létrehozása `flyway` vagy `liquibase` segítségével (pl. `users` tábla).
7.  A backend és frontend összekötése: a frontend hívjon meg egy `/api/health` végpontot a backenden, ami `"OK"`-val válaszol.

### Mérföldkő 1: MVP - Felhasználókezelés és Tartalom
*Cél: Egy működő, de minimális funkcionalitású platform, ahol a felhasználók regisztrálhatnak és láthatják a kurzusokat.*

1.  **Felhasználói regisztráció és bejelentkezés** megvalósítása (backend API + frontend UI).
2.  **JWT-alapú authentikáció** bevezetése a Spring Security segítségével.
3.  **Adatbázis modellek** létrehozása: `users`, `courses`, `missions`, `mission_steps`.
4.  **Admin felület** (vagy API végpontok) a kurzusok és küldetések feltöltéséhez.
5.  **Felhasználói felület** a kurzusok és küldetések böngészéséhez.

### Mérföldkő 2: Interaktív Kód-végrehajtási Környezet
*Cél: A felhasználók képesek kódot írni és azt a rendszer lefuttatja és kiértékeli.*

1.  **Kód-futtató szolgáltatás (`runner`) tervezése**: Egy API, ami fogad egy kódrészletet és egy tesztet, majd egy elszigetelt Docker konténerben futtatja azt.
2.  **Biztonsági megfontolások**: Erőforrás-limit (CPU, memória), timeout, hálózati izoláció.
3.  **Frontend kód-editor** integrálása (pl. Monaco Editor, ami a VS Code motorja).
4.  A futtatás eredményének (stdout, stderr, teszt eredmény) visszaküldése és megjelenítése a frontend-en.
5.  Az első **Python-alapú küldetés** létrehozása.

### Mérföldkő 3: Áramkör Szimulátor
*Cél: Tinkercad-hez hasonló áramkör-szerkesztő és szimulátor integrálása.*

1.  Megfelelő JavaScript könyvtár kiválasztása (pl. `circuit-simulator-js` vagy egyedi fejlesztés `Konva.js`/`Three.js` alapokon).
2.  Az áramkör-szerkesztő felület fejlesztése.
3.  Az áramkörök szimulációjának logikája.
4.  Új, elektronikai küldetéstípus bevezetése a platformon.

## 5. DevOps és CI/CD Folyamat

A GitHub Actions pipeline a `main` branch-re történő push esetén a következőket fogja végrehajtani:

1.  **Build & Test**: Lefuttatja a backend és frontend teszteket. Hiba esetén a folyamat leáll.
2.  **Build Docker Images**: Sikeres tesztek után legenerálja a `backend` és `frontend` új Docker image-eit, és feltölti őket egy Docker Registry-be (pl. Docker Hub, GitHub Container Registry).
3.  **Deploy**: (Ez a lépés egy saját szervert/cloud környezetet igényel)
    -   SSH-val bejelentkezik a szerverre.
    -   Leállítja a régi konténereket.
    -   Letölti az új image-eket (`docker-compose pull`).
    -   Elindítja az új verziójú konténereket (`docker-compose up -d`).

## 6. Első Lépések

1.  A terv jóváhagyása, esetleges módosítása.
2.  A Git repository és a kezdeti mappa struktúra létrehozása.
3.  **Mérföldkő 0** megkezdése.
