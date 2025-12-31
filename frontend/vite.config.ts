import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";
import { resolve } from "path";

// https://vite.dev/config/
export default defineConfig(({ command }) => {
  // Megnézzük, hogy build (élesítés) vagy dev (fejlesztés) módban vagyunk-e
  const isProd = command === "build";

  return {
    plugins: [react()],
    // GitHub Pages-hez csak build esetén kell a '/legymernok/' base path.
    // Fejlesztés közben (dev) '/' marad, így nem törik el a localhost:5173.
    base: isProd ? "/legymernok/" : "/",
    server: {
      watch: {
        usePolling: true, // Ez kell a WSL/Docker fájlfigyeléshez
      },
      host: true,
      port: 5173,
      fs: {
        allow: [".."],
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
      css: true,
    },
  } as any;
});
