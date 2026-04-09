import React from "react";
import type { BoardType, ComponentPinDefinitionResponse, ComponentType } from "../../../types/circuit";

export interface CircuitContextValue {
  pinCatalog: Map<string, ComponentPinDefinitionResponse[]>;
  boardType: BoardType;
}

export const CircuitContext = React.createContext<CircuitContextValue>({
  pinCatalog: new Map(),
  boardType: "ARDUINO_UNO",
});

/** Returns the pin definitions for a given component type (and optional board type). */
export function getPins(
  catalog: Map<string, ComponentPinDefinitionResponse[]>,
  type: ComponentType,
  board?: BoardType
): ComponentPinDefinitionResponse[] {
  const key = board ? `${type}:${board}` : type;
  return catalog.get(key) ?? [];
}
