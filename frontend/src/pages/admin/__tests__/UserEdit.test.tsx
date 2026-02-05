import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import UserEdit from "../cadets/UserEdit";
import axios from "axios";
import { MemoryRouter, Route, Routes } from "react-router-dom";

// Axios mockolása
vi.mock("axios");
const mockedAxios = axios as any;

// i18next mockolása
vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

const mockedNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return {
    ...actual,
    useNavigate: () => mockedNavigate,
  };
});

describe("UserEdit Component", () => {
  // --- JAVÍTÁS: MOCK ADATOK ---
  const mockUser = {
    id: "1",
    username: "luke",
    email: "luke@rebel.com",
    fullName: "Luke Skywalker",
    roles: [{ name: "ROLE_CADET", id: "role-1" }], // A UserEdit most már objektumot vár, nem stringet!
    avatarUrl: null,
  };

  const mockRoles = [
    { id: "role-1", name: "ROLE_CADET", description: "Cadet" },
    { id: "role-2", name: "ROLE_ADMIN", description: "Admin" },
  ];
  // -------------------------

  beforeEach(() => {
    vi.clearAllMocks();

    // --- JAVÍTÁS: Mock API válaszok beállítása ---
    mockedAxios.get.mockImplementation((url: string) => {
      if (url.includes("/roles")) {
        // Ha a szerepköröket kéri, adjuk vissza a mockRoles-t
        return Promise.resolve({ data: mockRoles });
      }
      if (url.includes("/users/")) {
        // Ha a usert kéri, adjuk vissza a mockUser-t
        return Promise.resolve({ data: mockUser });
      }
      return Promise.reject(new Error("not found"));
    });
    // ------------------------------------------
  });

  it("loads existing user data for editing", async () => {
    // A mockokat már a beforeEach beállította, itt nincs több teendő

    render(
      <MemoryRouter initialEntries={["/admin/users/1"]}>
        <Routes>
          <Route path="/admin/users/:id" element={<UserEdit />} />
        </Routes>
      </MemoryRouter>,
    );

    // Most már a `waitFor` megtalálja az elemeket
    await waitFor(() => {
      expect(screen.getByDisplayValue("luke")).toBeInTheDocument();
      expect(screen.getByDisplayValue("Luke Skywalker")).toBeInTheDocument();

      // Ellenőrizzük a szerepkörök betöltődését is
      expect(screen.getByText("ROLE_CADET")).toBeInTheDocument();
    });
  });

  it("creates new user", async () => {
    mockedAxios.post.mockResolvedValue({}); // POST hívás mockolása

    render(
      <MemoryRouter initialEntries={["/admin/users/new"]}>
        <Routes>
          <Route path="/admin/users/new" element={<UserEdit />} />
        </Routes>
      </MemoryRouter>,
    );

    // Várjuk meg, hogy a szerepkörök betöltődjenek
    await waitFor(() =>
      expect(screen.getByText("ROLE_CADET")).toBeInTheDocument(),
    );

    const usernameInput = screen.getByLabelText("username");
    const emailInput = screen.getByLabelText("email");
    const passwordInput = screen.getByLabelText("Jelszó");
    const saveButton = screen.getByText("Létrehozás");

    fireEvent.change(usernameInput, { target: { value: "leia" } });
    fireEvent.change(emailInput, { target: { value: "leia@rebel.com" } });
    fireEvent.change(passwordInput, { target: { value: "general" } });

    fireEvent.click(saveButton);

    await waitFor(() => {
      expect(mockedAxios.post).toHaveBeenCalledWith(
        expect.stringContaining("/users"),
        // A payload most már a role NAME-et küldi, nem az objektumot. Ellenőrizd a UserEdit handleSave-et!
        // A UserEdit-ben a payload ezt küldi: role: user.role ? user.role : "",
        // A user.role pedig a Select miatt a role neve (string) lesz.
        expect.objectContaining({
          username: "leia",
          email: "leia@rebel.com",
          password: "general",
          role: "ROLE_CADET", // A komponens ezt teszi bele alapból
        }),
        expect.any(Object),
      );
    });

    expect(mockedNavigate).toHaveBeenCalledWith("/admin/users");
  });
});
