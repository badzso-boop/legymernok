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
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("loads existing user data for editing", async () => {
    const mockUser = {
      id: "1",
      username: "luke",
      email: "luke@rebel.com",
      fullName: "Luke Skywalker",
      roles: ["ROLE_CADET"],
      avatarUrl: null,
    };

    mockedAxios.get.mockResolvedValue({ data: mockUser });

    render(
      <MemoryRouter initialEntries={["/admin/users/1"]}>
        <Routes>
          <Route path="/admin/users/:id" element={<UserEdit />} />
        </Routes>
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByDisplayValue("luke")).toBeInTheDocument();
      expect(screen.getByDisplayValue("Luke Skywalker")).toBeInTheDocument();
    });
  });

  it("creates new user", async () => {
    mockedAxios.post.mockResolvedValue({});

    render(
      <MemoryRouter initialEntries={["/admin/users/new"]}>
        <Routes>
          <Route path="/admin/users/new" element={<UserEdit />} />
        </Routes>
      </MemoryRouter>
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
        {
          username: "leia",
          email: "leia@rebel.com",
          password: "general",
          role: "ROLE_CADET",
          fullName: "",
        },
        expect.any(Object)
      );
    });

    expect(mockedNavigate).toHaveBeenCalledWith("/admin/users");
  });
});
