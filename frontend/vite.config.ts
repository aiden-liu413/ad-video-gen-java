import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

const backendTarget = process.env.VITE_API_TARGET || "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "0.0.0.0",
    port: 8002,
    proxy: {
      "/api": {
        target: backendTarget,
        changeOrigin: true,
        secure: false
      },
      "/uploads": {
        target: backendTarget,
        changeOrigin: true,
        secure: false
      },
      "/s": {
        target: backendTarget,
        changeOrigin: true,
        secure: false
      }
    }
  }
});
