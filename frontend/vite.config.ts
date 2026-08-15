import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";
import { resolve } from "path";

// https://vite.dev/config/
export default defineConfig(() => {
  const isGithubPages = process.env.GITHUB_PAGES === "true";

  return {
    plugins: [react()],
    // GitHub Pages-hez csak build esetén kell a '/legymernok/' base path.
    // Fejlesztés közben (dev) '/' marad, így nem törik el a localhost:5173.
    base: isGithubPages ? "/legymernok/" : "/",
    server: {
      watch: {
        usePolling: true, // Ez kell a WSL/Docker fájlfigyeléshez
      },
      host: true,
      port: 5173,
      fs: {
        allow: [".."],
      },
      // VITE_API_URL relatív (/api), hogy a prod buildben az nginx tudja
      // proxyzni — dev módban ugyanezt itt kell megoldani, különben a
      // localhost:5173/api hívások a semmibe mennek.
      proxy: {
        "/api": "http://localhost:8080",
        "/ws-mission-logs": { target: "http://localhost:8080", ws: true },
        "/ws-log": { target: "http://localhost:8080", ws: true },
      },
    },
    resolve: {
      alias: {
        "@root": resolve(__dirname, ".."),
      },
    },
    test: {
      globals: true,
      environment: "jsdom",
      setupFiles: "./src/setupTests.ts",
      css: false,
      // WSL2-ben a thread pool hajlamos timeout-olni; forks stabilabb
      pool: "forks",
      testTimeout: 15000,
      hookTimeout: 15000,
      server: {
        deps: {
          inline: [/@mui\/x-data-grid/],
        },
      },
    },
    define: {
      global: "window",
    },
  } as any;
});
