# Circuit Forge — Frontend Terv

> Állapot: Tervezési fázis | Ág: `circuit_forge`
> Kapcsolódó: `circuit-simulation.md` (backend), `new_direction_2026.md` (UX irányvonal)

---

## I. Összefoglalás

A circuit feature frontend két teljesen különböző felhasználói élményre bomlik szét:

| Oldal | Design stílus | Technológia |
|---|---|---|
| **Admin — Circuit Forge** | MUI clean/modern (mint a többi admin oldal) | @xyflow/react canvas, Monaco Editor |
| **Kadét — Circuit Player** | Retro space terminál (mint StarMap / MyForge) | avr8js Web Worker, @wokwi/elements, Monaco Editor |

Ez a kettősség az egész tervben végigvonul — az admin az infrastruktúrát építi, a kadét a "missziót teljesíti".

---

## II. Külső könyvtárak

### Hozzáadandók (még nem telepítve)

```bash
npm install @xyflow/react          # Drag-and-drop circuit canvas (MIT)
npm install avr8js                 # AVR mikrokontroller emulátor (MIT)
npm install @wokwi/elements        # Arduino / LED / komponens Web Components (MIT)
npm install comlink                # Web Worker kommunikáció wrapper (Apache-2.0)
npm install @lit-labs/react        # Wokwi Web Components → React wrapper (BSD-3)
npm install elkjs                  # Automatikus schematic elrendezés (EPL-2.0)
npm install d3-path                # SVG útvonal generálás kábelgörbékhez (ISC)
```

### Már telepítve (érintett)

| Csomag | Szerep a circuit feature-ben |
|---|---|
| `@monaco-editor/react` | sketch.ino kódszerkesztő |
| `@tanstack/react-query` | API state kezelés (canvas mentés, compile poll) |
| `react-hook-form` + `zod` | Circuit definition form validáció (admin) |
| `@stomp/stompjs` | Compile log streaming (WebSocket) |
| `framer-motion` | Panel animációk, szimuláció state átmenetek |
| `lucide-react` | Ikonok (Play, Stop, Compile, Verify, Save) |
| `@mui/material` | Admin oldal UI komponensek |

---

## III. TypeScript típusok (`frontend/src/types/circuit.ts`)

Új fájl, amely az összes circuit-specifikus típust tartalmazza.

