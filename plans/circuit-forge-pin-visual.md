# Circuit Forge — Vizuális Pin Rendszer Terv

> Állapot: Tervezési fázis | Ág: `circuit_forge`
> Előzmény: `circuit-forge-ux-next.md` — alapproblémák azonosítva
> Cél: Valós Arduino board vizuális megjelenítése pin-szintű kapcsolódással

---

## Koncepció

Az admin a canvas-on egy **vizuálisan az igazi Arduino boardhoz hasonlító** node-ot lát.
A board szélein a valódi pinek vannak elhelyezve, névvel és típussal.
Hover esetén tooltip mutatja a pin nevét és funkcióját (pl. `D3 — PWM_OUT`).
Az admin az egyik pin-re kattintva húzza a kábelt a másik komponens pin-jéhez.
A kapcsolat pin nevei **automatikusan rögzítésre kerülnek** — nincs ConnectionDialog.

---

## Backend — DataInitializer pin seed

### Mit seed-el

A `ComponentPinDefinition` entitás mezői:
- `componentType` — pl. `BOARD`, `LED`
- `boardType` — csak a BOARD típusnál töltött (UNO/Mega), komponenseknél null
- `pinName` — pl. `"D3"`, `"Anode"`
- `pinIndex` — sorrend a UI-on belül
- `pinType` — `PinType` enum érték
- `allowMultipleConnections` — GND és VCC esetén true (több kábelt kaphat)
- `posXOffset` — a handle pozíciója a node-on belül, vízszintes offset (px)
- `posYOffset` — a handle pozíciója a node-on belül, függőleges offset (px)

### Idempotencia minta (mint a jogosultságoknál)

```java
private void createPinIfNotFound(ComponentType type, BoardType board,
                                  String name, int index, PinType pinType,
                                  boolean multiConn, int xOff, int yOff) {
    boolean exists = pinRepository.existsByComponentTypeAndBoardTypeAndPinName(type, board, name);
    if (!exists) {
        pinRepository.save(ComponentPinDefinition.builder()
            .componentType(type).boardType(board)
            .pinName(name).pinIndex(index).pinType(pinType)
            .allowMultipleConnections(multiConn)
            .posXOffset(xOff).posYOffset(yOff)
            .build());
    }
}
```

### Arduino UNO pinjei (BOARD, ARDUINO_UNO)

Board node mérete: ~220px × 360px

**Power pinek — bal oldal fölül lefelé (xOffset=0):**
| pinIndex | pinName | pinType             | yOffset | multiConn |
|----------|---------|---------------------|---------|-----------|
| 0        | IOREF   | DIGITAL_IO          | 20      | false     |
| 1        | RESET   | DIGITAL_IO          | 44      | false     |
| 2        | 3V3     | POWER_VCC           | 68      | true      |
| 3        | 5V      | POWER_VCC           | 92      | true      |
| 4        | GND_P1  | POWER_GND           | 116     | true      |
| 5        | GND_P2  | POWER_GND           | 140     | true      |
| 6        | VIN     | POWER_VCC           | 164     | true      |

**Analóg pinek — bal oldal lejjebb (xOffset=0):**
| pinIndex | pinName | pinType   | yOffset | multiConn |
|----------|---------|-----------|---------|-----------|
| 7        | A0      | ANALOG_IN | 208     | false     |
| 8        | A1      | ANALOG_IN | 232     | false     |
| 9        | A2      | ANALOG_IN | 256     | false     |
| 10       | A3      | ANALOG_IN | 280     | false     |
| 11       | A4      | I2C_SDA   | 304     | false     |
| 12       | A5      | I2C_SCL   | 328     | false     |

