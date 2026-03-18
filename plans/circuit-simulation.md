# Mikroelektronika Szimuláció — Részletes Tervezési Dokumentum

> Állapot: Tervezési fázis | Ág: (új feature branch) | Kapcsolódó: `new_direction_2026.md` (Műhely koncepció)

---

## I. Vízió és Scope

### Cél
A LégyMérnök platformon belül egy interaktív, böngészőalapú mikroelektronika szimulátor, ahol a kadétok:
- Virtuális breadboardon áramköröket raknak össze
- Arduino/ESP/Raspberry Pi kódot írnak Monaco Editorban
- A szimulátorban futtatják a kódot (nincs szükség fizikai hardverre)
- Verifikált missziók keretében tanulnak forrasztás nélkül, biztonságosan

### Szimulált eszközök (prioritás sorrendben)
| Prioritás | Eszköz | Alap | Szimuláció szintje |
|---|---|---|---|
| P1 | Arduino Uno (ATmega328P) | avr8js | Teljes AVR emuláció |
| P1 | Arduino Mega 2560 (ATmega2560) | avr8js | Teljes AVR emuláció |
| P2 | ESP8266 (NodeMCU) | Mock state machine | GPIO + WiFi mock |
| P2 | ESP32 | Mock state machine | GPIO + WiFi + BT mock |
| P3 | Raspberry Pi (3B/4B/Zero) | Python szandbox | GPIO szoftver szimuláció |
| P4 | STM32 (BluePill) | Kísérleti | Részleges |

### Szimulált komponensek
| Kategória | Komponensek |
|---|---|
| Alapkomponensek | LED (több szín), ellenállás, kondenzátor, tekercs |
| Kijelzők | 7-szegmenses kijelző, 4x7-szeg, I2C 0.96" OLED (SSD1306), 16x2 LCD (I2C) |
| Bemenetek | Nyomógomb (pull-up/pull-down), billenőkapcsoló, potenciométer |
| Szenzorok | DHT11, DHT22 (hőmérséklet/páratartalom), DS18B20 (hőmérséklet, OneWire), HC-SR04 (ultrahang), LDR (fényszenzor), PIR mozgásérzékelő |
| Hajtások | Szervómotor (SG90), DC motor (L298N driver-rel), léptetőmotor (28BYJ-48) |
| Kommunikáció | Virtuális Serial monitor (UART), I2C bus, SPI bus |
| Tápellátás | VCC (5V, 3.3V), GND, breadboard power rail |
| Kábelek | Jumper wire (M-M, M-F, F-F), különböző hosszak és színek |

---

## II. Technológiai Stack

### 2.1 Frontend könyvtárak

#### avr8js — AVR mikrokontroller emulátor
- **npm:** `avr8js`
- **Licensz:** MIT
- **Verzió:** legújabb stabil
- **Miért:** Teljeskörű ATmega328P és ATmega2560 emuláció TypeScriptben, pontosan az Arduino Uno és Mega chip-jei
- **Képességek:**
  - GPIO portok (PORTA–PORTL), ADC, SPI, I2C (TWI), UART, PWM timerek
  - Megszakítások (INT0, INT1, PCINT), watchdog timer
  - PROGMEM, EEPROM emuláció
  - `.hex` fájl betöltése és futtatása
- **Integráció:** Web Worker-ben fut (nem blokkolja a UI thread-et)
- **Demo referencia:** `stackblitz.com/edit/avr8js-blink`

```typescript
// Példa integráció
import { CPU, AVRIOPort, portDConfig, AVRUSART, usart0Config } from 'avr8js';

const cpu = new CPU(new Uint16Array(hexData.buffer));
const portB = new AVRIOPort(cpu, portBConfig);
portB.addListener(() => {
  const pin13 = portB.pinState(5); // Arduino Uno D13 = PB5
  ledComponent.setState(pin13 === PinState.High);
});
```

#### @wokwi/elements — Hardver vizualizációs Web Components
- **npm:** `@wokwi/elements`
- **Licensz:** MIT
- **Miért:** Kész, pixel-perfect Arduino, breadboard, LED, kijelző Web Components; avr8js-sel szorosan együttműködik
- **Elérhető elemek:** `wokwi-arduino-uno`, `wokwi-arduino-mega`, `wokwi-led`, `wokwi-pushbutton`, `wokwi-7segment`, `wokwi-lcd1602`, `wokwi-ssd1306`, `wokwi-neopixel`, `wokwi-dht22`, stb.
- **React integráció:**
  ```tsx
  import '@wokwi/elements'; // side-effect import, registrálja a custom elementeket
  // ...
  <wokwi-led color="red" value={pinState} />
  <wokwi-arduino-uno />
  ```
  - `@lit-labs/react` csomaggal típusos React wrapper generálható
- **Korlát:** Web Components React 19-ben direkt is használhatók, de event handling figyelmet igényel

#### @xyflow/react (React Flow) — Schematic / breadboard canvas
- **npm:** `@xyflow/react`
- **Licensz:** MIT
- **Miért:** Interaktív node-edge diagram; a breadboard kapcsolatok (kábelek) megrajzolásához és a komponensek drag-and-dropjához
- **Szerepe:**
  - A "Schematic nézet"-ben a kapcsolási rajz szerkesztője
  - Komponens paletteból drag-and-drop
  - Élak = kábelkötések, handle-ok = lábkiosztás (pinout)
- **Testreszabás:** Custom node type-ok = elektronikai komponensek (SVG-alapú), custom edge type-ok = jumperwire-ek

#### CircuitJS1 — Analóg áramkör szimulátor (Falstad)
- **GitHub:** `pfalstad/circuitjs1`
- **Licensz:** MIT
- **Beágyazás módja:** `<iframe>` + `postMessage` API
- **Mikor használjuk:** Analóg fizika oktatásnál (Ohm-törvény, RC szűrők, tranzisztor kapcsolások)
- **Elkülönítés:** Ez a `CIRCUIT_SIMULATION` missionök **alapszintű** változata, amelyhez nem kell kódot írni — csak a kapcsolást kell megérteni
- **URL-alapú state:** A teljes áramköri állapot URL-ben kódolt → mentéshez és betöltéshez elegendő a string

```typescript
// postMessage kommunikáció
const iframe = document.getElementById('circuitjs') as HTMLIFrameElement;
iframe.contentWindow?.postMessage({ type: 'getCircuitState' }, '*');
window.addEventListener('message', (e) => {
  if (e.data.type === 'circuitState') {
    const circuitText = e.data.data; // Falstad szintaxis
  }
});
```

#### Monaco Editor — Kódszerkesztő
- **npm:** `@monaco-editor/react` (már telepítve a projektben)
- **Szerepe:** Arduino C++, MicroPython, Circuit Python kód szerkesztése
- **Konfiguráció:**
  - Arduino C++: `language: 'cpp'` + Arduino API autocomplete (custom completions)
  - MicroPython: `language: 'python'`
  - Téma: Dark (retro stílushoz illeszkedő, már van a projektben)

#### Egyéb frontend segédkönyvtárak
| Csomag | Szerepe | Licensz |
|---|---|---|
| `elkjs` | Schematic automatikus elrendezés (routing) | EPL-2.0 |
| `d3-path` | SVG útvonal generálás (kábelgörbék) | ISC |
| `comlink` | Web Worker kommunikáció wrapper (avr8js-hez) | Apache-2.0 |
| `@lit-labs/react` | Web Components → React wrapper generátor | BSD-3 |

---

### 2.2 Backend könyvtárak és eszközök

#### Arduino CLI — Kódfordítás
- **Telepítés:** Docker image-be beépítve (`arduino/arduino-cli`)
- **Licensz:** AGPL-3.0 (CLI eszköz, nem library — nem "fertőz")
- **Szerepe:** A kadét Arduino kódját `.hex` fájllá fordítja
- **Spring Boot integráció:**
  ```java
  // ArduinoCompilerService.java
  ProcessBuilder pb = new ProcessBuilder(
    "arduino-cli", "compile",
    "--fqbn", "arduino:avr:uno",
    "--output-dir", outputDir,
    sketchDir.toString()
  );
  ```
- **Kimenet:** `.hex` fájl → Base64 kódolva visszaküldve a frontendnek → avr8js betölti
- **Board FQBN-ek:**
  - Arduino Uno: `arduino:avr:uno`
  - Arduino Mega 2560: `arduino:avr:mega:cpu=atmega2560`
  - ESP8266: `esp8266:esp8266:nodemcuv2`
  - ESP32: `esp32:esp32:esp32`

#### ngspice (SPICE motor — opcionális, haladó fázis)
- **Telepítés:** Docker-ben: `apt-get install ngspice`
- **Java integráció:** ProcessBuilder + stdout parszolás VAGY JNI wrapper
- **Mikor kell:** Analóg szimulációs verifikáció (pl. "mérd meg a feszültségesést az ellenálláson")
- **Alternatíva frontend oldalon:** ngspice WASM (kísérleti, ~10MB, de nincs server round-trip)

#### Python szandbox (Raspberry Pi szimuláció)
- **Alap:** Docker konténer Python 3.11 + gpiozero + saját GPIO mock
- **Életciklus:** Kadétonként ephemeral konténer (start on demand, TTL 10 perc)
- **Kommunikáció:** WebSocket (STOMP — már van a projektben) → valós idejű stdout stream
- **GPIO mock library (saját):**
  ```python
  # legymernok-gpio-mock: saját mini könyvtár
  from gpiozero import Device
  from gpiozero.pins.mock import MockFactory
  Device.pin_factory = MockFactory()
  # A pin állapotváltozások WebSocket eseményként küldve a frontendnek
  ```

#### ahkab / lcapy (Python analóg szimulátor — egyszerű esetekhez)
- `lcapy`: Szimbolikus analízis (SymPy-alapú) — oktatási magyarázatokhoz (pl. "A feszültség: U = I × R = 0.02 × 220 = 4.4V")
- `ahkab`: Numerikus SPICE szimulátor pure Pythonban (DC/AC/Transient) — nincs natív függősége
- **Spring integráció:** Python microservice (FastAPI) vagy subprocess hívás

---

## III. Adatmodell (Backend)

### Tervezési elvek
- **Nincs nagy JSONB blob** — komponensek és összeköttetések normalizált táblákban élnek
- **Irány normalizálás** — minden összeköttetésnél a kisebb UUID kerül `from`-ba (service rétegben rendezve mentés előtt), így verifikációhoz egyetlen `EXISTS` query elég
- **Cadet kód Giteában** — az Arduino/Python kód Gitea repóban tárolódik, az adatbázisban csak a repo URL van
- **Properties tábla** — komponens-specifikus paraméterek (ellenállás értéke, LED feszültsége stb.) külön key-value táblában, mértékegységgel

---

### 3.1 Sablon entitások (admin által létrehozott misszió tartalom)

#### CircuitDefinitionStatus enum
```java
public enum CircuitDefinitionStatus {
    IN_WORK,   // csak admin látja és szerkesztheti, kadétoknak nem jelenik meg
    PUBLISHED  // kadétok aktívan használják; struktúra nem módosítható
               // visszavonáskor (PUBLISHED → IN_WORK) az összes CadetCircuitSave törlődik!
}
```

**Visszavonás logikája (PUBLISHED → IN_WORK):**
```
1. Confirm popup az adminnak: "Ez törli X kadét mentett munkáját. Biztosan folytatod?"
2. DELETE FROM cadet_circuit_saves WHERE mission_id = ?
   (cascade törli a komponenseket, kapcsolatokat, verifikációs eredményeket)
3. CircuitDefinition.status = IN_WORK
4. Opcionális: értesítés küldése az érintett kadétoknak (WebSocket vagy email)
```

#### CircuitDefinition — Misszió sablonos kapcsolása (fejléc)
```java
@Entity
@Table(name = "circuit_definitions")
public class CircuitDefinition {
    @Id @GeneratedValue UUID id;

    @OneToOne @JoinColumn(name = "mission_id")
    Mission mission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    BoardType boardType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    CircuitDefinitionStatus status = CircuitDefinitionStatus.IN_WORK;

    @Column(columnDefinition = "TEXT")
    String codeTemplate;          // kezdő kódsablon a Monaco Editorhoz

    @Column(nullable = false)
    Boolean allowSchematicEdit;   // szerkesztheti-e a cadet a kapcsolást, vagy csak kódot ír

    // Ha allowSchematicEdit=false → topology checkek kihagyva (sablon másolata mindig helyes)
    // Ha allowSchematicEdit=true  → topology checkek futnak

    @Column(nullable = false)
    Integer verificationWindowMs = 10000; // mennyi ideig fusson a szimuláció verifikáció alatt
    // Időalapú missziókhoz (pl. bináris óra bit3 = 8 mp) nagyobb érték szükséges: pl. 20000
    // A frontend gyorsított módban (4x) futtatja → 20000ms szimuláció = ~5 másodperc valós idő

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true)
    List<CircuitDefComponent> components;

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true)
    List<CircuitDefConnection> connections;

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true)
    List<CircuitVerificationCheck> verificationChecks;

    @CreationTimestamp Instant createdAt;
    @UpdateTimestamp  Instant updatedAt;
}
```

