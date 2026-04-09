# Áramkör Szimuláció — Admin & Kadét Flow Dokumentum

> **Állapot:** Tervezési referencia
> **Kapcsolódó:** `circuit-simulation.md` (adatmodell), `mission-forge.md` (analóg Forge minta)

---

## Áttekintés — Mi él hol?

| Adat | Tárolás helye | Megjegyzés |
|---|---|---|
| Breadboard kapcsolás (template) | PostgreSQL (`circuit_def_components`, `_connections`) | Admin szerkeszti |
| Breadboard kapcsolás (kadét) | PostgreSQL (`cadet_circuit_components`, `_connections`) | Sablon másolata + kadét módosításai |
| **Arduino kód (template)** | **Gitea repo** (`circuit-mission-{id}-template`) | Admin szerkeszti Monaco Editorban |
| **Arduino kód (kadét)** | **Gitea repo** (`circuit-{missionId}-{cadetId}`) | Kadét szerkeszti Monaco Editorban |
| Verifikációs szabályok | PostgreSQL (`circuit_verification_checks`) | Admin definiálja |
| Verifikáció eredménye | PostgreSQL (`cadet_verification_results`) | Backend számolja |
| Fordítási kimenet (`.hex`) | **Nem tárolva** — minden fordításnál generálódik | Arduino CLI adja vissza |
| Analóg áramkör (Falstad szöveg) | PostgreSQL (`analog_circuit_definitions`, `cadet_analog_saves`) | Nincs Gitea-függőség |

> ⚠️ **Implementációs gap:** A jelenlegi `CadetCircuitService.startCircuitMission()` **nem hívja** a `GiteaService`-t.
> A `CadetCircuitSave` entitásból **hiányzik** a `giteaRepoUrl` mező.
> Ezeket a digitális (avr8js) flow-ban pótolni kell, mielőtt a szimuláció elkészül.

---

## I. Admin Flow — Digitális (avr8js) Misszió Létrehozása

### Összefoglaló lépések

```
1. Admin létrehoz egy CIRCUIT_SIMULATION típusú Mission-t
2. Admin létrehozza a CircuitDefinition-t (board típus kiválasztás)
3. Admin feltölti a kódsablont Monaco Editorba → Gitea template repóba kerül
4. Admin összerakja a sablon kapcsolást (drag-and-drop canvas)
5. Admin definiálja a verifikációs szabályokat
6. Admin publikálja → kadétok elkezdhetik
```

### UML Szekvencia Diagram

