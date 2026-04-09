# Circuit Forge Admin UX — Következő Fejlesztési Irány

> Állapot: Tervezési fázis | Ág: `circuit_forge`
> Kontextus: Az admin Circuit Forge oldal alapjai megvannak (canvas, palette, properties panel).
> Ez a dokumentum rögzíti a következő iteráció követelményeit.

---

## 1. Pin rendszer — valós Arduino pinek

### Jelenlegi állapot (bug)
- `GET /api/circuit/catalog/pins` üres tömböt ad vissza
- A `DataInitializer` létrehozza a `circuit:read` permissiont, de **egyetlen pin definíciót sem seed-el**
- Ezért a ConnectionDialog üres, `fromPinName = ""` / `toPinName = ""` megy a backendre
- Mentés után eltűnnek a kapcsolatok (üres pin névvel nem menti a backend)

### Elvárt viselkedés

**Arduino UNO (ATmega328P) — valós pinek:**
- Digital: D0–D13 (D0=RX, D1=TX, D2–D13 GPIO, D3/D5/D6/D9/D10/D11 PWM-képes)
- Analog input: A0–A5 (GPIO-ként is használható)
- Power: 5V, 3.3V, GND (×2), RESET, AREF, IOREF, VIN
- I2C: SDA (A4), SCL (A5)
- SPI: MOSI (D11), MISO (D12), SCK (D13), SS (D10)
- Összesen: ~20 pin a fejlécen

**Arduino Mega 2560 — valós pinek:**
- Digital: D0–D53 (D2–D13 + D44–D46 PWM-képes)
- Analog input: A0–A15
- Power: 5V, 3.3V, GND (×4), RESET, AREF, IOREF, VIN
- I2C: SDA (D20), SCL (D21)
- SPI: MOSI (D51), MISO (D50), SCK (D52), SS (D53)
- Összesen: ~70 pin

**Komponensek pinjei (valós életbeli):**
| Komponens | Pinek |
|---|---|
| LED | Anode (+), Cathode (−) |
| RESISTOR | Pin1, Pin2 |
| PUSHBUTTON | Pin1, Pin2 (vagy 1A/1B/2A/2B négylábú) |
| CAPACITOR | Positive (+), Negative (−) |
| DHT11 | VCC, DATA, GND |
| HC_SR04 | VCC, TRIG, ECHO, GND |
| SERVO | VCC (red), GND (brown), Signal (orange) |
| POTENTIOMETER | VCC, Wiper (output), GND |
| VCC_5V | PWR |
| VCC_3V3 | PWR |
| GND | GND |

### Fontos megjegyzés a BOARD komponensről
A BOARD (Arduino UNO/Mega) **beépítve adja a 5V és 3.3V tápot** — nem kell külön VCC_5V vagy VCC_3V3 komponens a palette-ről ha a boardot már lerakták. De a palette-en maradhatnak ezek mint alternatív tápforrások.

---

## 2. Automatikus pin kiválasztás (ConnectionDialog UX javítás)

### Jelenlegi viselkedés (zavaros)
Az admin húz egy élt node A-ból node B-be → felugrik a ConnectionDialog → kézzel kell kiválasztani melyik pin melyikhez.

### Elvárt viselkedés
Az xyflow `Handle`-ök már **pin-specifikusak** legyenek — minden Handle egy konkrét pint reprezentál. Amikor az admin egy Handle-ről húz egy másik Handle-re, a pin nevek **automatikusan tudottak** (a Handle ID = pin neve). A ConnectionDialog így eltűnhet, vagy csak megerősítésre szolgál.

**Implementációs irány:**
- `CircuitNode`-ban minden pinhez külön `<Handle>` renderelve, `id={pinName}`
- Az xyflow `Connection` objektum `sourceHandle` és `targetHandle` mezői tartalmazzák a pin neveket
- `onConnect` közvetlenül létrehozza az edge-t pin dialógus nélkül
- A `ConnectionDialog` komponens **törölhető** vagy egyszerű megerősítő dialógussá alakítható

**Backend seed szükséges:**
- `DataInitializer`-be pin definíciók seed-elése minden komponens+board kombinációra
- Vagy: a frontend statikusan definiálja a pineket (nem DB-ből) — egyszerűbb MVP megoldás

---

## 3. Verification Checks UX — intuíció javítás

### Jelenlegi állapot (zavaros)
- `checkType` select (CIRCUIT_TOPOLOGY, PATH_EXISTS, stb.)
- `severity` select (INFO, WARNING, ERROR)
- `i18nKey` szabad szöveges mező — nem egyértelmű mit kell ide írni

### Mi az i18nKey?
Ez egy fordítási kulcs amit a kadét kap hibaüzenetként ha a check megbukik.
Pl.: `circuit.check.ledMustBeConnectedToResistor`
A platform ezt lefordítja: "Az LED-et ellenálláson keresztül kell a boardhoz kötni."
**Adminnak kell megadni mert ő definiálja a check szemantikáját.**

### Elvárt UX javítás (következő iteráció)
Opció A: Szabad szöveges `message` mező (admin magyarul/angolul írja be) → backend automatikusan generál i18nKey-t és tárolja a szöveget is
Opció B: Előre definiált check sablonok dropdown-ból (pl. "LED csatlakoztatva van", "Ellenállás a körben van") → admin csak kiválasztja
Opció C (legegyszerűbb MVP): i18nKey mező marad, de tooltip magyarázza mit kell ide írni

**Döntés szükséges:** melyik opciót valósítsuk meg.

---

## 4. SQL WARN induláskor — magyarázat

```
WARN constraint "uk..." of relation "circuit_def_connections" does not exist, skipping
```

**Ez nem hiba, csak warning.** A Hibernate `ddl-auto: update` módban indul, és megpróbálja
eltávolítani a régi unique constraint-eket mielőtt újakat ad hozzá. Mivel az adatbázis friss
(a constraintek még nem léteznek), a `DROP CONSTRAINT` sikertelen → "skipping".
**Következő indításra eltűnik** mert a constraintek már léteznek.

Érintett táblák (mind circuit-specifikus, újonnan létrehozott):
- `units_of_measure`, `component_pin_definitions`, `circuit_def_connections`
- `circuit_def_components`, `circuit_def_component_properties`
- `cadet_verification_results`, `cadet_circuit_saves`, `cadet_circuit_connections`
- `cadet_circuit_components`, `cadet_circuit_component_properties`, `cadet_analog_saves`

**Teendő:** Nincs — következő restart után eltűnik.

---

## 5. Következő fejlesztési prioritás sorrend

1. **Pin seed adatok** — `DataInitializer`-be UNO + Mega + alap komponens pinek
   → Ez oldja meg a ConnectionDialog üres listát és a kapcsolatok eltűnését
2. **Handle-alapú pin kiválasztás** — `CircuitNode` minden Handle = egy pin
   → ConnectionDialog eltűnik, UX sokkal természetesebb
3. **Verification check UX** — döntés az opciókról, majd implementáció
4. **Backend logging bővítés** — circuit-specifikus műveleteknél részletesebb log
