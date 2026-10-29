import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    {
      name: "javac-local-api",
      configureServer(server) {
        server.middlewares.use("/api", async (request, response) => {
          try {
            const module = await server.ssrLoadModule("/server/handler.ts");
            await module.default(request, response);
          } catch {
            response.statusCode = 503;
            response.setHeader("Content-Type", "application/json");
            response.end(
              JSON.stringify({
                message:
                  "The local data service could not start. Check the VS Code terminal.",
              }),
            );
          }
        });
      },
    },
  ],
  server: { port: 5173, strictPort: true },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    css: false,
  },
});
