# LégyMérnök.hu - API Specifikáció és Backend Architektúra

Ez a dokumentum a backend rendszer rétegzett felépítését és az API végpontjainak specifikációját tartalmazza.

## 1. Backend Architektúra (Rétegek)

A backend Java / Spring Boot alapokon nyugszik, szigorúan követve a **Service-Oriented Architecture** elveit a skálázhatóság és a tesztelhetőség érdekében.

### Rétegek (Layers)

1.  **Controller Layer (`web` package)**
    *   **Felelősség:** HTTP kérések fogadása, validáció (DTO szinten), válaszadás.
    *   **Szabály:** Nem tartalmaz üzleti logikát. Csak a Service réteggel kommunikál.
    *   *Példa:* `AuthController`, `UserController`.

2.  **Service Layer (`service` package)**
    *   **Felelősség:** Az üzleti logika központja. Tranzakciókezelés. Itt történik a döntéshozatal.
    *   **Orchestration:** Ez a réteg hívja meg az adatbázis repository-kat ÉS a külső integrációkat (pl. Gitea) is.
    *   *Példa:* `UserService` (meghívja a `UserRepository`-t és a `GiteaService`-t).

3.  **Integration Layer (`integration` package)**
    *   **Felelősség:** Kommunikáció külső rendszerekkel (Gitea, Docker Sandbox).
    *   **Szabály:** Elrejti a külső API-k hívásának technikai részleteit (REST call, Auth tokenek) a Service réteg elől.
    *   *Példa:* `GiteaClient` (Feign client vagy WebClient).

4.  **Repository Layer (`repository` package)**
    *   **Felelősség:** Közvetlen adatbázis műveletek (Spring Data JPA).
    *   *Példa:* `UserRepository`.

5.  **Domain/Model Layer (`model` package)**
    *   **Felelősség:** Az adatbázis entitások (Entity) és az üzleti objektumok.
    *   *Példa:* `Cadet` (Entity).

6.  **DTO Layer (`dto` package)**
    *   **Felelősség:** Adatátvitel a kliens és a szerver között (Request/Response objects).
    *   *Példa:* `RegisterRequest`, `UserResponse`.

---

## 2. User Management API Endpoints

Az alábbi végpontok a felhasználók (Kadétok és Adminok) kezelésére szolgálnak.

### Autentikáció

#### `POST /api/auth/register`
Új felhasználó regisztrálása.
*   **Folyamat:**
    1.  Validáció.
    2.  Felhasználó létrehozása a helyi adatbázisban (`cadets` tábla).
    3.  **Gitea Integráció:** Aszinkron vagy szinkron módon létrehoz egy Gitea felhasználót is ugyanazzal a névvel/jelszóval (vagy generált tokennel), és elmenti a kapott `gitea_user_id`-t.
*   **Request:**
    ```json
    {
      "username": "skywalker",
      "email": "luke@rebellion.com",
      "password": "StrongPassword123!"
    }
    ```
*   **Response (201 Created):**
    ```json
    {
      "id": "uuid-...",
      "username": "skywalker",
      "role": "CADET",
      "createdAt": "2023-..."
    }
    ```

#### `POST /api/auth/login`
Bejelentkezés és JWT token igénylése.
*   **Request:**
    ```json
    {
      "username": "skywalker",
      "password": "StrongPassword123!"
    }
    ```
*   **Response (200 OK):**
    ```json
    {
      "token": "eyJhbGciOiJIUzI1..."
    }
    ```

### Felhasználók (Users)

#### `GET /api/users`
Összes felhasználó listázása.
*   **Jogosultság:** Csak `ADMIN`.
*   **Query Params:** `?page=0&size=20` (lapozás).
*   **Response (200 OK):**
    ```json
    [
      {
        "id": "uuid-...",
        "username": "skywalker",
        "email": "luke@rebellion.com",
        "role": "CADET",
        "giteaUserId": 42
      },
      ...
    ]
    ```

#### `GET /api/users/{id}`
Egy konkrét felhasználó adatainak lekérése.
*   **Jogosultság:** `ADMIN` vagy ha a kért ID megegyezik a bejelentkezett felhasználóéval.
*   **Response (200 OK):** `UserResponse` objektum.

#### `GET /api/users/me`
A jelenleg bejelentkezett felhasználó saját adatainak lekérése.
*   **Jogosultság:** Bármely bejelentkezett felhasználó.
*   **Response (200 OK):** `UserResponse` objektum.

#### `PUT /api/users/{id}`
Felhasználó adatainak frissítése.
*   **Jogosultság:** `ADMIN` (bárkit szerkeszthet) vagy Saját felhasználó (csak saját magát).
*   **Megjegyzés:** Ha a jelszó vagy email változik, a **Gitea** fiókot is frissíteni kell a háttérben!
*   **Request:**
    ```json
    {
      "email": "new-email@rebellion.com",
      "avatarUrl": "..."
    }
    ```
*   **Response (200 OK):** Frissített `UserResponse`.

#### `DELETE /api/users/{id}`
Felhasználó törlése vagy archiválása.
*   **Jogosultság:** Csak `ADMIN`.
*   **Folyamat:**
    1.  Soft delete a helyi adatbázisban (vagy státusz állítás 'INACTIVE'-ra).
    2.  **Gitea Integráció:** A Gitea felhasználó zárolása vagy törlése (attól függően, hogy meg akarjuk-e tartani a kódját).
*   **Response (204 No Content)**

---

## 3. További Tervezett API Csoportok

A későbbi mérföldkövek során a következő végpont csoportok kerülnek kidolgozásra:

*   **Course Management (`/api/courses` / `/api/star-systems`):** Kurzusok létrehozása, szerkesztése (Admin).
*   **Mission Control (`/api/missions`):** Küldetések részletei, indítása.
    *   *Start Mission:* Repo létrehozása Gitea-n.
*   **Submission & Testing (`/api/submissions`):**
    *   *Submit:* Webhook fogadása a Gitea-tól vagy kézi indítás.
    *   Ez triggereli a Docker runner-t.