```mermaid
sequenceDiagram
    actor Admin
    participant UI as Admin UI<br/>(React)
    participant API as Backend API<br/>(Spring Boot)
    participant DB as PostgreSQL
    participant Gitea

    %% 1. Mission létrehozás
    Admin->>UI: Rákattint "Új Misszió" gombra<br/>(típus: CIRCUIT_SIMULATION)
    UI->>API: POST /api/missions/forge/initialize<br/>{name, starSystemId, type: CIRCUIT_SIMULATION}
    API->>DB: INSERT missions (type=CIRCUIT_SIMULATION, status=DRAFT)
    API->>Gitea: createEmptyRepository("circuit-{missionId}-template", isPrivate=true)
    Gitea-->>API: repoCloneUrl
    API->>DB: UPDATE missions SET templateRepositoryUrl = repoCloneUrl
    API-->>UI: MissionResponse {id, templateRepositoryUrl}

    %% 2. CircuitDefinition létrehozás
    Admin->>UI: Megnyílik a Circuit Editor oldal<br/>Kiválasztja a board típust (pl. Arduino Uno)
    UI->>API: POST /api/circuit/definitions<br/>{missionId, boardType: ARDUINO_UNO}
    API->>DB: INSERT circuit_definitions (missionId, boardType, status=IN_WORK)
    API-->>UI: CircuitDefinitionResponse {id, boardType, status}

    %% 3. Kódsablon feltöltés
    Admin->>UI: Monaco Editorban megírja<br/>az Arduino kódsablont (sketch.ino)
    UI->>API: POST /api/missions/{missionId}/forge/save<br/>{files: {"sketch.ino": "void setup()..."}}
    API->>Gitea: uploadFiles(adminRepo, "circuit-{missionId}-template",<br/>{"sketch.ino": kódTartalom})
    API-->>UI: OK

    %% 4. Sablon kapcsolás összerakása
    Admin->>UI: Drag-and-drop canvas-on<br/>komponenseket húz le (LED, Resistor, stb.)
    Admin->>UI: Összehúzogatja a pineket (kábelrajzolás)
    Admin->>UI: Rákattint "Mentés" gombra
    UI->>API: PUT /api/circuit/definitions/{defId}/canvas<br/>{components: [...], connections: [...]}
    API->>API: validateUniqueLabels(components)
    API->>DB: DELETE régi komponensek + kapcsolatok<br/>(bulk delete FK sorrendben)
    loop minden komponensnek
        API->>DB: INSERT circuit_def_components {label, type, posX, posY}
        opt ha van property (pl. ellenállás értéke)
            API->>DB: INSERT circuit_def_component_properties {key, value, unitId}
        end
    end
    loop minden kapcsolatnak
        API->>API: Normalizál: kisebb UUID → from
        API->>DB: INSERT circuit_def_connections {fromId, fromPin, toId, toPin}
    end
    API-->>UI: CircuitDefinitionResponse (aktuális canvas állapot)

    %% 5. Verifikációs szabályok
    Admin->>UI: Megnyitja a "Verifikáció" panelt
    Admin->>UI: Hozzáad egy CIRCUIT_TOPOLOGY checkot:<br/>"LED1 komponens legyen a canvason"
    UI->>API: POST /api/circuit/definitions/{defId}/checks<br/>{checkType: CIRCUIT_TOPOLOGY, labelFrom: "LED1", ...}
    API->>DB: INSERT circuit_verification_checks
    API-->>UI: CircuitVerificationCheckResponse

    Admin->>UI: Hozzáad egy GPIO_BEHAVIOR checkot:<br/>"D13 pin 1Hz villogás"
    UI->>API: POST /api/circuit/definitions/{defId}/checks<br/>{checkType: GPIO_BEHAVIOR, ...}
    API->>DB: INSERT circuit_verification_checks
    API-->>UI: CircuitVerificationCheckResponse

    %% 6. Publikálás
    Admin->>UI: Rákattint "Publikálás" gombra
    UI->>API: POST /api/circuit/definitions/{defId}/publish
    API->>DB: DELETE minden cadet_circuit_saves ahol<br/>circuitDefinitionId = defId (cascade törlés)
    Note over API,DB: Biztonsági lépés: ha volt korábbi tesztelés<br/>kadét-fiókkal, az törlődik
    API->>DB: UPDATE circuit_definitions SET status = PUBLISHED
    API-->>UI: CircuitDefinitionResponse {status: PUBLISHED}
    UI->>Admin: "Misszió publikálva — kadétok elkezdhetik"
```

### Admin UI oldalnézet — Circuit Editor képernyők

```
┌─────────────────────────────────────────────────────────────────────┐
│  Circuit Mission Editor — "LED Villogó"                             │
│                                                                     │
│  ┌─ 1. BOARD ──────┐  ┌─ 2. CANVAS ──────────────────────────────┐ │
│  │ Arduino Uno  ▼  │  │                                           │ │
│  └─────────────────┘  │   [BOARD: arduino_main]                  │ │
│                       │        │D13           │GND               │ │
│  ┌─ Komponens ────────│        │              │                  │ │
│  │  palette      ──┐ │      [R1: resistor]──[LED1: led]         │ │
│  │  🟥 LED          │ │           220Ω         anode/cathode     │ │
│  │  ▭ Resistor      │ │                                          │ │
│  │  ⏺ Pushbutton    │ │   [+] Drag komponenseket ide             │ │
│  └──────────────────┘ └──────────────────────────────────────────┘ │
│                                                                     │
│  ┌─ 3. KÓD (Monaco Editor) ─────────────────────────────────────┐  │
│  │  void setup() { pinMode(13, OUTPUT); }                        │  │
│  │  void loop() { digitalWrite(13, HIGH); delay(500);           │  │
│  │               digitalWrite(13, LOW);  delay(500); }          │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌─ 4. VERIFIKÁCIÓ ──────────────────────────────────────────────┐  │
│  │  [+] Új check                                                  │  │
│  │  ✓ CIRCUIT_TOPOLOGY — "LED1 jelen legyen"          [🗑]       │  │
│  │  ✓ PATH_EXISTS — "D13 → GND összekötve LED+R-en át"  [🗑]   │  │
│  │  ✓ GPIO_BEHAVIOR — "D13: 1Hz villogás"               [🗑]   │  │
│  └──────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  [Mentés]  [Tesztelés saját fiókkal]  [🚀 Publikálás]              │
└─────────────────────────────────────────────────────────────────────┘
```