```typescript
// ─── Enumerációk ──────────────────────────────────────────────────────────────

export type BoardType =
  | 'ARDUINO_UNO'
  | 'ARDUINO_MEGA_2560'
  | 'ESP8266'
  | 'ESP32';

export type ComponentType =
  | 'ARDUINO_UNO'
  | 'ARDUINO_MEGA'
  | 'LED'
  | 'RESISTOR'
  | 'CAPACITOR'
  | 'PUSH_BUTTON'
  | 'POTENTIOMETER'
  | 'DHT11'
  | 'HC_SR04'
  | 'SERVO'
  | 'VCC'
  | 'GND'
  | 'BREADBOARD';

export type SimulationStatus =
  | 'NEVER_RUN'
  | 'COMPILING'
  | 'COMPILE_ERROR'
  | 'RUNNING'
  | 'STOPPED';

export type CircuitDefinitionStatus = 'IN_WORK' | 'PUBLISHED' | 'STALE';

export type CheckType =
  | 'CIRCUIT_TOPOLOGY'
  | 'PATH_EXISTS'
  | 'GPIO_BEHAVIOR'
  | 'SERIAL_OUTPUT'
  | 'PWM';

export type CheckSeverity = 'ERROR' | 'WARNING' | 'INFO';

export type AnalogCheckType =
  | 'VOLTAGE_AT_NODE'
  | 'CURRENT_THROUGH'
  | 'COMPONENT_EXISTS'
  | 'LED_LIGHTS';

// ─── Katalógus ────────────────────────────────────────────────────────────────

export interface UnitOfMeasureResponse {
  id: string;
  symbol: string;
  name: string;
}

export interface ComponentPinDefinitionResponse {
  id: string;
  componentType: ComponentType;
  boardType: BoardType | null;
  pinName: string;
  pinNumber: number | null;
  pinType: 'DIGITAL' | 'ANALOG' | 'PWM' | 'POWER' | 'GROUND' | 'SDA' | 'SCL';
}

export interface ComponentElectricalSpecResponse {
  id: string;
  componentType: ComponentType;
  propertyKey: string;
  defaultValue: string;
  unit: UnitOfMeasureResponse | null;
}

// ─── Circuit Definition (Admin) ───────────────────────────────────────────────

export interface CircuitDefComponentPropertyResponse {
  id: string;
  propertyKey: string;
  propertyValue: string;
  unitOfMeasure: UnitOfMeasureResponse | null;
}

export interface CircuitDefComponentResponse {
  id: string;
  componentType: ComponentType;
  label: string;
  posX: number;
  posY: number;
  properties: CircuitDefComponentPropertyResponse[];
}

export interface CircuitDefConnectionResponse {
  id: string;
  fromComponentId: string;
  fromPinName: string;
  toComponentId: string;
  toPinName: string;
}

export interface CircuitVerificationCheckResponse {
  id: string;
  checkType: CheckType;
  labelFrom: string | null;
  labelTo: string | null;
  expectedValue: string | null;
  i18nKey: string | null;
  severity: CheckSeverity;
  orderIndex: number;
}

export interface CircuitDefinitionResponse {
  id: string;
  missionId: string;
  boardType: BoardType;
  status: CircuitDefinitionStatus;
  components: CircuitDefComponentResponse[];
  connections: CircuitDefConnectionResponse[];
  checks: CircuitVerificationCheckResponse[];
  createdAt: string;
  updatedAt: string;
}

// ─── Cadet Circuit Save ────────────────────────────────────────────────────────

export interface CadetCircuitComponentPropertyResponse {
  id: string;
  propertyKey: string;
  propertyValue: string;
  unitOfMeasure: UnitOfMeasureResponse | null;
}

export interface CadetCircuitComponentResponse {
  id: string;
  componentType: ComponentType;
  label: string;
  posX: number;
  posY: number;
  properties: CadetCircuitComponentPropertyResponse[];
}

export interface CadetCircuitConnectionResponse {
  id: string;
  fromComponentId: string;
  fromPinName: string;
  toComponentId: string;
  toPinName: string;
}

export interface CadetVerificationResultResponse {
  checkId: string;
  i18nKey: string | null;
  orderIndex: number | null;
  severity: CheckSeverity | null;
  passed: boolean;
  message: string | null;
  checkedAt: string | null;
}

export interface CadetCircuitSaveResponse {
  id: string;
  circuitDefinitionId: string;
  giteaRepoUrl: string | null;
  lastCompileError: string | null;
  stale: boolean;
  simulationStatus: SimulationStatus;
  simulationStartedAt: string | null;
  compilationTimeMs: number | null;
  totalTimeSpentMs: number | null;
  components: CadetCircuitComponentResponse[];
  connections: CadetCircuitConnectionResponse[];
  verificationResults: CadetVerificationResultResponse[];
  createdAt: string;
  updatedAt: string;
}

// ─── Compile ──────────────────────────────────────────────────────────────────

export interface CompileCircuitResponse {
  success: boolean;
  hexBase64: string | null;
  fqbn: string | null;
  boardType: BoardType | null;
  compilationTimeMs: number | null;
  cached: boolean;
  error: string | null;
}

// ─── Analog ──────────────────────────────────────────────────────────────────

export interface CadetAnalogSaveResponse {
  id: string;
  missionId: string;
  falstadText: string | null;
  updatedAt: string;
}

export interface AnalogVerificationCheckResponse {
  id: string;
  checkType: AnalogCheckType;
  nodeOrLabel: string | null;
  expectedValue: number | null;
  tolerancePercent: number;
  i18nKey: string | null;
  severity: CheckSeverity;
  orderIndex: number;
}

export interface AnalogCheckResultResponse {
  checkId: string;
  i18nKey: string | null;
  passed: boolean;
  observed: number | null;
  expected: number | null;
  message: string | null;
}

export interface AnalogCircuitDefinitionResponse {
  id: string;
  missionId: string;
  status: CircuitDefinitionStatus;
  falstadText: string | null;
  checks: AnalogVerificationCheckResponse[];
  createdAt: string;
  updatedAt: string;
}

// ─── Request típusok ──────────────────────────────────────────────────────────

export interface UpsertCircuitComponentRequest {
  componentType: ComponentType;
  label: string;
  posX: number;
  posY: number;
  properties: { propertyKey: string; propertyValue: string; unitOfMeasureId: string | null }[];
}

export interface UpsertCircuitConnectionRequest {
  fromLabel: string;
  fromPinName: string;
  toLabel: string;
  toPinName: string;
}

export interface SaveCadetCircuitRequest {
  components: UpsertCircuitComponentRequest[];
  connections: UpsertCircuitConnectionRequest[];
}

export interface VerifyBehaviorRequest {
  gpioPinStates: Record<string, string>;
  serialOutputLines: string[];
  pwmDutyCycles: Record<string, number>;
}
```

