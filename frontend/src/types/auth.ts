export interface User {
    username: string;
    roles: string[]; // Set<String> a backendről tömbként jön
    exp?: number; // Lejárat
}

export interface AuthState {
    user: User | null;
    token: string | null;
    isAuthenticated: boolean;
}

export interface LoginResponse {
    token: string;
    username: string;
}