**Digitális pinek — jobb oldal fölül lefelé (xOffset=220):**
| pinIndex | pinName | pinType      | yOffset | multiConn |
|----------|---------|--------------|---------|-----------|
| 13       | D0_RX   | UART_RX      | 20      | false     |
| 14       | D1_TX   | UART_TX      | 44      | false     |
| 15       | D2      | DIGITAL_IO   | 68      | false     |
| 16       | D3      | PWM_OUT      | 92      | false     |
| 17       | D4      | DIGITAL_IO   | 116     | false     |
| 18       | D5      | PWM_OUT      | 140     | false     |
| 19       | D6      | PWM_OUT      | 164     | false     |
| 20       | D7      | DIGITAL_IO   | 188     | false     |
| 21       | D8      | DIGITAL_IO   | 212     | false     |
| 22       | D9      | PWM_OUT      | 236     | false     |
| 23       | D10     | SPI_CS       | 260     | false     |
| 24       | D11     | SPI_MOSI     | 284     | false     |
| 25       | D12     | SPI_MISO     | 308     | false     |
| 26       | D13     | SPI_SCK      | 332     | false     |

**Top (fejléc) pinek — felső él (yOffset=0):**
| pinIndex | pinName | pinType    | xOffset | multiConn |
|----------|---------|------------|---------|-----------|
| 27       | AREF    | ANALOG_IN  | 60      | false     |
| 28       | GND_D   | POWER_GND  | 84      | true      |

---

### Arduino MEGA 2560 pinjei (BOARD, ARDUINO_MEGA_2560)

Board node mérete: ~280px × 720px

A Mega layout hasonló elvű, de jóval több pin:
- Bal oldal: Power + A0–A15 (32 pin)
- Jobb oldal: D0–D53 (54 pin, D2-D13 + D44-D46 PWM)
- Fentebb: AREF, GND

*A pontos yOffset értékek: 24px × pinIndex alapján számolható, nincs kézzel felsorolva.*

DataInitializerben ez egy for-ciklus lesz a digital pineknél, hardkódolt lista a PWM-képes pineknél.

---

### Komponens pinek (boardType=null — board-független)

| componentType | pinIndex | pinName  | pinType          | xOffset | yOffset | multiConn |
|---------------|----------|----------|------------------|---------|---------|-----------|
| LED           | 0        | Anode    | COMPONENT_ANODE  | 0       | 20      | false     |
| LED           | 1        | Cathode  | COMPONENT_CATHODE| 60      | 20      | false     |
| RESISTOR      | 0        | Pin1     | PASSIVE_A        | 0       | 20      | false     |
| RESISTOR      | 1        | Pin2     | PASSIVE_B        | 60      | 20      | false     |
| CAPACITOR     | 0        | Positive | COMPONENT_ANODE  | 0       | 20      | false     |
| CAPACITOR     | 1        | Negative | COMPONENT_CATHODE| 60      | 20      | false     |
| PUSHBUTTON    | 0        | Pin1A    | PASSIVE_A        | 0       | 12      | false     |
| PUSHBUTTON    | 1        | Pin1B    | PASSIVE_A        | 0       | 36      | false     |
| PUSHBUTTON    | 2        | Pin2A    | PASSIVE_B        | 60      | 12      | false     |
| PUSHBUTTON    | 3        | Pin2B    | PASSIVE_B        | 60      | 36      | false     |
| POTENTIOMETER | 0        | VCC      | POWER_VCC        | 0       | 12      | false     |
| POTENTIOMETER | 1        | Wiper    | SIGNAL_OUT       | 30      | 40      | false     |
| POTENTIOMETER | 2        | GND      | POWER_GND        | 60      | 12      | false     |
| DHT11         | 0        | VCC      | POWER_VCC        | 0       | 12      | false     |
| DHT11         | 1        | DATA     | ONE_WIRE         | 30      | 40      | false     |
| DHT11         | 2        | GND      | POWER_GND        | 60      | 12      | false     |
| HC_SR04       | 0        | VCC      | POWER_VCC        | 0       | 20      | false     |
| HC_SR04       | 1        | TRIG     | SIGNAL_IN        | 20      | 40      | false     |
| HC_SR04       | 2        | ECHO     | SIGNAL_OUT       | 45      | 40      | false     |
| HC_SR04       | 3        | GND      | POWER_GND        | 65      | 20      | false     |
| SERVO         | 0        | VCC      | POWER_VCC        | 0       | 12      | false     |
| SERVO         | 1        | GND      | POWER_GND        | 0       | 36      | false     |
| SERVO         | 2        | Signal   | SIGNAL_IN        | 60      | 24      | false     |
| VCC_5V        | 0        | PWR      | POWER_VCC        | 30      | 40      | true      |
| VCC_3V3       | 0        | PWR      | POWER_VCC        | 30      | 40      | true      |
| GND           | 0        | GND      | POWER_GND        | 30      | 40      | true      |

