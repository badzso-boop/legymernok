export interface PermissionResponse {
  id: string;
  name: string;
  description: string;
}

export interface RoleResponse {
  id: string;
  name: string;
  description: string;
  permissions: PermissionResponse[];
}

export interface CreateRoleRequest {
  name: string;
  description: string;
  permissionIds: string[]; // UUID-k listája
}
