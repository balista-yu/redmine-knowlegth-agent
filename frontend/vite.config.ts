/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

// =============================================================================
// Vite + React + Tailwind v4 + Vitest 設定
// =============================================================================
// - dev: localhost:5173 で待ち受け、/api を backend (localhost:8080) にプロキシ
// - build: dist/ に最小化バンドル出力
// - test: jsdom 環境 + setup ファイルで @testing-library/jest-dom を有効化
// CORS 設定が必要なくなるよう dev proxy で同一オリジン化する (API 仕様: docs/openapi.yaml)。
// =============================================================================

const BACKEND_URL = process.env.VITE_BACKEND_URL ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    host: "0.0.0.0",
    port: 5173,
    proxy: {
      "/api": {
        target: BACKEND_URL,
        changeOrigin: true,
      },
      "/actuator": {
        target: BACKEND_URL,
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: true,
  },
});
