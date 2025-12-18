import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import MainLayout from './layouts/MainLayout';
import LandingPage from './pages/LandingPage';
import LoginPage from './pages/auth/LoginPage';
import RegisterPage from './pages/auth/RegisterPage';

// A Router konfigurációja
const router = createBrowserRouter([
  {
    path: '/',
    element: <MainLayout />, // A közös fejléc
    children: [
      {
        index: true, // Ez a gyökér útvonal (/)
        element: <LandingPage />,
      },
      {
        path: 'login',
        element: <LoginPage />,
      },
      {
        path: 'register',
        element: <RegisterPage />,
      },
    ],
  },
  // Később ide jön az /admin útvonal az AdminLayout-tal!
]);

function App() {
  return <RouterProvider router={router} />;
}

export default App;
