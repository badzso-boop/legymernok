export interface ContinueResponse {
  type: "MISSION" | "GROUP";
  missionId: string | null;
  groupId: string | null;
  starSystemId: string;
  name: string;
}

export interface ActivityFeedItemResponse {
  cadetId: string;
  cadetUsername: string;
  type: "GROUP_STEP" | "FILL_IN_BLANK" | "QUIZ";
  label: string;
  occurredAt: string;
}
