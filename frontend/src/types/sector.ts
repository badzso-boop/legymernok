export interface SectorResponse {
  id: string;
  name: string;
  description: string | null;
  iconUrl: string | null;
  orderIndex: number;
  starSystemCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CreateSectorRequest {
  name: string;
  description?: string;
  iconUrl?: string;
}
