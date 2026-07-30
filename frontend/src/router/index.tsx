import { createHashRouter, Navigate, Outlet } from "react-router-dom";
import ChatWidget from "../components/chat/ChatWidget";
import { useFeatureFlag } from "../hooks/useFeatureFlag";

const RootLayout = () => {
  // A chatbot widget alapértelmezetten KIKAPCSOLT (lásd V6 migráció seed adata),
  // admin a /admin/feature-flags oldalon tudja bekapcsolni.
  const chatbotEnabled = useFeatureFlag("ai_chatbot");

  return (
    <>
      <Outlet />
      {chatbotEnabled && <ChatWidget />}
    </>
  );
};
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
import RoleList from "../pages/admin/roles/RoleList";
import PermissionList from "../pages/admin/permissions/PermissionList";
import FeatureFlagList from "../pages/admin/feature-flags/FeatureFlagList";
import RoleEdit from "../pages/admin/roles/RoleEdit";
import LogList from "../pages/admin/adminlogs/LogList";
import MissionForgePage from "../pages/mission-forge/MissionForgePage";
import QuizPlayerPage from "../pages/mission-forge/QuizPlayerPage";
import StarMapPage from "../pages/starmap/StarMapPage";
import StarSystemDetailPage from "../pages/star-system-detail/StarSystemDetailPage";
import MyForgePage from "../pages/mission-forge/MyForgePage";
import GroupPlayerPage from "../pages/play/GroupPlayerPage";
import ContentMissionPage from "../pages/play/ContentMissionPage";
import CodingMissionPage from "../pages/play/CodingMissionPage";
import FeedbackPage from "../pages/feedback/FeedbackPage";

interface ProtectedRouteProps {
  children: JSX.Element;
  requiredRole?: string;
}

interface NavControl {
  id: string;
  labelKey: string; // Fordításhoz
  color: "red" | "blue" | "yellow" | "green";
  path: string;
  disabled?: boolean;
}

// A LOBBY és ARENA korábban itt állt, "disabled: true"-val, WIP-célpont
// nélkül (a /lobby és /arena route-ok sosem léteztek) — ezek a funkciók
// egyelőre elnapolásra kerültek. Helyettük a VISSZAJELZÉS gomb egy valódi,
// most elkészült funkcióra mutat, nem egy üres helyfoglalóra.
export const mainNavigationControls: NavControl[] = [
  {
    id: "STAR_SYSTEMS",
    labelKey: "controlPanel.starSystems",
    color: "red",
    path: "/star-map",
  },
  {
    id: "MY_FORGE",
    labelKey: "controlPanel.myForge",
    color: "blue",
    path: "/my-forge",
  },
  {
    id: "FEEDBACK",
    labelKey: "controlPanel.feedback",
    color: "yellow",
    path: "/feedback",
  },
];

const ProtectedRoute = ({ children, requiredRole }: ProtectedRouteProps) => {
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

  // 1. Alapvető token ellenőrzés
  if (!token) {
    return <Navigate to="/login" replace />;
  }

  // 2. Szerepkör ellenőrzés (csak ha specifikus role-t kérünk)
  if (requiredRole && !hasRole(requiredRole)) {
    // Ha admin felületre próbál lépni, de nem admin, küldjük a főoldalra
    return <Navigate to="/" replace />;
  }

  return children;
};

export const router = createHashRouter([
  {
    element: <RootLayout />,
    children: [
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
  {
    path: "forge",
    element: (
      <ProtectedRoute>
        <MissionForgePage />
      </ProtectedRoute>
    ),
  },
  {
    path: "forge/:missionId",
    element: (
      <ProtectedRoute>
        <MissionForgePage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/my-forge",
    element: (
      <ProtectedRoute>
        <MyForgePage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/play/quiz/:missionId",
    element: (
      <ProtectedRoute>
        <QuizPlayerPage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/play/group/:groupId",
    element: (
      <ProtectedRoute>
        <GroupPlayerPage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/play/content/:missionId",
    element: (
      <ProtectedRoute>
        <ContentMissionPage />
      </ProtectedRoute>
    ),
  },
  {
    path: "/play/coding/:missionId",
    element: (
      <ProtectedRoute>
        <CodingMissionPage />
      </ProtectedRoute>
    ),
  },

  {
    path: "/star-map",
    element: (
      // Itt kellene egy olyan védett route, ami bármilyen bejelentkezett usernek engedélyezi
      <ProtectedRoute>
        <StarMapPage />
      </ProtectedRoute>
    ),
  },

  {
    path: "/star-systems/:id", // Dinamikus ID
    element: (
      <ProtectedRoute>
        <StarSystemDetailPage />
      </ProtectedRoute>
    ),
  },

  {
    path: "/feedback",
    element: (
      <ProtectedRoute>
        <FeedbackPage />
      </ProtectedRoute>
    ),
  },

  // Védett Admin útvonalak
  {
    path: "/admin",
    element: (
      <ProtectedRoute requiredRole="ROLE_ADMIN">
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
        element: <RoleList />,
      },
      {
        path: "roles/new",
        element: <RoleEdit />,
      },
      {
        path: "roles/:id",
        element: <RoleEdit />,
      },
      {
        path: "permissions",
        element: <PermissionList />,
      },
      {
        path: "feature-flags",
        element: <FeatureFlagList />,
      },
      {
        path: "logs",
        element: <LogList />,
      },
    ],
  },
  ]},  // RootLayout children zárás
]);