#### BoardType enum
```java
public enum BoardType {
    ARDUINO_UNO,       // ATmega328P — avr8js
    ARDUINO_MEGA_2560, // ATmega2560 — avr8js
    ESP8266,           // Xtensa LX106 — mock (P2)
    ESP32,             // Xtensa LX6 dual-core — mock (P2)
    RASPBERRY_PI_3,    // Python szandbox (P3)
    RASPBERRY_PI_4,
    RASPBERRY_PI_ZERO
}
```

#### CircuitDefComponent — Sablon komponense
```java
@Entity
@Table(name = "circuit_def_components")
public class CircuitDefComponent {
    @Id @GeneratedValue UUID id;

    @ManyToOne @JoinColumn(name = "definition_id", nullable = false)
    CircuitDefinition definition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ComponentType componentType;

    @Column(nullable = false)
    String label;          // ember-olvasható azonosító: "led1", "r1", "arduino_main"

    String color;          // vizuális szín (LED-eknél, kábelszínek)
    Double positionX;
    Double positionY;
    Integer rotation;      // 0 / 90 / 180 / 270 fok

    @Column(nullable = false)
    Boolean isLocked;      // true = cadet nem mozgathatja

    @OneToMany(mappedBy = "component", cascade = CascadeType.ALL, orphanRemoval = true)
    List<CircuitDefComponentProperty> properties;
}
```

#### UnitOfMeasure — Mértékegység törzsadat (admin által karbantartható)
```java
@Entity
@Table(name = "units_of_measure")
public class UnitOfMeasure {
    @Id @GeneratedValue UUID id;

    @Column(nullable = false, unique = true)
    String symbol;         // megjelenített jel: "Ω", "V", "mA", "°C", "%", "Hz"

    @Column(nullable = false)
    String name;           // teljes név: "Ohm", "Volt", "Milliampere", "Celsius-fok"

    @Column(nullable = false)
    String category;       // csoportosítás: "RESISTANCE", "VOLTAGE", "CURRENT", "TEMPERATURE", "FREQUENCY", "OTHER"

    @Column(nullable = false)
    Integer displayOrder;  // sorrend az admin legördülőben
}
```

**Seed adatok (DataInitializer):**
| symbol | name | category | displayOrder |
|---|---|---|---|
| Ω | Ohm | RESISTANCE | 1 |
| kΩ | Kiloohm | RESISTANCE | 2 |
| MΩ | Megaohm | RESISTANCE | 3 |
| V | Volt | VOLTAGE | 10 |
| mV | Millivolt | VOLTAGE | 11 |
| A | Amper | CURRENT | 20 |
| mA | Milliamper | CURRENT | 21 |
| µA | Mikroamper | CURRENT | 22 |
| °C | Celsius-fok | TEMPERATURE | 30 |
| % | Százalék | OTHER | 40 |
| Hz | Hertz | FREQUENCY | 50 |
| kHz | Kilohertz | FREQUENCY | 51 |
| µF | Mikrofarad | CAPACITANCE | 60 |
| nF | Nanofarad | CAPACITANCE | 61 |
| µH | Mikrohenry | INDUCTANCE | 70 |

#### CircuitDefComponentProperty — Komponens paraméterek
```java
@Entity
@Table(name = "circuit_def_component_properties")
public class CircuitDefComponentProperty {
    @Id @GeneratedValue UUID id;

    @ManyToOne @JoinColumn(name = "component_id", nullable = false)
    CircuitDefComponent component;

    @Column(nullable = false)
    String name;           // ember-olvasható megnevezés: "Ellenállás értéke"

    @Column(nullable = false)
    String key;            // gépi kulcs: "resistance"

    @Column(nullable = false)
    String value;          // érték stringként: "220"

    // null = nincs mértékegység (pl. szín, boolean flag)
    @ManyToOne @JoinColumn(name = "unit_of_measure_id")
    UnitOfMeasure unitOfMeasure;
}
```

**Példa sorok:**
| name | key | value | unitOfMeasure.symbol |
|---|---|---|---|
| Ellenállás értéke | resistance | 220 | Ω |
| Előre feszültség | forwardVoltage | 2.0 | V |
| Max. áram | maxCurrent | 20 | mA |
| Hőmérséklet (szimuláció) | simulatedTemp | 25.0 | °C |

#### CircuitDefConnection — Sablon összeköttetés (irány normalizált)
```java
@Entity
@Table(name = "circuit_def_connections")
public class CircuitDefConnection {
    @Id @GeneratedValue UUID id;

    @ManyToOne @JoinColumn(name = "definition_id", nullable = false)
    CircuitDefinition definition;

    // Irány normalizálás: from.id < to.id (UUID lexikografikus sorrend)
    // A service réteg garantálja ezt mentés előtt
    @ManyToOne @JoinColumn(name = "from_component_id", nullable = false)
    CircuitDefComponent fromComponent;
    @Column(nullable = false) String fromPin;  // "D13", "anode", "A", "GND"

    @ManyToOne @JoinColumn(name = "to_component_id", nullable = false)
    CircuitDefComponent toComponent;
    @Column(nullable = false) String toPin;

    String color;   // kábel vizuális színe: "red", "black", "green"
    String label;   // opcionális felirat
}
```

**Service réteg — irány normalizálás mentés előtt:**
```java
// CircuitConnectionNormalizer.java
public static void normalize(CircuitDefConnection conn) {
    if (conn.getFromComponent().getId().compareTo(conn.getToComponent().getId()) > 0) {
        // swap
        CircuitDefComponent tmpComp = conn.getFromComponent();
        String tmpPin = conn.getFromPin();
        conn.setFromComponent(conn.getToComponent());
        conn.setFromPin(conn.getToPin());
        conn.setToComponent(tmpComp);
        conn.setToPin(tmpPin);
    }
}
```

**Verifikációs query — egyetlen EXISTS, sorrend-független:**
```java
// CircuitDefConnectionRepository.java
boolean existsByConnection(UUID definitionId,
                           UUID compAId, String pinA,
                           UUID compBId, String pinB) {
    UUID fromId = compAId.compareTo(compBId) <= 0 ? compAId : compBId;
    UUID toId   = compAId.compareTo(compBId) <= 0 ? compBId : compAId;
    String fromPin = compAId.compareTo(compBId) <= 0 ? pinA : pinB;
    String toPin   = compAId.compareTo(compBId) <= 0 ? pinB : pinA;

    return repo.existsByDefinitionIdAndFromComponentIdAndFromPinAndToComponentIdAndToPin(
        definitionId, fromId, fromPin, toId, toPin
    );
}
```

#### ComponentType enum (Java és TypeScript oldalon szinkronban tartva)
```java
public enum ComponentType {
    // Passzív
    RESISTOR, CAPACITOR, INDUCTOR,
    // Aktív / Kijelző
    LED, RGB_LED, SEVEN_SEGMENT, LCD_1602, OLED_SSD1306,
    // Bemenetek
    PUSHBUTTON, TOGGLE_SWITCH, POTENTIOMETER,
    // Szenzorok
    DHT11, DHT22, DS18B20, HC_SR04, LDR, PIR,
    // Hajtások
    SERVO, DC_MOTOR, STEPPER_28BYJ48,
    // Kommunikáció (virtuális)
    SERIAL_MONITOR,
    // Tápellátás
    VCC_5V, VCC_3V3, GND,
    // Board
    BOARD   // maga az Arduino/ESP/Pi board node
}
```

#### ComponentPinDefinition — Komponens lábkiosztás (admin által karbantartható)
```java
@Entity
@Table(name = "component_pin_definitions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"component_type", "board_type", "pin_name"}))
public class ComponentPinDefinition {
    @Id @GeneratedValue UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ComponentType componentType;

    // Csak BOARD componentType esetén töltve ki — más komponenseknél null.
    // Azért kell, mert az Arduino Uno D13 = PB5, a Mega D13 = PB7 (különböző AVR port bitek).
    @Enumerated(EnumType.STRING)
    @Column(name = "board_type")
    BoardType boardType;      // null ha componentType != BOARD

    @Column(nullable = false)
    String pinName;           // "D13", "anode", "A", "SDA", "VCC", "GND"

    @Column(nullable = false)
    String displayName;       // "Digital Pin 13 (LED_BUILTIN)", "Anód (+)", "A kapu"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    PinType pinType;

    // Az AVR port bit leképezés — csak BOARD componentType esetén releváns.
    // A frontend circuit-engine PinResolver ezt tükrözi.
    String avrPort;           // "B", "C", "D", null ha nem AVR
    Integer avrBit;           // 0–7, null ha nem AVR

    @Column(nullable = false)
    Boolean allowMultipleConnections; // I2C/SPI buszhoz true, normál GPIO-hoz false

    @Column(nullable = false)
    Boolean interruptCapable;  // true = hardware interrupt forrás (INT0/INT1 vagy PCINT)
                               // Frontend vizuálisan jelzi, admin PIN_MUST_BE_INTERRUPT_CAPABLE
                               // checkkel kényszerítheti hogy a kadét ide kösse a gombot

    @Column(nullable = false)
    Integer displayOrder;     // sorrend a pinout UI-ban
}
```

```java
public enum PinType {
    DIGITAL_IO,      // általános digitális be/kivitel (pl. D2–D13 Arduino Uno-n)
    ANALOG_IN,       // csak analóg bemenet (pl. A0–A5)
    PWM_OUT,         // PWM képes digitális kimenet (pl. D3, D5, D6, D9, D10, D11 Uno-n)
    POWER_VCC,       // tápfeszültség kimenet (5V, 3.3V)
    POWER_GND,       // föld
    I2C_SDA,         // I2C adatvezeték
    I2C_SCL,         // I2C órajel
    SPI_MOSI,
    SPI_MISO,
    SPI_SCK,
    SPI_CS,
    UART_TX,
    UART_RX,
    ONE_WIRE,        // Dallas OneWire (DS18B20)
    COMPONENT_ANODE,   // LED/dióda anód
    COMPONENT_CATHODE, // LED/dióda katód
    PASSIVE_A,         // passzív kétpólusú A vége (ellenállás, kondenzátor)
    PASSIVE_B,         // passzív kétpólusú B vége
    SIGNAL_OUT,        // szenzor digitális/analóg kimenet
    SIGNAL_IN          // aktuátor vezérlő bemenet (pl. szervó, buzzer)
}
```

**Seed adatok (részlet) — DataInitializer:**
| componentType | boardType | pinName | displayName | pinType | avrPort | avrBit | allowMultiple | interruptCapable |
|---|---|---|---|---|---|---|---|---|
| BOARD | ARDUINO_UNO | D0 | Digital 0 / RX | UART_RX | D | 0 | false | false |
| BOARD | ARDUINO_UNO | D2 | Digital 2 (INT0) | DIGITAL_IO | D | 2 | false | **true** |
| BOARD | ARDUINO_UNO | D3 | Digital 3 (INT1) | DIGITAL_IO | D | 3 | false | **true** |
| BOARD | ARDUINO_UNO | D13 | Digital 13 / LED_BUILTIN | DIGITAL_IO | B | 5 | false | false |
| BOARD | ARDUINO_UNO | A0 | Analog 0 | ANALOG_IN | C | 0 | false | false |
| BOARD | ARDUINO_UNO | SDA | SDA (A4) | I2C_SDA | C | 4 | **true** | false |
| BOARD | ARDUINO_UNO | SCL | SCL (A5) | I2C_SCL | C | 5 | **true** | false |
| BOARD | ARDUINO_UNO | 5V | 5V Power | POWER_VCC | null | null | **true** | false |
| BOARD | ARDUINO_UNO | GND | Ground | POWER_GND | null | null | **true** | false |
| BOARD | ARDUINO_MEGA_2560 | D2 | Digital 2 (INT4) | DIGITAL_IO | E | 4 | false | **true** |
| BOARD | ARDUINO_MEGA_2560 | D3 | Digital 3 (INT5) | DIGITAL_IO | E | 5 | false | **true** |
| BOARD | ARDUINO_MEGA_2560 | D13 | Digital 13 / LED_BUILTIN | DIGITAL_IO | B | 7 | false | false |
| BOARD | ARDUINO_MEGA_2560 | SDA | SDA (Pin 20) | I2C_SDA | D | 1 | **true** | false |
| BOARD | ARDUINO_MEGA_2560 | SCL | SCL (Pin 21) | I2C_SCL | D | 0 | **true** | false |
| LED | null | anode | Anód (+) | COMPONENT_ANODE | null | null | false | false |
| LED | null | cathode | Katód (−) | COMPONENT_CATHODE | null | null | false | false |
| RESISTOR | null | A | A kapu | PASSIVE_A | null | null | false | false |
| RESISTOR | null | B | B kapu | PASSIVE_B | null | null | false | false |
| DHT22 | null | VCC | Tápfeszültség | POWER_VCC | null | null | false | false |
| DHT22 | null | GND | Föld | POWER_GND | null | null | false | false |
| DHT22 | null | DATA | Adatvezeték | ONE_WIRE | null | null | false | false |
| OLED_SSD1306 | null | VCC | 3.3–5V táp | POWER_VCC | null | null | false | false |
| OLED_SSD1306 | null | GND | Föld | POWER_GND | null | null | false | false |
| OLED_SSD1306 | null | SDA | I2C adat | I2C_SDA | null | null | **true** | false |
| OLED_SSD1306 | null | SCL | I2C órajel | I2C_SCL | null | null | **true** | false |

