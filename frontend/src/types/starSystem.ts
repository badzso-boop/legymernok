import type { MissionResponse } from "./mission";
import type { MissionGroupResponse } from "./group";

export interface StarSystemResponse {
  id: string;
  name: string;
  description: string;
  createdAt: string;
  updatedAt: string;
}

export interface StarSystemWithMissionsResponse {
  id: string;
  name: string;
  description: string;
  iconUrl: string;
  missions: MissionResponse[];
}

export interface StarSystemItemResponse {
  type: "GROUP" | "MISSION";
  orderIndex: number;
  mission?: MissionResponse;
  group?: MissionGroupResponse;
  groupMissions?: MissionResponse[];
}

export interface StarSystemWithItemsResponse {
  id: string;
  name: string;
  description: string | null;
  iconUrl: string | null;
  createdAt: string;
  updatedAt: string;
  items: StarSystemItemResponse[];
}

export type StarSystemProgressStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";

export interface StarSystemWithProgressResponse {
  id: string;
  name: string;
  description: string | null;
  iconUrl: string | null;
  createdAt: string;
  updatedAt: string;
  status: StarSystemProgressStatus;
}

export interface StarSystemSearchResult {
  id: string;
  name: string;
  description: string | null;
  iconUrl: string | null;
  similarity: number;
}
