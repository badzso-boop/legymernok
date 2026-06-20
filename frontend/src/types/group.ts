import type { MissionResponse } from "./mission";

export interface MissionGroupResponse {
  id: string;
  name: string;
  description: string | null;
  starSystemId: string;
  orderIndex: number;
  createdAt: string;
  updatedAt: string;
}

export interface MissionGroupWithMissionsResponse {
  id: string;
  name: string;
  description: string | null;
  starSystemId: string;
  starSystemName: string;
  orderIndex: number;
  missions: MissionResponse[];
}

export interface GroupProgressResponse {
  groupId: string;
  completed: boolean;
  nextMissionId: string | null;
  completedCount: number;
  totalCount: number;
  startedAt: string;
  lastUpdatedAt: string;
  completedAt: string | null;
}

export type GroupProgressStatus = "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED";

export interface GroupDisplayProgress {
  status: GroupProgressStatus;
  completedCount: number;
  totalCount: number;
}

export interface CreateMissionGroupRequest {
  name: string;
  description?: string;
  starSystemId: string;
  orderIndex?: number;
}

export interface ReorderResponse {
  updated: Array<{ id: string; orderIndex: number }>;
}
