export interface StarSystemResponse {
  id: string;
  name: string;
  description: string;
  createdAt: string;
  updatedAt: string;
}

export interface MissionResponse {
  id: string;
  name: string;
  descriptionMarkdown: string;
  orderInSystem: number;
  difficulty: string;
  missionType: string;
}

export interface StarSystemWithMissionsResponse {
  id: string;
  name: string;
  description: string;
  iconUrl: string;
  missions: MissionResponse[];
}