---

## II. Kadét Flow — Digitális (avr8js) Misszió Teljesítése

### Összefoglaló lépések

```
1. Kadét megnyitja a Star System oldalt → látja a missziókat
2. Kadét rákattint a CIRCUIT_SIMULATION típusú missziókra → Mission Detail
3. Kadét rákattint "Misszió Elkezdése" → Gitea repo jön létre, sablon másolódik
4. Kadét az áramkör canvason módosíthat (ha allowSchematicEdit=true)
5. Kadét Monaco Editorban írja/módosítja az Arduino kódot
6. Kadét rákattint "Fordítás" → backend Arduino CLI → .hex vissza
7. A .hex betöltődik avr8js-be → szimuláció fut a böngészőben
8. Kadét rákattint "Verifikálás" → backend ellenőriz + frontend GPIO adatokat küld
9. Sikeres verifikáció → Misszió COMPLETED státuszba kerül
```

### UML Szekvencia Diagram

```mermaid
sequenceDiagram
    actor Cadet
    participant UI as Kadét UI<br/>(React + avr8js)
    participant Worker as Web Worker<br/>(avr8js emulátor)
    participant API as Backend API<br/>(Spring Boot)
    participant DB as PostgreSQL
    participant Gitea
    participant ArduinoCLI as Arduino CLI<br/>(Docker)

    %% 1-2. Misszió megnyitás
    Cadet->>UI: Star Map → Star System → "LED Villogó" misszió
    UI->>API: GET /api/missions/{id}
    API-->>UI: MissionResponse {type: CIRCUIT_SIMULATION, ...}
    UI->>Cadet: Mission Detail oldal<br/>"Misszió elkezdése" gomb

    %% 3. Misszió elkezdése (HIÁNYZÓ GITEA RÉSZ!)
    Cadet->>UI: Kattint "Misszió elkezdése"
    UI->>API: POST /api/circuit/missions/{missionId}/start

    rect rgb(255, 240, 240)
        Note over API: ⚠️ Ez a rész NINCS implementálva ⚠️
        API->>DB: Megvan már CadetCircuitSave?
        alt Még nem — első indítás
            API->>Gitea: createMissionRepository(missionId, "circuit", cadet)<br/>→ template repóból másolja a sketch.ino-t
            Gitea-->>API: giteaRepoUrl
            API->>DB: INSERT cadet_circuit_saves<br/>{cadetId, circuitDefinitionId, giteaRepoUrl}
            API->>DB: Másolja a sablon komponenseket + kapcsolatokat<br/>(circuit_def_components → cadet_circuit_components)
        else Már megvan — folytatás
            Note over API: Visszaadja a meglévő save-et
        end
    end

    API-->>UI: CadetCircuitSaveResponse<br/>{id, giteaRepoUrl, components, connections}

    %% 4. Canvas szerkesztés (ha engedélyezett)
    Cadet->>UI: Módosítja a kapcsolást<br/>(komponens áthelyezés, kábel húzás)
    UI->>API: PUT /api/circuit/missions/{missionId}/canvas<br/>{components: [...], connections: [...]}
    API->>DB: Full-replace: DELETE régi + INSERT új<br/>komponensek és kapcsolatok
    API-->>UI: Frissített canvas állapot

    %% 5. Kód szerkesztés
    Cadet->>UI: Monaco Editorban módosítja a kódot
    Note over UI: A kód Gitea-ból töltődik be:<br/>GET /api/missions/{id}/forge/files
    UI->>API: POST /api/missions/{id}/forge/save<br/>{files: {"sketch.ino": módosítottKód}}
    API->>Gitea: uploadFiles(adminRepo, cadetRepoName, files)
    API-->>UI: OK

    %% 6. Fordítás
    Cadet->>UI: Rákattint "Fordítás" gombra
    UI->>API: POST /api/circuit/missions/{missionId}/compile

    rect rgb(255, 240, 240)
        Note over API: ⚠️ Ez a szolgáltatás NINCS implementálva ⚠️<br/>(ArduinoCompilerService szükséges)
        API->>Gitea: getFileContent(cadetRepo, "sketch.ino")
        Gitea-->>API: kódSzöveg
        API->>ArduinoCLI: arduino-cli compile --fqbn arduino:avr:uno sketch.ino
        ArduinoCLI-->>API: .hex bináris (sikeres) VAGY hibaüzenet
        API->>DB: UPDATE cadet_circuit_saves<br/>SET simulationStatus = COMPILE_ERROR (ha hiba)
    end

    API-->>UI: {hexBase64: "...", success: true} VAGY {error: "..."}

    %% 7. Szimuláció futtatása (tisztán frontend!)
    UI->>Worker: loadHex(hexBase64)<br/>startSimulation()
    Worker->>Worker: avr8js CPU tick loop<br/>(AVR emulálás)
    Worker-->>UI: pinStateChanged(pin: "D13", state: HIGH)
    UI->>UI: LED1 wokwi-led component state=HIGH<br/>(vizuális visszajelzés)
    Worker-->>UI: pinStateChanged(pin: "D13", state: LOW)
    UI->>UI: LED1 state=LOW

    %% 8. Verifikálás
    Cadet->>UI: Rákattint "Ellenőrzés" gombra
    UI->>Worker: collectGPIOData(durationMs: 10000)
    Worker-->>UI: gpioLog: [{time: 0, pin: "D13", state: HIGH}, ...]
    UI->>API: POST /api/circuit/missions/{missionId}/verify<br/>{gpioLog: [...]}

    Note over API: Phase 1 — Backend (instant, DB alapú):<br/>CIRCUIT_TOPOLOGY és PATH_EXISTS checkek
    API->>DB: SELECT cadet_circuit_components WHERE saveId = ?
    API->>DB: SELECT cadet_circuit_connections WHERE saveId = ?
    API->>API: BFS gráfbejárás — path_exists checkek

    Note over API: Phase 2 — Backend (GPIO log alapú):<br/>GPIO_BEHAVIOR, SERIAL_OUTPUT, PWM checkek
    API->>API: gpioLog elemzés — villogás frekvencia, duty cycle, serial szöveg

    API->>DB: DELETE régi cadet_verification_results
    API->>DB: INSERT új cadet_verification_results (minden checkhez)
    API-->>UI: [{checkId, passed: true, i18nKey: "check.topology"}, ...]

    %% 9. Sikeres teljesítés
    alt Minden check passed=true
        UI->>API: POST /api/missions/{id}/start (misszió befejezése)
        API->>DB: UPDATE cadet_missions SET status = COMPLETED
        API-->>UI: Siker
        UI->>Cadet: 🎉 "Misszió teljesítve!"<br/>(XP animáció, kitüntetés)
    else Van hibás check
        UI->>Cadet: Hibák listája (i18n kulcsok alapján lokalizált<br/>hibaüzenetekkel), javítási javaslatok
    end
```

