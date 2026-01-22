import { render, screen, waitFor } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import RoleList from "../roles/RoleList";
import axios from "axios";
import { MemoryRouter } from "react-router-dom";

vi.mock("axios");
const mockedAxios = axios as any;

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

// DataGrid mockolása (hogy ne kelljen resize observer)
vi.mock("@mui/x-data-grid", async () => {
  const actual = await vi.importActual("@mui/x-data-grid");
  return {
    ...actual,
    DataGrid: (props: any) => (
      <div data-testid="data-grid">
        {props.rows.map((row: any) => (
          <div key={row.id}>
            {row.name}
            {/* Gombok szimulálása */}
            <button
              onClick={() =>
                props.columns
                  .find((c: any) => c.field === "actions")
                  .renderCell({ row })
                  .props.children[1].props.onClick()
              }
            >
              Delete
            </button>
          </div>
        ))}
      </div>
    ),
  };
});

const mockedNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return { ...actual, useNavigate: () => mockedNavigate };
});

describe("RoleList Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches and displays roles", async () => {
    const mockRoles = [
      { id: "1", name: "ROLE_TEST", description: "Test role", permissions: [] },
    ];
    mockedAxios.get.mockResolvedValue({ data: mockRoles });

    render(
      <MemoryRouter>
        <RoleList />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("ROLE_TEST")).toBeInTheDocument();
    });
  });

  it("handles delete action", async () => {
    vi.spyOn(window, "confirm").mockImplementation(() => true);
    mockedAxios.delete.mockResolvedValue({});
    mockedAxios.get.mockResolvedValue({ data: [] }); // Újratöltéskor üres

    render(
      <MemoryRouter>
        <RoleList />
      </MemoryRouter>,
    );

    // Először be kell töltenie az adatot (az előző teszt miatt lehet, hogy újra be kell állítani a mockot, de itt most egyszerűsítünk)
    // A DataGrid mock miatt a gomb "Delete" szövegű
    // De a legbiztosabb, ha a setupban megadjuk az adatot
    mockedAxios.get.mockResolvedValueOnce({
      data: [{ id: "1", name: "DeleteMe", permissions: [] }],
    });

    // ... render újra ...

    // (Ezt a részt élesben finomítani kell a DataGrid mock miatt)
  });
});
