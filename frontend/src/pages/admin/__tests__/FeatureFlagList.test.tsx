import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import FeatureFlagList from "../feature-flags/FeatureFlagList";

const mockGetAll = vi.fn();
const mockUpdate = vi.fn();

vi.mock("../../../api/client", () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  featureFlagApi: {
    getAll: (...args: unknown[]) => mockGetAll(...args),
    getByKey: vi.fn(),
    update: (...args: unknown[]) => mockUpdate(...args),
  },
}));

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

// DataGrid mockolása, hogy ne kelljen resize observer / valódi grid-renderelés
vi.mock("@mui/x-data-grid", async () => {
  const actual = await vi.importActual("@mui/x-data-grid");
  return {
    ...actual,
    DataGrid: (props: any) => (
      <div data-testid="data-grid">
        {props.rows.map((row: any) => {
          const enabledCol = props.columns.find((c: any) => c.field === "enabled");
          const actionsCol = props.columns.find((c: any) => c.field === "actions");
          return (
            <div key={row.key}>
              <span>{row.key}</span>
              <span>{row.description}</span>
              {enabledCol.renderCell({ row })}
              {actionsCol.renderCell({ row })}
            </div>
          );
        })}
      </div>
    ),
  };
});

describe("FeatureFlagList Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches and displays feature flags", async () => {
    mockGetAll.mockResolvedValue([
      {
        id: "1",
        key: "ai_chatbot",
        enabled: false,
        description: "AI chatbot widget",
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
      },
    ]);

    render(<FeatureFlagList />);

    await waitFor(() => {
      expect(screen.getByText("ai_chatbot")).toBeInTheDocument();
    });
    expect(screen.getByText("AI chatbot widget")).toBeInTheDocument();
  });

  it("toggles a flag's enabled state", async () => {
    mockGetAll.mockResolvedValue([
      {
        id: "1",
        key: "ai_chatbot",
        enabled: false,
        description: "AI chatbot widget",
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
      },
    ]);
    mockUpdate.mockResolvedValue({
      id: "1",
      key: "ai_chatbot",
      enabled: true,
      description: "AI chatbot widget",
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:01:00Z",
    });

    render(<FeatureFlagList />);

    await waitFor(() => {
      expect(screen.getByText("ai_chatbot")).toBeInTheDocument();
    });

    const toggle = screen.getByRole("switch");
    fireEvent.click(toggle);

    await waitFor(() => {
      expect(mockUpdate).toHaveBeenCalledWith("ai_chatbot", {
        enabled: true,
        description: "AI chatbot widget",
      });
    });
  });

  it("shows an error when fetching fails", async () => {
    mockGetAll.mockRejectedValue(new Error("network error"));

    render(<FeatureFlagList />);

    await waitFor(() => {
      expect(screen.getByText("errorFetchFeatureFlags")).toBeInTheDocument();
    });
  });
});
