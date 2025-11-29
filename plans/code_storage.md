# Felhasználói Kód Tárolása: Dedikált Git Szerver Implementáció

Ez a dokumentum a felhasználói kód tárolására választott, Git-alapú megoldás implementációs tervét részletezi.

## 1. Választott Technológia: Gitea

A rendszer egy dedikált, saját hosztolású Git szervert fog használni. A javasolt technológia a **Gitea**.

**Indoklás:**
- **Könnyűsúlyú:** Go nyelven íródott, lényegesen kevesebb erőforrást igényel, mint pl. a GitLab.
- **Egyszerű Üzemeltetés:** Docker konténerként egyszerűen futtatható és beilleszthető a meglévő `docker-compose.yml` struktúránkba.
- **Teljeskörű API:** Minden szükséges funkció (felhasználó- és repo-kezelés, fájlműveletek) elérhető a REST API-ján keresztül, ami elengedhetetlen a backendünk számára.

## 2. Architektúra és Folyamatok

### 2.1. Infrastruktúra (`docker-compose.yml`)

A `docker-compose.yml` fájl ki lesz egészítve egy új `gitea` szervizzel:

```yaml
services:
  # ... meglévő backend, frontend, db szervizek

  gitea:
    image: gitea/gitea:latest
    container_name: legymernok-gitea
    environment:
      - USER_UID=1000
      - USER_GID=1000
      - GITEA__database__DB_TYPE=postgres
      - GITEA__database__HOST=legymernok-db:5432
      - GITEA__database__NAME=gitea
      - GITEA__database__USER=gitea
      - GITEA__database__PASSWD=gitea_secret
      # ... egyéb Gitea beállítások
    restart: always
    networks:
      - legymernok-network
    volumes:
      - ./gitea-data:/data
    ports:
      - "3000:3000" # Gitea web UI
      - "2222:22"   # Gitea SSH
```
**Feladat:** A `legymernok-db` Postgres szerverén létre kell hozni egy külön `gitea` adatbázist és felhasználót.

### 2.2. Felhasználó-kezelés és Szinkronizáció

A `legymernok` és a `gitea` felhasználóinak szinkronban kell lenniük.

1.  **Admin Token:** A Gitea telepítése után manuálisan létre kell hozni egy adminisztrátori felhasználót, és generálni kell egy API tokent. Ezt a tokent a `legymernok-backend` fogja használni, hogy adminisztrátori műveleteket végezzen a Gitea-n (pl. felhasználók létrehozása).
2.  **Regisztráció:** Amikor egy új kadét regisztrál a `legymernok.hu`-n:
    a. A `legymernok-backend` sikeresen elmenti az új `cadets` rekordot az adatbázisba.
    b. Közvetlenül ezután a backend meghívja a Gitea API `/api/v1/admin/users` végpontját, hogy létrehozza a Gitea-n is a felhasználót (véletlenszerű jelszóval, mivel a Gitea felületére a kadétnak nem kell belépnie).
    c. A Gitea válaszából kapott **numerikus user ID**-t a backend elmenti a `cadets` tábla `gitea_user_id` oszlopába.

### 2.3. Küldetés Kezdése: A Repository Létrehozása

1.  A kadét a frontend-en rákattint egy küldetés "Indítás" gombjára.
2.  A frontend lekérdezi a backendet, hogy a kadét elkezdte-e már ezt a küldetést.
3.  Ha a `cadet_missions` táblában még nincs bejegyzés:
    a. A backend meghívja a Gitea API `/api/v1/user/repos` végpontját a kadét nevében (vagy adminisztrátorként, megadva a szerző `gitea_user_id`-ját).
    b. Létrehoz egy **új, privát repository-t**, pl. `python-alapok-valtozok` néven.
    c. A backend **API-n keresztül feltölti a repo-ba** a küldetéshez tartozó alap fájlokat (pl. `main.py`, `README.md`). Ezt a Gitea fájl API-ja teszi lehetővé, így nem szükséges lokális `git clone`.
    d. A backend létrehoz egy új rekordot a `cadet_missions` táblában, amiben elmenti a `cadet_id`, `mission_id`, a `status` (`IN_PROGRESS`), és a frissen létrehozott `repository_url`-t.
4.  A backend visszaadja a repo URL-jét (és a fájlok tartalmát) a frontendnek, ami betölti a kódot a "Szimulátorba".

### 2.4. Kódolás és Mentés: Commit Létrehozása

A frontend kód editora **NEM** egy teljes értékű Git kliens. A folyamat sokkal egyszerűbb:

1.  A kadét írja a kódot a böngészőben.
2.  A "Diagnosztika" gomb megnyomásakor (vagy egy periodikus auto-save hatására) a frontend elküldi a **fájl(ok) teljes, aktuális tartalmát** a `legymernok-backend`-nek.
3.  A backend a kapott tartalommal **meghívja a Gitea API fájl módosító végpontját**, ami atomi módon létrehoz egy új commitot a kadét repo-jában. A commit üzenet lehet pl. `"Szimulátor mentés - 2025.11.28. 15:30"`.

### 2.5. Tesztelés: A Kód Futtatása a Sandbox-ban

1.  A commit sikeres létrehozása után a backend elindítja a tesztelési folyamatot.
2.  Elindít egy **elszigetelt, ideiglenes Docker konténert** (a "sandbox"-ot).
3.  A konténeren belül egy `git clone <repository_url>` paranccsal letölti a kadét kódjának legfrissebb állapotát.
4.  A backend lekérdezi a `mission_tests` táblából az adott küldetéshez tartozó tesztkódo(ka)t, és bemásolja a sandbox-ban lévő klónozott repo megfelelő helyére.
5.  A sandbox-on belül elindítja a teszt futtató parancsot (pl. `pytest`).
6.  A parancs kimenetét (stdout, stderr) és a kilépési kódját a backend "elfogja", és visszaküldi a frontendnek, ami megjeleníti azt a "Diagnosztikai Jelentés" fülön.
7.  A tesztelés befejeztével a sandbox Docker konténer leáll és törlődik.