---

## IV. API kliens (`frontend/src/api/circuitApi.ts`)

Új, önálló modul — ugyanolyan stílusban mint a meglévő `forgeApi`, `quizApi`.

```typescript
import apiClient from './client';
import type {
  CircuitDefinitionResponse,
  CadetCircuitSaveResponse,
  CadetVerificationResultResponse,
  AnalogCircuitDefinitionResponse,
  CadetAnalogSaveResponse,
  AnalogCheckResultResponse,
  ComponentPinDefinitionResponse,
  ComponentElectricalSpecResponse,
  SaveCadetCircuitRequest,
  VerifyBehaviorRequest,
  BoardType,
  ComponentType,
} from '../types/circuit';

// ─── Admin: Circuit Definition ────────────────────────────────────────────────
export const circuitDefinitionApi = {
  create: (missionId: string, boardType: BoardType) =>
    apiClient.post<CircuitDefinitionResponse>('/circuit/definitions', { missionId, boardType }),

  getById: (id: string) =>
    apiClient.get<CircuitDefinitionResponse>(`/circuit/definitions/${id}`),

  getAllByMission: (missionId: string) =>
    apiClient.get<CircuitDefinitionResponse[]>(`/circuit/definitions/by-mission/${missionId}`),

  saveCanvas: (id: string, data: { components: unknown[]; connections: unknown[] }) =>
    apiClient.put<CircuitDefinitionResponse>(`/circuit/definitions/${id}/canvas`, data),

  addCheck: (id: string, data: unknown) =>
    apiClient.post(`/circuit/definitions/${id}/checks`, data),

  deleteCheck: (id: string, checkId: string) =>
    apiClient.delete(`/circuit/definitions/${id}/checks/${checkId}`),

  publish: (id: string) =>
    apiClient.post<CircuitDefinitionResponse>(`/circuit/definitions/${id}/publish`),

  unpublish: (id: string) =>
    apiClient.post<CircuitDefinitionResponse>(`/circuit/definitions/${id}/unpublish`),

  delete: (id: string) =>
    apiClient.delete(`/circuit/definitions/${id}`),
};

// ─── Admin: Analog Definition ─────────────────────────────────────────────────
export const analogDefinitionApi = {
  create: (missionId: string) =>
    apiClient.post<AnalogCircuitDefinitionResponse>('/circuit/analog/definitions', { missionId }),

  getById: (id: string) =>
    apiClient.get<AnalogCircuitDefinitionResponse>(`/circuit/analog/definitions/${id}`),

  updateFalstad: (id: string, falstadText: string) =>
    apiClient.put<AnalogCircuitDefinitionResponse>(
      `/circuit/analog/definitions/${id}/falstad`,
      falstadText,
      { headers: { 'Content-Type': 'text/plain' } }
    ),

  addCheck: (id: string, data: unknown) =>
    apiClient.post(`/circuit/analog/definitions/${id}/checks`, data),

  deleteCheck: (id: string, checkId: string) =>
    apiClient.delete(`/circuit/analog/definitions/${id}/checks/${checkId}`),

  publish: (id: string) =>
    apiClient.post<AnalogCircuitDefinitionResponse>(`/circuit/analog/definitions/${id}/publish`),
};

// ─── Kadét: Circuit ───────────────────────────────────────────────────────────
export const cadetCircuitApi = {
  start: (missionId: string) =>
    apiClient.post<CadetCircuitSaveResponse>(`/circuit/missions/${missionId}/start`),

  get: (missionId: string) =>
    apiClient.get<CadetCircuitSaveResponse>(`/circuit/missions/${missionId}`),

  saveCanvas: (missionId: string, data: SaveCadetCircuitRequest) =>
    apiClient.put<CadetCircuitSaveResponse>(`/circuit/missions/${missionId}/canvas`, data),

  compile: (missionId: string) =>
    apiClient.post<void>(`/circuit/missions/${missionId}/compile`),

  verify: (missionId: string) =>
    apiClient.post<CadetVerificationResultResponse[]>(`/circuit/missions/${missionId}/verify`),

  verifyBehavior: (missionId: string, data: VerifyBehaviorRequest) =>
    apiClient.post<CadetVerificationResultResponse[]>(
      `/circuit/missions/${missionId}/verify-behavior`,
      data
    ),
};

// ─── Kadét: Analog ────────────────────────────────────────────────────────────
export const cadetAnalogApi = {
  get: (missionId: string) =>
    apiClient.get<CadetAnalogSaveResponse>(`/circuit/analog/missions/${missionId}`),

  save: (missionId: string, data: { falstadText: string }) =>
    apiClient.put<CadetAnalogSaveResponse>(`/circuit/analog/missions/${missionId}`, data),

  verify: (missionId: string, data: { componentValues: Record<string, number> }) =>
    apiClient.post<AnalogCheckResultResponse[]>(
      `/circuit/analog/missions/${missionId}/verify`,
      data
    ),
};

// ─── Katalógus (mindkét oldal) ────────────────────────────────────────────────
export const circuitCatalogApi = {
  getPinDefinitions: (params?: { componentType?: ComponentType; boardType?: BoardType }) =>
    apiClient.get<ComponentPinDefinitionResponse[]>('/circuit/catalog/pins', { params }),

  getElectricalSpecs: (params?: { componentType?: ComponentType }) =>
    apiClient.get<ComponentElectricalSpecResponse[]>('/circuit/catalog/specs', { params }),
};
```

