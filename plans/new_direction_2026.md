# LégyMérnök.hu 2.0 - Új Irány (The 2026 Vision)

Ez a dokumentum a projekt új, gamifikált és mobil-első irányvonalát rögzíti. A cél egy olyan oktatási platform, ami nem egy unalmas admin felületre hasonlít, hanem egy interaktív űrhajó vezérlőpultjára.

---

## 1. Vízió és Hangulat (Look & Feel)
*   **Stílus:** Pixel Art / Retro-Futurisztikus Sci-Fi.
*   **Platform:** Mobile-First PWA (Progressive Web App). Teljes képernyős élmény, mintha egy natív app lenne.
*   **Karakter:** Egy barátságos, 2D Pixel Art robot, aki a segítőnk és narrátorunk.

---

## 2. User Flow (A Felhasználó Útja)

### A) Leszállás (Landing Page)
1.  **A Találkozás:** A felhasználó belép. Nincs unalmas menü. Egy Pixel Art robot integet.
2.  **Az Interakció:** A robot felett buborék: *"Készen állsz egy kalandra?"*.
3.  **Az Indulás:** "Igen" gomb -> Animáció: A robot legurul/kirepül a képernyőről, a kamera "bemegy" az űrhajóba.

### B) A Parancsnoki Híd (Command Deck - Főmenü)
Ez a központi hub. Nem egy lista, hanem egy grafikus műszerfal, nagy, érintésbarát blokkokkal.
*   **Térkép (Navigation):** A fő sztori szál.
*   **Agytorna (Fejtörők):** Napi logikai mini-játékok (Solo missions).
*   **Tudástár (Érdekességek):** A "Hard Science" részleg (Matek, Fizika, Elektronika). Itt lehet mélyebb tudást szerezni, amiért több XP/Jelvény jár.
*   **Karakter (Profile):** Fejlődés, statisztikák.

### C) A Galaxis Térkép (Navigation Map)
*   **Nézet:** 2D gráf (csillagtérkép).
*   **Csomópontok:** A Csillagrendszerek (Star Systems).
    *   **Színek:** Különböző típusok/állapotok (Villog = Aktuális, Szürke = Zárt, Zöld = Kész).
    *   **Kapcsolatok:** Gráf élek jelzik, melyik után melyik jön.
*   **Interakció:** Rábökés -> Részletes nézet (Mission Selector).

### D) A Műhely (Workshop / Electronics)
*   **Koncepció:** A virtuálisból a valóságba.
*   **Szimuláció:** Áramkörök összeállítása (Elem + Ellenállás + LED = Fény).
*   **Valóság:** Tervrajzok, amik alapján otthon is megépíthető.
*   **Webshop (Távlati):** Alkatrészcsomagok rendelése.

---

## 3. Technikai Megvalósítási Terv

### Frontend (React + Vite)
*   **Animációk:** `framer-motion` (átvezetések, buborékok, UI mozgások).
*   **Grafika:** Pixel Art assetek (SVG vagy PNG sprite-ok).
*   **Térkép:** `react-flow` vagy D3.js a gráf kirajzolásához.
*   **CSS:** Tailwind CSS (Grid layout a Command Deckhez).
*   **PWA:** `vite-plugin-pwa` a telepíthetőséghez és offline módhoz.

### Backend (Java Spring Boot)
*   **Adatmodell Bővítés:**
    *   `StarSystem`: `parentId` (előfeltétel), `coordinates` (x, y a térképhez).
    *   `Mission`: Típusok bővítése (`PUZZLE`, `ELECTRONICS`).
*   **Logika:**
    *   A "kódolós" feladatoknál a backend fordítja a parancsokat (pl. blokkokból) valódi kódra és commitolja Giteára.

---

## 4. Fejlesztési Ütemezés (Roadmap)

1.  **Fázis 1: Landing & Command Deck UI** (A "Wow" faktor megteremtése).
2.  **Fázis 2: Galaxis Térkép** (Adatbázis bővítés + Gráf vizualizáció).
3.  **Fázis 3: Tudástár & Fejtörők** (Statikus tartalom + Mini-logika).
4.  **Fázis 4: Robot Vezérlés** (A fő mission loop átalakítása).
