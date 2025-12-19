import { createBrowserRouter, Navigate } from 'react-router-dom';
import MainLayout from '../layouts/MainLayout';
import AdminLayout from '../layouts/AdminLayout';
import LandingPage from '../pages/LandingPage';
import LoginPage from '../pages/auth/LoginPage';
import RegisterPage from '../pages/auth/RegisterPage';
import UserList from '../pages/admin/UserList';
import UserEdit from '../pages/admin/UserEdit';
import { useAuth } from '../context/AuthContext';

// Egyszerűbb védelem: csak ha van token
const ProtectedRoute = ({ children }: { children: JSX.Element }) => {
    const { isAuthenticated, hasRole, user } = useAuth();

    const token = localStorage.getItem('token');

    if (!token) {
        return <Navigate to="/login" replace />;
    }

    if (!hasRole('ROLE_ADMIN')) {
        return <Navigate to="/" replace />;
    }

    return children;
};

export const router = createBrowserRouter([
    {
        path: '/',
        element: <MainLayout />,
        children: [
            { index: true, element: <LandingPage /> },
            { path: 'login', element: <LoginPage /> },
            { path: 'register', element: <RegisterPage /> },
        ],
    },
    {
        path: '/admin',
        element: (
            <ProtectedRoute>
                <AdminLayout />
            </ProtectedRoute>
        ),
        children: [
            { index: true, element: <Navigate to="/admin/users" replace /> },
            { path: 'users', element: <UserList /> },
            { path: 'users/:id', element: <UserEdit /> },
            { path: 'courses', element: <div>Kurzusok fejlesztés alatt...</div> },
            { path: 'missions', element: <div>Feladatok fejlesztés alatt...</div> },
            { path: 'roles', element: <div>Role-ok fejlesztés alatt...</div> },
            { path: 'permissions', element: <div>Permission-ök fejlesztés alatt...</div> },
        ],
    },
]);