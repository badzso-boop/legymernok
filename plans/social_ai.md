# Social AI - Az Automatikus Hajónapló

Ez a dokumentum a **LégyMérnök.hu** "Building in Public" stratégiáját támogató, automatizált tartalomgeneráló rendszer tervét tartalmazza.

## 1. Koncepció: "A Fedélzeti Számítógép Jelenti"

A cél, hogy a fejlesztés minden jelentősebb lépését (commitját) transzparens, de szórakoztató módon osszuk meg a közönséggel. Nem száraz changelogokat posztolunk, hanem a projekt "történetét" meséljük el a választott űrhajós narratíván keresztül.

**Az AI Persona:**
A posztok írója a rendszer "Fedélzeti Mesterséges Intelligenciája". Stílusa:
-   **Technikai, de lelkes:** Érti a mérnöki részleteket, de izgatott a haladás miatt.
-   **Narratív:** A kódot az űrhajó építéseként interpretálja (pl. Docker beállítása = "Létfenntartó kapszulák izolálása").

## 2. Működési Folyamat (Workflow)

A folyamat egy **GitHub Actions** pipeline-ra épül, ami csak a `main` branch-re érkező push-ok esetén fut le, és csak akkor, ha a commit üzenet nem tartalmazza a `[no-social]` flaget.

### Lépések:

1.  **Trigger:** Code push a `main` ágra.
2.  **Elemzés (Extraction):**
    -   A script kinyeri a commit üzenetet és a módosított fájlok listáját (`git diff --stat`).
    -   Eldönti a commit típusát (pl. `feat`, `fix`, `chore`, `refactor`).
3.  **Szöveg Generálás (LLM - pl. Gemini Pro / GPT-4):**
    -   A commit adatok alapján generál egy rövid (Twitter/LinkedIn) és egy hosszú (Blog) poszt szöveget.
    -   Stílus: "Space Engineer" / "Sci-Fi".
4.  **Kép Generálás (Image Gen - pl. Gemini / DALL-E 3 / Midjourney):**
    -   A szöveg alapján generál egy promptot, majd abból egy képet.
    -   Stílus: Cyberpunk, Space Opera, Blueprint, Neon.
5.  **Publikálás / Jóváhagyás:**
    -   **V1 (Biztonságos):** Az elkészült szöveget és képet elküldi egy privát **Discord** csatornára (Webhookon keresztül) vagy egy Pull Request kommentbe. A fejlesztő innen manuálisan posztolja.
    -   **V2 (Automata):** API-n keresztül közvetlenül kiteszi Twitterre/LinkedIn-re (csak megbízható promptok esetén ajánlott).

## 3. Prompt Engineering Tervek

### A) Szöveg Generálás (Text Prompt Template)

```text
Szerep: Te vagy a LégyMérnök.hu oktatási platform fedélzeti számítógépe.
Feladat: Írj egy social media posztot az alábbi kódváltoztatásról.
Bemenet (Commit): "{commit_message}"
Változások: "{git_diff_summary}"

Instrukciók:
1. Használd a projekt űrhajós/sci-fi metaforáit (pl. Backend = Hajtómű, Frontend = Műszerfal, Bug = Űrtörmelék/Glitch).
2. Legyél lelkes, mintha most szereltünk volna be egy új alkatrészt az űrhajóba.
3. A poszt vége tartalmazzon releváns hashtageket (#buildinpublic #java #react #coding).
4. Kimenet formátuma: JSON (twitter_text, linkedin_text, blog_summary).
```

### B) Kép Generálás (Image Prompt ötletek)

Az AI-nak a technikai változást kell vizualizálnia.

| Commit Típus | Vizuális Metafora | Kép Stílus Prompt Részlet |
|---|---|---|
| **Adatbázis (SQL/Postgres)** | Adatkristályok, Holografikus könyvtár, Szerver termek kábelekkel | `futuristic server room, glowing blue data crystals, isometric view, cyan and magenta lighting` |
| **Frontend (React/UI)** | Űrhajó pilótafülke, Hologram kivetítő, HUD (Head-up Display) | `spaceship cockpit view, complex holographic interface, digital dashboard, floating screens, ux design concept` |
| **Backend (Java/Spring)** | Hajtómű, Reaktormag, Gépterem, Csövek és áramkörök | `engine room of a starship, glowing energy core, intricate mechanical details, steam and sparks, engineering aesthetic` |
| **Bugfix** | Hegesztő robot, Szikrák, Rendszer helyreállítva (zöld fények) | `robot repairing a hull breach, welding sparks, system diagnostic screen showing 'OK' in green, gritty sci-fi` |
| **CI/CD / Docker** | Robotkarok összeszerelnek valamit, Konténerek az űrben | `automated factory arm assembling a futuristic device, shipping containers floating in zero gravity, organized chaos` |

## 4. Technikai Megvalósítás (Mérföldkő 0 kiegészítés)

A rendszerhez létrehozunk egy `.github/workflows/social-ai.yml` fájlt és egy `scripts/social_generator.py` Python scriptet.

**Szükséges API Kulcsok (GitHub Secrets):**
-   `LLM_API_KEY` (A szövegíráshoz és a kép promptoláshoz)
-   `IMAGE_GEN_API_KEY` (Ha külön szolgáltatás kell a képhez)
-   `DISCORD_WEBHOOK_URL` (A generált tartalom elküldéséhez)

## 5. Példa Kimenet

**Commit:** `feat: Add dedicated Docker container for code execution`

**Generált Tweet:**
> 🚀 A hajónapló új bejegyzése: A biztonsági szimulációs kamrák (Docker konténerek) aktívak! Mostantól minden kadét kódja egy izolált térben fut, így nem robban fel a fő hajtómű, ha valaki végtelen ciklust ír. 🌌👨‍🚀
>
> #LégyMérnök #DevLog #Docker #SafetyFirst #CodingEducation

**Generált Kép:**
Egy futurisztikus, lebegő üvegkapszula a sötét űrben, benne egy ragyogó kódsorral, amit kék lézerek szkennelnek.