> **Több kapcsolat (I2C busz) — szabály:** Ha `allowMultipleConnections = true`, a frontend engedi több kábel húzását ugyanarra a pinre. Az I2C busz így szimulálható: `Arduino.SDA → OLED.SDA` és `Arduino.SDA → Sensor.SDA` egyszerre érvényes. Ha `false`, a második kábelhúzás kísérleténél a frontend hibát jelez.

---

#### ComponentElectricalSpec — Elektromos határértékek és validációs szabályok
```java
@Entity
@Table(name = "component_electrical_specs")
public class ComponentElectricalSpec {
    @Id @GeneratedValue UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ComponentType componentType;

    @Column(nullable = false)
    String specName;           // "Maximális tápfeszültség"

    @Column(nullable = false)
    String key;                // "maxVcc"

    @Column(nullable = false)
    String value;              // "5.5"

    @ManyToOne @JoinColumn(name = "unit_of_measure_id")
    UnitOfMeasure unitOfMeasure;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ElectricalValidationType validationType; // MAX, MIN, TYPICAL, REQUIRED_COMPONENT, POLARITY

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ValidationSeverity severity; // WARNING, ERROR

    @Column(nullable = false, columnDefinition = "TEXT")
    String warningMessage;     // "A DHT22 max. tápfeszültsége 5.5V. Magasabb feszültségen meghibásodik."

    @Column(nullable = false)
    String educationalExplanation; // "Az IC belső logikája 3.3V-os, a 5V-os jel hosszabb idő alatt tönkreteszi."
}
```

```java
public enum ElectricalValidationType {
    MAX_VCC,               // max tápfeszültség — ha a bekötött rail magasabb → severity alapján figyelmeztetés/hiba
    MIN_VCC,               // min tápfeszültség — ha alacsonyabb
    MAX_CURRENT,           // max áram — ha a számított áram meghaladja
    FORWARD_VOLTAGE,       // LED/dióda előre feszültség (szimulációhoz)
    REQUIRES_SERIES_RESISTOR, // ha közvetlenül tápra kötik ellenállás nélkül → ERROR
    POLARITY_SENSITIVE,      // anód/katód felcserélése → WARNING ("A LED fordítva van bekötve")
    MAX_SIGNAL_VOLTAGE,      // pl. 3.3V-os ESP32 GPIO-ra 5V-os jelet küldeni → WARNING
    REQUIRES_PULLUP_ON_DATA, // DATA lábhoz pull-up ellenállás szükséges (DHT11/22, OneWire)
    SHORT_CIRCUIT            // VCC és GND közvetlen kapcsolata → ERROR
}
```

```java
public enum ValidationSeverity {
    INFO,    // tájékoztató, nem blokkolja a futtatást
    WARNING, // figyelmeztető, futtatható de jelzik
    ERROR    // blokkolja a szimuláció indítását, kötelező javítani
}
```

**Seed adatok (részlet):**
| componentType | specName | key | value | unit | validationType | severity | warningMessage |
|---|---|---|---|---|---|---|---|
| LED | Szükséges soros ellenállás | requiresResistor | true | — | REQUIRES_SERIES_RESISTOR | ERROR | "Az LED közvetlenül tápra kötve meghibásodik. Kötj be soros ellenállást!" |
| LED | Polaritásérzékeny | polaritySensitive | true | — | POLARITY_SENSITIVE | WARNING | "A LED fordítva van bekötve (anód → GND irányban). Így nem fog világítani." |
| LED | Előre feszültség | forwardVoltage | 2.0 | V | FORWARD_VOLTAGE | INFO | "Piros LED tipikus előre feszültsége 2.0V." |
| DHT22 | Max. tápfeszültség | maxVcc | 5.5 | V | MAX_VCC | WARNING | "A DHT22 max. tápfeszültsége 5.5V." |
| DHT22 | Min. tápfeszültség | minVcc | 3.3 | V | MIN_VCC | WARNING | "A DHT22 legalább 3.3V tápot igényel." |
| DHT22 | Pull-up ellenállás szükséges DATA lábra | requiresPullup | true | — | REQUIRES_PULLUP_ON_DATA | WARNING | "A DHT22 DATA lábához 4.7kΩ pull-up ellenállás szükséges VCC és DATA között. Enélkül megbízhatatlan az adatolvasás." |
| HC_SR04 | Max. jelszint | maxSignalVoltage | 5.0 | V | MAX_SIGNAL_VOLTAGE | ERROR | "Az ESP32 GPIO-ja 3.3V-os. Az HC-SR04 ECHO lába 5V-ot ad ki — feszültségosztó szükséges!" |
| CAPACITOR | Polaritásérzékeny (elektrolit) | polaritySensitive | true | — | POLARITY_SENSITIVE | WARNING | "Elektrolit kondenzátor fordítva bekötve meghibásodhat." |
| — | Rövidzárlat | shortCircuit | — | — | SHORT_CIRCUIT | ERROR | "VCC és GND közvetlen összekötése rövidzárlatot okoz!" |

> Az elektromos validáció a frontend `circuit-engine` könyvtárban fut (mentéskor és futtatáskor egyaránt), és a backend `/verify` endpoint is elvégzi másodszorra. Az `educationalExplanation` mező a hibapanelen részletes magyarázatot ad — ez az oktatási értéke.

---

#### CircuitVerificationCheck — Normalizált verifikációs szabályok
```java
@Entity
@Table(name = "circuit_verification_checks")
public class CircuitVerificationCheck {
    @Id @GeneratedValue UUID id;

    @ManyToOne @JoinColumn(name = "definition_id", nullable = false)
    CircuitDefinition definition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    CheckType checkType;  // GPIO_BEHAVIOR, SERIAL_OUTPUT, PWM, CIRCUIT_TOPOLOGY

    @Column(nullable = false)
    String description;   // "D13 pin 1 Hz-es villogás"

    String targetPin;      // "D13", "D9" — null ha nem pin-alapú check

    String expectedBehavior; // "BLINK", "PWM", "CONTAINS", "HIGH", "LOW"

    // Check-specifikus kis paraméterek (tolerancia, frekvencia stb.)
    // Ez az egyetlen "kis" JSONB ami megmarad — max 4-5 numerikus érték
    @Column(columnDefinition = "JSONB")
    String parametersJson; // {"frequencyHz": 1.0, "tolerancePercent": 20}

    @Column(nullable = false)
    Integer orderIndex;   // kijelzési sorrend az eredménylistában
}
```

```java
public enum CheckType {
    GPIO_BEHAVIOR,      // pin HIGH/LOW/BLINK mintázat
    SERIAL_OUTPUT,      // UART kimenet tartalom
    PWM,                // duty cycle ellenőrzés
    CIRCUIT_TOPOLOGY    // kapcsolás gráfbejárással — részletes formátum lent
}
```

**CIRCUIT_TOPOLOGY check — `parametersJson` formátum:**

Két szint lehetséges:

*1. szint — Közvetlen kapcsolat lista (egyszerű esetekhez):*
```json
{
  "type": "REQUIRED_CONNECTIONS",
  "connections": [
    { "from": { "label": "arduino_main", "pin": "D13" }, "to": { "label": "r1",          "pin": "A"      } },
    { "from": { "label": "r1",          "pin": "B"  }, "to": { "label": "led1",        "pin": "anode"  } },
    { "from": { "label": "led1",        "pin": "cathode" }, "to": { "label": "arduino_main", "pin": "GND" } }
  ]
}
```
Minden sor egy EXISTS query (normalizált irányban) — ha mindhárom megvan, a check sikeres.

*2. szint — Útvonal elérhetőség (gráfbejárás, összetettebb esetekhez):*
```json
{
  "type": "PATH_EXISTS",
  "from": { "label": "arduino_main", "pin": "D13" },
  "to":   { "label": "arduino_main", "pin": "GND" },
  "mustPassThrough": [
    { "componentType": "RESISTOR" },
    { "componentType": "LED" }
  ],
  "description": "D13 legyen összekötve GND-vel ellenálláson és LED-en keresztül"
}
```
BFS/DFS a `CadetCircuitConnection` gráfon, szomszédossági listával felépítve.

**GraphTraversalService — BFS implementáció:**
```java
// A cadet_circuit_connections alapján szomszédossági lista épül
// Csomópontok: (componentId, pinName) párok
// Élek: normalizált CadetCircuitConnection rekordok (mindkét irányban bejárható)

public boolean pathExists(UUID saveId, PinRef from, PinRef to, List<ComponentTypeRequirement> mustPassThrough) {
    Map<PinRef, List<PinRef>> adjacency = buildAdjacencyMap(saveId);
    // BFS: from → to, közben ellenőrzi a mustPassThrough feltételeket
    // Ha az út megtalálható és minden szükséges ComponentType szerepel benne → true
}
```

```java
```

---

### 3.2 Analóg áramkör entitás (CircuitJS1 — külön úton)

Az analóg szimulációhoz (CircuitJS1 / Falstad) teljesen más adatformátum szükséges, mint a digitális breadboard modellhez. Ezért teljesen önálló entitás.

#### AnalogCircuitDefinition
```java
@Entity
@Table(name = "analog_circuit_definitions")
public class AnalogCircuitDefinition {
    @Id @GeneratedValue UUID id;

    @OneToOne @JoinColumn(name = "mission_id")
    Mission mission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    CircuitDefinitionStatus status; // IN_WORK / PUBLISHED (ugyanaz a szabály)

    // Falstad szövegformátum — amit a CircuitJS1 közvetlenül be tud tölteni
    @Column(columnDefinition = "TEXT", nullable = false)
    String starterCircuitText;    // a kadét kiindulópontja (hiányos kapcsolás)

    @Column(columnDefinition = "TEXT")
    String solutionCircuitText;   // a helyes megoldás (admin tölti ki, kadétnak nem látható)

    @Column(columnDefinition = "TEXT")
    String taskDescription;       // Markdown — mit kell megvalósítani

    @OneToMany(mappedBy = "definition", cascade = CascadeType.ALL, orphanRemoval = true)
    List<AnalogVerificationCheck> verificationChecks;

    @CreationTimestamp Instant createdAt;
    @UpdateTimestamp  Instant updatedAt;
}
```

#### AnalogVerificationCheck — Feszültség/áram ellenőrzés
```java
@Entity
@Table(name = "analog_verification_checks")
public class AnalogVerificationCheck {
    @Id @GeneratedValue UUID id;

    @ManyToOne @JoinColumn(name = "definition_id", nullable = false)
    AnalogCircuitDefinition definition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    AnalogCheckType checkType;

    @Column(nullable = false)
    String description;          // "A LED-en átfolyó áram 10–30 mA között legyen"

    String nodeLabel;            // CircuitJS1 belső csomópont neve/azonosítója

    Double expectedMin;
    Double expectedMax;

    @ManyToOne @JoinColumn(name = "unit_of_measure_id")
    UnitOfMeasure unitOfMeasure; // mA, V, Ω stb.

    @Column(nullable = false)
    Integer orderIndex;
}
```

```java
public enum AnalogCheckType {
    NODE_VOLTAGE,      // adott csomóponton a feszültség tartományban van-e
    BRANCH_CURRENT,    // adott ágon az áram tartományban van-e
    COMPONENT_EXISTS,  // adott típusú komponens szerepel-e a kapcsolásban
    COMPONENT_VALUE,   // adott komponens értéke (pl. ellenállás) tartományban van-e
    LED_LIGHTS         // a LED éget-e (forward voltage + áram > min)
}
```

