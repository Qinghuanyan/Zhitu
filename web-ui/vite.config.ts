import fs from "node:fs";
import path from "node:path";
import { reactRouter } from "@react-router/dev/vite";
import tailwindcss from "@tailwindcss/vite";
import { defineConfig } from "vite";
import tsconfigPaths from "vite-tsconfig-paths";
import svgr from "vite-plugin-svgr";

const ZHITU_HTML_MODULE_ID = "virtual:zhitu-html";
const resolvedZhituHtmlModuleId = `\0${ZHITU_HTML_MODULE_ID}`;
const zhituHtmlPath = path.resolve(__dirname, "../../ai_travel_planner.html");

function zhituHtmlPlugin() {
  return {
    name: "zhitu-html-source",
    resolveId(id: string) {
      if (id === ZHITU_HTML_MODULE_ID) {
        return resolvedZhituHtmlModuleId;
      }
      return null;
    },
    load(id: string) {
      if (id !== resolvedZhituHtmlModuleId) {
        return null;
      }

      const html = fs.readFileSync(zhituHtmlPath, "utf8");
      return `export default ${JSON.stringify(html)};`;
    },
  };
}

export default defineConfig({
  plugins: [tailwindcss(), reactRouter(), tsconfigPaths(), svgr(), zhituHtmlPlugin()],
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
      },
    },
  },
});
