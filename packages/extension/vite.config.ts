import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import webExtension from "vite-plugin-web-extension";
import { resolve } from "path";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, __dirname, "");
  const backendOrigin = new URL(env.VITE_API_BASE_URL || "http://localhost:8080").origin;
  if (!/^https?:$/.test(new URL(backendOrigin).protocol)) {
    throw new Error("VITE_API_BASE_URL must use http or https");
  }
  return {
  plugins: [
    react(),
    webExtension({
      manifest: "./manifest.json",
      disableAutoLaunch: true,
      skipManifestValidation: true,
      transformManifest: manifest => ({
        ...manifest,
        host_permissions: [
          "https://www.bilibili.com/*",
          `${backendOrigin}/*`,
        ],
      }),
    }),
  ],
  resolve: {
    alias: {
      "@omnistudy/shared": resolve(__dirname, "../shared/src/index.ts"),
      "@shared": resolve(__dirname, "../shared/src/index.ts"),
    },
  },
  };
});