#### CadetAnalogSave — Kadét analóg mentése
```java
@Entity
@Table(name = "cadet_analog_saves",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cadet_id", "mission_id"}))
public class CadetAnalogSave {
    @Id @GeneratedValue UUID id;

    @ManyToOne @JoinColumn(name = "cadet_id",  nullable = false) Cadet cadet;
    @ManyToOne @JoinColumn(name = "mission_id", nullable = false) Mission mission;

    // A kadét által módosított Falstad kapcsolás szövege
    @Column(columnDefinition = "TEXT", nullable = false)
    String circuitText;

    @Enumerated(EnumType.STRING)
    SimulationStatus lastStatus;

    @UpdateTimestamp Instant savedAt;
}
```

> **Megjegyzés:** Az analóg verifikáció a CircuitJS1 `postMessage` API-n keresztül lekért szimulációs értékeken (feszültség, áram csomópontonként) alapul — ezeket a frontend küldi fel a `/api/circuit/{missionId}/analog/verify` endpointba.

---

### 3.3 Cadet entitások (kadét mentett munkája)

#### CadetCircuitSave — Létrehozás folyamata ("Misszió elkezdése" gomb)

```
1. Kadét rákattint a "Misszió elkezdése" gombra a CircuitSimPage-en
2. POST /api/circuit/{missionId}/start
3. CircuitStartService:
   a. CadetMission rekord létrehozása (JOIN tábla — misszió "elkezdve" státusz)
   b. Gitea repo létrehozása: GiteaService.createMissionRepository(...)
      → mission-circuit-template-ből másolva
      → cadet hozzáadva collaboratorként
   c. CadetCircuitSave létrehozása:
      - giteaRepoUrl = új repo URL
      - lastStatus = NEVER_RUN
   d. Komponensek másolása a sablonból (property örökléssel):
      FOR EACH CircuitDefComponent c IN definition.components:
        CadetCircuitComponent cadetComp = copy(c)
        cadetComp.templateComponent = c       ← FK a sablon komponensre
        FOR EACH CircuitDefComponentProperty p IN c.properties:
          CadetCircuitComponentProperty cadetProp = copy(p)  ← unit_of_measure FK is másolódik
          cadetComp.properties.add(cadetProp)
   e. Kapcsolatok másolása a sablonból:
      FOR EACH CircuitDefConnection conn IN definition.connections:
        CadetCircuitConnection cadetConn = copy(conn)
        cadetConn.fromComponent = lookup(cadetComponents, conn.fromComponent)
        cadetConn.toComponent   = lookup(cadetComponents, conn.toComponent)
4. Response: CadetCircuitSave ID + Gitea repo URL

Megjegyzés: Ha a CadetCircuitSave már létezik (korábban elkezdve),
a start endpoint visszaadja a meglévőt — nem hoz létre duplikátumot.
```

**Property öröklés és admin módosítás:**
- Ha az admin PUBLISHED → IN_WORK visszavon: minden `CadetCircuitSave` törlődik (cascade)
- Amikor az admin újra PUBLISHED-re állít: a következő kadét "Misszió elkezdésekor" friss másolatot kap az aktuális sablonból
- A már meglévő cadet save-ek NEM frissülnek automatikusan — csak törlés + újrakezdés esetén kapnak friss másolatot

#### CadetCircuitSave — Kadét munkájának fejléce
```java
@Entity
@Table(name = "cadet_circuit_saves",
       uniqueConstraints = @UniqueConstraint(columnNames = {"cadet_id", "mission_id"}))
public class CadetCircuitSave {
    @Id @GeneratedValue UUID id;

    @ManyToOne @JoinColumn(name = "cadet_id", nullable = false)  Cadet cadet;
    @ManyToOne @JoinColumn(name = "mission_id", nullable = false) Mission mission;

    // A kód Gitea repóban van — csak a link kerül DB-be
    @Column(nullable = false)
    String giteaRepoUrl;          // pl. "http://gitea:3000/legymernok_admin/circuit-{missionId}-{cadetId}"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    SimulationStatus lastStatus;

    @Column(columnDefinition = "TEXT")
    String lastCompileError;      // null ha nincs hiba

    @OneToMany(mappedBy = "save", cascade = CascadeType.ALL, orphanRemoval = true)
    List<CadetCircuitComponent> components;

    @OneToMany(mappedBy = "save", cascade = CascadeType.ALL, orphanRemoval = true)
    List<CadetCircuitConnection> connections;

    @OneToMany(mappedBy = "save", cascade = CascadeType.ALL, orphanRemoval = true)
    List<CadetVerificationResult> verificationResults;

    @UpdateTimestamp Instant savedAt;
}
```

#### SimulationStatus enum
```java
public enum SimulationStatus {
    NEVER_RUN,
    COMPILING,
    COMPILE_ERROR,
    RUNNING,
    PAUSED,
    SUCCESS,
    FAILED
}
```

#### CadetCircuitComponent — Kadét által elhelyezett komponens
```java
@Entity
@Table(name = "cadet_circuit_components")
public class CadetCircuitComponent {
    @Id @GeneratedValue UUID id;

    @ManyToOne @JoinColumn(name = "save_id", nullable = false)
    CadetCircuitSave save;

    // Ha a template-ből örökölt (zárolt) komponens, hivatkozunk az eredetire.
    // null = cadet maga adta hozzá (csak allowSchematicEdit=true esetén lehetséges)
    @ManyToOne @JoinColumn(name = "template_component_id")
    CircuitDefComponent templateComponent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ComponentType componentType;

    @Column(nullable = false)
    String label;

    String color;
    Double positionX;
    Double positionY;
    Integer rotation;

    @OneToMany(mappedBy = "component", cascade = CascadeType.ALL, orphanRemoval = true)
    List<CadetCircuitComponentProperty> properties;
}
```

#### CadetCircuitComponentProperty — Kadét komponens paraméterei
```java
@Entity
@Table(name = "cadet_circuit_component_properties")
public class CadetCircuitComponentProperty {
    @Id @GeneratedValue UUID id;

    @ManyToOne @JoinColumn(name = "component_id", nullable = false)
    CadetCircuitComponent component;

    @Column(nullable = false) String name;
    @Column(nullable = false) String key;
    @Column(nullable = false) String value;

    @ManyToOne @JoinColumn(name = "unit_of_measure_id")
    UnitOfMeasure unitOfMeasure; // null = nincs mértékegység
}
```

#### CadetCircuitConnection — Kadét által húzott kábel (irány normalizált)
```java
@Entity
@Table(name = "cadet_circuit_connections")
public class CadetCircuitConnection {
    @Id @GeneratedValue UUID id;

    @ManyToOne @JoinColumn(name = "save_id", nullable = false)
    CadetCircuitSave save;

    // Irány normalizálás — ugyanaz a szabály mint a sablon oldalon
    @ManyToOne @JoinColumn(name = "from_component_id", nullable = false)
    CadetCircuitComponent fromComponent;
    @Column(nullable = false) String fromPin;

    @ManyToOne @JoinColumn(name = "to_component_id", nullable = false)
    CadetCircuitComponent toComponent;
    @Column(nullable = false) String toPin;

    String color;
}
```

#### CadetVerificationResult — Per-check eredmény (normalizált)
```java
@Entity
@Table(name = "cadet_verification_results")
public class CadetVerificationResult {
    @Id @GeneratedValue UUID id;

    @ManyToOne @JoinColumn(name = "save_id", nullable = false)
    CadetCircuitSave save;

    @ManyToOne @JoinColumn(name = "check_id", nullable = false)
    CircuitVerificationCheck check;

    @Column(nullable = false)
    Boolean passed;

    String actualValue;   // pl. "0.97 Hz" — amit ténylegesen mértünk
    String message;       // ember-olvasható magyarázat

    @CreationTimestamp Instant evaluatedAt;
}
```

---

### 3.3 Adatbázis séma áttekintés

```
units_of_measure (törzsadat, admin által karbantartható)

circuit_definitions (1)
  ├── circuit_def_components (N)
  │     └── circuit_def_component_properties (N) → FK → units_of_measure
  ├── circuit_def_connections (N)
  └── circuit_verification_checks (N)

cadet_circuit_saves (1)
  ├── cadet_circuit_components (N)
  │     └── cadet_circuit_component_properties (N) → FK → units_of_measure
  ├── cadet_circuit_connections (N)
  └── cadet_verification_results (N) → FK → circuit_verification_checks
```

**Gitea repo struktúra (circuit misszióknál):**
```
circuit-{missionId}-{cadetId}/
├── sketch/
│   └── sketch.ino      ← a kadét Arduino kódja (vagy main.py Pi esetén)
└── README.md           ← automatikusan generált, misszió neve + leírás
```

---

## IV. Backend API Endpointok

### Új permission-ök (DataInitializer-be)
```
circuit:view      - CIRCUIT_SIMULATION misszió megtekintése
circuit:run       - szimuláció futtatása
circuit:edit      - kapcsolás szerkesztése
circuit:compile   - kód fordítás indítása
circuit:manage    - admin: CircuitDefinition CRUD
```

### Új kontrollerek és endpointok

#### CircuitController — `/api/circuit`
| Metódus | Path | Permission | Leírás |
|---|---|---|---|
| GET | `/api/circuit/{missionId}` | `circuit:view` | CircuitDefinition betöltése (sablon + cadet mentett állapot merge) |
| POST | `/api/circuit/{missionId}/save` | `circuit:run` | Kapcsolás + kód mentése CadetCircuitSave-be |
| POST | `/api/circuit/{missionId}/compile` | `circuit:compile` | Kód fordítása Arduino CLI-vel → .hex visszaadása |
| POST | `/api/circuit/{missionId}/verify` | `circuit:run` | Verifikáció futtatása (backend oldali check) |
| GET | `/api/circuit/{missionId}/result` | `circuit:view` | Utolsó verifikáció eredménye |

#### CircuitAdminController — `/api/admin/circuit`
| Metódus | Path | Permission | Leírás |
|---|---|---|---|
| POST | `/api/admin/circuit/{missionId}` | `circuit:manage` | CircuitDefinition létrehozása misszióhoz |
| PUT | `/api/admin/circuit/{missionId}` | `circuit:manage` | CircuitDefinition módosítása |
| GET | `/api/admin/circuit/{missionId}/preview` | `circuit:manage` | Admin előnézet |

#### UnitOfMeasureController — `/api/admin/units-of-measure`
| Metódus | Path | Permission | Leírás |
|---|---|---|---|
| GET | `/api/admin/units-of-measure` | `circuit:manage` | Összes mértékegység listázása (kategória szerint rendezve) |
| POST | `/api/admin/units-of-measure` | `circuit:manage` | Új mértékegység felvétele |
| PUT | `/api/admin/units-of-measure/{id}` | `circuit:manage` | Mértékegység szerkesztése (symbol, name, displayOrder) |
| DELETE | `/api/admin/units-of-measure/{id}` | `circuit:manage` | Törlés — csak ha nincs rá hivatkozó property sor (FK constraint) |

> A GET `/api/admin/units-of-measure` publikusan is elérhető lesz `circuit:view` permission-nel, mert a kadétoknak is kell a listázáshoz (pl. component properties megjelenítésekor).

#### ComponentPinDefinitionController — `/api/circuit/pin-definitions`

A frontend **induláskor egyszer** tölti le az összes pin definíciót és cachelii — ezáltal a `PinResolver.ts` nem tartalmaz hardkódolt map-et, az adatbázis az egyetlen igazságforrás.

| Metódus | Path | Permission | Leírás |
|---|---|---|---|
| GET | `/api/circuit/pin-definitions` | `circuit:view` | Összes `ComponentPinDefinition` sor, `boardType` + `componentType` szerint csoportosítva |
| POST | `/api/admin/circuit/pin-definitions` | `circuit:manage` | Új pin definíció felvétele |
| PUT | `/api/admin/circuit/pin-definitions/{id}` | `circuit:manage` | Pin definíció módosítása |
| DELETE | `/api/admin/circuit/pin-definitions/{id}` | `circuit:manage` | Törlés — csak ha nincs rá hivatkozó connection |

**Response struktúra (`GET /api/circuit/pin-definitions`):**
```json
{
  "BOARD": {
    "ARDUINO_UNO": [
      { "pinName": "D2", "displayName": "Digital 2 (INT0)", "pinType": "DIGITAL_IO",
        "avrPort": "D", "avrBit": 2, "allowMultipleConnections": false,
        "interruptCapable": true, "displayOrder": 2 },
      { "pinName": "D13", "displayName": "Digital 13 / LED_BUILTIN", ... }
    ],
    "ARDUINO_MEGA_2560": [ ... ]
  },
  "LED": {
    "null": [
      { "pinName": "anode",   "pinType": "COMPONENT_ANODE", ... },
      { "pinName": "cathode", "pinType": "COMPONENT_CATHODE", ... }
    ]
  }
}
```