### Kadét UI oldalnézet — Circuit Simulator képernyők

```
┌─────────────────────────────────────────────────────────────────────┐
│  🔭 LED Villogó Misszió                           [⏱ 00:12:34]     │
│                                                                     │
│  ┌─ CANVAS (React Flow / @xyflow) ─────────────────────────────┐   │
│  │                                                               │   │
│  │    [🟦 arduino_main]                                          │   │
│  │     D13 ──── [▭ R1: 220Ω] ──── [🔴 LED1] ──── GND           │   │
│  │                                                               │   │
│  │  [+ Komponens hozzáadása] (ha allowSchematicEdit=true)        │   │
│  └───────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─ KÓD EDITOR (Monaco) ──────────────────────────────────────┐    │
│  │  void setup() {                                              │    │
│  │    pinMode(13, OUTPUT);                                      │    │
│  │  }                                                           │    │
│  │  void loop() {                                               │    │
│  │    digitalWrite(13, HIGH); delay(500);  // ← kadét írja     │    │
│  │    digitalWrite(13, LOW);  delay(500);                       │    │
│  │  }                                                           │    │
│  └──────────────────────────────────────────────────────────────┘    │
│                                                                     │
│  [💾 Kód mentése]  [🔨 Fordítás]  [▶ Szimuláció]  [✅ Ellenőrzés]  │
│                                                                     │
│  ┌─ SZIMULÁTOR (wokwi-elements Web Components) ───────────────┐    │
│  │  <wokwi-arduino-uno />                                       │    │
│  │  <wokwi-led color="red" value={pin13State} />                │    │
│  │                                                               │    │
│  │  💡 LED villog (1Hz)     [⏸ Megállítás]  [4x gyorsítás]    │    │
│  └───────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  ┌─ ELLENŐRZÉSI EREDMÉNYEK ─────────────────────────────────────┐   │
│  │  ✅  LED1 komponens megtalálható a canvason                   │   │
│  │  ✅  D13 összekötve GND-vel LED-en és ellenálláson át        │   │
│  │  ❌  D13 villogási frekvencia: 2Hz (elvárás: 1Hz ±20%)       │   │
│  └───────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## III. Admin Flow — Analóg (Falstad/CircuitJS1) Misszió Létrehozása

### Összefoglaló lépések

```
1. Admin létrehoz CIRCUIT_SIMULATION típusú Mission-t
2. Admin létrehozza az AnalogCircuitDefinition-t
3. Admin a Falstad iframeben megrajzolja a kezdő (hiányos) kapcsolást
4. Admin elmenti a Falstad szöveget (starter + solution)
5. Admin definiálja a verifikációs feltételeket (feszültség, áram tartományok)
6. Admin publikálja
```

### UML Szekvencia Diagram

```mermaid
sequenceDiagram
    actor Admin
    participant UI as Admin UI<br/>(React)
    participant Falstad as CircuitJS1<br/>(iframe)
    participant API as Backend API<br/>(Spring Boot)
    participant DB as PostgreSQL

    Note over Admin,DB: Analóg misszióhoz NEM kell Gitea — nincs kód!

    %% 1. Mission + AnalogCircuitDefinition
    Admin->>UI: "Új Misszió" → típus: CIRCUIT_SIMULATION, altípus: ANALOG
    UI->>API: POST /api/missions/forge/initialize<br/>{type: CIRCUIT_SIMULATION}
    API->>DB: INSERT missions
    API-->>UI: MissionResponse {id}

    UI->>API: POST /api/circuit/analog/definitions<br/>{missionId, falstadText: "$ 1 ..."}
    API->>DB: INSERT analog_circuit_definitions<br/>{missionId, falstadText, status=IN_WORK}
    API-->>UI: AnalogCircuitDefinitionResponse {id}

    %% 2. Kapcsolás rajzolás
    Admin->>UI: Megnyitja az Analog Editor oldalt
    UI->>Falstad: iframe betöltése (CircuitJS1)
    Admin->>Falstad: Megrajzolja a hiányos kapcsolást<br/>(pl. ellenállás benne, LED hiányzik)
    Admin->>UI: Kattint "Mentés sablonként"
    UI->>Falstad: postMessage({type: 'getCircuitState'})
    Falstad-->>UI: postMessage({type: 'circuitState', data: "$ 1 0.000005..."})
    UI->>API: PUT /api/circuit/analog/definitions/{id}/falstad<br/>{falstadText: "$ 1 ..."}
    API->>DB: UPDATE analog_circuit_definitions SET falstad_text = ?
    API-->>UI: OK

    %% 3. Verifikációs feltételek
    Admin->>UI: Hozzáad egy NODE_VOLTAGE checkot:<br/>"A LED-en 2V legyen"
    UI->>API: POST /api/circuit/analog/definitions/{id}/checks<br/>{checkType: NODE_VOLTAGE, nodeOrLabel: "led_node",<br/>expectedValue: 2.0, tolerance: 0.3, unitId: voltId}
    API->>DB: INSERT analog_verification_checks
    API-->>UI: AnalogVerificationCheckResponse

    Admin->>UI: Hozzáad egy LED_LIGHTS checkot:<br/>"A LED világítson"
    UI->>API: POST /api/circuit/analog/definitions/{id}/checks<br/>{checkType: LED_LIGHTS, ...}
    API->>DB: INSERT analog_verification_checks

    %% 4. Publikálás
    Admin->>UI: Kattint "Publikálás"
    UI->>API: POST /api/circuit/analog/definitions/{id}/publish
    API->>DB: UPDATE analog_circuit_definitions SET status = PUBLISHED
    API-->>UI: {status: PUBLISHED}
