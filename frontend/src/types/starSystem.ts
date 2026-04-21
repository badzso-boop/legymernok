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
