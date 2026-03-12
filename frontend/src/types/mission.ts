export type MissionType = "CODING" | "CIRCUIT_SIMULATION" | "QUIZ";
export type Difficulty = "EASY" | "MEDIUM" | "HARD" | "EXPERT";

export interface MissionResponse {
  id: string;
  starSystemId: string;
  name: string;
  descriptionMarkdown: string;
  templateRepositoryUrl: string | null;
  missionType: MissionType;
  difficulty: Difficulty;
  orderInSystem: number;
  createdAt: string;
}
