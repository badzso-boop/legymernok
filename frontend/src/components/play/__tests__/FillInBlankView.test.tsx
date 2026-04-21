import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import FillInBlankView from "../FillInBlankView";

// --- Mockok ---

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string, opts?: any) => {
    if (opts?.pct !== undefined) return `Már teljesítetted! (${opts.pct}%)`;
    return key;
  }}),
}));

vi.mock("../../../api/client", () => ({
  fillInBlankApi: {
    getForUser: vi.fn(),
    getLastAttempt: vi.fn(),
    submit: vi.fn(),
  },
}));

import { fillInBlankApi } from "../../../api/client";
const mockedGetForUser = vi.mocked(fillInBlankApi.getForUser);
const mockedGetLastAttempt = vi.mocked(fillInBlankApi.getLastAttempt);
const mockedSubmit = vi.mocked(fillInBlankApi.submit);

// Segédfüggvény RetroButton megkereséshez
const getRetroBtn = (labelText: string): HTMLButtonElement =>
  screen
    .getByText(labelText)
    .closest(".button-group")!
    .querySelector("button")!;

// --- Test adatok ---

const mockDefinition = {
  missionId: "m1",
  templateText: "A víz forráspontja [[blank_1]] Celsius.",
  passThreshold: 50,
  blanks: [
    {
      id: "b1",
      key: "blank_1",
      orderIndex: 0,
      options: [
        { id: "opt1", optionText: "100", orderIndex: 0 },
        { id: "opt2", optionText: "50", orderIndex: 1 },
        { id: "opt3", optionText: "0", orderIndex: 2 },
      ],
    },
  ],
};

// --- Tesztek ---

describe("FillInBlankView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedGetLastAttempt.mockRejectedValue({ response: { status: 404 } });
    mockedGetForUser.mockResolvedValue(mockDefinition as any);
  });

  it("Pool chip kattintás → az első üres slotba kerül", async () => {
    render(<FillInBlankView missionId="m1" onComplete={vi.fn()} />);

    // Várjuk, hogy betöltődjön
    await waitFor(() => {
      expect(screen.getByText("100")).toBeTruthy();
    });

    // Kattintunk a "100" pool chipre
    const chip100 = screen.getByText("100");
    fireEvent.click(chip100);

    // A slot-nak most "100"-t kell tartalmaznia és a pool-ból el kell tűnni
    await waitFor(() => {
      // A pool-ban már nincs "100" (csak egyszer szerepel)
      const allChips = screen.getAllByText("100");
      // Megjelenik a slotban, de a pool-ban nem (1 darab van összesen)
      expect(allChips.length).toBe(1);
    });
  });

  it("Kitöltött slot kattintás → visszakerül a pool-ba", async () => {
    render(<FillInBlankView missionId="m1" onComplete={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText("100")).toBeTruthy();
    });

    // Slot-ba tesszük a "100"-at
    fireEvent.click(screen.getByText("100"));

    await waitFor(() => {
      // "100" a slotban van, pool-ban nincs
      const chips = screen.getAllByText("100");
      expect(chips.length).toBe(1);
    });

    // Kattintunk a slotra (a [[blank_1]] szöveg helyett most "100" látszik)
    const slotChip = screen.getByText("100");
    fireEvent.click(slotChip);

    await waitFor(() => {
      // Visszakerült a pool-ba: a slot megint [[blank_1]] feliratú
      expect(screen.getByText("[[blank_1]]")).toBeTruthy();
      // "100" megint a pool-ban van
      expect(screen.getByText("100")).toBeTruthy();
    });
  });

  it("Submit gomb disabled amíg nem minden slot ki van töltve", async () => {
    render(<FillInBlankView missionId="m1" onComplete={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText("play.submit")).toBeTruthy();
    });

    const submitBtn = getRetroBtn("play.submit");
    expect(submitBtn).toBeDisabled();
  });

  it("passed: true submit → 'Következő' gomb jelenik meg", async () => {
    mockedSubmit.mockResolvedValue({
      score: 1,
      maxScore: 1,
      percentage: 100,
      passed: true,
      submittedAt: new Date().toISOString(),
      details: [
        {
          blankKey: "blank_1",
          selectedOptionId: "opt1",
          correct: true,
          correctOptionTexts: ["100"],
        },
      ],
    } as any);

    render(<FillInBlankView missionId="m1" onComplete={vi.fn()} />);

    await waitFor(() => expect(screen.getByText("100")).toBeTruthy());

    // Töltjük a slotot
    fireEvent.click(screen.getByText("100"));

    await waitFor(() => {
      const submitBtn = getRetroBtn("play.submit");
      expect(submitBtn).not.toBeDisabled();
    });

    fireEvent.click(getRetroBtn("play.submit"));

    await waitFor(() => {
      expect(screen.getByText("play.next")).toBeTruthy();
    });
  });

  it("passed: false submit → 'Próbáld újra' gomb jelenik meg", async () => {
    mockedSubmit.mockResolvedValue({
      score: 0,
      maxScore: 1,
      percentage: 0,
      passed: false,
      submittedAt: new Date().toISOString(),
      details: [
        {
          blankKey: "blank_1",
          selectedOptionId: "opt2",
          correct: false,
          correctOptionTexts: ["100"],
        },
      ],
    } as any);

    render(<FillInBlankView missionId="m1" onComplete={vi.fn()} />);

    await waitFor(() => expect(screen.getByText("100")).toBeTruthy());
    fireEvent.click(screen.getByText("100"));

    await waitFor(() => {
      expect(getRetroBtn("play.submit")).not.toBeDisabled();
    });

    fireEvent.click(getRetroBtn("play.submit"));

    await waitFor(() => {
      expect(screen.getByText("play.tryAgain")).toBeTruthy();
    });
  });

  it("lastAttempt.passed: true → 'Már teljesítetted' banner + Következő gomb", async () => {
    mockedGetLastAttempt.mockResolvedValue({
      passed: true,
      percentage: 80,
      submittedAt: new Date().toISOString(),
    } as any);

    render(<FillInBlankView missionId="m1" onComplete={vi.fn()} />);

    await waitFor(() => {
      expect(screen.getByText(/Már teljesítetted/i)).toBeTruthy();
      expect(screen.getByText("play.next")).toBeTruthy();
    });
  });
});
