export type RoleName = string;
export interface UserResponse {
  id: string;
  username: string;
  fullName: string;
  email: string;
  roles: RoleName[];
  avatarUrl: string | null;
  createdAt: string;
  updatedAt: string;
}
