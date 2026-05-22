const fs = require("fs");
const path = require("path");
const assert = require("assert");

const htmlPath = path.resolve(__dirname, "../ai_travel_planner.html");
const html = fs.readFileSync(htmlPath, "utf8");

const scriptMatches = [...html.matchAll(/<script(?:[^>]*)>([\s\S]*?)<\/script>/g)];
if (scriptMatches.length < 2) {
  throw new Error("Failed to locate runtime script block in ai_travel_planner.html");
}
const runtimeScript = scriptMatches[scriptMatches.length - 2][1];
new Function(runtimeScript);

class FakeClassList {
  constructor() {
    this.set = new Set();
  }
  add(...tokens) {
    tokens.forEach((token) => this.set.add(token));
  }
  remove(...tokens) {
    tokens.forEach((token) => this.set.delete(token));
  }
  toggle(token, force) {
    if (force === undefined) {
      if (this.set.has(token)) {
        this.set.delete(token);
        return false;
      }
      this.set.add(token);
      return true;
    }
    if (force) {
      this.set.add(token);
      return true;
    }
    this.set.delete(token);
    return false;
  }
  contains(token) {
    return this.set.has(token);
  }
}

class FakeNode {
  constructor(id = "") {
    this.id = id;
    this.innerHTML = "";
    this.textContent = "";
    this.style = {};
    this.dataset = {};
    this.value = "";
    this.scrollHeight = 1000;
    this.scrollTop = 0;
    this.classList = new FakeClassList();
    this._queryMap = new Map();
    this._queryAllMap = new Map();
  }
  setQuery(selector, node) {
    this._queryMap.set(selector, node);
  }
  setQueryAll(selector, nodes) {
    this._queryAllMap.set(selector, nodes);
  }
  querySelector(selector) {
    return this._queryMap.get(selector) || null;
  }
  querySelectorAll(selector) {
    return this._queryAllMap.get(selector) || [];
  }
  getAttribute(name) {
    if (name === "data-tab") return this.dataset.tab || null;
    if (name === "data-profile-tab") return this.dataset.profileTab || null;
    return null;
  }
  closest(selector) {
    if (selector === `#${this.id}`) return this;
    return null;
  }
}

const listeners = { click: [], keydown: [] };
const sentMessages = [];

const chatContainer = new FakeNode("chat-container");
const chatInput = new FakeNode("chat-input");
const sendBtn = new FakeNode("send-btn");
const headerMapBtn = new FakeNode("header-map-btn");
const userAvatarBtn = new FakeNode("user-avatar-btn");
const profileCloseBtn = new FakeNode("profile-overlay-close");
const itineraryCloseBtn = new FakeNode("itinerary-overlay-close");
const profileHistory = new FakeNode("profile-tab-history");
const profileFavorites = new FakeNode("profile-tab-favorites");
const itineraryBody = new FakeNode("itinerary-body");
const itineraryTitle = new FakeNode("itinerary-title");
const itinerarySubtitle = new FakeNode("itinerary-subtitle");
const headerTitle = new FakeNode("header-title");
const headerSubtitle = new FakeNode("header-subtitle");

const profileName = new FakeNode("profile-name");
const statCards = [new FakeNode(), new FakeNode(), new FakeNode()];
statCards.forEach((card) => {
  const value = new FakeNode();
  const label = new FakeNode();
  card.setQueryAll("p", [value, label]);
});

const profileOverlay = new FakeNode("profile-overlay");
profileOverlay.setQuery("h3.font-bold.text-gray-900.text-base.leading-tight", profileName);
profileOverlay.setQueryAll(".grid.grid-cols-3 > div", statCards);

const itineraryOverlay = new FakeNode("itinerary-full-overlay");
itineraryOverlay.setQuery("h2", itineraryTitle);
itineraryOverlay.setQuery("p", itinerarySubtitle);
itineraryOverlay.setQuery(".flex-1.overflow-y-auto", itineraryBody);

const quickTabs = ["itinerary", "hotel", "food", "activity", "map"].map((tab) => {
  const node = new FakeNode();
  node.dataset.tab = tab;
  return node;
});

const profileTabs = ["history", "favorites"].map((tab) => {
  const node = new FakeNode();
  node.dataset.profileTab = tab;
  return node;
});

const elementsById = new Map([
  ["chat-container", chatContainer],
  ["chat-input", chatInput],
  ["send-btn", sendBtn],
  ["header-map-btn", headerMapBtn],
  ["user-avatar-btn", userAvatarBtn],
  ["profile-overlay-close", profileCloseBtn],
  ["itinerary-overlay-close", itineraryCloseBtn],
  ["profile-tab-history", profileHistory],
  ["profile-tab-favorites", profileFavorites],
  ["profile-overlay", profileOverlay],
  ["itinerary-full-overlay", itineraryOverlay],
]);

global.window = {
  RikkaZhituBridge: {
    postMessage(message) {
      sentMessages.push(JSON.parse(message));
    },
  },
};

