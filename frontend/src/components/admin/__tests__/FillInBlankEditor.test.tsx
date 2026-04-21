import React from "react";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import FillInBlankEditor from "../FillInBlankEditor";

// --- Mockok ---

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock("../../../api/client", () => ({
  fillInBlankApi: {
    getForAdmin: vi.fn(),
    save: vi.fn(),
    update: vi.fn(),
  },
}));

import { fillInBlankApi } from "../../../api/client";
const mockedGetForAdmin = vi.mocked(fillInBlankApi.getForAdmin);
const mockedSave = vi.mocked(fillInBlankApi.save);

// QueryClientProvider wrapper — minden teszthez friss kliens, retry=0
const createWrapper = () => {
  const qc = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
};

// Segédfüggvény RetroButton megkereséshez
const getRetroBtn = (labelText: string): HTMLButtonElement =>
  screen
    .getByText(labelText)
    .closest(".button-group")!
    .querySelector("button")!;

// --- Tesztek ---

describe("FillInBlankEditor", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // Alapértelmezett: nem létezik mentett definíció (404)
    mockedGetForAdmin.mockRejectedValue({ response: { status: 404 } });
  });

  it("[[blank_1]] [[blank_2]] template-ből 2 blank panel jelenik meg", async () => {
    render(<FillInBlankEditor missionId="m1" />, { wrapper: createWrapper() });

    const textarea = screen.getByPlaceholderText(/Pl\./i);
    fireEvent.change(textarea, {
      target: { value: "A [[blank_1]] és [[blank_2]] értékek." },
    });

    expect(screen.getByText("[[blank_1]]")).toBeTruthy();
    expect(screen.getByText("[[blank_2]]")).toBeTruthy();
  });

  it("Törölt blank kulcs → panel eltűnik", async () => {
    render(<FillInBlankEditor missionId="m1" />, { wrapper: createWrapper() });

    const textarea = screen.getByPlaceholderText(/Pl\./i);
    fireEvent.change(textarea, { target: { value: "Test [[blank_1]] vége." } });
    expect(screen.getByText("[[blank_1]]")).toBeTruthy();

    fireEvent.change(textarea, { target: { value: "Test vége." } });
    expect(screen.queryByText("[[blank_1]]")).toBeNull();
  });

  it("Max 5 blank: gomb disabled ha 5 kulcs van a template-ben", async () => {
    render(<FillInBlankEditor missionId="m1" />, { wrapper: createWrapper() });

    const textarea = screen.getByPlaceholderText(/Pl\./i);
    fireEvent.change(textarea, {
      target: { value: "[[a]] [[b]] [[c]] [[d]] [[e]]" },
    });

    const addBtn = getRetroBtn("fillInBlank.addBlank");
    expect(addBtn).toBeDisabled();
  });

  it("Opció onBlur → hozzáadódik a blank-hez, üres input jelenik meg", async () => {
    render(<FillInBlankEditor missionId="m1" />, { wrapper: createWrapper() });

    const textarea = screen.getByPlaceholderText(/Pl\./i);
    fireEvent.change(textarea, { target: { value: "Teszt [[blank_1]] szöveg." } });

    const optionInput = screen.getByPlaceholderText(/Új opció/i);
    fireEvent.change(optionInput, { target: { value: "Valami opció" } });
    fireEvent.blur(optionInput);

    await waitFor(() => {
      expect(screen.getByText("Valami opció")).toBeTruthy();
    });
  });

  it("Mentés: fillInBlankApi.save helyes payload-dal hívódik", async () => {
    mockedSave.mockResolvedValue({} as any);

    render(<FillInBlankEditor missionId="m42" />, { wrapper: createWrapper() });

    const textarea = screen.getByPlaceholderText(/Pl\./i);
    fireEvent.change(textarea, { target: { value: "A [[blank_1]] fontos." } });

    const saveBtn = getRetroBtn("forge.save");
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(mockedSave).toHaveBeenCalledWith(
        "m42",
        expect.objectContaining({
          templateText: "A [[blank_1]] fontos.",
          blanks: expect.arrayContaining([
            expect.objectContaining({ key: "blank_1" }),
          ]),
        }),
      );
    });
  });

  it("getForAdmin betöltés → form feltöltődik", async () => {
    mockedGetForAdmin.mockResolvedValue({
      missionId: "m99",
      templateText: "Ez [[loaded_blank]] teszt.",
      passThreshold: 80,
      blanks: [
        {
          id: "b1",
          key: "loaded_blank",
          orderIndex: 0,
          options: [
            { id: "o1", optionText: "opcio1", correct: true, orderIndex: 0 },
          ],
        },
      ],
    } as any);

    render(<FillInBlankEditor missionId="m99" />, { wrapper: createWrapper() });

    await waitFor(() => {
      expect(screen.getByDisplayValue("Ez [[loaded_blank]] teszt.")).toBeTruthy();
      expect(screen.getByText("[[loaded_blank]]")).toBeTruthy();
      expect(screen.getByText("opcio1")).toBeTruthy();
    });
  });
});