---

## V. Routing (`frontend/src/router/index.tsx`)

### Hozzáadandó útvonalak

```typescript
// Admin routes (AdminLayout alatt)
{ path: '/admin/circuit/:missionId',       element: <CircuitForgeAdminPage /> }
{ path: '/admin/analog/:missionId',        element: <AnalogForgeAdminPage />  }

// Kadét routes (MainLayout alatt, auth védett)
{ path: '/missions/:missionId/circuit',    element: <CircuitPlayerPage /> }
{ path: '/missions/:missionId/analog',     element: <AnalogPlayerPage />  }
```

### Megjegyzés a MissionForgePage integrációhoz

A `MissionForgePage.tsx`-ben a `missionType` szerinti elágazásba be kell kötni:
```typescript
if (missionType === 'CIRCUIT_SIMULATION') {
  return <CircuitForgeEditor missionId={missionId} />;
}
```

---

## VI. Admin oldal

> **Design:** MUI clean/modern — pontosan ugyanolyan mint a meglévő admin oldalak (Paper, TextField, Button, DataGrid).
> A circuit forge két aloldalból áll: az egyik a digital (Arduino) canvas, a másik az analog (Falstad) canvas.

---

### VI.1 Adminnavigáció kiegészítése

**`AdminLayout.tsx`** — Sidebar-ba egy új szekció:

```
Circuit  (ElectricalServicesIcon)
 ├─ /admin/circuit-catalog      → ComponentCatalogPage (pin def + electrical spec kezelés)
```

A missziókhoz tartozó circuit szerkesztőt **a MissionEdit oldalról** kell megnyitni (link/gomb formájában), nem a sidebar-ból.

---

### VI.2 MissionEdit kiegészítés

**`frontend/src/pages/admin/missions/MissionEdit.tsx`**

Ha a misszió `missionType === 'CIRCUIT_SIMULATION'`, az oldal aljára kerül egy új szekció:

```
┌─────────────────────────────────────────────────────────────┐
│  CIRCUIT CONFIGURATION                                       │
│  ─────────────────────────────────────────────────────────  │
│  [ Digital (Arduino) ]  [ Analog (Falstad) ]   ← tab-ok    │
│                                                              │
│  Aktív definíció: PUBLISHED  [Szerkesztés megnyitása ↗]    │
│  vagy: [Új circuit definíció létrehozása]                   │
└─────────────────────────────────────────────────────────────┘
```

A gomb `/admin/circuit/:missionId` vagy `/admin/analog/:missionId` oldalra navigál.

---

### VI.3 CircuitForgeAdminPage

**Fájl:** `frontend/src/pages/admin/circuit/CircuitForgeAdminPage.tsx`

**Layout:** Teljes képernyős, háromoszlopos split