**Frontend cache — induláskor egyszer:**
```typescript
// circuitEngineInit.ts — az alkalmazás indulásakor hívódik meg
export async function initCircuitEngine(): Promise<void> {
  const response = await apiClient.get('/api/circuit/pin-definitions');
  PinResolver.loadFromApi(response.data);       // feltölti a runtime map-et
  ElectricalValidator.loadSpecs(await apiClient.get('/api/circuit/electrical-specs'));
}
```

```typescript
// PinResolver.ts — nincs több hardkódolt map
class PinResolver {
  private static pinMap: Map<string, ComponentPinDefinition> = new Map();

  static loadFromApi(data: PinDefinitionResponse): void {
    // Feltölti a map-et: "ARDUINO_UNO:D13" → { avrPort: 'B', avrBit: 5, ... }
    Object.entries(data).forEach(([compType, boardMap]) => {
      Object.entries(boardMap).forEach(([boardType, pins]) => {
        pins.forEach(pin => {
          const key = `${boardType}:${pin.pinName}`;
          PinResolver.pinMap.set(key, pin);
        });
      });
    });
  }

  static resolve(boardType: BoardType, pinName: string): ComponentPinDefinition {
    return PinResolver.pinMap.get(`${boardType}:${pinName}`)
        ?? PinResolver.pinMap.get(`null:${pinName}`); // nem-board komponensek
  }
}
```

#### ComponentElectricalSpecController — `/api/circuit/electrical-specs`
| Metódus | Path | Permission | Leírás |
|---|---|---|---|
| GET | `/api/circuit/electrical-specs` | `circuit:view` | Összes elektromos spec, componentType szerint csoportosítva (frontend cache-eli) |
| POST | `/api/admin/circuit/electrical-specs` | `circuit:manage` | Új spec felvétele |
| PUT | `/api/admin/circuit/electrical-specs/{id}` | `circuit:manage` | Spec módosítása |
| DELETE | `/api/admin/circuit/electrical-specs/{id}` | `circuit:manage` | Törlés |

### ArduinoCompilerService részletes logika

```
POST /api/circuit/{missionId}/compile

1. JWT → cadet azonosítás
2. Rate limiting: max 10 compile/perc/cadet (egyszerű in-memory counter)
3. CadetCircuitSave.giteaRepoUrl alapján: kód lekérése Giteából
   (GiteaService.getFileContent(repoUrl, "sketch/sketch.ino"))
4. Temp könyvtár létrehozása: /tmp/compile-{UUID}/sketch/sketch.ino
5. Arduino CLI futtatása (külön Docker image-ben):
   arduino-cli compile --fqbn {boardFqbn} --output-dir /tmp/compile-{UUID}/out /tmp/compile-{UUID}/sketch
6. Sikeres esetén: .hex fájl beolvasása → Base64 → response
7. Hiba esetén: stderr visszaadása (compile error szöveg) → CadetCircuitSave.lastCompileError frissítve
8. Cleanup: temp könyvtár törlése (finally blokkban)

Response:
{
  "success": true,
  "hexBase64": "...",
  "boardType": "ARDUINO_UNO",
  "sizeBytes": 2048,
  "memoryUsed": { "flash": "6%", "sram": "3%" }
}
```

**Board FQBN mapping:**
```java
private static final Map<BoardType, String> FQBN_MAP = Map.of(
    BoardType.ARDUINO_UNO,       "arduino:avr:uno",
    BoardType.ARDUINO_MEGA_2560, "arduino:avr:mega:cpu=atmega2560",
    BoardType.ESP8266,           "esp8266:esp8266:nodemcuv2",
    BoardType.ESP32,             "esp32:esp32:esp32"
);
```

### CircuitSaveService logika (kód mentés Giteába)

```
POST /api/circuit/{missionId}/save
Body: { "code": "void setup() {...}", "components": [...], "connections": [...] }

1. JWT → cadet azonosítás
2. CadetCircuitSave betöltése vagy létrehozása
3. Ha új save (első mentés):
   → GiteaService.createEmptyRepository("circuit-{missionId}-{cadetId}")
   → GiteaService.addCollaborator(repoName, cadet.getGiteaUsername(), "write")
   → CadetCircuitSave.giteaRepoUrl = repo URL
4. Kód feltöltése Giteába:
   → GiteaService.uploadFile(repoName, "sketch/sketch.ino", body.getCode())
5. Komponensek és összeköttetések mentése DB-be:
   → Régi CadetCircuitComponent + CadetCircuitConnection sorok törlése (orphanRemoval)
   → Új sorok létrehozása, irány normalizálással
6. CadetCircuitSave.lastStatus = NEVER_RUN (ha kód változott)
```

### CircuitVerificationService logika

**Kétfázisú verifikáció — külön endpointok:**

```
Fázis 1 — CSAK topológia (szimuláció nélkül, azonnali):
POST /api/circuit/{missionId}/verify/topology

Fázis 2 — Viselkedés (szimuláció után):
POST /api/circuit/{missionId}/verify/behavior
Body: {
  "simulationLog": [
    { "t": 0,    "pin": "D13", "value": 0 },
    { "t": 500,  "pin": "D13", "value": 1 },
    { "t": 1000, "pin": "D13", "value": 0 }
  ],
  "serialOutput": "Hello World\n",
  "durationMs": 16000
}
```

> Ha `allowSchematicEdit=false`, a topology fázis ki van hagyva (a sablon kapcsolatai mindig helyesek).

**Alapelv: 1 DB query, aztán minden memóriában**

A verifikáció teljes folyamata egyetlen `@EntityGraph` lekérdezéssel indul, majd az összes ellenőrzés kizárólag memóriában zajlik — nincs több DB hívás check-enként.

```java
// CircuitVerificationService.java

public VerificationResult verify(UUID cadetId, UUID missionId, SimulationLog log) {

    // ── 1. Egyetlen DB query — EntityGraph mindent betölt ──────────────────
    CadetCircuitSave save = saveRepo
        .findByCadetAndMissionWithAll(cadetId, missionId)
        .orElseThrow(...);

    CircuitDefinition definition = definitionRepo
        .findByMissionIdWithAll(missionId)
        .orElseThrow(...);
    // Ezután nincs több DB hívás a verifikáció során.

    // ── 2. Memória-struktúrák felépítése ───────────────────────────────────

    // Label → UUID map (az összes cadet komponens egyszerre)
    Map<String, UUID> labelToId = save.getComponents().stream()
        .collect(toMap(CadetCircuitComponent::getLabel, c -> c.getId()));

    // Normalizált connection HashSet — O(1) lookup
    record ConnKey(UUID fromId, String fromPin, UUID toId, String toPin) {}
    Set<ConnKey> connectionSet = save.getConnections().stream()
        .map(c -> new ConnKey(
            c.getFromComponent().getId(), c.getFromPin(),
            c.getToComponent().getId(),   c.getToPin()))
        .collect(toSet());

    // Szomszédossági lista BFS-hez (mindkét irány, mert a kapcsolat szimmetrikus)
    Map<UUID, List<PinRef>> adjacency = buildAdjacencyList(save.getConnections());

    // ── 3. Összes check memóriában ─────────────────────────────────────────
    List<CheckResult> results = new ArrayList<>();

    for (CircuitVerificationCheck check : definition.getVerificationChecks()) {

        // Ha a kadét nem szerkeszthette a kapcsolást (allowSchematicEdit=false),
        // a topology checkek ki vannak hagyva — a sablon másolata garantáltan helyes.
        if (!definition.getAllowSchematicEdit()
                && (check.getCheckType() == CIRCUIT_TOPOLOGY
                    || check.getCheckType() == PATH_EXISTS)) {
            results.add(CheckResult.skipped(check, "circuit.info.topology_skipped_readonly"));
            continue;
        }

        CheckResult result = switch (check.getCheckType()) {

            case CIRCUIT_TOPOLOGY -> checkTopology(
                check, labelToId, connectionSet          // O(1) per connection
            );

            case PATH_EXISTS -> checkPath(
                check, labelToId, adjacency              // O(V+E) BFS, memóriában
            );

            case GPIO_BEHAVIOR -> checkGpioBehavior(
                check, log.getEvents()                   // idősor elemzés
            );

            case SERIAL_OUTPUT -> checkSerialOutput(
                check, log.getSerialOutput()             // regex/contains
            );

            case PWM -> checkPwm(
                check, log.getEvents()                   // duty cycle számítás
            );
        };
        results.add(result);
    }

    // ── 4. Eredmények perzisztálása — 1 batch insert ──────────────────────
    saveVerificationResults(save, results);           // batch save, nem loop
    updateSaveStatus(save, results);
    if (allPassed(results)) createMissionResult(save);

    return new VerificationResult(results);
}
```

**Topology check — O(1) HashSet lookup:**
```java
private CheckResult checkTopology(CircuitVerificationCheck check,
                                   Map<String, UUID> labelToId,
                                   Set<ConnKey> connectionSet) {
    var params = parseTopologyParams(check.getParametersJson());

    for (var expected : params.connections()) {
        UUID idA = labelToId.get(expected.fromLabel());
        UUID idB = labelToId.get(expected.toLabel());

        if (idA == null || idB == null) {
            return CheckResult.failed(check, "circuit.error.component_not_found");
        }

        // Normalizálás — kisebb UUID = from
        boolean aIsFrom = idA.compareTo(idB) <= 0;
        var key = new ConnKey(
            aIsFrom ? idA : idB,  aIsFrom ? expected.fromPin() : expected.toPin(),
            aIsFrom ? idB : idA,  aIsFrom ? expected.toPin()   : expected.fromPin()
        );

        if (!connectionSet.contains(key)) {
            return CheckResult.failed(check, "circuit.error.connection_missing",
                expected.fromLabel(), expected.fromPin(),
                expected.toLabel(),   expected.toPin());
        }
    }
    return CheckResult.passed(check);
}
```

**BFS path check — O(V+E), memóriában:**
```java
private CheckResult checkPath(CircuitVerificationCheck check,
                               Map<String, UUID> labelToId,
                               Map<UUID, List<PinRef>> adjacency) {
    var params = parsePathParams(check.getParametersJson());
    UUID startId = labelToId.get(params.fromLabel());
    UUID endId   = labelToId.get(params.toLabel());

    Set<UUID> visited = new HashSet<>();
    Deque<UUID> queue = new ArrayDeque<>();
    queue.add(startId);

    while (!queue.isEmpty()) {
        UUID current = queue.poll();
        if (current.equals(endId)) return CheckResult.passed(check);
        if (!visited.add(current)) continue;

        adjacency.getOrDefault(current, List.of())
                 .stream()
                 .filter(n -> meetsRequirements(n, params.mustPassThrough()))
                 .forEach(n -> queue.add(n.componentId()));
    }
    return CheckResult.failed(check, "circuit.error.path_not_found");
}
```

**Query összehasonlítás:**

| | Régi megközelítés | Új megközelítés |
|---|---|---|
| DB query-k száma | 2 + (N check × 3) | **2 összesen** |
| 10 connection check | ~32 query | **2 query** |
| Connection check sebesség | O(1) DB round-trip | **O(1) HashSet** |
| Path check sebesség | N×M query | **O(V+E) memóriában** |
| Unit tesztelhetőség | Mock repository szükséges | **Csak adatstruktúra kell** |

### @EntityGraph — repository metódusok

```java
// CircuitDefinitionRepository.java
@EntityGraph(attributePaths = {
    "components",
    "components.properties",
    "components.properties.unitOfMeasure",
    "connections",
    "connections.fromComponent",
    "connections.toComponent",
    "verificationChecks"
})
@Query("SELECT cd FROM CircuitDefinition cd WHERE cd.mission.id = :missionId")
Optional<CircuitDefinition> findByMissionIdWithAll(@Param("missionId") UUID missionId);
```

```java
// CadetCircuitSaveRepository.java
@EntityGraph(attributePaths = {
    "components",
    "components.properties",
    "components.properties.unitOfMeasure",
    "components.templateComponent",
    "connections",
    "connections.fromComponent",
    "connections.toComponent",
    "verificationResults",
    "verificationResults.check"
})
@Query("SELECT s FROM CadetCircuitSave s WHERE s.cadet.id = :cadetId AND s.mission.id = :missionId")
Optional<CadetCircuitSave> findByCadetAndMissionWithAll(
    @Param("cadetId") UUID cadetId, @Param("missionId") UUID missionId);
```

> `ComponentPinDefinition` és `ComponentElectricalSpec` ritkán változnak → `@Cacheable` annotáció, Spring Cache-ben tárolva. Ezek betöltése nem kell minden verifikációhoz.

### i18n stratégia