```

---

## IV. Kadét Flow — Analóg (Falstad) Misszió Teljesítése

### Összefoglaló lépések

```
1. Kadét megnyitja az analóg missziókat
2. A Falstad iframe betöltődik a starter kapcsolással
3. Kadét kiegészíti a kapcsolást (LED, összeköttetések)
4. Kadét kattint "Mentés" → Falstad szöveg DB-be kerül
5. Kadét kattint "Ellenőrzés" → frontend lekéri a szimulációs értékeket
6. Frontend POST-olja az értékeket a backendnek → eredmény
```

### UML Szekvencia Diagram

```mermaid
sequenceDiagram
    actor Cadet
    participant UI as Kadét UI<br/>(React)
    participant Falstad as CircuitJS1<br/>(iframe)
    participant API as Backend API<br/>(Spring Boot)
    participant DB as PostgreSQL

    %% 1. Megnyitás
    Cadet->>UI: Mission Detail → "Misszió Elkezdése"
    UI->>API: GET /api/circuit/analog/missions/{missionId}

    alt Nincs még cadet mentés
        Note over API: getCadetSave → nem találja → 404
        UI->>API: PUT /api/circuit/analog/missions/{missionId}<br/>{falstadText: def.falstadText} (automatikus init)
        API->>DB: INSERT cadet_analog_saves<br/>{cadetId, definitionId, falstadText=starter}
    end

    API-->>UI: CadetAnalogSaveResponse {falstadText: "$ 1 ..."}

    %% 2. iframe betöltés
    UI->>Falstad: iframe src betöltése + postMessage circuitLoad(falstadText)
    Falstad->>Cadet: Megjelenik a hiányos kapcsolás<br/>(pl. ellenállás + üres helyek)

    %% 3. Szerkesztés
    Cadet->>Falstad: Beilleszti a LED komponenst<br/>Összehúzza a kapcsolatokat

    %% 4. Mentés
    Cadet->>UI: Kattint "Mentés"
    UI->>Falstad: postMessage({type: 'getCircuitState'})
    Falstad-->>UI: {type: 'circuitState', data: "$ 1 ... w 192 ... r 220 ..."}
    UI->>API: PUT /api/circuit/analog/missions/{missionId}<br/>{falstadText: "$ 1 ... w 192 ..."}
    API->>DB: UPDATE cadet_analog_saves SET falstad_text = ?
    API-->>UI: CadetAnalogSaveResponse

    %% 5-6. Ellenőrzés
    Cadet->>UI: Kattint "Ellenőrzés"
    UI->>Falstad: postMessage({type: 'getNodeVoltages'})
    Falstad-->>UI: {type: 'nodeVoltages', data: {"led_node": 2.1, "gnd": 0.0, ...}}
    UI->>Falstad: postMessage({type: 'getBranchCurrents'})
    Falstad-->>UI: {type: 'branchCurrents', data: {"led_branch": 0.015, ...}}

    Note over UI,API: ⚠️ Analog verify endpoint még nem implementált<br/>POST /api/circuit/analog/missions/{missionId}/verify szükséges
    UI->>API: POST /api/circuit/analog/missions/{missionId}/verify<br/>{nodeVoltages: {...}, branchCurrents: {...}}
    API->>DB: SELECT analog_verification_checks WHERE definitionId = ?
    loop minden checkre
        API->>API: check.expectedValue ±tolerance összehasonlítás
    end
    API-->>UI: [{checkType: NODE_VOLTAGE, passed: true, ...}, ...]

    alt Minden check passed
        UI->>Cadet: ✅ "Kapcsolás helyes — misszió teljesítve!"
    else Hibás checkek
        UI->>Cadet: ❌ Hibák listája (pl. "A LED-en mért feszültség: 0V, elvárás: 2V ±0.3V")
    end