```
┌──────────────┬────────────────────────────────────┬─────────────────┐
│  KOMPONENS   │                                     │   TULAJDON-     │
│  PALETTA     │         CANVAS (@xyflow/react)      │   SÁGOK         │
│  (240px)     │                                     │   (280px)       │
│              │   [drag-and-drop komponensek]        │                 │
│ • Arduino    │   [él = kábel, handle = pin]         │ Kijelölt komp:  │
│ • LED        │                                     │  Label: ______  │
│ • Resistor   │                                     │  Type: [select] │
│ • Button     │                                     │  Resistance: __ │
│ • GND/VCC    │                                     │  Unit: [select] │
│ • ...        │                                     │                 │
│              │                                     │ VERIFICATION    │
│              │                                     │ CHECKS          │
│              │                                     │ [+ Hozzáadás]   │
│              │                                     │ • CIRCUIT_TOPO  │
│              │                                     │ • PATH_EXISTS   │
│              │                                     │ • GPIO_BEHAVIOR │
└──────────────┴────────────────────────────────────┴─────────────────┘
│ [← Vissza]  [Mentés]  [Publish]  [Unpublish]  Státusz: IN_WORK      │
└─────────────────────────────────────────────────────────────────────┘
```

**Alkomponensek:**

```
CircuitForgeAdminPage/
├── CircuitComponentPalette.tsx    # Bal oldali paletta, drag-and-drop source
├── CircuitCanvas.tsx              # @xyflow/react ReactFlow wrapper
│   ├── CircuitNode.tsx            # Egyéni node: komponens ikon + label + pinok
│   └── CircuitEdge.tsx            # Egyéni él: kábel megjelenítés (animated SVG)
├── CircuitPropertiesPanel.tsx     # Jobb panel: kijelölt node tulajdonságok
│   └── PropertyRow.tsx            # Egy property: key + value + unit select
├── CircuitChecksPanel.tsx         # Jobb panel alrész: verifikációs checkek listája
│   └── CheckRow.tsx               # Egy check: típus + labelek + expected value
└── CircuitForgeToolbar.tsx        # Alsó toolbar: mentés/publish gombok + státusz chip
```

**State kezelés:**
- `useQuery(['circuit-def', missionId])` → `circuitDefinitionApi.getAllByMission(missionId)`
- `useMutation(circuitDefinitionApi.saveCanvas)` — canvas save (debounced, 2s)
- `useMutation(circuitDefinitionApi.publish)` / `unpublish`
- `useReactFlow()` hook a canvas state-hez (nodes, edges)
- Nodes ↔ `CircuitDefComponentResponse[]` mapping (konverzió mentésnél)
- Edges ↔ `CircuitDefConnectionResponse[]` mapping

**Canvas ↔ API konverzió:**
```typescript
// ReactFlow Node → UpsertCircuitDefComponentRequest
const nodeToRequest = (node: Node): UpsertCircuitComponentRequest => ({
  componentType: node.data.componentType,
  label: node.id,          // a node id = komponens label (R1, LED1, stb.)
  posX: node.position.x,
  posY: node.position.y,
  properties: node.data.properties,
});

// ReactFlow Edge → UpsertCircuitDefConnectionRequest
const edgeToRequest = (edge: Edge): UpsertCircuitConnectionRequest => ({
  fromLabel: edge.source,
  fromPinName: edge.sourceHandle!,
  toLabel: edge.target,
  toPinName: edge.targetHandle!,
});
```

---

### VI.4 AnalogForgeAdminPage

**Fájl:** `frontend/src/pages/admin/circuit/AnalogForgeAdminPage.tsx`

**Layout:** Két panel

```
┌───────────────────────────────────┬─────────────────────────────┐
│    FALSTAD IFRAME                 │  VERIFIKÁCIÓS CHECKEK       │
│    (CircuitJS1 embed)             │  ──────────────────────     │
│                                   │  [+ Új check]               │
│    circuitjs1 hosted instance     │                             │
│    iframe + postMessage           │  • VOLTAGE_AT_NODE          │
│    ↕ szinkronizál                 │    node: R1_OUT             │
│                                   │    expected: 2.2V           │
│                                   │    tolerance: 5%            │
│                                   │                             │
│    [Állapot betöltése]            │  • LED_LIGHTS               │
│    [Állapot mentése]              │    label: LED1              │
└───────────────────────────────────┴─────────────────────────────┘
│ [← Vissza]  [Mentés]  [Publish]                                  │
└─────────────────────────────────────────────────────────────────┘
```

**Alkomponensek:**

```
AnalogForgeAdminPage/
├── FalstadEmbed.tsx          # iframe wrapper, postMessage kommunikáció
├── FalstadStateSync.tsx      # Szinkronizáció state ↔ DB (save/load gomb)
└── AnalogChecksPanel.tsx     # Check lista + hozzáadás form
    └── AnalogCheckRow.tsx    # Egy check megjelenítés + törlés
```