```
Backend:  GlobalExceptionHandler visszaad i18n kulcsot a hibákhoz
          (pl. "circuit.error.pin.not_found"), NEM fordított szöveget.
          A ComponentElectricalSpec.warningMessage és educationalExplanation
          mezők i18n kulcsokat tárolnak (pl. "circuit.warning.led_no_resistor"),
          nem nyers szöveget.

Frontend: i18n fordítástábla (hu.json / en.json) tartalmazza a tényleges szövegeket.
          A backend által visszaadott kulcsokat a frontend fordítja le.

Példa:
  Backend response: { "warningKey": "circuit.warning.led_no_resistor" }
  hu.json: { "circuit.warning.led_no_resistor": "Az LED közvetlenül tápra kötve meghibásodik. Kötj be soros ellenállást!" }
  en.json: { "circuit.warning.led_no_resistor": "LED connected directly to power will burn out. Add a series resistor!" }
```

---

## V. Frontend Architektúra

### 5.1 Oldalak és komponenshierarchia

```
CircuitSimPage.tsx            ← fő oldal (CIRCUIT_SIMULATION mission-höz)
├── CircuitHeader.tsx         ← mission neve, board típus, status badge
├── CircuitWorkspace.tsx      ← a fő munkaterület
│   ├── ComponentPalette.tsx  ← bal oldal: drag-and-drop komponensek listája
│   ├── BreadboardCanvas.tsx  ← középső: interaktív breadboard + React Flow
│   │   ├── BoardNode.tsx     ← az Arduino/ESP/Pi node (wokwi-arduino-uno stb.)
│   │   ├── ComponentNode.tsx ← egyéb komponensek (LED, szenzor, stb.)
│   │   └── WireEdge.tsx      ← kábelek (colored edge, animated)
│   └── CodePanel.tsx         ← jobb oldal: Monaco Editor + Serial Monitor
│       ├── MonacoEditor      ← (már meglévő)
│       └── SerialMonitor.tsx ← UART kimenet textarea
├── SimulationControls.tsx    ← Play/Pause/Stop/Reset gombok, sebesség slider
└── VerificationPanel.tsx     ← check lista, eredmények, Submit gomb
```

### 5.2 State Management

**React Context: `CircuitSimContext`**

```typescript
interface CircuitSimState {
  // Definíció (szerverről betöltve)
  definition: CircuitDefinition;
  boardType: BoardType;

  // Cadet munkája
  components: ComponentPlacement[];
  connections: Connection[];
  userCode: string;

  // Szimuláció
  simulationStatus: SimulationStatus;
  simulationSpeed: number; // 1x, 2x, 4x, 0.5x
  simulationLog: SimulationEvent[];

  // Fordítás
  compileStatus: 'idle' | 'compiling' | 'success' | 'error';
  compileError: string | null;
  hexData: Uint16Array | null;

  // Verifikáció
  verificationResult: VerificationResult | null;
}
```

### 5.3 Szimulációs motor (Web Worker)

```typescript
// circuit-worker.ts — Web Worker, nem blokkolja a UI-t
import { CPU, AVRIOPort, AVRUSART, portBConfig, portCConfig, portDConfig, usart0Config } from 'avr8js';

let cpu: CPU;
let running = false;

self.onmessage = (e: MessageEvent<WorkerMessage>) => {
  switch (e.data.type) {
    case 'LOAD_HEX':
      cpu = new CPU(new Uint16Array(e.data.hexData));
      setupPeripherals();
      break;
    case 'START':
      running = true;
      runLoop();
      break;
    case 'PAUSE':
      running = false;
      break;
    case 'RESET':
      // CPU reset
      break;
    case 'PIN_INPUT':
      // Külső esemény: pl. nyomógomb lenyomása a UI-ról
      // → ADC vagy digitális pin érték beállítása
      break;
  }
};

function setupPeripherals() {
  // GPIO portok
  const portB = new AVRIOPort(cpu, portBConfig);
  portB.addListener(() => {
    // Pin változás → üzenet a UI thread-nek
    self.postMessage({ type: 'PIN_CHANGE', port: 'B', value: portB.portValue });
  });

  // UART / Serial
  const usart = new AVRUSART(cpu, usart0Config, 16e6);
  usart.onByteTransmit = (byte: number) => {
    self.postMessage({ type: 'SERIAL_OUTPUT', char: String.fromCharCode(byte) });
  };
}

function runLoop() {
  const CYCLES_PER_FRAME = 160000; // ~10ms / frame @ 16MHz
  function frame() {
    if (!running) return;
    for (let i = 0; i < CYCLES_PER_FRAME; i++) {
      cpu.tick();
    }
    self.postMessage({ type: 'FRAME_DONE', clockCycles: cpu.cycles });
    requestAnimationFrame(frame); // Web Worker-ben: setTimeout-tal helyettesíteni
  }
  setTimeout(frame, 0);
}
```

### 5.4 GPIO ↔ Komponens híd

```typescript
// CircuitBridge.ts — összeköti az avr8js pin-állapotokat a vizuális komponensekkel
class CircuitBridge {
  private pinToComponent: Map<string, ComponentRef[]>;

  constructor(connections: Connection[]) {
    this.buildMap(connections);
  }

  onPinChange(port: string, portValue: number) {
    // pl. port='B', portValue=0b00100000 → D13 (PB5) HIGH
    const pinStates = this.decodePorts(port, portValue);
    pinStates.forEach(({ pinName, isHigh }) => {
      const components = this.pinToComponent.get(pinName) ?? [];
      components.forEach(comp => comp.setState(isHigh));
    });
  }

  onButtonPress(componentId: string) {
    // UI esemény → Worker-nek küldött PIN_INPUT üzenet
    const pin = this.componentToPin.get(componentId);
    worker.postMessage({ type: 'PIN_INPUT', pin, value: true });
  }
}
```

### 5.5 Szenzor szimulációs stratégiák

| Szenzor | Szimuláció módja |
|---|---|
| **DHT11/DHT22** | Worker-ben: időzített single-wire protokoll emuláció; UI-n slider a hőmérséklethez/páratartalomhoz |
| **DS18B20** | OneWire protokoll emuláció; hőmérséklet UI-n beállítható |
| **HC-SR04** | TRIG pin figyelése → delay után ECHO pin HIGH → Low; távolság UI-n slider |
| **LDR** | ADC értéket módosít; fényszint UI-n slider |
| **PIR** | Digitális kimenet, UI-n toggle |
| **Potenciométer** | ADC értéket módosít (0–1023 Arduino Uno-n); UI-n drag slider |

### 5.6 ESP8266/ESP32 mock szimuláció (P2 fázis)

Az ESP chipek XTENSA architektúrán futnak, amihez nincs nyílt WASM emulátor. Ezért:

**Megközelítés: Szintaktikai + Viselkedési mock**

```typescript
// EspMockRuntime.ts
// Nem AVR bytecode-ot futtat, hanem a C++ kód string-jét parszolja AST-szinten
// és egy szimplifikált state machine-t futtat

class EspMockRuntime {
  private pinStates: Map<string, boolean> = new Map();
  private pwmDuty: Map<string, number> = new Map();

  // A kódot egy egyszerűsített interpreter értelmezi:
  // - digitalRead/Write → state machine
  // - delay → timer
  // - Serial.println → output stream
  // - WiFi → mock AP/STA
  // Korlátok: nem futtat tetszőleges C++ kódot, csak az ismert API hívásokat értelmezi
}
```

**Kompromisszum:** ESP missziók esetén a backend Arduino CLI-vel forditja az ESP kódot, az elvárt viselkedést pedig pattern-matching alapján verifikálja (nem igazi emuláció).

### 5.7 Raspberry Pi szimuláció (P3 fázis)

**Architektúra: Backend Python szandbox**

```
Frontend (Monaco Editor - Python kód)
    ↓ POST /api/circuit/{missionId}/pi/run
Backend (Spring Boot)
    ↓ Docker exec
Python szandbox konténer (per-session, TTL 10 perc)
    ├── gpiozero (MockFactory)
    ├── RPi.GPIO mock
    ├── smbus2 mock (I2C)
    └── legymernok-gpio-bridge.py (WebSocket pin state sender)
    ↓ WebSocket STOMP
Frontend (GPIO vizualizáció)
```

**legymernok-gpio-bridge.py (saját mini könyvtár):**
```python
import websocket
import json
from gpiozero import Device, LED, Button
from gpiozero.pins.mock import MockFactory

Device.pin_factory = MockFactory()

# Monkey-patch: pin változáskor WS üzenet küldése
_orig_drive_high = MockFactory.pin.drive_high
def patched_drive_high(self):
    _orig_drive_high(self)
    ws_client.send(json.dumps({"type": "PIN_CHANGE", "pin": self.number, "value": True}))
```

---

## VI. Saját könyvtár: `@legymernok/circuit-engine`

A projekt igényeire szabott szimulátor magkönyvtár, amelyet a frontend importál. Ez a rész amit teljes mértékben magunk írunk.

### Struktúra

```
frontend/src/lib/circuit-engine/
├── index.ts                    ← public API
├── types/
│   ├── CircuitDefinition.ts    ← összes TS interfész
│   ├── ComponentType.ts        ← enum
│   └── VerificationConfig.ts
├── worker/
│   ├── circuit-worker.ts       ← Web Worker entry point
│   ├── avr-bridge.ts           ← avr8js integráció
│   └── sensor-emulators/
│       ├── DhtEmulator.ts      ← DHT11/22 protokoll
│       ├── UltrasonicEmulator.ts ← HC-SR04
│       └── OneWireEmulator.ts  ← DS18B20
├── canvas/
│   ├── BreadboardLayout.ts     ← breadboard lyuk-koordináta számítás
│   ├── ConnectionRouter.ts     ← kábel útvonal görbék (d3-path)
│   └── PinResolver.ts          ← fizikai lábkiosztás (pin → port bit)
├── verification/
│   ├── SimulationRecorder.ts   ← pin változások naplózása
│   ├── GpioBehaviorChecker.ts  ← BLINK, PWM frekvencia stb.
│   └── SerialOutputChecker.ts  ← UART kimenet ellenőrzés
└── constants/
    ├── boards/
    │   ├── ArduinoUno.ts       ← pinout, FQBN, memória
    │   ├── ArduinoMega.ts
    │   └── Esp32.ts
    └── components/
        ├── LedSpec.ts          ← LED fizikai paraméterek
        └── ResistorSpec.ts
```

### PinResolver — kulcsfontosságú belső osztály

```typescript
// A fizikai pin neveket (D13, A0, SCL) leképezi AVR port bit-ekre
// Forráslap: Arduino Uno schematic

const UNO_PIN_MAP: Record<string, { port: 'B'|'C'|'D', bit: number }> = {
  'D0':  { port: 'D', bit: 0 },  // RX
  'D1':  { port: 'D', bit: 1 },  // TX
  'D2':  { port: 'D', bit: 2 },
  // ...
  'D13': { port: 'B', bit: 5 },  // Built-in LED
  'A0':  { port: 'C', bit: 0 },
  'A1':  { port: 'C', bit: 1 },
  // ...
  'SDA': { port: 'C', bit: 4 },
  'SCL': { port: 'C', bit: 5 },
};

const MEGA_PIN_MAP: Record<string, { port: string, bit: number }> = {
  // ATmega2560 — 70+ digitális, 16 analóg pin
  'D13': { port: 'B', bit: 7 },
  // ...
};
```

### BreadboardLayout — valóságos breadboard snap-to-grid

A szimulátor a valóságos breadboard fizikai elrendezését tükrözi pontosan.

**Szabványos half-size breadboard (400 lyuk):**
```
Felső power rail:  [ + + + + + ... + ]  (25 lyuk, felosztott középen)
                   [ - - - - - ... - ]  (25 lyuk, felosztott középen)
                   ─────────────────────
Sorok 1–30:        a b c d e | f g h i j  (oszloponként 10 lyuk, középső árok = nincs kapcsolat)
                   ─────────────────────
Alsó power rail:   [ - - - - - ... - ]
                   [ + + + + + ... + ]
```

**Fizikai méretek (2.54mm / 0.1 inch rács):**
```typescript
const HOLE_PITCH_MM = 2.54;       // lyukak közötti távolság mm-ben
const HOLE_PITCH_PX = 19;         // képernyőn pixelben (skálázható)
const ROWS = ['a','b','c','d','e','f','g','h','i','j'];
const COLS = 30;                  // half-size breadboard: 30 oszlop
const TRENCH_GAP_PX = 10;        // az árok vizuális rése e és f között

interface BreadboardHole {
  col: number;        // 1–30
  row: string;        // 'a'–'e' | 'f'–'j' | 'power_top_plus' | 'power_top_minus' | stb.
  x: number;          // pixel koordináta (canvas-on)
  y: number;
  netId: string;      // összekötött lyukak hálózat azonosítója
}
```