---

## Frontend — Architektúra változások

### Új típus: `PinDefinition` (frontend oldal)

```typescript
// src/types/circuit.ts kiegészítése:
export interface PinDefinition {
  pinName: string;
  pinIndex: number;
  pinType: string;
  allowMultipleConnections: boolean;
  posXOffset: number;
  posYOffset: number;
}
```

A `ComponentPinDefinitionResponse` mostantól tartalmazza a pozíciót is (backendről jön).

### Pin betöltés (`CircuitForgeAdminPage.tsx`)

Az oldal betöltésekor a `boardType` ismert → párhuzamosan betöltjük:
```typescript
const [pinCatalog, setPinCatalog] = useState<Map<string, PinDefinition[]>>(new Map());

// load:
const allPins = await getAllPinDefinitions();
const catalog = new Map<string, PinDefinition[]>();
for (const pin of allPins) {
  const key = pin.boardType ? `${pin.componentType}:${pin.boardType}` : pin.componentType;
  if (!catalog.has(key)) catalog.set(key, []);
  catalog.get(key)!.push(pin);
}
setPinCatalog(catalog);
```

Helper:
```typescript
function getPins(catalog: Map<string, PinDefinition[]>, type: ComponentType, board?: BoardType) {
  return catalog.get(board ? `${type}:${board}` : type) ?? [];
}
```

### `BoardNode.tsx` — ÚJ KOMPONENS

Ez a legfontosabb változás. A BOARD componentType-hoz külön, nagyobb node.

```
┌─────────────────────────────┐
│ ○ IOREF  [Arduino UNO]  D0 ○│
│ ○ RESET               D1 ○  │
│ ○ 3V3         [kép]   D2 ○  │
│ ○ 5V                  D3 ○  │
│ ○ GND                 D4 ○  │
│ ○ GND                 D5 ○  │
│ ○ VIN                 ...   │
│         AREF GND            │
│ ○ A0                  D13 ○ │
│ ○ A1                        │
│ ○ A2                        │
│ ○ A3                        │
│ ○ A4/SDA                    │
│ ○ A5/SCL                    │
└─────────────────────────────┘
```

Technikai megvalósítás:
- A `Handle` komponensek `style={{ position: "absolute", left: pin.posXOffset, top: pin.posYOffset }}` elhelyezéssel
- Hover: MUI `Tooltip` az egész Handle-t beburkol, title = `"${pin.pinName} — ${pin.pinType}"`
- `id={pin.pinName}` az xyflow-nak
- `type="source"` (bármely irányban húzható, az xyflow kezeli)
- Szín PinType szerint:
  - `PWM_OUT`: narancssárga `#ff9800`
  - `ANALOG_IN`: lila `#9c27b0`
  - `POWER_VCC`: piros `#f44336`
  - `POWER_GND`: fekete `#37474f`
  - `DIGITAL_IO`: zöld `#4caf50`
  - `UART_TX/RX`: kék `#2196f3`
  - `SPI_*`: cyan `#00bcd4`
  - `I2C_*`: sárga `#ffeb3b`