---

### VI.5 ComponentCatalogPage

**Fájl:** `frontend/src/pages/admin/circuit/ComponentCatalogPage.tsx`

Admin CRUD a pin definíciókhoz és electrical spec-ekhez. MUI DataGrid, ugyanolyan stílusban mint a többi admin lista oldal.

```
┌─────────────────────────────────────────────────────────────────┐
│  PIN DEFINITIONS                           [+ Új pin definíció] │
│  ─────────────────────────────────────────────────────────────  │
│  DataGrid: ComponentType | BoardType | PinName | PinType | ...  │
│                                                                  │
│  ELECTRICAL SPECS                          [+ Új spec]          │
│  ─────────────────────────────────────────────────────────────  │
│  DataGrid: ComponentType | PropertyKey | DefaultValue | Unit    │
└─────────────────────────────────────────────────────────────────┘
```

---

## VII. Kadét oldal

> **Design:** Retro space terminál — `control-panel-casing`, screws, `crt-monitor`, `VT323` font, `#00ff88` akcentszín.
> Pontosan ugyanolyan hangulatú mint a StarMapPage és ForgeEditor.

---

### VII.1 CircuitPlayerPage

**Fájl:** `frontend/src/pages/circuit/CircuitPlayerPage.tsx`

Ez az Arduino (digital) misszió teljesítő oldala. Három fő részre oszlik.

**Teljes layout:**

```
┌─────────────────────────────────────────────────────────────────────────┐
│  [← BACK]  CIRCUIT_MISSION // LED_BLINK_001 // ARDUINO_UNO   [CADET]   │  ← header sáv, #00ff88 terminál szöveg
├──────────────────────────────────┬──────────────────────────────────────┤
│                                  │                                        │
│    SIMULATION CANVAS             │   sketch.ino                          │
│    (@wokwi/elements              │   ─────────────────────────────────   │
│     + avr8js Web Worker)         │   Monaco Editor (cpp, dark theme)     │
│                                  │                                        │
│    [wokwi-arduino-uno]           │   void setup() {                      │
│    [wokwi-led]                   │     pinMode(13, OUTPUT);              │
│    [wokwi-resistor]              │   }                                   │
│    (komponensek pozíciói         │   void loop() {                       │
│     a CircuitDefinition          │     digitalWrite(13, HIGH);           │
│     alapján, nem drag-and-drop)  │     delay(1000);                      │
│                                  │   }                                   │
│    ● RUNNING                     │                                        │
│    [▶ RUN]  [■ STOP]             │   [COMPILE]   [SAVE CODE]             │
├──────────────────────────────────┴──────────────────────────────────────┤
│  SERIAL MONITOR                                                           │  ← alsó panel (összecsukható)
│  > Hello World                                                            │
│  > Temperature: 23.5 C                                                    │
│  ▌                                                                        │
├─────────────────────────────────────────────────────────────────────────┤
│  VERIFICATION                                                             │
│  ✓ D13 komponens jelen van    ✓ GND-D13 összeköttetés létezik             │
│  ✗ GPIO_13 = HIGH (elvárás: HIGH, nem futott még)  [VERIFY]  [SUBMIT]    │
└─────────────────────────────────────────────────────────────────────────┘
```

**Alkomponensek:**

```
CircuitPlayerPage/
├── CircuitSimulationCanvas.tsx    # wokwi-elements megjelenítés + avr8js integráció
│   ├── useAvrSimulator.ts         # Web Worker hook (avr8js futtatás)
│   └── WokwiComponentMap.tsx      # ComponentType → wokwi web component mapping
├── SketchEditor.tsx               # Monaco Editor arduino cpp + compile gomb
│   └── useCompilePoller.ts        # React Query poll: compile státusz figyelés
├── SerialMonitor.tsx              # Szimuláció serial output (avr8js USART stream)
│   └── SerialMonitorLine.tsx      # Egy sor: timestamp + szöveg + szín (ERROR piros)
└── VerificationPanel.tsx          # Check eredmények + Verify + Submit gombok
    └── VerificationCheckRow.tsx   # Egy check: ikon (✓/✗/⏳) + leírás
```

---

### VII.2 useAvrSimulator hook

**Fájl:** `frontend/src/hooks/useAvrSimulator.ts`

Az avr8js Web Worker-ben fut (Comlink segítségével). A hook a React komponensektől elszigeteli az alacsony szintű emulátor logikát.

