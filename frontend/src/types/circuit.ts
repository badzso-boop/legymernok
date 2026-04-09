export type BoardType =
  | "ARDUINO_UNO"
  | "ARDUINO_MEGA_2560"
  | "ESP8266"
  | "ESP32";

export type ComponentType =
  | "BOARD"
  | "LED"
  | "RESISTOR"
  | "CAPACITOR"
  | "PUSHBUTTON"
  | "POTENTIOMETER"
  | "DHT11"
  | "HC_SR04"
  | "SERVO"
  | "VCC_5V"
  | "VCC_3V3"
  | "GND";

export type CircuitDefinitionStatus = "IN_WORK" | "PUBLISHED" | "STALE";
export type CheckType =
  | "CIRCUIT_TOPOLOGY"
  | "PATH_EXISTS"
  | "GPIO_BEHAVIOR"
  | "SERIAL_OUTPUT"
  | "PWM";
export type CheckSeverity = "INFO" | "WARNING" | "ERROR";
export type SimulationStatus =
  | "NEVER_RUN"
  | "RUNNING"
  | "PAUSED"
  | "SUCCESS"
  | "FAILED";

export interface UnitOfMeasureResponse {
  id: string;
  name: string;
  symbol: string;
}

export interface CircuitDefComponentPropertyResponse {
  id: string;
  key: string;
  value: string;
  unitOfMeasure?: UnitOfMeasureResponse;
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
  labelFrom?: string;
  labelTo?: string;
  pinFrom?: string;
  pinTo?: string;
  expectedValue?: string;
  unitOfMeasure?: UnitOfMeasureResponse;
  severity: CheckSeverity;
  i18nKey: string;
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

export interface CreateCircuitDefinitionRequest {
  missionId: string;
  boardType: BoardType;
}

export interface SaveCanvasComponentRequest {
  id: string;
  componentType: ComponentType;
  label: string;
  posX: number;
  posY: number;
  properties?: SaveCanvasPropertyRequest[];
}

export interface SaveCanvasPropertyRequest {
  propertyKey: string;
  propertyValue: string;
  unitOfMeasureId?: string;
}

export interface SaveCanvasConnectionRequest {
  fromComponentId: string;
  fromPinName: string;
  toComponentId: string;
  toPinName: string;
}

export interface SaveCanvasRequest {
  components: SaveCanvasComponentRequest[];
  connections: SaveCanvasConnectionRequest[];
}

export interface AddVerificationCheckRequest {
  checkType: CheckType;
  labelFrom?: string;
  labelTo?: string;
  pinFrom?: string;
  pinTo?: string;
  expectedValue?: string;
  severity: CheckSeverity;
  i18nKey: string;
  orderIndex: number;
}

export interface ComponentPinDefinitionResponse {
  componentType: ComponentType;
  boardType: BoardType | null;
  pinName: string;
  pinIndex: number;
  pinType: string;
  allowMultipleConnections: boolean;
  posXOffset: number;
  posYOffset: number;
}

export interface CircuitNodeData {
  componentType: ComponentType;
  label: string;
  properties: CircuitDefComponentPropertyResponse[];
  [key: string]: unknown;
}

export const PIN_TYPE_COLORS: Record<string, string> = {
  PWM_OUT:           "#ff9800",
  ANALOG_IN:         "#9c27b0",
  POWER_VCC:         "#f44336",
  POWER_GND:         "#546e7a",
  DIGITAL_IO:        "#4caf50",
  UART_TX:           "#2196f3",
  UART_RX:           "#42a5f5",
  I2C_SDA:           "#fdd835",
  I2C_SCL:           "#ffee58",
  SPI_MOSI:          "#00bcd4",
  SPI_MISO:          "#26c6da",
  SPI_SCK:           "#00acc1",
  SPI_CS:            "#0097a7",
  ONE_WIRE:          "#ff5722",
  SIGNAL_IN:         "#8bc34a",
  SIGNAL_OUT:        "#cddc39",
  COMPONENT_ANODE:   "#ef5350",
  COMPONENT_CATHODE: "#78909c",
  PASSIVE_A:         "#bdbdbd",
  PASSIVE_B:         "#bdbdbd",
};