```

### Kadét UI oldalnézet — Analog Circuit képernyő

```
┌─────────────────────────────────────────────────────────────────────┐
│  ⚡ Ohm-törvény Misszió — "Köss be egy LED-et az ellenállás után"   │
│                                                                     │
│  📋 FELADAT:                                                        │
│  Egészítsd ki a kapcsolást! Az ellenállás már be van kötve.         │
│  Adj hozzá egy LED-et, és kösd rá a feszültségforrásra!             │
│                                                                     │
│  ┌─ FALSTAD IFRAME ──────────────────────────────────────────────┐  │
│  │                                                                │  │
│  │   [+5V] ──── [220Ω] ──── [_ _ _] ──── [GND]                  │  │
│  │                          ↑ ide kell                           │  │
│  │                          a LED                                │  │
│  │                                                                │  │
│  │   ◉ Szimulálás fut   Feszültség: 3.0V  Áram: 13.6mA          │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  [💾 Mentés]                                    [✅ Ellenőrzés]     │
│                                                                     │
│  ┌─ ELLENŐRZÉSI EREDMÉNYEK ─────────────────────────────────────┐   │
│  │  ✅  LED komponens megtalálható a kapcsolásban                 │   │
│  │  ✅  A LED-en mért feszültség: 2.1V (elvárás: 2.0V ±0.3V)    │   │
│  │  ✅  Ágáram: 13.6mA (elvárás: 10–30mA)                       │   │
│  └───────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## V. Hiányzó Implementációs Elemek (Gap Analízis)

