import { createRoot } from "react-dom/client";
import App from "./App.tsx";
import React from "react";
import "./i18n/config"; // i18n inicializálása
import "./theme/animations.css"; // StarfieldBackground/NebulaLayer @keyframes
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "./context/AuthContext.tsx";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { ThemeModeProvider } from "./theme/ThemeModeProvider.tsx";

// React Query kliens
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // Az adatok 5 percig frissnek minősülnek
      refetchOnWindowFocus: false, // Megakadályozza az automatikus újra lekérdezést az ablak fókuszálásakor
    },
  },
});

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeModeProvider>
        <AuthProvider>
          <App />
          <ReactQueryDevtools initialIsOpen={false} />
        </AuthProvider>
      </ThemeModeProvider>
    </QueryClientProvider>
  </React.StrictMode>,
);
