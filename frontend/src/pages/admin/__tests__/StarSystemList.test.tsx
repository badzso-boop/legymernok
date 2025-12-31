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
    t: (key: string) => key,
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

    // Keressük meg a törlés gombot. Mivel több gomb is lehet, specifikusabban kellene,
    // de itt csak egy sor van. A tooltip segít azonosítani.
    const deleteButton = screen.getByLabelText("Törlés"); // Tooltip címe alapján, vagy icon gomb

    // Ha a tooltip nem működik így testing-library-vel közvetlenül, akkor a testid lenne a legjobb,
    // de most az implementáció alapján próbálkozunk.
    // A DeleteIcon-t tartalmazó gombot keressük.

    fireEvent.click(deleteButton);

    await waitFor(() => {
      expect(mockedAxios.delete).toHaveBeenCalledWith(
        expect.stringContaining("/star-systems/1"),
        expect.any(Object)
      );
    });
  });
});
