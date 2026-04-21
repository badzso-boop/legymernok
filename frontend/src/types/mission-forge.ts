import type { MissionType, Difficulty } from "./mission";

export type VerificationStatus =
  | "DRAFT"
  | "PENDING"
  | "APPROVED"
  | "REJECTED"
  | "SUCCESS"
  | "FAILED"
  | "REVIEW_NEEDED";

export interface CreateMissionInitialRequest {
  starSystemId: string;
  name: string;
  descriptionMarkdown?: string;
  missionType: MissionType;
  difficulty: Difficulty;
  orderIndex: number;
  templateLanguage: "javascript" | "python";
}

export interface MissionForgeContentRequest {
  missionId: string;
  files: Record<string, string>;
}

export interface MissionForgeResponse {
  id: string;
  starSystemId: string;
  name: string;
  descriptionMarkdown: string;
  templateRepositoryUrl: string | null;
  missionType: MissionType;
  difficulty: Difficulty;
  orderIndex: number;
  createdAt: string;
  ownerId: string;
  ownerUsername: string;
  verificationStatus: VerificationStatus;
}
