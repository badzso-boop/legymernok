import { describe, it, expect } from "vitest";
import { findNextPlayableMission, getMissionPlayPath } from "../missionNavigation";
import type { MissionResponse } from "../../types/mission";

function makeMission(overrides: Partial<MissionResponse>): MissionResponse {
  return {
    id: "id",
    starSystemId: "ss-1",
    name: "Mission",
    descriptionMarkdown: "",
    templateRepositoryUrl: null,
    missionType: "CONTENT",
    difficulty: "EASY",
    orderIndex: 0,
    groupId: null,
    groupOrder: null,
    createdAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

describe("getMissionPlayPath", () => {
  it("returns the play route for CONTENT/QUIZ/CODING missions", () => {
    expect(getMissionPlayPath(makeMission({ id: "c1", missionType: "CONTENT" }))).toBe(
      "/play/content/c1",
    );
    expect(getMissionPlayPath(makeMission({ id: "q1", missionType: "QUIZ" }))).toBe(
      "/play/quiz/q1",
    );
    expect(getMissionPlayPath(makeMission({ id: "co1", missionType: "CODING" }))).toBe(
      "/play/coding/co1",
    );
  });

  it("returns null for types with no standalone play route", () => {
    expect(getMissionPlayPath(makeMission({ missionType: "FILL_IN_BLANK" }))).toBeNull();
    expect(getMissionPlayPath(makeMission({ missionType: "CIRCUIT_SIMULATION" }))).toBeNull();
  });
});

describe("findNextPlayableMission", () => {
  it("finds the next mission in orderIndex order", () => {
    const missions = [
      makeMission({ id: "a", orderIndex: 0, missionType: "CONTENT" }),
      makeMission({ id: "b", orderIndex: 1, missionType: "QUIZ" }),
      makeMission({ id: "c", orderIndex: 2, missionType: "CODING" }),
    ];
    expect(findNextPlayableMission(missions, "a")?.id).toBe("b");
    expect(findNextPlayableMission(missions, "b")?.id).toBe("c");
  });

  it("skips missions with no standalone play route", () => {
    const missions = [
      makeMission({ id: "a", orderIndex: 0, missionType: "CONTENT" }),
      makeMission({ id: "b", orderIndex: 1, missionType: "FILL_IN_BLANK" }),
      makeMission({ id: "c", orderIndex: 2, missionType: "CODING" }),
    ];
    expect(findNextPlayableMission(missions, "a")?.id).toBe("c");
  });

  it("returns null when the current mission is the last playable one", () => {
    const missions = [
      makeMission({ id: "a", orderIndex: 0, missionType: "CONTENT" }),
      makeMission({ id: "b", orderIndex: 1, missionType: "QUIZ" }),
    ];
    expect(findNextPlayableMission(missions, "b")).toBeNull();
  });

  it("returns null when the current mission id isn't in the list", () => {
    const missions = [makeMission({ id: "a", orderIndex: 0 })];
    expect(findNextPlayableMission(missions, "missing")).toBeNull();
  });

  it("sorts by orderIndex regardless of input array order, nulls last", () => {
    const missions = [
      makeMission({ id: "c", orderIndex: null }),
      makeMission({ id: "a", orderIndex: 0 }),
      makeMission({ id: "b", orderIndex: 1 }),
    ];
    expect(findNextPlayableMission(missions, "a")?.id).toBe("b");
    expect(findNextPlayableMission(missions, "b")?.id).toBe("c");
  });
});
