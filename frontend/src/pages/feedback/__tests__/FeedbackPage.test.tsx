import { render, screen, fireEvent } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import { MemoryRouter } from "react-router-dom";
import FeedbackPage from "../FeedbackPage";

vi.mock("react-i18next", () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock("../../../api/client", () => ({
  feedbackApi: {
    list: vi.fn(),
    submit: vi.fn(),
  },
}));

const mockMutate = vi.fn();
let mockMutationState: any = { isPending: false, isError: false, isSuccess: false };
let mockQueryState: any = { data: [], isLoading: false, isError: false };

vi.mock("@tanstack/react-query", async () => {
  const actual = await vi.importActual("@tanstack/react-query");
  return {
    ...actual,
    useQuery: vi.fn(() => mockQueryState),
    useMutation: vi.fn(() => ({ ...mockMutationState, mutate: mockMutate })),
    useQueryClient: vi.fn(() => ({ invalidateQueries: vi.fn() })),
  };
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <FeedbackPage />
    </MemoryRouter>,
  );

describe("FeedbackPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockMutationState = { isPending: false, isError: false, isSuccess: false };
    mockQueryState = { data: [], isLoading: false, isError: false };
  });

  it("megjeleníti a form és a lista címeit", () => {
    renderPage();
    expect(screen.getByText("feedbackPage.title")).toBeInTheDocument();
    expect(screen.getByText("feedbackPage.formTitle")).toBeInTheDocument();
    expect(screen.getByText("feedbackPage.listTitle")).toBeInTheDocument();
  });

  it("üres cím/leírás esetén validációs hibát mutat, nem hívja a mutate-et", () => {
    renderPage();
    fireEvent.click(screen.getByText("feedbackPage.submitButton"));

    expect(screen.getByText("feedbackPage.validationError")).toBeInTheDocument();
    expect(mockMutate).not.toHaveBeenCalled();
  });

  it("kitöltött form beküldésekor meghívja a mutate-et a megfelelő payloaddal", () => {
    renderPage();

    fireEvent.change(screen.getByLabelText("feedbackPage.titleLabel"), {
      target: { value: "Hiányzó funkció" },
    });
    fireEvent.change(screen.getByLabelText("feedbackPage.descriptionLabel"), {
      target: { value: "Kérlek adjátok hozzá X-et" },
    });
    fireEvent.click(screen.getByText("feedbackPage.submitButton"));

    expect(mockMutate).toHaveBeenCalledWith({
      title: "Hiányzó funkció",
      description: "Kérlek adjátok hozzá X-et",
    });
  });

  it("üres listánál az emptyList üzenetet mutatja", () => {
    renderPage();
    expect(screen.getByText("feedbackPage.emptyList")).toBeInTheDocument();
  });

  it("meglévő visszajelzéseket listáz", () => {
    mockQueryState = {
      data: [
        {
          number: 5,
          title: "Sötét mód",
          bodyPreview: "Legyen sötét téma",
          url: "https://github.com/badzso-boop/legymernok/issues/5",
          state: "open",
          authorUsername: "qa_cadet",
          createdAt: "2026-07-30T10:00:00Z",
        },
      ],
      isLoading: false,
      isError: false,
    };
    renderPage();

    expect(screen.getByText("Sötét mód")).toBeInTheDocument();
    expect(screen.getByText("feedbackPage.stateOpen")).toBeInTheDocument();
  });
});