```tsx
// BoardNode.tsx vázlat
export const BoardNode: React.FC<NodeProps<Node<CircuitNodeData>>> = ({ data, selected }) => {
  const { pinCatalog, boardType } = useCircuitContext(); // context vagy prop
  const pins = getPins(pinCatalog, "BOARD", boardType);

  return (
    <Box sx={{ position: "relative", width: 220, height: 360, ... }}>
      {/* Board vizuális alap */}
      <Box sx={{ ... /* board háttér stílus */ }}>
        <Typography>Arduino {boardType === "ARDUINO_UNO" ? "UNO" : "MEGA"}</Typography>
        {/* csatlakozók, chip rajz SVG-vel */}
      </Box>

      {/* Pin handle-ök */}
      {pins.map(pin => (
        <Tooltip key={pin.pinName} title={`${pin.pinName} — ${pin.pinType}`} placement="left">
          <Handle
            type="source"
            position={Position.Left}  // a xyflow position csak fallback, a style overrideolja
            id={pin.pinName}
            style={{
              position: "absolute",
              left: pin.posXOffset,
              top: pin.posYOffset,
              width: 10, height: 10,
              background: PIN_TYPE_COLORS[pin.pinType],
              border: "2px solid #fff",
              cursor: "crosshair",
            }}
          />
        </Tooltip>
      ))}
    </Box>
  );
};
```

### `CircuitNode.tsx` — módosítás

A jelenlegi 1 top + 1 bottom handle helyett a komponens pinjei kerülnek be:

```tsx
export const CircuitNode: React.FC<NodeProps<Node<CircuitNodeData>>> = ({ data, selected }) => {
  const { pinCatalog } = useCircuitContext();
  const pins = getPins(pinCatalog, data.componentType);

  return (
    <Box sx={{ position: "relative", minWidth: 80, ... }}>
      {/* Vizuális tartalom: ikon + label */}
      ...

      {/* Pin handle-ök pozícionálva */}
      {pins.map(pin => (
        <Tooltip key={pin.pinName} title={pin.pinName} placement="top">
          <Handle
            type="source"
            position={Position.Left}
            id={pin.pinName}
            style={{
              position: "absolute",
              left: pin.posXOffset,
              top: pin.posYOffset,
              width: 8, height: 8,
              background: PIN_TYPE_COLORS[pin.pinType] ?? "#888",
            }}
          />
        </Tooltip>
      ))}
    </Box>
  );
};
```

### `onConnect` — automatikus pin capture, dialógus nélkül

```typescript
// CircuitForgeAdminPage.tsx-ben
const handleConnect = useCallback((connection: Connection) => {
  const fromPin = connection.sourceHandle ?? "";
  const toPin = connection.targetHandle ?? "";

  // Duplikált él ellenőrzés
  const duplicate = edges.some(
    e => e.source === connection.source && e.sourceHandle === connection.sourceHandle
      && !allowMultiple(fromPin)
  );
  if (duplicate) {
    showSnackbar(t("circuit.pinAlreadyConnected"), "warning");
    return;
  }

  setEdges(eds => addEdge({
    ...connection,
    id: crypto.randomUUID(),
    data: { fromPinName: fromPin, toPinName: toPin },
    label: `${fromPin} → ${toPin}`,
  }, eds));
  setUnsavedChanges(true);
}, [edges, setEdges]);
```

**`ConnectionDialog` törlésre kerül** — nem kell ha a Handle ID = pin neve.

### `CircuitContext` — új React context

A `pinCatalog` és a `boardType` mélyen le kell passzolni (`CircuitForgeAdminPage` → `CircuitCanvas` → `CircuitNode`). Prop drilling helyett egy kis context:

```typescript
// src/pages/admin/circuit/CircuitContext.ts
interface CircuitContextValue {
  pinCatalog: Map<string, PinDefinition[]>;
  boardType: BoardType;
}
export const CircuitContext = React.createContext<CircuitContextValue>(...);
```

`CircuitForgeAdminPage` providelja, `BoardNode` és `CircuitNode` consumeálja.

---

## Módosítandó fájlok összefoglalója

### Backend

| Fájl | Változás |
|---|---|
| `config/DataInitializer.java` | `ComponentPinDefinitionRepository` inject + UNO/Mega/komponens pin seed |
| `repository/circuit/ComponentPinDefinitionRepository.java` | `existsByComponentTypeAndBoardTypeAndPinName()` metódus |
| `dto/circuit/ComponentPinDefinitionResponse.java` | `posXOffset`, `posYOffset` mezők hozzáadása (ha hiányoznak) |
| `service/circuit/ComponentCatalogService.java` | `getAllPinDefinitions()` megfelelően visszaadja az összes pint |

