import { createContext, useContext, useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import type { User, AuthState, LoginResponse } from '../types/auth';
import { jwtDecode } from "jwt-decode";

interface AuthContextType extends AuthState {
    login: (data: LoginResponse) => void;
    logout: () => void;
    hasRole: (role: string) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

// Helper a token dekódolásához
const decodeUser = (token: string): User | null => {
    try {
        const decoded: any = jwtDecode(token);
        return {
            username: decoded.sub, // 'sub' a standard JWT subject (username)
            roles: decoded.roles || [], // A backendünk 'roles' claim-be teszi
            exp: decoded.exp
        };
    } catch (error) {
        return null;
    }
};

export const AuthProvider = ({ children }: { children: ReactNode }) => {
    const [state, setState] = useState<AuthState>({
        user: null,
        token: null,
        isAuthenticated: false,
    });

    // Induláskor megnézzük, van-e elmentett token
    useEffect(() => {
        const storedToken = localStorage.getItem('token');
        if (storedToken) {
            const user = decodeUser(storedToken);
            if (user && (user.exp ? user.exp * 1000 > Date.now() : true)) {
                setState({
                    token: storedToken,
                    user: user,
                    isAuthenticated: true,
                });
            } else {
                localStorage.removeItem('token'); // Lejárt
            }
        }
    }, []);

    const login = (data: LoginResponse) => {
        localStorage.setItem('token', data.token);
        const user = decodeUser(data.token);
        setState({
            token: data.token,
            user: user,
            isAuthenticated: true,
        });
    };

    const logout = () => {
        localStorage.removeItem('token');
        setState({
            token: null,
            user: null,
            isAuthenticated: false,
        });
    };

    const hasRole = (role: string): boolean => {
        return state.user?.roles.includes(role) || false;
    };

    return (
        <AuthContext.Provider value={{ ...state, login, logout, hasRole }}>
            {children}
        </AuthContext.Provider>
    );
};

// Custom hook a könnyű használathoz
export const useAuth = () => {
    const context = useContext(AuthContext);
    if (context === undefined) {
      throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
};