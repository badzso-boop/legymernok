import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import ContentMissionView from "../ContentMissionView";

// --- Mockok ---

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

const mockedNavigate = vi.fn();
vi.mock("react-router-dom", async () => {
  const actual = await vi.importActual("react-router-dom");
  return { ...actual, useNavigate: () => mockedNavigate };
});

vi.mock("../../../api/client", () => ({
  forgeApi: {
    getContentPage: vi.fn(),
  },
}));

// react-markdown egyszerűsített mock (jsdom nem renderel markdown-t)
vi.mock("react-markdown", () => ({
  default: ({ children }: { children: string }) => (
    <div data-testid="markdown-content">{children}</div>
  ),
}));
vi.mock("remark-gfm", () => ({ default: () => {} }));

import { forgeApi } from "../../../api/client";
const mockedGetContentPage = vi.mocked(forgeApi.getContentPage);

// Segédfüggvény RetroButton megkereséshez
const getRetroBtn = (labelText: string): HTMLButtonElement =>
  screen
    .getByText(labelText)
    .closest(".button-group")!
    .querySelector("button")!;

// --- Tesztek ---

describe("ContentMissionView", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedNavigate.mockReset();
  });

  it("Első betöltés után a markdown tartalom megjelenik", async () => {
    mockedGetContentPage.mockResolvedValue({
      missionId: "m1",
      missionName: "Test Mission",
      content: "# Fejléc\nEz egy **tartalom**.",
      page: 0,
      pageSize: 100,
      totalLines: 5,
      totalPages: 1,
      hasNextPage: false,
      hasPreviousPage: false,
    });

    render(
      <MemoryRouter>
        <ContentMissionView missionId="m1" />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("markdown-content")).toBeTruthy();
      expect(screen.getByTestId("markdown-content").textContent).toContain(
        "# Fejléc",
      );
    });
  });

  it("hasNextPage: true → 'Load More' gomb látható", async () => {
    mockedGetContentPage.mockResolvedValue({
      missionId: "m1",
      missionName: "Test Mission",
      content: "Első oldal tartalma.",
      page: 0,
      pageSize: 100,
      totalLines: 200,
      totalPages: 2,
      hasNextPage: true,
      hasPreviousPage: false,
    });

    render(
      <MemoryRouter>
        <ContentMissionView missionId="m1" />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("play.loadMore")).toBeTruthy();
    });
  });

  it("'Load More' gomb kattintás → tartalom appendelődik, nem felülírja", async () => {
    mockedGetContentPage
      .mockResolvedValueOnce({
        missionId: "m1",
        missionName: "Test Mission",
        content: "Első rész.",
        page: 0,
        pageSize: 100,
        totalLines: 200,
        totalPages: 2,
        hasNextPage: true,
        hasPreviousPage: false,
      })
      .mockResolvedValueOnce({
        missionId: "m1",
        missionName: "Test Mission",
        content: "Második rész.",
        page: 1,
        pageSize: 100,
        totalLines: 200,
        totalPages: 2,
        hasNextPage: false,
        hasPreviousPage: true,
      });

    render(
      <MemoryRouter>
        <ContentMissionView missionId="m1" />
      </MemoryRouter>,
    );

    // Várjuk meg az első betöltést
    await waitFor(() => {
      expect(screen.getByTestId("markdown-content").textContent).toContain(
        "Első rész.",
      );
    });

    // Load More kattintás
    const loadMoreBtn = getRetroBtn("play.loadMore");
    fireEvent.click(loadMoreBtn);

    await waitFor(() => {
      const content = screen.getByTestId("markdown-content").textContent ?? "";
      expect(content).toContain("Első rész.");
      expect(content).toContain("Második rész.");
    });
  });

  it("hasNextPage: false → 'Load More' gomb nem jelenik meg", async () => {
    mockedGetContentPage.mockResolvedValue({
      missionId: "m1",
      missionName: "Test",
      content: "Teljes tartalom.",
      page: 0,
      pageSize: 100,
      totalLines: 3,
      totalPages: 1,
      hasNextPage: false,
      hasPreviousPage: false,
    });

    render(
      <MemoryRouter>
        <ContentMissionView missionId="m1" />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.queryByText("play.loadMore")).toBeNull();
    });
  });

  it("Group mode: 'Következő' gomb → onComplete() hívódik", async () => {
    mockedGetContentPage.mockResolvedValue({
      missionId: "m1",
      missionName: "Test",
      content: "Tartalom.",
      page: 0,
      pageSize: 100,
      totalLines: 1,
      totalPages: 1,
      hasNextPage: false,
      hasPreviousPage: false,
    });

    const onComplete = vi.fn();

    render(
      <MemoryRouter>
        <ContentMissionView missionId="m1" onComplete={onComplete} />
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByText("play.next")).toBeTruthy();
    });

    const nextBtn = getRetroBtn("play.next");
    fireEvent.click(nextBtn);

    expect(onComplete).toHaveBeenCalledTimes(1);
  });
});