| # | Hiányzó rész | Érintett fájl(ok) | Prioritás |
|---|---|---|---|
| 1 | `CadetCircuitSave.giteaRepoUrl` mező hiányzik | `CadetCircuitSave.java` | P1 — kötelező digitális flow-hoz |
| 2 | `startCircuitMission()` nem hívja `GiteaService`-t | `CadetCircuitService.java` | P1 — kötelező digitális flow-hoz |
| 3 | `ArduinoCompilerService` nincs megírva | Új fájl kellene | P1 — fordítás nélkül nincs szimuláció |
| 4 | `POST /api/circuit/missions/{id}/compile` endpoint hiányzik | Új controller metódus | P1 |
| 5 | `gpioLog` fogadása a `/verify` endpointban | `CadetCircuitController.java`, `CircuitVerificationService.java` | P1 — GPIO check most mindig false |
| 6 | Analóg `/verify` endpoint nincs megírva | Új controller metódus + service | P2 |
| 7 | `CadetMission` rekord létrehozása `start`-kor hiányzik | `CadetCircuitService.java` | P2 — misszió státusz tracking |
| 8 | Arduino CLI Docker image nem tartalmazza a `Dockerfile`-ban | `docker-compose.yml` / `Dockerfile` | P1 |

---

## VI. Komponent-szintű összefüggés térkép

```
ADMIN OLDAL                                     KADÉT OLDAL
────────────────────────────────────────────────────────────────

Mission                                         Mission
(CIRCUIT_SIMULATION)                            (CIRCUIT_SIMULATION)
     │                                               │
     ▼                                               ▼
CircuitDefinition ──── [sablon másolás] ──► CadetCircuitSave
  ├─ boardType                                  ├─ giteaRepoUrl ← ⚠️ HIÁNYZIK
  ├─ status (IN_WORK → PUBLISHED)               ├─ simulationStatus
  ├─ CircuitDefComponent[]                      ├─ CadetCircuitComponent[]
  │    └─ CircuitDefComponentProperty[]         │    └─ CadetCircuitComponentProperty[]
  ├─ CircuitDefConnection[]                     ├─ CadetCircuitConnection[]
  └─ CircuitVerificationCheck[]                 └─ CadetVerificationResult[]
                                                       └─ check (FK → CircuitVerificationCheck)

AnalogCircuitDefinition                         CadetAnalogSave
  ├─ falstadText (starter)                        ├─ falstadText (kadét módosítása)
  └─ AnalogVerificationCheck[]                    └─ simulationStatus

ComponentPinDefinition[] ─────────────────────────► Frontend PinResolver
  (Board pinout katalógus)                          (milyen pinre lehet kötni)

ComponentElectricalSpec[] ────────────────────────► Frontend circuit-engine
  (Elektromos szabályok)                            (real-time validáció mentéskor)
```
