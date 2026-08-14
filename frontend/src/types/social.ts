export interface CadetSummaryResponse {
  id: string;
  username: string;
  fullName: string | null;
  avatarUrl: string | null;
}

export interface CadetProfileResponse {
  id: string;
  username: string;
  fullName: string | null;
  avatarUrl: string | null;
  currentStreak: number;
  longestStreak: number;
  totalCompletedMissions: number;
  totalCompletedGroups: number;
  followerCount: number;
  followingCount: number;
  memberSince: string;
}

export interface MeResponse {
  id: string;
  username: string;
  email: string;
  fullName: string | null;
  roles: string[];
  giteaUserId: number | null;
  themePreference: "SPACE" | "DARK" | "LIGHT";
  currentStreak: number;
  longestStreak: number;
  createdAt: string;
  updatedAt: string;
}