```typescript
interface AvrSimulatorState {
  status: 'idle' | 'running' | 'stopped';
  pinStates: Record<string, 'HIGH' | 'LOW'>;   // pl. { "13": "HIGH" }
  pwmDutyCycles: Record<string, number>;        // pl. { "9": 50 }
  serialLines: string[];
  elapsedMs: number;
}

interface UseAvrSimulatorReturn {
  state: AvrSimulatorState;
  start: (hexBase64: string, boardType: BoardType) => void;
  stop: () => void;
  reset: () => void;
}

// Belső működés:
// 1. Worker létrehozás: new Worker('/avr-worker.js') + Comlink.wrap()
// 2. start(): hexBase64 → Uint16Array → CPU(new Uint16Array(hex.buffer))
// 3. portB/portD listener: pinStates state update → React re-render
// 4. USART listener: serialLines push
// 5. setInterval 16ms: cpu.execute() ciklusok → animációs frame ütemezés
// 6. stop(): worker terminate, state reset
```

**Worker fájl:** `frontend/public/avr-worker.js` — Comlink expose, avr8js CPU loop.

---

### VII.3 useCompilePoller hook

**Fájl:** `frontend/src/hooks/useCompilePoller.ts`

A compile gomb megnyomása után a szimuláció state-et 2 másodpercenként pollozza, amíg `COMPILING` a státusz.

```typescript
const useCompilePoller = (missionId: string) => {
  const queryClient = useQueryClient();
  const [isPolling, setIsPolling] = useState(false);

  // React Query refetchInterval alapú polling
  const { data: saveData } = useQuery({
    queryKey: ['circuit-save', missionId],
    queryFn: () => cadetCircuitApi.get(missionId),
    refetchInterval: isPolling ? 2000 : false,
  });

  // Compile indítás
  const startCompile = async () => {
    await cadetCircuitApi.compile(missionId);
    setIsPolling(true);
  };

  // Poll megállítás ha kész
  useEffect(() => {
    if (saveData?.simulationStatus !== 'COMPILING') {
      setIsPolling(false);
    }
  }, [saveData?.simulationStatus]);

  return { saveData, isPolling, startCompile };
};
```

---

### VII.4 AnalogPlayerPage

**Fájl:** `frontend/src/pages/circuit/AnalogPlayerPage.tsx`

Az analóg áramköri misszió teljesítő oldala. Design: ugyanolyan retro, mint a CircuitPlayerPage.

**Layout:**

```
┌─────────────────────────────────────────────────────────────────────────┐
│  [← BACK]  ANALOG_CIRCUIT // RC_FILTER_101 // FALSTAD              │  ← header
├──────────────────────────────────┬──────────────────────────────────────┤
│                                   │                                       │
│   FALSTAD SZIMULÁTOR              │  FELADAT LEÍRÁS                      │
│   (iframe embed)                  │  ────────────────────────────        │
│                                   │  Kösd össze az RC szűrőt...          │
│   circuitjs1 hosted               │                                       │
│   postMessage szinkronizáció      │  VERIFIKÁCIÓ                         │
│   ↕ Falstad szöveg mentése        │  ────────────────────────────        │
│                                   │  ✗ Feszültség R1-en: 2.2V            │
│   [Mentés]                        │  ✗ LED1 világít                       │
│                                   │  [ELLENŐRZÉS FUTTATÁSA]              │
└──────────────────────────────────┴──────────────────────────────────────┘
```

**Alkomponensek:**

```
AnalogPlayerPage/
├── FalstadPlayer.tsx          # iframe + postMessage kommunikáció
│   └── useFalstadSync.ts      # State ↔ DB szinkronizáció hook (autosave 3s debounce)
└── AnalogVerificationPanel.tsx # Check eredmények + ellenőrzés gomb
```

---

## VIII. Közös komponensek

```
frontend/src/components/circuit/
├── SimulationStatusBadge.tsx     # SimulationStatus → MUI Chip (COMPILING=info, ERROR=error, stb.)
├── BoardTypeBadge.tsx            # BoardType → kis chip (ARDUINO_UNO → "UNO", stb.)
├── VerificationResultList.tsx    # CadetVerificationResultResponse[] lista megjelenítés
│   └── VerificationResultRow.tsx # Egy sor: severity ikon + i18nKey szöveg + passed/failed
└── CompileProgressBar.tsx        # Fordítás közben animált progress bar + "COMPILING..." szöveg
```

---

## IX. Web Worker architektúra

