export interface RecentCadetSummary {
  id: string;
  username: string;
  fullName: string | null;
  createdAt: string;
}

export interface PopularStarSystemSummary {
  id: string;
  name: string;
  startedByCount: number;
}

export interface AdminStatsResponse {
  totalCadets: number;
  totalStarSystems: number;
  totalMissions: number;
  newCadetsThisWeek: number;
  openFeedbackCount: number;
  recentCadets: RecentCadetSummary[];
  mostStartedStarSystems: PopularStarSystemSummary[];
}
