import { renderHook, waitFor } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach } from "vitest";
import { useFeatureFlag } from "../useFeatureFlag";

const mockGetByKey = vi.fn();
let mockIsAuthenticated = true;

vi.mock("../../api/client", () => ({
  featureFlagApi: {
    getByKey: (...args: unknown[]) => mockGetByKey(...args),
  },
}));

vi.mock("../../context/AuthContext", () => ({
  useAuth: () => ({ isAuthenticated: mockIsAuthenticated }),
}));

describe("useFeatureFlag", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockIsAuthenticated = true;
  });

  it("returns false while not authenticated, without calling the API", async () => {
    mockIsAuthenticated = false;

    const { result } = renderHook(() => useFeatureFlag("ai_chatbot"));

    expect(result.current).toBe(false);
    expect(mockGetByKey).not.toHaveBeenCalled();
  });

  it("returns true once the flag resolves as enabled", async () => {
    mockGetByKey.mockResolvedValue({ key: "ai_chatbot", enabled: true });

    const { result } = renderHook(() => useFeatureFlag("ai_chatbot"));

    await waitFor(() => {
      expect(result.current).toBe(true);
    });
    expect(mockGetByKey).toHaveBeenCalledWith("ai_chatbot");
  });

  it("returns false if the flag is disabled", async () => {
    mockGetByKey.mockResolvedValue({ key: "ai_chatbot", enabled: false });

    const { result } = renderHook(() => useFeatureFlag("ai_chatbot"));

    await waitFor(() => {
      expect(mockGetByKey).toHaveBeenCalled();
    });
    expect(result.current).toBe(false);
  });

  it("fails closed (false) if the API call errors", async () => {
    mockGetByKey.mockRejectedValue(new Error("network error"));

    const { result } = renderHook(() => useFeatureFlag("ai_chatbot"));

    await waitFor(() => {
      expect(mockGetByKey).toHaveBeenCalled();
    });
    expect(result.current).toBe(false);
  });
});