**netId szabályok:**
```
col=5, row='a' ... col=5, row='e'  → azonos net: "col_5_top"
col=5, row='f' ... col=5, row='j'  → azonos net: "col_5_bot"  (árok elválaszt!)
power rail felső +, bal fele (1–15) → net: "pwr_top_plus_left"
power rail felső +, jobb fele (16–30) → net: "pwr_top_plus_right"
```

**Komponens snap logika — pin pozíció a lyukakhoz:**
```typescript
// Minden ComponentType-hoz definiált a lábak egymástól való távolsága lyukakban
const COMPONENT_FOOTPRINT: Record<ComponentType, ComponentFootprint> = {
  RESISTOR: {
    pins: [
      { pinName: 'A', rowOffset: 0, colOffset: 0 },
      { pinName: 'B', rowOffset: 0, colOffset: 5 },  // 5 lyuk távolság = 12.7mm (valós méret)
    ],
    orientation: 'HORIZONTAL'
  },
  LED: {
    pins: [
      { pinName: 'anode',   rowOffset: 0, colOffset: 0 },
      { pinName: 'cathode', rowOffset: 0, colOffset: 2 },  // 2 lyuk = 5.08mm
    ],
    orientation: 'VERTICAL_OR_HORIZONTAL'
  },
  DHT22: {
    pins: [
      { pinName: 'VCC',  rowOffset: 0, colOffset: 0 },
      { pinName: 'DATA', rowOffset: 0, colOffset: 1 },
      { pinName: 'NC',   rowOffset: 0, colOffset: 2 },  // nem bekötött láb
      { pinName: 'GND',  rowOffset: 0, colOffset: 3 },
    ],
    orientation: 'HORIZONTAL'
  },
  // ...
};

// Drag-and-drop snap: a komponens "A" vagy "anode" lábát a legközelebbi
// lyukra snappeli; a többi láb automatikusan a footprint offset szerint pozicionálódik.
function snapToGrid(dragX: number, dragY: number, componentType: ComponentType): SnapResult {
  const nearestHole = findNearestHole(dragX, dragY);
  const footprint = COMPONENT_FOOTPRINT[componentType];
  return calculatePinPositions(nearestHole, footprint);
}
```

**Érvénytelen elhelyezés detektálás:**
- Ha egy láb az árkon (trench) esik át → piros jelzés, nem engedélyezett
- Ha két komponens lába ugyanarra a lyukra kerülne → ütközés jelzés
- Ha a lab a breadboard határán kívülre kerül → nem engedélyezett

### SimulationRecorder

```typescript
class SimulationRecorder {
  private log: SimulationEvent[] = [];
  private startTime: number;

  recordPinChange(pin: string, value: boolean | number) {
    this.log.push({
      t: Date.now() - this.startTime,
      pin,
      value: value ? 1 : 0
    });
  }

  recordSerial(text: string) {
    this.log.push({ t: Date.now() - this.startTime, type: 'SERIAL', text });
  }

  export(): SimulationLog {
    return { events: this.log, durationMs: Date.now() - this.startTime };
  }
}
```

### CircuitHistoryManager — Undo/Redo (max 20 lépés)

```typescript
// A CircuitSimContext useReducer history stack-kel kiegészítve

const MAX_HISTORY = 20;

interface CircuitHistory {
  past: CircuitEditorState[];    // max 20 elem
  present: CircuitEditorState;
  future: CircuitEditorState[];
}

// Minden szerkesztési művelet (komponens hozzáad/töröl/mozgat, kábel húz/töröl)
// új bejegyzést rak a past-ba és törli a future-t.
// Undo: present → future elejére, past vége → present
// Redo: present → past végére, future eleje → present

type CircuitEditorAction =
  | { type: 'ADD_COMPONENT';    payload: CadetCircuitComponent }
  | { type: 'REMOVE_COMPONENT'; payload: string }           // componentId
  | { type: 'MOVE_COMPONENT';   payload: { id: string; x: number; y: number } }
  | { type: 'ADD_CONNECTION';   payload: CadetCircuitConnection }
  | { type: 'REMOVE_CONNECTION'; payload: string }          // connectionId
  | { type: 'UNDO' }
  | { type: 'REDO' };

// Billentyűkötések: Ctrl+Z → UNDO, Ctrl+Y / Ctrl+Shift+Z → REDO
// Szimuláció futása közben az undo/redo disabled (csak leállítás után)
```

---

## VII. Admin Felület — Circuit Mission Szerkesztő

Az adminisztrátorok (és missziót készítő kadétok) egy dedikált szerkesztőn keresztül hozzák létre a CIRCUIT_SIMULATION missziókat.

### CircuitMissionEdit.tsx (admin oldal)

```
CircuitMissionEdit.tsx
├── Bal panel: Alap mission adatok (name, description, difficulty — már meglév layout)
├── Közép panel: CircuitEditor (ugyanaz a canvas, de admin módban)
│   ├── Komponens palette (drag-drop)
│   ├── "Lock" mód: admin zárolhatja a komponenseket (cadet nem mozdíthatja)
│   └── "Expected" mód: admin rajzolja be a helyes megoldást (referenciaként)
└── Jobb panel: Verification Config szerkesztő
    ├── Board type selector
    ├── Check lista (add/remove/edit)
    │   ├── GPIO_BEHAVIOR check
    │   ├── SERIAL_OUTPUT check
    │   └── PWM check
    └── "Test Run" gomb (admin lefuttatja a saját megoldással)
```

---

## VIII. Gitea Template Repók

### 8.1 `mission-circuit-template` — Digitális (Arduino/ESP) misszió sablon

Ez a repó az admin (`legymernok_admin`) fiókjában él, és minden új digitális circuit misszió létrehozásakor ebből másolja a tartalmát a `GiteaService.copyRepositoryContents()`.

**Repó struktúra:**
```
mission-circuit-template/
├── sketch/
│   └── sketch.ino          ← üres Arduino vázlat, alapkommentekkel
└── README.md               ← automatikusan felülíródik a misszió adataival
```

**`sketch/sketch.ino` tartalma:**
```cpp
/*
 * LégyMérnök.hu — Circuit Mission
 * Mission ID: {MISSION_ID}
 *
 * Feladat: Lásd a misszió leírását a platformon.
 * Board: {BOARD_TYPE}
 */

void setup() {
  Serial.begin(9600);
  // TODO: inicializáld a pineket itt
}

void loop() {
  // TODO: írd ide a megoldásod
}
```

**`README.md` tartalma:**
```markdown
# {MISSION_NAME}

**Misszió azonosító:** `{MISSION_ID}`
**Board:** {BOARD_TYPE}
**Nehézség:** {DIFFICULTY}

## Feladat leírása

{MISSION_DESCRIPTION}

## Hogyan dolgozz?

1. Nyisd meg a missziót a LégyMérnök platformon
2. A breadboard szerkesztőben rakd össze a kapcsolást
3. A kódszerkesztőben írd meg az Arduino kódot
4. Kattints a ▶ Futtatás gombra a szimuláció indításához
5. Ha minden ellenőrzés zöld, add be a megoldást

## Hasznos linkek

- [Arduino referencia](https://www.arduino.cc/reference/en/)
- [LégyMérnök platform](https://legymernok.hu)
```

---

### 8.2 `mission-analog-template` — Analóg (CircuitJS1) misszió sablon

```
mission-analog-template/
└── README.md     ← misszió neve + leírás + analóg feladat útmutató
```

**`README.md` tartalma:**
```markdown
# {MISSION_NAME}

**Típus:** Analóg áramköri szimuláció
**Nehézség:** {DIFFICULTY}

## Feladat leírása

{MISSION_DESCRIPTION}

## Hogyan dolgozz?

1. Nyisd meg a missziót a LégyMérnök platformon
2. Az analóg szimulátor szerkesztőjében módosítsd a kapcsolást
3. A szimulátor valós időben mutatja a feszültséget és áramot
4. Ha minden mérési feltétel teljesül, add be a megoldást

## Fizikai háttér

Az analóg elektronika alapjai: Ohm-törvény (U = I × R),
Kirchhoff törvényei, RC szűrők, félvezető komponensek.
```

---

### 8.3 `mission-cpp-template` — C++ alapok misszió sablon

A beágyazott rendszerek programozása (Arduino C++) előtt a kadétok C++ alapokat tanulnak egy külön `CPP_CODING` típusú misszióban. Ez a template a meglévő kódolós missziók mintájára épül (Gitea repo + CI), de C++ környezetben fut.

**Új MissionType enum érték:**
```java
public enum MissionType {
    CODING,               // JavaScript / Python (meglévő)
    QUIZ,                 // kvíz (meglévő)
    CIRCUIT_SIMULATION,   // beágyazott szimuláció (új)
    CPP_CODING            // C++ alapok — előfeltétel CIRCUIT_SIMULATION előtt
}
```

**Gitea repó struktúra:**
```
mission-cpp-template/
├── .gitea/
│   └── workflows/
│       └── ci.yml          ← g++ fordítás + tesztek futtatása
├── src/
│   └── solution.cpp        ← a kadét megoldása (üres)
├── tests/
│   └── solution_test.cpp   ← Google Test alapú tesztek (admin írja)
├── CMakeLists.txt          ← CMake build konfig
└── README.md
```

**`ci.yml` tartalma:**
```yaml
name: C++ Mission CI
on: [push]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Install dependencies
        run: sudo apt-get install -y cmake g++ libgtest-dev
      - name: Build
        run: cmake -B build && cmake --build build
      - name: Test
        run: ./build/solution_test
      - name: Report result
        run: |
          STATUS=${{ job.status == 'success' && 'SUCCESS' || 'FAILED' }}
          curl -X POST ${{ secrets.BACKEND_URL }}/api/mission-verification/${{ github.event.repository.name }}/callback \
            -H "X-Secret: ${{ secrets.MISSION_VERIFICATION_SECRET }}" \
            -d "{\"status\": \"$STATUS\"}"
```

**`CMakeLists.txt` tartalma:**
```cmake
cmake_minimum_required(VERSION 3.14)
project(solution)
set(CMAKE_CXX_STANDARD 17)

enable_testing()
find_package(GTest REQUIRED)

add_executable(solution_test tests/solution_test.cpp src/solution.cpp)
target_link_libraries(solution_test GTest::gtest_main)
add_test(NAME SolutionTest COMMAND solution_test)
```

> A `CPP_CODING` misszió teljesen független a `CIRCUIT_SIMULATION`-tól — a meglévő Gitea CI pipeline-t használja (ugyanolyan callback mechanizmus mint a JS/Python missziók). Csak a fordítási környezet különbözik (g++ helyett Node/Python).

---

### 8.4 Mission Forge integráció

A Mission Forge-ban (kadétok által készített missziók) a `CIRCUIT_SIMULATION` típus:

1. **Inicializáláskor:** `CircuitDefinition` üres sablonnal jön létre (üres breadboard, alap kódtemplate) — `mission-circuit-template`-ből másolva
2. **Szerkesztéskor:** A `ForgeEditor.tsx`-ben egy új tab: "Circuit" — a CircuitEditor megnyílik
3. **Verifikációs konfig:** Egyszerűsített UI a check-ek beállításához
4. **Kód tárolás:** A Gitea repóban a `sketch/sketch.ino` — konzisztens a sablon struktúrával

---

## IX. Megvalósítási Fázisok

### Fázis 1 — Analóg alap (CircuitJS1 integráció)
**Cél:** Gyors eredmény, analóg fizika oktatás azonnal elérhető
- [ ] CircuitJS1 self-hosting (statikus build a Docker image-ben)
- [ ] `CircuitSimPage.tsx` → `<iframe>` + postMessage wrapper
- [ ] CircuitDefinition adatmodell + migráció (csak `falstadCircuitText` mező)
- [ ] Admin: circuit text szerkesztő + mentés
- [ ] Verifikáció: Falstad circuit state exportálás + backend JSON-összehasonlítás
- [ ] Új permission-ök + DataInitializer seed

### Fázis 2 — Arduino szimulálás (avr8js core)
**Cél:** Teljes Arduino Uno + Mega programozás és szimuláció
- [ ] npm: `avr8js`, `@wokwi/elements`, `@xyflow/react`, `comlink` telepítés
- [ ] Arduino CLI Docker image-be integrálás
- [ ] `ArduinoCompilerService.java` + `/api/circuit/{missionId}/compile` endpoint
- [ ] `circuit-worker.ts` Web Worker (avr8js futtatás)
- [ ] `BreadboardCanvas.tsx` alap canvas (React Flow, wokwi-elements)
- [ ] Alapkomponensek: LED, ellenállás, nyomógomb, potenciométer
- [ ] `SerialMonitor.tsx` UART kimenet kijelzés
- [ ] `SimulationRecorder.ts` + `GpioBehaviorChecker.ts`
- [ ] `/api/circuit/{missionId}/verify` backend endpoint
- [ ] `CadetCircuitSave` entitás + mentés endpoint
- [ ] Arduino Mega 2560 pinout + FQBN támogatás

