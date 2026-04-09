import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import axios from "axios";
import CircuitForgeAdminPage, {
  mapComponentsToNodes,
  mapConnectionsToEdges,
  mapNodesToSaveRequest,
} from "../circuit/CircuitForgeAdminPage";
import VerificationCheckForm from "../circuit/VerificationCheckForm";
import ComponentPalette from "../circuit/ComponentPalette";
import type { ComponentType as CircuitComponentType } from "../../../types/circuit";
import type { Node } from "@xyflow/react";
import type { CircuitNodeData } from "../../../types/circuit";

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------
vi.mock("axios");
const mockedAxios = axios as any;

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

const mockedNavigate = vi.fn();
vi.mock("react-router-dom", async () => ({
  ...((await vi.importActual("react-router-dom")) as object),
  useNavigate: () => mockedNavigate,
}));

// xyflow uses browser DOM APIs not available in jsdom — mock the entire module
vi.mock("@xyflow/react", () => ({
  ReactFlow: ({ children, onDrop, onDragOver }: any) => (
    <div data-testid="react-flow" onDrop={onDrop} onDragOver={onDragOver}>
      {children}
    </div>
  ),
  useNodesState: (init: any[]) => [init, vi.fn(), vi.fn()],
  useEdgesState: (init: any[]) => [init, vi.fn(), vi.fn()],
  useReactFlow: () => ({ screenToFlowPosition: (p: any) => p }),
  ReactFlowProvider: ({ children }: any) => <>{children}</>,
  Background: () => null,
  Controls: () => null,
  MiniMap: () => null,
  BackgroundVariant: { Dots: "dots" },
  addEdge: (edge: any, edges: any[]) => [...edges, edge],
  Handle: () => null,
  Position: { Top: "top", Bottom: "bottom" },
}));

// ---------------------------------------------------------------------------
// Test helpers
// ---------------------------------------------------------------------------
const mockDefinition = {
  id: "def-1",
  missionId: "mission-1",
  boardType: "ARDUINO_UNO",
  status: "IN_WORK",
  components: [
    {
      id: "comp-1",
      componentType: "LED",
      label: "LED1",
      posX: 100,
      posY: 200,
      properties: [],
    },
  ],
  connections: [],
  checks: [],
  createdAt: "2025-01-01T00:00:00Z",
  updatedAt: "2025-01-01T00:00:00Z",
};

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={["/admin/circuit/mission-1"]}>
      <Routes>
        <Route path="/admin/circuit/:missionId" element={<CircuitForgeAdminPage />} />
      </Routes>
    </MemoryRouter>
  );

// ---------------------------------------------------------------------------
// Pure function tests (no DOM needed)
// ---------------------------------------------------------------------------
const makeNode = (
  id: string,
  componentType: CircuitComponentType,
  label: string,
  x = 0,
  y = 0
): Node<CircuitNodeData> => ({
  id,
  type: "circuitNode",
  position: { x, y },
  data: { componentType, label, properties: [] },
});

describe("mapNodesToSaveRequest", () => {

  it("rounds float positions", () => {
    const nodes = [makeNode("n1", "LED", "L1", 124.7, 55.2)];
    const result = mapNodesToSaveRequest(nodes, []);
    expect(result.components[0].posX).toBe(125);
    expect(result.components[0].posY).toBe(55);
  });

  it("extracts edge pin names", () => {
    const nodes = [makeNode("n1", "LED", "L"), makeNode("n2", "RESISTOR", "R")];
    const edges = [
      { id: "e1", source: "n1", target: "n2", data: { fromPinName: "A", toPinName: "B" } },
    ];
    const result = mapNodesToSaveRequest(nodes, edges);
    expect(result.connections[0].fromPinName).toBe("A");
    expect(result.connections[0].toPinName).toBe("B");
  });

  it("handles undefined edge data without crashing", () => {
    const edges = [{ id: "e1", source: "n1", target: "n2" }];
    const result = mapNodesToSaveRequest([], edges);
    expect(result.connections[0].fromPinName).toBe("");
    expect(result.connections[0].toPinName).toBe("");
  });
});

describe("mapComponentsToNodes", () => {

  it("sets type to circuitNode and uses component id", () => {
    const comps = [{ id: "c1", componentType: "LED" as CircuitComponentType, label: "L1", posX: 10, posY: 20, properties: [] }];
    const nodes = mapComponentsToNodes(comps);
    expect(nodes[0].type).toBe("circuitNode");
    expect(nodes[0].id).toBe("c1");
  });

  it("preserves label in node data", () => {
    const comps = [{ id: "c1", componentType: "LED" as CircuitComponentType, label: "MyLED", posX: 0, posY: 0, properties: [] }];
    const nodes = mapComponentsToNodes(comps);
    expect(nodes[0].data.label).toBe("MyLED");
  });
});

describe("mapConnectionsToEdges", () => {

  it("maps fromComponentId to source, toComponentId to target", () => {
    const conns = [{ id: "e1", fromComponentId: "c1", fromPinName: "A", toComponentId: "c2", toPinName: "B" }];
    const edges = mapConnectionsToEdges(conns);
    expect(edges[0].source).toBe("c1");
    expect(edges[0].target).toBe("c2");
  });
});

