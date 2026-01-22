import { createContext, useContext, useState, useEffect } from "react";
import type { ReactNode } from "react";
import type { User, AuthState, LoginResponse } from "../types/auth";
import { jwtDecode } from "jwt-decode";
import axios from "axios";

// API URL (env-ből vagy fallback)
const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api";

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
      username: decoded.sub,
      roles: decoded.roles || [],
      exp: decoded.exp,
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
    isLoading: true,
  });

  useEffect(() => {
    const initAuth = async () => {
      const storedToken = localStorage.getItem("token");

      if (storedToken) {
        const decodedUser = decodeUser(storedToken);

        // 1. Gyors ellenőrzés a token alapján (Lejárat)
        if (
          decodedUser &&
          (decodedUser.exp ? decodedUser.exp * 1000 > Date.now() : true)
        ) {
          // Beállítjuk az ideiglenes állapotot a tokenből
          setState({
            token: storedToken,
            user: decodedUser,
            isAuthenticated: true,
            isLoading: true, // Még töltünk, mert frissítünk a szerverről!
          });

          // 2. Friss adatok lekérése a szerverről (/api/auth/me)
          try {
            const response = await axios.get(`${API_URL}/auth/me`, {
              headers: { Authorization: `Bearer ${storedToken}` },
            });

            // Ha sikerült, felülírjuk a usert a valódi DB adatokkal (pl. friss role-ok)
            setState((prev) => ({
              ...prev,
              user: {
                ...prev.user!, // Megtartjuk az exp-et stb.
                username: response.data.username,
                roles: response.data.roles, // FRISS ROLE-OK!
                // Itt jöhet még avatarUrl, fullName, ha a backend küldi
              },
              isLoading: false,
            }));
          } catch (error) {
            console.error("Failed to fetch user details", error);
            // Ha a token lejárt a szerver szerint, vagy a user törölve lett
            localStorage.removeItem("token");
            setState({
              token: null,
              user: null,
              isAuthenticated: false,
              isLoading: false,
            });
          }
        } else {
          // Lejárt token
          localStorage.removeItem("token");
          setState((prev) => ({ ...prev, isLoading: false }));
        }
      } else {
        // Nincs token
        setState((prev) => ({ ...prev, isLoading: false }));
      }
    };

    initAuth();
  }, []);

  const login = (data: LoginResponse) => {
    localStorage.setItem("token", data.token);
    const user = decodeUser(data.token);
    setState({
      token: data.token,
      user: user,
      isAuthenticated: true,
      isLoading: false,
    });
    // Opcionális: itt is meghívhatnánk a /me végpontot, ha a login válasz nem teljes
  };

  const logout = () => {
    localStorage.removeItem("token");
    setState({
      token: null,
      user: null,
      isAuthenticated: false,
      isLoading: false,
    });
  };

  const hasRole = (role: string): boolean => {
    // A backend már 'flattened' permissionöket is küldhet role-ként,
    // de mi itt egyszerű string egyezést nézünk.
    return state.user?.roles.includes(role) || false;
  };

  return (
    <AuthContext.Provider value={{ ...state, login, logout, hasRole }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
};