### Fázis 3 — Bővített komponensek
**Cél:** Haladó szenzorok és kijelzők a gazdag oktatási tartalomhoz
- [ ] DHT11/22 emulátor (single-wire protokoll)
- [ ] DS18B20 emulátor (OneWire)
- [ ] HC-SR04 ultrahang emulátor
- [ ] I2C OLED (SSD1306) vizualizáció
- [ ] Szervómotor vizualizáció (SVG forgó kar)
- [ ] I2C 16x2 LCD vizualizáció
- [ ] Admin Circuit Mission szerkesztő (`CircuitMissionEdit.tsx`)
- [ ] Mission Forge integráció (circuit.json Gitea-ban)

### Fázis 4 — ESP8266/ESP32 mock
**Cél:** WiFi/IoT oktatás alapjai
- [ ] `EspMockRuntime.ts` state machine interpreter
- [ ] ESP8266/ESP32 board vizualizáció (`@wokwi/elements` - NodeMCU elem)
- [ ] WiFi mock (AP, STA, HTTP kliens állapot vizualizáció)
- [ ] ESP32 dual-core mock (alapszintű)
- [ ] Arduino CLI ESP board support integrálás (`esp8266:esp8266:nodemcuv2`, `esp32:esp32:esp32`)

### Fázis 5 — Raspberry Pi szandbox
**Cél:** Python/Linux embedded oktatás
- [ ] Python szandbox Docker image (gpiozero + mock + legymernok-bridge)
- [ ] Per-session konténer lifecycle management (Spring Cloud Gateway? vagy egyszerű ProcessBuilder)
- [ ] WebSocket (STOMP — már kész) alapú valós idejű GPIO stream
- [ ] Raspberry Pi GPIO board vizualizáció (40-pin header SVG)
- [ ] `RPiSimPage.tsx` (külön oldal, más UX)

---

## X. Biztonsági szempontok

### Arduino CLI sandbox
- Futtatás Docker konténerben (ne host-on)
- Kód méretlimit: max 50KB
- CPU timeout: 30 másodperc (ProcessBuilder `waitFor(30, SECONDS)`)
- Temp könyvtárak: UUID-alapú, `finally` blokkban cleanup
- **Veszélyes Arduino library-k blacklist:** wifi, ethernet, SD (nem kell szimulátorban)

### Python szandbox (Pi)
- Docker `--network none` (nincs hálózat)
- `--memory 128m --cpus 0.5` resource limit
- Konténer TTL: 10 perc, majd `docker stop` + `rm`
- Nem írhat fájlrendszerre (read-only root, csak `/tmp`)
- `seccomp` profil: felesleges syscall-ok tiltása

### Frontend
- A `.hex` fájl a browser sandbox-ában fut (avr8js), nincs natív kód végrehajtás
- Circuit JSON input validálás: ismert ComponentType értékek whitelist-je
- Compiled hex méretlimit: max 256KB (ATmega2560 flash mérete)

---

## XI. Tesztelési stratégia

### Backend unit tesztek (JUnit 5 / Mockito)
```java
// CircuitVerificationServiceTest.java
@Test void blinkDetection_1Hz_success() {
    var log = List.of(
        new SimEvent(0, "D13", 0),
        new SimEvent(500, "D13", 1),
        new SimEvent(1000, "D13", 0),
        new SimEvent(1500, "D13", 1)
    );
    var result = service.checkGpioBehavior(log, "D13", "BLINK", Map.of("frequencyHz", 1.0));
    assertTrue(result.isPassed());
}
```

### Frontend unit tesztek (Vitest)
```typescript
// circuit-engine/verification/GpioBehaviorChecker.test.ts
it('detects 1Hz blink with 20% tolerance', () => {
  const checker = new GpioBehaviorChecker();
  const result = checker.check(mockLog, { pin: 'D13', expected: 'BLINK', params: { frequencyHz: 1.0 } });
  expect(result.passed).toBe(true);
});
```

### Cypress E2E tesztek
```typescript
// cypress/e2e/circuit_simulation.cy.ts
it('Arduino Uno LED blink mission teljesítése', () => {
  cy.login('cadet1');
  cy.visit('/star-systems/1/missions/arduino-blink');
  cy.get('[data-cy="monaco-editor"]').type(BLINK_SKETCH);
  cy.get('[data-cy="compile-btn"]').click();
  cy.get('[data-cy="compile-status"]').should('contain', 'Sikeres fordítás');
  cy.get('[data-cy="run-btn"]').click();
  cy.get('[data-cy="led-component"]').should('have.class', 'led-on');
  cy.get('[data-cy="verify-btn"]').click();
  cy.get('[data-cy="verification-result"]').should('contain', 'SUCCESS');
});
```

---

## XII. Lezárt döntések (2026-03-18)

| # | Kérdés | Döntés |
|---|---|---|
| 1 | **Analog vs. Digital first** | **Mindkettő** — CircuitJS1 (analóg) és avr8js (digitális) párhuzamosan, Fázis 1-ben CircuitJS1 önállóan is futhat |
| 2 | **Breadboard vs. Schematic nézet** | **Mindkettő** — nézetek között váltható; Schematic nézet AI integráció szempontjából is jobb jövőbeli alap |
| 3 | **ESP real vs. mock** | **Mock** egyelőre; valós XTENSA emuláció távlati jövő, Wokwi fizetős API nem bevállalt |
| 4 | **Arduino kód fordítás helye** | **Backend** — külön Docker image Arduino CLI-vel; biztonságosabb, egyszerűbb |
| 5 | **Gitea CI integráció** | **Nem** — Gitea csak tárolás; verifikáció közvetlenül a `/verify` endpointban, Actions nélkül |
| 6 | **Offline támogatás** | **Nem erőltetjük** — frontend betöltéséhez is kell internet, nincs értelme offline-t erőltetni |
| 7 | **Adatmodell: JSONB vs. normalizált** | **Normalizált táblák** — `circuit_def_components`, `circuit_def_connections`, stb.; nincs nagy JSONB blob |
| 8 | **Component properties** | **Külön tábla** (`circuit_def_component_properties`) — name/key/value/unitOfMeasure sorok |
| 9 | **Összeköttetés irány** | **DB-ben normalizált** — mentés előtt service réteg rendezi (kisebb UUID = from); egyetlen EXISTS query verifikációnál |
| 10 | **Cadet kód tárolás** | **Gitea** — `sketch/sketch.ino` a repo-ban; DB-ben csak a Gitea URL |
| 11 | **Library helye** | **Monorepo-n belül** — `frontend/src/lib/circuit-engine/`; nem külön npm package |
| 12 | **UnitOfMeasure tárolás** | **Külön tábla** (`units_of_measure`) — admin felületről karbantartható; properties tábla FK-val hivatkozik rá, nem szabad szöveg |
| 13 | **Pin definíciók** | **`component_pin_definitions` tábla** — admin által karbantartható; `allowMultipleConnections` flag az I2C/busz kezeléséhez |
| 14 | **Breadboard vs. Schematic pozíció** | **Csak breadboard pozíció tárolva** — schematic nézet pozícióit az ELK.js számolja automatikusan, nem tároljuk |
| 15 | **CircuitDefinition életciklus** | **`IN_WORK` / `PUBLISHED`** státusz; PUBLISHED → IN_WORK visszavonáskor minden `CadetCircuitSave` törlődik (admin confirm popup) |
| 16 | **Analóg adatmodell** | **Teljesen külön** `analog_circuit_definitions` tábla Falstad szövegformátummal; a digitális modellel nem kompatibilis |
| 17 | **Topology check szintjei** | **Kétszintű**: REQUIRED_CONNECTIONS (egyszerű EXISTS lista) és PATH_EXISTS (BFS gráfbejárás `mustPassThrough` feltétellel) |
| 18 | **Elektromos validáció** | **`component_electrical_specs` tábla** — max feszültség, áram, polaritás, rövidzárlat; `ERROR`/`WARNING`/`INFO` severity; oktatási magyarázattal |
| 19 | **Több kapcsolat ugyanarra a pinre** | **`allowMultipleConnections` flag** a `ComponentPinDefinition`-ben — I2C/power rail esetén `true`, normál GPIO-n `false`; frontend blokkolja a második kábelt ha `false` |
| 20 | **Gitea template repók** | **`mission-circuit-template`** (digitális) és **`mission-analog-template`** (analóg) — teljes tartalom specifikálva a VIII. fejezetben |
| 21 | **BoardType a pin definícióban** | `ComponentPinDefinition.boardType` nullable oszlop — csak `BOARD` componentType esetén töltve; `avrPort`+`avrBit` mezők a PinResolver szinkronhoz |
| 22 | **I2C szimuláció mélysége** | Döntés elhalasztva Fázis 3-ra; hajlam a teljes protokoll (A opció) felé, de erőforrásigény felmérés szükséges |
| 23 | **Property öröklés sablon→kadét** | Automatikus másolás "Misszió elkezdése" gombra; admin módosítás csak PUBLISHED→IN_WORK visszavonással lehetséges (minden cadet save törlődik) |
| 24 | **CadetMission életciklus** | Explicit "Misszió elkezdése" gomb → `CadetMission` + `CadetCircuitSave` + Gitea repo egyszerre jön létre |
| 25 | **Undo/Redo** | Max 20 lépés; `useReducer` + history stack; szimuláció futása közben disabled |
| 26 | **N+1 lekérdezés** | `@EntityGraph` a betöltő repository metódusokon; `ComponentPinDefinition`/`ComponentElectricalSpec` `@Cacheable` |
| 27 | **DB séma létrehozás** | JPA `ddl-auto` — entitásokból generálódik (nincs Flyway) |
| 28 | **i18n stratégia** | Backend: i18n kulcsokat ad vissza (nem fordított szöveget); frontend: `hu.json`/`en.json` fordítástáblák; `warningMessage` mező i18n kulcsot tárol |
| 29 | **Breadboard snap-to-grid** | Valóságos 2.54mm rács; komponensenkénti `COMPONENT_FOOTPRINT` lábpozíció definíció; érvénytelen snap (árok-átfedés, ütközés) jelzés |
| 30 | **`verificationWindowMs`** | `CircuitDefinition` entitásban; default 10000ms; időalapú missziókhoz nagyobb érték; frontend 4x gyorsítással futtatja → valós idő = windowMs/4 |
| 31 | **`interruptCapable` flag** | `ComponentPinDefinition`-ben; seed adatokban D2/D3 true (Uno INT0/INT1), D2/D3 true (Mega INT4/INT5); frontend vizuálisan jelzi |
| 32 | **PinResolver API-ból tölt** | `GET /api/circuit/pin-definitions` endpoint; frontend induláskor cache-eli; nincs hardkódolt map; `ComponentElectricalSpec` ugyanígy |
| 33 | **Topology skip** | `allowSchematicEdit=false` esetén `CIRCUIT_TOPOLOGY` és `PATH_EXISTS` checkek automatikusan `SKIPPED` státuszba kerülnek — sablon másolata garantáltan helyes |
| 34 | **DHT22 pull-up validáció** | `REQUIRES_PULLUP_ON_DATA` új `ElectricalValidationType` enum érték + seed sor a DHT22-höz |
| 35 | **`CPP_CODING` MissionType** | Új misszió típus C++ alapok tanulásához; `mission-cpp-template` Gitea repó CMake + Google Test + CI pipeline-nal; előfeltétel CIRCUIT_SIMULATION előtt |

---

## XIII. Könyvtár licensz összefoglaló

| Könyvtár | Licensz | Kereskedelmi? | Megjegyzés |
|---|---|---|---|
| avr8js | MIT | ✅ | Szabad |
| @wokwi/elements | MIT | ✅ | Szabad |
| @xyflow/react | MIT | ✅ | Szabad |
| CircuitJS1 | MIT | ✅ | Szabad, self-host kell |
| comlink | Apache-2.0 | ✅ | Szabad |
| elkjs | EPL-2.0 | ⚠️ | Plugin use esetén figyelni kell |
| d3-path | ISC | ✅ | Szabad |
| Arduino CLI | AGPL-3.0 | ✅ (tool) | CLI eszköz, nem library → nem "fertőz" |
| ngspice | BSD | ✅ | Szabad |
| PySpice | GPLv3 | ⚠️ | Ha backend-en belül, zárt forrásnál figyelni |
| ahkab | LGPL-2.0+ | ✅ | Dynamic linking esetén OK |
| lcapy | MIT | ✅ | Szabad |
| gpiozero | LGPL-3.0 | ✅ | Szabad |