### Frontend

| Fájl | Változás |
|---|---|
| `types/circuit.ts` | `PinDefinition` típus kiegészítés |
| `pages/admin/circuit/CircuitContext.ts` | ÚJ: context a pinCatalog + boardType-hoz |
| `pages/admin/circuit/BoardNode.tsx` | ÚJ: vizuális board node pozicionált handle-ökkel |
| `pages/admin/circuit/CircuitNode.tsx` | Handle-ök cseréje pin-specifikusakra |
| `pages/admin/circuit/CircuitForgeAdminPage.tsx` | pinCatalog betöltés, handleConnect módosítás, ConnectionDialog eltávolítás |
| `pages/admin/circuit/CircuitCanvas.tsx` | `boardNode: BoardNode` nodeTypes-ba |
| `pages/admin/circuit/ConnectionDialog.tsx` | TÖRLÉS |
| `api/circuitApi.ts` | `getAllPinDefinitions()` – szükséges ha nincs |

---

## Implementációs sorrend

1. **Backend `ComponentPinDefinitionRepository`** — `existsByComponentTypeAndBoardTypeAndPinName` + `findAll` visszaadja a pozíciókat
2. **Backend `DataInitializer`** — UNO power + analog + digital pinek seed-elése, majd komponens pinek
3. **Backend `ComponentPinDefinitionResponse` DTO** — `posXOffset`, `posYOffset` mezők ellenőrzése, hozzáadása ha hiányzik
4. **Frontend `CircuitContext.ts`** — minimális context létrehozása
5. **Frontend `BoardNode.tsx`** — vizuális board, pozicionált handle-ök
6. **Frontend `CircuitNode.tsx`** — pin-specifikus handle-ök
7. **Frontend `CircuitForgeAdminPage.tsx`** — pinCatalog betöltés, `handleConnect` módosítás, ConnectionDialog eltávolítás
8. **Frontend `CircuitCanvas.tsx`** — boardNode regisztrálása nodeTypes-ba

*Nincs unit teszt módosítás az 1-8 között — a tesztek a funkcionális implementáció után frissülnek.*

---

## Ami NEM szerepel ebben az iterációban

- ESP8266 / ESP32 pin definíciók (MVP: UNO + Mega elég)
- Mega 2560 részletes pozíciótáblázat (DataInitializerben for-ciklus, xOffset=280 fix, yOffset=24*i)
- Board rajzolása fényképszerűen / SVG chip-ekkel (CSS-alapú elrendezés elegendő)
- Verification Check UX javítás (külön iteráció)
- Cadet oldali szimuláció (avr8js integráció — külön fázis)

---

## Döntések (lezárva)

1. **BoardNode magassága Mega esetén:** A canvas legyen nagyobb, a felhasználó görgethet. Nincs szükség összecsukható/kétoszlopos megoldásra MVP-ben.

2. **Handle type:** Minden Handle `type="source"` ÉS `type="target"` is — az xyflow `connectionMode="loose"` módban ez működik: source→source és target→source kapcsolat is engedélyezett. Az `onConnect` mindkét irányt kezeli.

3. **DTO és service állapot (ellenőrzött):**
   - `ComponentPinDefinitionResponse` — **tartalmaz** `posXOffset`, `posYOffset`, `allowMultipleConnections` mezőket, a mapper kitölti őket ✓
   - `ComponentCatalogService.getAllPinDefinitions()` — **létezik**, `findAll()` + mapper ✓
   - `ComponentPinDefinitionRepository` — `existsByComponentTypeAndBoardTypeAndPinName()` **hiányzik** → 1 sor hozzáadandó a DataInitializer idempotenciájához

## Frontend technikai döntés — Handle típus

```tsx
// ReactFlow-ban:
<ReactFlow connectionMode={ConnectionMode.Loose} ...>

// Handle-ön:
<Handle type="source" ... />  // elegendő — Loose módban target-ként is elfogadja
```

`ConnectionMode.Loose` import: `import { ConnectionMode } from "@xyflow/react"`