global.document = {
  body: {},
  getElementById(id) {
    return elementsById.get(id) || null;
  },
  querySelector(selector) {
    if (selector === "header h1") return headerTitle;
    if (selector === "header p") return headerSubtitle;
    return null;
  },
  querySelectorAll(selector) {
    if (selector === ".quick-tab[data-tab]") return quickTabs;
    if (selector === ".profile-tab") return profileTabs;
    return [];
  },
  addEventListener(type, handler) {
    if (listeners[type]) listeners[type].push(handler);
  },
};

global.requestAnimationFrame = (fn) => fn();
global.lucide = { createIcons() {} };

eval(runtimeScript);

assert.strictEqual(typeof window.__ZHITU_RECEIVE_STATE__, "function", "runtime should expose __ZHITU_RECEIVE_STATE__");

const baseState = {
  version: 1,
  context: "android",
  currentTab: "home",
  conversation: {
    id: "conv-1",
    title: "杭州旅行",
    isGenerating: false,
    suggestions: ["帮我生成行程"],
    messages: [],
  },
  travelPlan: null,
  travelUiState: {
    searchQuery: "杭州两日游，两个人，预算 3000",
    weatherSummary: "晴 24°C",
    selectedMapFilter: "hotel",
    selectedDestination: { id: "dest-1", name: "杭州", subtitle: "浙江" },
    suggestions: [{ id: "s1", name: "杭州", subtitle: "浙江" }],
  },
  user: {
    name: "测试用户",
    subtitle: "杭州",
    stats: [
      { label: "历史行程", value: "1" },
      { label: "收藏夹", value: "2" },
      { label: "地图点位", value: "0" },
    ],
  },
  historyConversations: [],
  historyTrips: [],
  favoriteItems: [],
  currentTripSummary: null,
  profileUiState: { activeTab: "history" },
  availableActions: {
    sendMessage: true,
    generatePlan: true,
    openMap: true,
    openRecommendations: true,
    openLegacyPanel: true,
    exportConversation: false,
  },
  navigationTargets: {},
};

window.__ZHITU_RECEIVE_STATE__(JSON.stringify(baseState));
assert(chatContainer.innerHTML.includes("杭州"), "home tab should render real destination state");
assert(!chatContainer.innerHTML.includes("地图已准备就绪"), "home tab must not render old map demo text");
assert(!chatContainer.innerHTML.includes("已记录您关于"), "home tab must not render old echo demo text");

window.__ZHITU_RECEIVE_STATE__(
  JSON.stringify({
    ...baseState,
    currentTab: "itinerary",
    travelPlan: {
      brief: {
        destination: "杭州",
        origin: "上海",
        dateRange: "2026-06-01 ~ 2026-06-02",
        days: 2,
        travelerCount: 2,
        budgetText: "预算 3000",
        budgetLevel: "中",
        travelStyleTags: ["美食", "西湖"],
        transportPreferences: [],
        userIntentSummary: "两日轻松游",
      },
      hotels: [],
      foods: [],
      activities: [],
      pois: [],
      itineraryDays: [
        {
          dayIndex: 1,
          title: "西湖漫步",
          dateText: "2026-06-01",
          weatherHint: "晴",
          items: [
            {
              id: "item-1",
              timeSlot: "09:00",
              title: "断桥 - 白堤",
              description: "步行游览",
              category: "SIGHT",
              estimatedCost: "免费",
              transportHint: "步行",
            },
          ],
        },
      ],
      status: "Generated",
    },
  })
);
assert(chatContainer.innerHTML.includes("西湖漫步"), "itinerary tab should render real itinerary title");
assert(itineraryBody.innerHTML.includes("西湖漫步"), "itinerary overlay should render real itinerary data");

window.__ZHITU_RECEIVE_STATE__(
  JSON.stringify({
    ...baseState,
    currentTab: "map",
    travelPlan: {
      brief: {
        destination: "杭州",
        origin: "",
        dateRange: "",
        days: 2,
        travelerCount: 2,
        budgetText: "",
        budgetLevel: "",
        travelStyleTags: [],
        transportPreferences: [],
        userIntentSummary: "",
      },
      hotels: [],
      foods: [],
      activities: [],
      pois: [{ id: "poi-1", name: "西湖", category: "景点", address: "杭州西湖" }],
      itineraryDays: [],
      status: "Generated",
    },
  })
);
assert(chatContainer.innerHTML.includes("西湖"), "map tab should render real poi data");
assert(!chatContainer.innerHTML.includes("5 个核心景点"), "map tab must not render old hardcoded demo count");

chatInput.value = "杭州两日游";
listeners.click.forEach((handler) =>
  handler({
    target: {
      closest(selector) {
        return selector === "#send-btn" ? sendBtn : null;
      },
    },
    preventDefault() {},
    stopPropagation() {},
  })
);
assert(
  sentMessages.some((message) => message.action === "send_message" && message.payload.text === "杭州两日游"),
  "click send should bridge send_message with real input value"
);

window._dispatchTabByKey("map");
assert(
  sentMessages.some((message) => message.action === "switch_tab" && message.payload.tab === "map"),
  "_dispatchTabByKey should bridge switch_tab"
);

console.log("zhitu-runtime-smoke-test: ok");
