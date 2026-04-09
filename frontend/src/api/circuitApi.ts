import apiClient from "./client";
import type {
  CircuitDefinitionResponse,
  CircuitVerificationCheckResponse,
  ComponentPinDefinitionResponse,
  UnitOfMeasureResponse,
  CreateCircuitDefinitionRequest,
  SaveCanvasRequest,
  AddVerificationCheckRequest,
  ComponentType,
  BoardType,
} from "../types/circuit";

export const getCircuitDefinitionsByMission = (missionId: string) =>
  apiClient
    .get<CircuitDefinitionResponse[]>(`/circuit/definitions/by-mission/${missionId}`)
    .then((r) => r.data);

export const createCircuitDefinition = (data: CreateCircuitDefinitionRequest) =>
  apiClient
    .post<CircuitDefinitionResponse>(`/circuit/definitions`, data)
    .then((r) => r.data);

export const saveCanvas = (id: string, data: SaveCanvasRequest) =>
  apiClient
    .put<CircuitDefinitionResponse>(`/circuit/definitions/${id}/canvas`, data)
    .then((r) => r.data);

export const addVerificationCheck = (id: string, data: AddVerificationCheckRequest) =>
  apiClient
    .post<CircuitVerificationCheckResponse>(`/circuit/definitions/${id}/checks`, data)
    .then((r) => r.data);

export const deleteVerificationCheck = (id: string, checkId: string) =>
  apiClient
    .delete(`/circuit/definitions/${id}/checks/${checkId}`)
    .then(() => undefined);

export const publishCircuitDefinition = (id: string) =>
  apiClient
    .post<CircuitDefinitionResponse>(`/circuit/definitions/${id}/publish`)
    .then((r) => r.data);

export const unpublishCircuitDefinition = (id: string) =>
  apiClient
    .post<CircuitDefinitionResponse>(`/circuit/definitions/${id}/unpublish`)
    .then((r) => r.data);

export const deleteCircuitDefinition = (id: string) =>
  apiClient
    .delete(`/circuit/definitions/${id}`)
    .then(() => undefined);

export const getAllPinDefinitions = () =>
  apiClient
    .get<ComponentPinDefinitionResponse[]>(`/circuit/catalog/pins`)
    .then((r) => r.data);

export const getPinsForComponent = (componentType: ComponentType, boardType: BoardType) =>
  apiClient
    .get<ComponentPinDefinitionResponse[]>(`/circuit/catalog/pins`, {
      params: { componentType, boardType },
    })
    .then((r) => r.data);

export const getUnitsOfMeasure = () =>
  apiClient
    .get<UnitOfMeasureResponse[]>(`/circuit/units`)
    .then((r) => r.data);
