import { render, screen, waitFor } from "@testing-library/react";
import { vi, describe, it, expect } from "vitest";
import UserList from "../cadets/UserList";
import axios from "axios";
import { BrowserRouter } from "react-router-dom";

// Axios Mockolása
vi.mock("axios");
const mockedAxios = axios as any;

describe("UserList Component", () => {
  it("fetches and displays users", async () => {
    // 1. Mock adatok beállítása
    const mockUsers = [
      {
        id: "1",
        username: "teszt_user",
        email: "test@example.com",
        roles: ["ROLE_CADET"],
        createdAt: "2023-01-01",
      },
    ];

    // Megmondjuk az axiosnak, hogy mit adjon vissza a .get() hívásra
    mockedAxios.get.mockResolvedValue({ data: mockUsers });

    // 2. Renderelés (Routerbe csomagolva a useNavigate miatt)
    render(
      <BrowserRouter>
        <UserList />
      </BrowserRouter>
    );

    // 4. Ellenőrzés: Megjelent-e a user a képernyőn?
    // A waitFor megvárja, amíg az aszinkron műveletek lefutnak és a DOM frissül
    await waitFor(() => {
      expect(screen.getByText("teszt_user")).toBeInTheDocument();
      expect(screen.getByText("test@example.com")).toBeInTheDocument();
    });

    // 5. Ellenőrzés: Tényleg meghívta az API-t?
    expect(mockedAxios.get).toHaveBeenCalledWith(
      expect.stringContaining("/users"),
      expect.any(Object) // Headers object
    );
  });
});