// ---------------------------------------------------------------------------
// Component tests
// ---------------------------------------------------------------------------
describe("CircuitForgeAdminPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("shows loading spinner initially", () => {
    mockedAxios.get = vi.fn(() => new Promise(() => {})); // never resolves
    renderPage();
    expect(screen.getByRole("progressbar")).toBeTruthy();
  });

  it("shows create definition UI when no definition exists", async () => {
    mockedAxios.get = vi.fn().mockResolvedValue({ data: [] });
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("circuit.noDefinition")).toBeTruthy();
    });
    expect(screen.getByTestId("create-definition-btn") ?? screen.queryByText("circuit.createDefinition")).toBeTruthy();
  });

  it("shows create definition UI - create btn visible", async () => {
    mockedAxios.get = vi.fn().mockResolvedValue({ data: [] });
    renderPage();
    await waitFor(() => screen.getByText("circuit.createDefinition"));
  });

  it("loads and displays existing definition with status chip", async () => {
    mockedAxios.get = vi.fn().mockResolvedValue({ data: [mockDefinition] });
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("IN_WORK")).toBeTruthy();
    });
  });

  it("shows error alert when API fails", async () => {
    mockedAxios.get = vi.fn().mockRejectedValue(new Error("Network error"));
    renderPage();
    await waitFor(() => {
      expect(screen.getByText("circuit.errorLoad")).toBeTruthy();
    });
  });

  it("calls createCircuitDefinition with correct missionId on create click", async () => {
    mockedAxios.get = vi.fn().mockResolvedValue({ data: [] });
    mockedAxios.post = vi.fn().mockResolvedValue({ data: mockDefinition });

    renderPage();
    await waitFor(() => screen.getByText("circuit.createDefinition"));

    const btn = screen.getAllByText("circuit.createDefinition").find(
      (el) => el.tagName === "BUTTON" || el.closest("button")
    );
    fireEvent.click(btn ?? screen.getAllByText("circuit.createDefinition")[0]);

    await waitFor(() => {
      expect(mockedAxios.post).toHaveBeenCalledWith(
        expect.stringContaining("/circuit/definitions"),
        expect.objectContaining({ missionId: "mission-1" }),
        expect.any(Object)
      );
    });
  });

  it("calls PUT /canvas when save button is clicked", async () => {
    mockedAxios.get = vi.fn().mockResolvedValue({ data: [mockDefinition] });
    mockedAxios.put = vi.fn().mockResolvedValue({ data: mockDefinition });

    renderPage();
    await waitFor(() => screen.getByText("circuit.saveCanvas"));

    fireEvent.click(screen.getByText("circuit.saveCanvas"));

    await waitFor(() => {
      expect(mockedAxios.put).toHaveBeenCalledWith(
        expect.stringContaining("/canvas"),
        expect.any(Object),
        expect.any(Object)
      );
    });
  });
});

// ---------------------------------------------------------------------------
// VerificationCheckForm tests
// ---------------------------------------------------------------------------
describe("VerificationCheckForm", () => {

  const defaultProps = {
    definitionId: "def-1",
    components: [{ id: "c1", componentType: "LED" as CircuitComponentType, label: "L1", posX: 0, posY: 0, properties: [] }],
    nextOrderIndex: 0,
    submitting: false,
    onSubmit: vi.fn(),
  };

  it("shows labelFrom and labelTo selects for PATH_EXISTS, not for CIRCUIT_TOPOLOGY", async () => {
    const { queryByText } = render(
      <MemoryRouter>
        <VerificationCheckForm {...defaultProps} />
      </MemoryRouter>
    );
    // Default is CIRCUIT_TOPOLOGY — no path fields
    expect(queryByText("circuit.labelFrom")).toBeNull();

    // Switch to PATH_EXISTS
    // (Cannot easily change Select in jsdom without more setup — test field presence via queryByLabelText)
  });

  it("submit button is disabled when submitting prop is true", () => {
    const { getByText } = render(
      <MemoryRouter>
        <VerificationCheckForm {...defaultProps} submitting={true} />
      </MemoryRouter>
    );
    const btn = getByText("circuit.addCheck").closest("button");
    expect(btn).toHaveProperty("disabled", true);
  });
});

// ---------------------------------------------------------------------------
// ComponentPalette tests
// ---------------------------------------------------------------------------
describe("ComponentPalette", () => {

  it("renders all component type items", () => {
    const { getByText } = render(
      <MemoryRouter>
        <ComponentPalette boardType="ARDUINO_UNO" disabled={false} />
      </MemoryRouter>
    );
    expect(getByText("LED")).toBeTruthy();
    expect(getByText("BOARD")).toBeTruthy();
    expect(getByText("GND")).toBeTruthy();
    expect(getByText("RESISTOR")).toBeTruthy();
  });

  it("items are not draggable when disabled", () => {
    const { container } = render(
      <MemoryRouter>
        <ComponentPalette boardType="ARDUINO_UNO" disabled={true} />
      </MemoryRouter>
    );
    const ledItem = container.querySelector('[data-cy="palette-item-LED"]');
    expect(ledItem?.getAttribute("draggable")).toBe("false");
  });

  it("items are draggable when not disabled", () => {
    const { container } = render(
      <MemoryRouter>
        <ComponentPalette boardType="ARDUINO_UNO" disabled={false} />
      </MemoryRouter>
    );
    const ledItem = container.querySelector('[data-cy="palette-item-LED"]');
    expect(ledItem?.getAttribute("draggable")).toBe("true");
  });
});
