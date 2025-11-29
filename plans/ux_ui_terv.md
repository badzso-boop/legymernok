# LégyMérnök.hu - UX és UI Terv (Űrhajós Kiadás)

Ez a dokumentum a `legymernok.hu` platform felhasználói élményének (UX) és felhasználói felületének (UI) alapkoncepcióját vázolja fel, egy űrutazásos narratívára építve.

## 1. UX Alapelvek: A Kadét Utazása

- **Felfedezés és Kaland:** A tanulás nem feladat, hanem egy kaland a kozmoszban. A felhasználó (a "kadét") új csillagrendszereket fedez fel, miközben elsajátítja a mérnöki tudást.
- **Építkezés és Haladás:** A kadét a nulláról építi fel a saját űrhajóját (a tudását). Minden sikeres küldetés egy új alkatrész, egy fejlettebb rendszer, ami közelebb viszi a végső célhoz. A haladást látványos animációk és jelvények kísérik.
- **Közösségi Élmény:** A kadétok egy flotta tagjai, akik segítik egymást. Később fórumok, közös projektek erősíthetik ezt.

## 2. Főbb Képernyők (Hajónaplók)

### A) Érkezés a Hangárba (Nem Authentikált Felhasználó)

#### 1. Dokkoló Kapu (Főoldal)
*Cél: A kadét beszervezése, a kaland ígéretének felvillantása.*
- **Konzol (Header):** Logó, "Csillagtérkép", "Dokkolás" (Bejelentkezés), "Besorozás" (Regisztráció) gomb.
- **Központi Kivető (Hero szekció):** Látványos, barátságos animáció: a Föld, mellette egy épülő űrhajó, háttérben a csillagok. Egy kedves asztronauta karakter integet.
    - Főcím: **"Programozd be a saját űrutazásod!"**
    - Alcím: "Tanuld meg a mérnöki alapokat küldetéseken keresztül, és építsd meg az űrhajót, ami eljuttat a csillagokig."
    - CTA gomb: **"Hajtóművek Indítása!"**
- **Kiképzési Terv ("Hogyan működik?"):**
    1.  **Tervezőasztal:** Építsd meg az űrhajód vázát (tanuld meg az alapokat).
    2.  **Szimulátor:** Teljesíts küldetéseket a Földön és a Holdon (oldj meg problémákat).
    3.  **Csillagközi Ugrás:** Fedezd fel a galaxist (juss el a haladóbb témákig).
- **Felfedezhető Csillagrendszerek (Kiemelt Kurzusok):** 3-4 kártya, csillagrendszerek látványos képeivel.
    - "A Python-köd" (Python Alapok)
    - "A Java Galaxis" (Java a Gyakorlatban)

#### 2. Csillagtérkép (Kurzusok Oldal)
*Cél: A galaxis és a benne rejlő tudás felfedezése.*
- **Navigációs Konzol:** Kereső és szűrők (pl. technológia: `Python`, `Java`; típus: `Űrhajó Rendszerek`, `Űrállomás Menedzsment`).
- **Csillagrendszerek Listája:** A kurzusok mint csillagrendszerek vagy galaxisok jelennek meg látványos kártyákon.
    - **Egy kártya tartalma:** Csillagrendszer neve, rövid leírás ("Ebben a rendszerben a Python hajtóanyagot állíthatod elő..."), nehézségi szint (pl. "Biztonságos zóna"), benne rejlő küldetések száma.

#### 3. Besorozás / Dokkolás (Regisztráció / Bejelentkezés)
*Cél: Belépés a flottába.*
- Tematikus űrlapok.
    - **Besorozás:** Kadét azonosító, Kommunikációs csatorna (E-mail), Biztonsági kód (Jelszó)...
    - **Dokkolás:** Azonosító és kód a belépéshez.

### B) A Pilótafülkében (Authentikált Felhasználó)

#### 1. Pilótafülke (Dashboard)
*Cél: A kadét személyes irányítópultja, minden fontos információ egy helyen.*
- **Konzol:** Logó, "Navigáció", "Csillagtérkép", Profil ikon (lenyíló menü: Hajónapló, Kijelentkezés).
- **Üdvözlő üzenet:** "Üdv a fedélzeten, [Kadét Név] kapitány!"
- **Aktuális Küldetés:** Egy nagy panel, ami a legutóbbi küldetést mutatja. "Vissza a szimulátorba!" gombbal.
- **Navigációs Célpontok:** A megkezdett csillagrendszerek listája. A progress bar egy hiperhajtómű töltöttségét jelző csík.
- **Felderítési Javaslatok:** A rendszer által ajánlott új csillagrendszerek.

#### 2. Navigációs Térkép (Kurzus Részletező Oldal)
*Cél: Egy csillagrendszer bolygóinak (küldetéseinek) áttekintése.*
- **Háttér:** Az adott csillagrendszer térképe.
- **Küldetés-lánc:** A küldetések mint bolygók vagy aszteroidák jelennek meg egy útvonalon.
    - **Egy bolygó (küldetés) a térképen:** Sorszám, név ("1. Bolygó: A 'Változók' légkörének elemzése"). A már meglátogatott bolygók színesek és egy kis zászló van rajtuk (✅). A következő bolygó villog. A távolabbiak még szürkék (🔒).

#### 3. Szimulátor (Workspace)
*Cél: A tényleges tanulási és kódolási felület, egy űrhajó konzoljának kinézetével.*
- **Háromosztatú, futurisztikus design:**
    1.  **Küldetési Napló (Leírás):**
        - A küldetés célja, a bolygó leírása.
        - Lépésről-lépésre instrukciók, mint egy hivatalos küldetési parancs.
    2.  **Parancssori Interfész (IDE):**
        - Beágyazott kód editor futurisztikus kerettel.
        - Gombok: "Szimuláció Futtatása", "Diagnosztika" (Tesztelés).
    3.  **Szenzorok (Kimenet):**
        - Fülek: "Telemetria" (Konzol) és "Diagnosztikai Jelentés" (Tesztek).
        - **Diagnosztikai Jelentés:** A tesztek eredményei. `RENDSZER OK` (zöld) vagy `KRITIKUS HIBA` (piros) üzenetekkel.
        - **Sikeres megoldás esetén:** Animáció: **"Küldetés Teljesítve! Hiperhajtómű feltöltve. Ugrás a következő koordinátára!"**. A "Tovább" gomb felirata: **"UGRÁS!"**

## 3. UI Stílusjegyek

- **Színpaletta:** Mély űr-kék és fekete háttér. Élénk, neon színek (cián, magenta, zöld) a szövegekhez, gombokhoz és aktív elemekhez, mintha egy hologram lenne.
- **Tipográfia:** Modern, sci-fi hatású, de jól olvasható betűtípus (pl. Orbitron, Space Grotesk). A kódhoz monospace (Fira Code).
- **Ikonok és Animációk:** Letisztult, futurisztikus ikonok. Finom animációk: pulzáló fények, scannelési vonalak, a hajó mozgása a térképen.
