export type Role = string;
export interface UserResponse {
  id: string;
  username: string;
  fullName: string;
  email: string;
  roles: Role[];
  avatarUrl: string | null;
  createdAt: string;
  updatedAt: string;
}
