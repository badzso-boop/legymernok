export interface FeatureFlagResponse {
  id: string;
  key: string;
  enabled: boolean;
  description: string;
  createdAt: string;
  updatedAt: string;
}

export interface UpdateFeatureFlagRequest {
  enabled: boolean;
  description?: string;
}
