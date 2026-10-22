import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173, strictPort: true,
    proxy: {
      "/api": "http://127.0.0.1:8080",
      "/oauth2": "http://127.0.0.1:8080",
      "/login/oauth2": "http://127.0.0.1:8080",
    },
  },
  test: { environment: "jsdom", setupFiles: "./src/test-setup.ts" },
  build: { rollupOptions: { output: { manualChunks: { charts: ["recharts"], forms: ["react-hook-form", "@hookform/resolvers/zod", "zod"] } } } },
});
