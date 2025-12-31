import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import StarSystemList from "../star-system/StarSystemList";
import axios from "axios";
import { BrowserRouter } from "react-router-dom";

// Axios mockolása
vi.mock("axios");
const mockedAxios = axios as any;

// i18next mockolása
vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    // Mivel a komponens paraméteres fordítást használ a törlésnél (t("deleteStarSystemConfirm", { name })),
    // a mocknak kezelnie kell a második paramétert is, különben "[object Object]" lesz a szövegben.
    t: (key: string, options?: any) => {
      if (key === "deleteStarSystemConfirm" && options?.name) {
        return `deleteStarSystemConfirm ${options.name}`;
      }
      return key;
    },
  }),
}));

// useNavigate mockolása
const mockedNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockedNavigate,
  };
});

describe("StarSystemList Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches and displays star systems", async () => {
    const mockSystems = [
      {
        id: "1",
        name: "Tatooine",
        description: "Desert planet",
        createdAt: "2023-01-01T10:00:00Z",
        updatedAt: "2023-01-02T10:00:00Z",
      },
    ];

    mockedAxios.get.mockResolvedValue({ data: mockSystems });

    render(
      <BrowserRouter>
        <StarSystemList />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("Tatooine")).toBeInTheDocument();
      expect(screen.getByText(/Desert planet/)).toBeInTheDocument();
    });
  });

  it("handles delete action", async () => {
    const mockSystems = [
      {
        id: "1",
        name: "Alderaan",
        description: "Peaceful planet",
        createdAt: "2023-01-01T10:00:00Z",
        updatedAt: "2023-01-02T10:00:00Z",
      },
    ];

    mockedAxios.get.mockResolvedValue({ data: mockSystems });
    mockedAxios.delete.mockResolvedValue({});

    // Window.confirm mockolása
    vi.spyOn(window, "confirm").mockImplementation(() => true);

    render(
      <BrowserRouter>
        <StarSystemList />
      </BrowserRouter>
    );

    await waitFor(() => {
      expect(screen.getByText("Alderaan")).toBeInTheDocument();
    });

    // JAVÍTVA: A fordítási kulcsot keressük ("delete")
    const deleteButton = screen.getByLabelText("delete");

    fireEvent.click(deleteButton);

    // Ellenőrizzük, hogy a confirm meghívódott-e (opcionális, de jó teszt)
    // A mockolt 't' függvényünk miatt a szöveg ez lesz: "deleteStarSystemConfirm Alderaan"
    expect(window.confirm).toHaveBeenCalledWith(
      expect.stringContaining("deleteStarSystemConfirm")
    );

    await waitFor(() => {
      expect(mockedAxios.delete).toHaveBeenCalledWith(
        expect.stringContaining("/star-systems/1"),
        expect.any(Object)
      );
    });
  });
});
