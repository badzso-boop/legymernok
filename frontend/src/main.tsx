import { createRoot } from "react-dom/client";
import "./index.css";
import App from "./App.tsx";
import React from "react";
import "./i18n/config"; // i18n inicializálása
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider, createTheme } from "@mui/material/styles";
import CssBaseline from "@mui/material/CssBaseline";
import { AuthProvider } from "./context/AuthContext.tsx";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";

// React Query kliens
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5, // Az adatok 5 percig frissnek minősülnek
      refetchOnWindowFocus: false, // Megakadályozza az automatikus újra lekérdezést az ablak fókuszálásakor
    },
  },
});

// MUI Theme (Sötét mód alapból, mert űrhajósok vagyunk)
const darkTheme = createTheme({
  palette: {
    mode: "dark",
    primary: {
      main: "#90caf9",
    },
    background: {
      default: "#0f172a", // Tailwind slate-900-hoz hasonló
      paper: "#1e293b",
    },
  },
});

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={darkTheme}>
        <CssBaseline />
        <AuthProvider>
          <App />
          <ReactQueryDevtools initialIsOpen={false} />
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  </React.StrictMode>,
);
