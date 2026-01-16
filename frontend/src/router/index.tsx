import { createHashRouter, Navigate } from "react-router-dom";
import { CircularProgress } from "@mui/material";
import MainLayout from "../layouts/MainLayout";
import AdminLayout from "../layouts/AdminLayout";
import LandingPage from "../pages/LandingPage";
import LoginPage from "../pages/auth/LoginPage";
import RegisterPage from "../pages/auth/RegisterPage";
import UserList from "../pages/admin/cadets/UserList";
import UserEdit from "../pages/admin/cadets/UserEdit";
import StarSystemEdit from "../pages/admin/star-system/StarSystemEdit";
import StarSystemList from "../pages/admin/star-system/StarSystemList";
import { useAuth } from "../context/AuthContext";
import type { JSX } from "react";
import ChangelogPage from "../pages/changelog/ChangelogPage";
import MissionEdit from "../pages/admin/missions/MissionEdit";
import MissionList from "../pages/admin/missions/MissionList";

// Egyszerűbb védelem: csak ha van token
const ProtectedRoute = ({ children }: { children: JSX.Element }) => {
  const { hasRole, isLoading } = useAuth();

  if (isLoading) {
    return (
      <CircularProgress
        size={80}
        thickness={2}
        sx={{ color: "primary.main" }}
      />
    );
  }

  const token = localStorage.getItem("token");

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  if (!hasRole("ROLE_ADMIN")) {
    return <Navigate to="/" replace />;
  }

  return children;
};

export const router = createHashRouter([
  // Publikus útvonalak (MainLayout alatt)
  {
    path: "/",
    element: <MainLayout />,
    children: [
      {
        path: "/",
        element: <LandingPage />,
      },
      {
        path: "/login",
        element: <LoginPage />,
      },
      {
        path: "/register",
        element: <RegisterPage />,
      },
      {
        path: "/changelog",
        element: <ChangelogPage />,
      },
    ],
  },

  // Védett Admin útvonalak
  {
    path: "/admin",
    element: (
      <ProtectedRoute>
        <AdminLayout />
      </ProtectedRoute>
    ),
    children: [
      {
        index: true, // Ez jelenti a "/admin" alapértelmezését
        element: <Navigate to="/admin/dashboard" replace />,
      },
      {
        path: "dashboard",
        element: <div>Admin Dashboard (Work in Progress)</div>,
      },
      // Felhasználók kezelése
      {
        path: "users",
        element: <UserList />,
      },
      {
        path: "users/new",
        element: <UserEdit />,
      },
      {
        path: "users/:id",
        element: <UserEdit />,
      },
      // Csillagrendszerek kezelése
      {
        path: "star-systems",
        element: <StarSystemList />,
      },
      {
        path: "star-systems/new",
        element: <StarSystemEdit />,
      },
      {
        path: "star-systems/:id",
        element: <StarSystemEdit />,
      },
      // Egyéb (Work in Progress) menüpontok
      {
        path: "courses",
        element: <div>Kurzusok fejlesztés alatt...</div>,
      },
      {
        path: "missions",
        element: <MissionList />,
      },
      {
        path: "missions/new",
        element: <MissionEdit />,
      },
      {
        path: "missions/:id",
        element: <MissionEdit />,
      },
      {
        path: "roles",
        element: <div>Role-ok fejlesztés alatt...</div>,
      },
      {
        path: "permissions",
        element: <div>Permission-ök fejlesztés alatt...</div>,
      },
    ],
  },
]);