Az avr8js CPU-intenzív — a fő UI szálban futtatva lefagyasztja a böngészőt. Web Worker-ben fut Comlink segítségével.

```
frontend/
├── public/
│   └── avr-worker.js          # Comlink expose, avr8js CPU loop (Vite worker bundle)
└── src/
    └── workers/
        └── avr.worker.ts      # TypeScript forrás → Vite build: ?worker import
```

**Vite konfiguráció (`vite.config.ts`):**
```typescript
// Vite 5+ natívan kezeli a ?worker importot, nincs extra plugin szükséges
// avr-worker.ts importálása:
// import AvrWorker from './workers/avr.worker?worker';
```

---

## X. Implementációs sorrend

### Fázis 1 — Admin oldal (circuit forge)

1. `frontend/src/types/circuit.ts` — TypeScript típusok
2. `frontend/src/api/circuitApi.ts` — API kliens modulok
3. Router bővítés
4. `CircuitForgeAdminPage` vázas layout (Canvas nélkül, csak statikus)
5. `CircuitCanvas.tsx` — @xyflow/react integráció, custom node-ok
6. `CircuitComponentPalette.tsx` — drag source, komponens lista
7. `CircuitPropertiesPanel.tsx` — kijelölt node szerkesztés
8. `CircuitChecksPanel.tsx` — check CRUD
9. `CircuitForgeToolbar.tsx` — mentés + publish flow
10. `AnalogForgeAdminPage` — Falstad iframe + check panel
11. `ComponentCatalogPage` — pin def + electrical spec CRUD
12. `MissionEdit.tsx` kiegészítés — circuit szekció + link

### Fázis 2 — Kadét oldal

13. `avr-worker.ts` Web Worker + Comlink setup
14. `useAvrSimulator.ts` hook
15. `CircuitSimulationCanvas.tsx` — wokwi-elements megjelenítés
16. `SketchEditor.tsx` — Monaco Editor cpp + compile trigger
17. `useCompilePoller.ts` — React Query polling
18. `SerialMonitor.tsx` — avr8js USART stream
19. `VerificationPanel.tsx` — topology + behavior verify flow
20. `CircuitPlayerPage.tsx` — teljes összerakás
21. `AnalogPlayerPage.tsx` — Falstad player + verify

### Fázis 3 — Csiszolás

22. Animációk (framer-motion): szimuláció start/stop state átmenetek
23. i18n kulcsok: verification check `i18nKey`-k magyar fordításai
24. Cypress E2E tesztek: admin canvas + kadet compile + verify flow
25. Vitest unit tesztek: `useAvrSimulator`, `useCompilePoller`, konverziós függvények

---

## XI. Fontos döntések és korlátok

### @wokwi/elements + React 19
A wokwi Web Components React 19-ben közvetlenül is használhatók (React 19 natív custom element support), de az event kezelés figyelmet igényel. Az `@lit-labs/react` csomag típusos wrappert generál, de opcionális.

### Falstad hosting
A CircuitJS1 szimulátor saját instance-t igényel (nem lehet a harmadik fél által hosztolt verziót közvetlenül beágyazni postMessage-hez). Opciók:
- **A opció:** Self-hosted Falstad a Docker Compose-ban (külön konténer)
- **B opció:** `circuitjs1` GitHub repo klónozás + statikus build → Nginx-en serve-elni a frontend mellé

**Ajánlott: A opció** — konténer alapú, izolált, docker-compose.yml-be illeszkedik.

### Canvas mentés stratégia
Az admin canvas változásakor **nem** mentünk azonnal minden egyes node mozgatásnál — csak:
1. "Mentés" gombra kattintáskor
2. Navigáció előtt ("Nem mentett változások vannak" dialógus)

A kadet canvas automatikusan ment 3 másodperces debounce-al (mint a ForgeEditor fájlmentése).

### avr8js végrehajtási sebesség
Az avr8js alapból valós idejű AVR sebességre lett tervezve (16 MHz). A Web Worker `setInterval(16ms)` ütemezéssel 1000 CPU ciklust hajt végre iterációnként — ez elegendő LED blink és egyszerű szenzor szimulációkhoz. Bonyolultabb kódnál (pl. 128x64 OLED rajzolás) a sebességet növelni kell (iteráció/frame növelés).

### Compile 409 kezelés
Ha a kadet a Compile gombot megnyomja és 409-et kap (már fordít), a frontend a `CompileProgressBar`-t mutatja és polloz — nem hibát jelez.
