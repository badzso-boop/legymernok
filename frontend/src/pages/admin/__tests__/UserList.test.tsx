import { render, screen, waitFor } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import UserList from "../cadets/UserList";
import axios from "axios";
import { MemoryRouter } from "react-router-dom";

// Axios Mockolása
vi.mock("axios");
const mockedAxios = axios as any;

// AuthContext Mockolása
vi.mock("../../../context/AuthContext", () => ({
  useAuth: () => ({
    hasRole: (role: string) => true, // Mindig van joga a tesztben
    isLoading: false,
  }),
}));

// AuthContext Mockolása
vi.mock("../../../context/AuthContext", () => ({
  useAuth: () => ({
    hasRole: (role: string) => true, // Az admin felület teszteléséhez feltételezzük, hogy van joga
    isLoading: false,
  }),
}));

// i18n Mockolása
vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

describe("UserList Component", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("fetches and displays users", async () => {
    // 1. Mock adatok beállítása
    const mockUsers = [
      {
        id: "1",
        username: "teszt_user",
        email: "test@example.com",
        roles: ["ROLE_CADET"],
        createdAt: "2023-01-01T10:00:00Z",
        updatedAt: "2023-01-01T10:00:00Z",
      },
    ];

    // Megmondjuk az axiosnak, hogy mit adjon vissza a .get() hívásra
    mockedAxios.get.mockResolvedValue({ data: mockUsers });

    // 2. Renderelés (MemoryRouter-t használunk a tesztkörnyezetben)
    render(
      <MemoryRouter>
        <UserList />
      </MemoryRouter>
    );

    // 3. Ellenőrzés: Megjelent-e a user a képernyőn?
    await waitFor(() => {
      expect(screen.getByText("teszt_user")).toBeInTheDocument();
      expect(screen.getByText("test@example.com")).toBeInTheDocument();
    });

    // 4. Ellenőrzés: Tényleg meghívta az API-t a megfelelő headerrel?
    expect(mockedAxios.get).toHaveBeenCalledWith(
      expect.stringContaining("/users"),
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: expect.stringContaining("Bearer"),
        }),
      })
    );
  });
});
