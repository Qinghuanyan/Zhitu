const fs = require("fs");
const path = require("path");

const repoRoot = path.resolve(__dirname, "..");
const externalHtmlPath = path.resolve(repoRoot, "../ZhituHtml/ai_travel_planner.html");
const projectHtmlPath = path.resolve(repoRoot, "ai_travel_planner.html");
const runtimePath = path.resolve(__dirname, "zhitu_runtime.js");

const runtime = fs.readFileSync(runtimePath, "utf8").trim();

function replaceOrThrow(source, pattern, replacement, label) {
  if (!pattern.test(source)) {
    return source;
  }
  pattern.lastIndex = 0;
  return source.replace(pattern, replacement);
}

function patchHtml(html) {
  let next = html;

  next = replaceOrThrow(next, /<title>[\s\S]*?<\/title>/, "<title></title>", "title");

  next = replaceOrThrow(
    next,
    /body\s*\{[\s\S]*?font-family:\s*'Inter',\s*sans-serif;\s*\}/,
    `body {
            margin: 0;
            min-height: 100dvh;
            overflow: hidden;
            background-color: #FAFAFA;
            display: block;
            font-family: 'Inter', 'PingFang SC', 'Microsoft YaHei', sans-serif;
        }`,
    "body style"
  );

  next = replaceOrThrow(
    next,
    /#app-container\s*\{[\s\S]*?display:\s*flex;\s*flex-direction:\s*column;\s*\}/,
    `#app-container {
            position: fixed;
            inset: 0;
            width: 100vw;
            height: 100dvh;
            max-width: 100vw;
            max-height: 100dvh;
            background-color: #FAFAFA;
            overflow: hidden;
            border-radius: 0;
            box-shadow: none;
            border: none;
            display: flex;
            flex-direction: column;
        }`,
    "app container style"
  );

  next = replaceOrThrow(
    next,
    /<h1 class="font-bold text-gray-800 text-sm tracking-wide">[\s\S]*?<\/h1>/,
    `<h1 class="font-bold text-gray-800 text-sm tracking-wide">智途 TravelHub</h1>`,
    "header title"
  );

  next = replaceOrThrow(
    next,
    /<span class="w-1\.5 h-1\.5 rounded-full bg-brand-green animate-pulse"><\/span>[\s\S]*?<\/p>/,
    `<span class="w-1.5 h-1.5 rounded-full bg-brand-green animate-pulse"></span> 行程助手已连接</p>`,
    "header subtitle"
  );

  next = replaceOrThrow(next, /<span>[\s\S]*?<\/span>\s*<\/button>\s*<button class="quick-tab[\s\S]*?data-tab="hotel"/, `<span>行程</span>
                </button>
                <button class="quick-tab flex-shrink-0 flex items-center gap-1.5 px-3 py-1.5 rounded-full text-xs font-semibold border border-gray-200 bg-white/80 text-gray-500 transition-all duration-200 hover:border-brand-cyan/40 hover:text-brand-cyan" data-tab="hotel"`, "itinerary tab");

  next = replaceOrThrow(next, /data-tab="hotel"[\s\S]*?<span>[\s\S]*?<\/span>/, `data-tab="hotel">
                    <i data-lucide="building-2" class="w-3.5 h-3.5 pointer-events-none"></i>
                    <span>住宿</span>`, "hotel tab");

  next = replaceOrThrow(next, /data-tab="food"[\s\S]*?<span>[\s\S]*?<\/span>/, `data-tab="food">
                    <i data-lucide="utensils" class="w-3.5 h-3.5 pointer-events-none"></i>
                    <span>餐饮</span>`, "food tab");

  next = replaceOrThrow(next, /data-tab="activity"[\s\S]*?<span>[\s\S]*?<\/span>/, `data-tab="activity">
                    <i data-lucide="zap" class="w-3.5 h-3.5 pointer-events-none"></i>
                    <span>活动</span>`, "activity tab");

  next = replaceOrThrow(
    next,
    /placeholder="[\s\S]*?"/,
    `placeholder="输入你的目的地、日期或偏好..."`,
    "input placeholder"
  );

  next = replaceOrThrow(
    next,
    /<script>\s*\(function \(\) \{[\s\S]*?<\/script>\s*<script>/,
    `    <script>\n${runtime}\n    </script>\n\n    <script>`,
    "runtime block"
  );

  return next;
}

const externalHtml = fs.readFileSync(externalHtmlPath, "utf8");
const patchedHtml = patchHtml(externalHtml);

fs.writeFileSync(externalHtmlPath, patchedHtml, "utf8");
fs.writeFileSync(projectHtmlPath, patchedHtml, "utf8");

console.log("sync_zhitu_html: ok");
