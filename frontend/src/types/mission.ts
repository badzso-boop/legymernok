export type MissionType =
  | "CODING"
  | "CIRCUIT_SIMULATION"
  | "QUIZ"
  | "CONTENT"
  | "FILL_IN_BLANK";
export type Difficulty = "EASY" | "MEDIUM" | "HARD" | "EXPERT";

export interface MissionResponse {
  id: string;
  starSystemId: string;
  name: string;
  descriptionMarkdown: string;
  templateRepositoryUrl: string | null;
  missionType: MissionType;
  difficulty: Difficulty;
  orderIndex: number | null;
  groupId: string | null;
  groupOrder: number | null;
  createdAt: string;
}

export interface ContentPageResponse {
  missionId: string;
  missionName: string;
  content: string;
  page: number;
  pageSize: number;
  totalLines: number;
  totalPages: number;
  hasNextPage: boolean;
  hasPreviousPage: boolean;
}
