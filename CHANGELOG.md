# Captain's Log: LégyMérnök.hu Fejlesztési Napló

Ez a dokumentum a LégyMérnök.hu projekt fejlesztésének történetét örökíti meg, űrhajós napló stílusban.

---

## 🚀 Bejegyzés #7: A Térkép Aktiválása (Swagger UI)
**Stardate:** 2025.12.15
**Status:** Sikeres Küldetés

A hajó rendszerei bonyolulttá váltak. Szükségünk volt egy térképre, hogy eligazodjunk a végpontok (API Endpoints) labirintusában. Aktiváltuk a **Swagger UI** modult. Kezdeti inkompatibilitási turbulenciák (`NoSuchMethodError`) léptek fel a régi navigációs szoftver (`springdoc 2.3.0`) és az új hajtómű (`Spring Boot 3.4+`) között, de egy verziófrissítéssel (`2.6.0`) stabilizáltuk a rendszert. Most már minden tiszt tisztán látja a hajó összes funkcióját egy interaktív felületen.

*   **Technikai részletek:**
    *   `springdoc-openapi` integráció.
    *   Security Config finomhangolása a publikus dokumentációhoz.
    *   Verziókonfliktus elhárítása.

---

## 🛰️ Bejegyzés #6: Mission Control Automatizáció
**Stardate:** 2025.12.15
**Status:** Rendszer Élesítve

A Parancsnokság (Admin) számára lehetővé tettük, hogy ne csak manuálisan adminisztráljanak. Megépítettük az automatizált csatornát a Backend és a Kódraktár (Gitea) között. Mostantól, ha egy tiszt új küldetést (Mission) definiál, a rendszer a háttérben automatikusan létrehozza a hozzá tartozó tárolót és feltölti a kezdőcsomagot. A manuális munka a múlté.

*   **Technikai részletek:**
    *   `GiteaService` bővítése: `createRepository`, `createFile` API hívások.
    *   `MissionService` refaktorálás: Template fájlok fogadása és feltöltése.
    *   `CreateMissionRequest` DTO módosítása.

---

## 🛡️ Bejegyzés #5: Védelmi Pajzsok és Identitás (Auth & Security)
**Stardate:** 2025.12.14
**Status:** Pajzsok 100%-on

A hajó biztonsága elsődleges. Beüzemeltük a **Spring Security** védelmi rendszert. Minden kadét és tiszt mostantól egyedi azonosítót és titkosított belépési kódot (BCrypt) kap. A kommunikációt **JWT (JSON Web Token)** alapú igazolványokkal biztosítottuk, így a rendszerünk állapota megmarad (Stateless), de a biztonság garantált. A Gitea identitásokat szinkronizáltuk a központi adatbázissal.

*   **Technikai részletek:**
    *   `SecurityConfig` és `JwtAuthenticationFilter` implementálása.
    *   Jelszó hash-elés (`PasswordEncoder`).
    *   Role-based authorization (ADMIN vs CADET).
    *   Custom Exception Handling (`UserNotFound`, `BadCredentials`).

---

## 📦 Bejegyzés #4: A Kódraktár (Gitea) Integrációja
**Stardate:** 2025.11.30
**Status:** Kapcsolat Stabil

Sikeresen felvettük a kapcsolatot a külső Kódraktárral (Gitea). A hajó mostantól képes önállóan kommunikálni a raktárral, felhasználókat létrehozni és törölni. Ez a lépés elengedhetetlen volt ahhoz, hogy minden kadétnak saját, privát munkaterülete legyen a jövőben.

*   **Technikai részletek:**
    *   `GiteaService` létrehozása (RestClient).
    *   API kommunikáció implementálása (User CRUD).
    *   `application.properties` konfiguráció.

---

## 🏗️ Bejegyzés #3: A Hajótest Felépítése (Backend & DB)
**Stardate:** 2025.11.29
**Status:** Szerkezet Stabil

Lefektettük az alapokat. A hajtómű (Spring Boot Backend) és az üzemanyagtartály (PostgreSQL Adatbázis) a helyére került. Megterveztük a belső tereket (Adatbázis Séma): Csillagrendszerek (Kurzusok) és Küldetések (Leckék) tárolására alkalmas rekeszeket hoztunk létre.

*   **Technikai részletek:**
    *   Spring Boot projekt scaffold.
    *   PostgreSQL kapcsolat (`spring-boot-starter-data-jpa`).
    *   Liquibase/Flyway helyett `ddl-auto` (fejlesztői mód).
    *   Entitások (`Cadet`, `StarSystem`, `Mission`) létrehozása.

---

## 🐳 Bejegyzés #2: Konténerizáció (Docker Setup)
**Stardate:** 2025.11.29
**Status:** Környezet Izolálva

Hogy a hajó bárhol bevethető legyen, az egész rendszert konténerekbe zártuk. A `docker-compose` vezérlőpult segítségével egyetlen paranccsal indítható a teljes flotta: Adatbázis, Backend, Frontend és Gitea. A hálózati kommunikáció a konténerek között biztosított.

*   **Technikai részletek:**
    *   `Dockerfile`-ok írása (Backend: Multi-stage build, Frontend: Node+Nginx).
    *   `docker-compose.yml` összeállítása.
    *   Hálózati izoláció és Volume-ok konfigurálása.

---

## 📜 Bejegyzés #1: A Terv (Genesis)
**Stardate:** 2025.11.29
**Status:** Projekt Indulása

Megszületett a vízió. Egy rendszer, ahol a jövő mérnökei játékos formában, valós eszközökkel tanulhatnak. A tervrajzok (`terv.md`, `api_spec.md`) elkészültek, az irány kijelölve. A cél: A csillagok.

*   **Technikai részletek:**
    *   Projekt struktúra kialakítása.
    *   Dokumentációk (Terv, API specifikáció, DB séma) megírása.
    *   Git repository inicializálása.
