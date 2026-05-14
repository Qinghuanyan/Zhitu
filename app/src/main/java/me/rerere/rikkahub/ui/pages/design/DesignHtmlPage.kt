package me.rerere.rikkahub.ui.pages.design

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.rememberWebViewState
import me.rerere.rikkahub.ui.context.LocalNavController
import java.nio.charset.StandardCharsets

private const val DESIGN_BRIDGE_NAME = "RikkaDesignBridge"

private data class Hotzone(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
    val target: String,
)

private class DesignHtmlBridge(
    private val navigate: (String) -> Unit,
) {
    @JavascriptInterface
    fun navigate(target: String) {
        navigate(target)
    }
}

@Composable
fun DesignHtmlPage(assetPath: String) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val bridge = remember(navController, mainHandler) {
        DesignHtmlBridge { target ->
            mainHandler.post {
                when (target) {
                    "back" -> navController.popBackStack()
                    "home" -> navController.clearAndNavigate(
                        Screen.TravelHub(
                            id = "design-home",
                            startTab = "home",
                        )
                    )

                    "map" -> navController.clearAndNavigate(
                        Screen.TravelHub(
                            id = "design-map",
                            startTab = "map",
                        )
                    )

                    "itinerary" -> navController.clearAndNavigate(
                        Screen.TravelHub(
                            id = "design-itinerary",
                            startTab = "itinerary",
                        )
                    )

                    "ai" -> navController.clearAndNavigate(Screen.Assistant)
                    "profile" -> navController.clearAndNavigate(Screen.Favorite)
                    "search-home" -> navController.clearAndNavigate(Screen.Assistant)
                    "search-map" -> navController.clearAndNavigate(
                        Screen.TravelHub(
                            id = "design-map",
                            startTab = "map",
                        )
                    )

                    "search-hotel" -> navController.navigate(Screen.TravelHotels("design-hotel")) { launchSingleTop = true }
                    "search-food" -> navController.navigate(Screen.TravelFoods("design-food")) { launchSingleTop = true }
                    "search-activity" -> navController.navigate(Screen.TravelActivities("design-activity")) { launchSingleTop = true }
                    "hotel" -> navController.navigate(Screen.TravelHotels("design-hotel")) { launchSingleTop = true }
                    "food" -> navController.navigate(Screen.TravelFoods("design-food")) { launchSingleTop = true }
                    "activity" -> navController.navigate(Screen.TravelActivities("design-activity")) { launchSingleTop = true }
                    "history" -> navController.navigate(Screen.History) { launchSingleTop = true }
                    "favorite" -> navController.navigate(Screen.Favorite) { launchSingleTop = true }
                    "settings" -> navController.navigate(Screen.Setting) { launchSingleTop = true }
                    "about" -> navController.navigate(Screen.SettingAbout) { launchSingleTop = true }
                    "stats" -> navController.navigate(Screen.Stats) { launchSingleTop = true }
                }
            }
        }
    }

    val htmlContent = remember(assetPath, context) {
        runCatching {
            context.assets.open(assetPath).use { input ->
                String(input.readBytes(), StandardCharsets.UTF_8)
            }
        }.getOrElse { error ->
            """
            <html>
              <head><meta charset="utf-8" /></head>
              <body style="font-family:sans-serif;padding:24px;">
                <h2>页面加载失败</h2>
                <p>无法读取设计稿资源：</p>
                <pre>$assetPath</pre>
                <pre>${error.message ?: error::class.java.simpleName}</pre>
              </body>
            </html>
            """.trimIndent()
        }
    }

    val baseUrl = remember(assetPath) {
        val lastSlash = assetPath.lastIndexOf('/')
        val dir = if (lastSlash >= 0) assetPath.substring(0, lastSlash + 1) else ""
        "file:///android_asset/$dir"
    }

    val state = rememberWebViewState(
        data = htmlContent,
        baseUrl = baseUrl,
        mimeType = "text/html",
        interfaces = mapOf(DESIGN_BRIDGE_NAME to bridge),
        settings = {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
        }
    )

    var injectedUrl by remember(assetPath) { mutableStateOf<String?>(null) }
    val bindingScript = remember(assetPath) { buildBindingScript(assetPath) }

    WebView(
        state = state,
        modifier = Modifier.fillMaxSize(),
        onUpdated = { webView ->
            val currentUrl = state.currentUrl
            if (!state.isLoading && !currentUrl.isNullOrBlank() && injectedUrl != currentUrl) {
                injectedUrl = currentUrl
                webView.evaluateJavascript(bindingScript, null)
            }
        }
    )
}

private fun buildBindingScript(assetPath: String): String {
    val textEntriesJs = textBindings().joinToString(",\n") { (label, target) ->
        "[${jsString(label)}, ${jsString(target)}]"
    }
    val hotzonesJs = hotzonesForAsset(assetPath).joinToString(",\n") { zone ->
        "{l:${zone.left},t:${zone.top},w:${zone.width},h:${zone.height},target:${jsString(zone.target)}}"
    }

    return """
        (function() {
          const bridge = window.$DESIGN_BRIDGE_NAME;
          if (!bridge || !document.body) return;

          const normalize = (value) => (value || "").replace(/\s+/g, " ").trim();

          const pickTapTarget = (element) => {
            const candidates = [];
            let current = element;
            for (let i = 0; i < 8 && current; i += 1) {
              const rect = current.getBoundingClientRect();
              if (rect.width >= 36 && rect.height >= 20) {
                candidates.push({ element: current, rect: rect });
              }
              current = current.parentElement;
            }

            const cardCandidate = candidates.find((candidate) => {
              const rect = candidate.rect;
              return rect.width >= window.innerWidth * 0.40 &&
                rect.width <= window.innerWidth * 0.96 &&
                rect.height >= 44 &&
                rect.height <= window.innerHeight * 0.35;
            });
            if (cardCandidate) return cardCandidate.element;

            return candidates.length > 0 ? candidates[candidates.length - 1].element : element;
          };

          const bindTarget = (label, target, mode) => {
            document.querySelectorAll("body *").forEach((element) => {
              if (element.dataset.rikkaDesignBound) return;
              const text = normalize(element.textContent);
              if (!text) return;
              const matched = mode === "contains" ? text.indexOf(label) !== -1 : text === label;
              if (!matched) return;
              if (element.children.length > 0 && normalize(element.innerText) !== text) return;
              const tapTarget = pickTapTarget(element);
              if (!tapTarget || tapTarget.dataset.rikkaDesignBound) return;
              tapTarget.dataset.rikkaDesignBound = "1";
              tapTarget.style.cursor = "pointer";
              tapTarget.addEventListener("click", function(event) {
                event.preventDefault();
                event.stopPropagation();
                bridge.navigate(target);
              }, true);
            });
          };

          const ensureOverlayRoot = () => {
            let root = document.getElementById("rikka-design-hotzones");
            if (!root) {
              root = document.createElement("div");
              root.id = "rikka-design-hotzones";
              root.style.position = "fixed";
              root.style.left = "0";
              root.style.top = "0";
              root.style.right = "0";
              root.style.bottom = "0";
              root.style.pointerEvents = "none";
              root.style.zIndex = "2147483647";
              document.body.appendChild(root);
            }
            root.innerHTML = "";
            return root;
          };

          const bindHotzone = (zone) => {
            const root = ensureOverlayRoot();
            const button = document.createElement("button");
            button.type = "button";
            button.setAttribute("aria-label", zone.target);
            button.style.position = "absolute";
            button.style.left = (zone.l * 100) + "vw";
            button.style.top = (zone.t * 100) + "vh";
            button.style.width = (zone.w * 100) + "vw";
            button.style.height = (zone.h * 100) + "vh";
            button.style.margin = "0";
            button.style.padding = "0";
            button.style.border = "0";
            button.style.background = "transparent";
            button.style.opacity = "0";
            button.style.pointerEvents = "auto";
            button.style.cursor = "pointer";
            button.addEventListener("click", function(event) {
              event.preventDefault();
              event.stopPropagation();
              bridge.navigate(zone.target);
            }, true);
            root.appendChild(button);
          };

          const textEntries = [
            $textEntriesJs
          ];
          textEntries.forEach(([label, target]) => bindTarget(label, target, "exact"));

          const hotzones = [
            $hotzonesJs
          ];
          hotzones.forEach(bindHotzone);
        })();
    """.trimIndent()
}

private fun hotzonesForAsset(assetPath: String): List<Hotzone> {
    val commonTabs = listOf(
        Hotzone(0.03, 0.765, 0.18, 0.09, "home"),
        Hotzone(0.21, 0.765, 0.18, 0.09, "map"),
        Hotzone(0.39, 0.765, 0.18, 0.09, "itinerary"),
        Hotzone(0.57, 0.765, 0.18, 0.09, "ai"),
        Hotzone(0.75, 0.765, 0.18, 0.09, "profile"),
    )

    return when {
        assetPath.contains("Frame21") -> commonTabs + listOf(
            Hotzone(0.05, 0.12, 0.74, 0.07, "search-home"),
            Hotzone(0.80, 0.08, 0.14, 0.15, "ai"),
            Hotzone(0.06, 0.225, 0.18, 0.12, "food"),
            Hotzone(0.26, 0.225, 0.18, 0.12, "hotel"),
            Hotzone(0.46, 0.225, 0.18, 0.12, "activity"),
            Hotzone(0.66, 0.225, 0.18, 0.12, "map"),
            Hotzone(0.05, 0.365, 0.42, 0.17, "itinerary"),
            Hotzone(0.49, 0.365, 0.42, 0.17, "itinerary"),
            Hotzone(0.05, 0.555, 0.84, 0.06, "food"),
            Hotzone(0.05, 0.615, 0.84, 0.06, "hotel"),
            Hotzone(0.05, 0.675, 0.84, 0.06, "activity"),
            Hotzone(0.05, 0.825, 0.84, 0.09, "ai"),
        )

        assetPath.contains("Frame21009") -> commonTabs + listOf(
            Hotzone(0.05, 0.04, 0.10, 0.08, "back"),
            Hotzone(0.84, 0.04, 0.10, 0.08, "ai"),
            Hotzone(0.05, 0.12, 0.84, 0.07, "search-hotel"),
            Hotzone(0.05, 0.20, 0.84, 0.08, "hotel"),
            Hotzone(0.05, 0.28, 0.84, 0.04, "hotel"),
            Hotzone(0.05, 0.315, 0.84, 0.22, "hotel"),
            Hotzone(0.05, 0.545, 0.84, 0.22, "hotel"),
            Hotzone(0.05, 0.775, 0.84, 0.16, "hotel"),
            Hotzone(0.80, 0.315, 0.10, 0.08, "favorite"),
            Hotzone(0.80, 0.545, 0.10, 0.08, "favorite"),
            Hotzone(0.05, 0.93, 0.84, 0.06, "ai"),
        )

        assetPath.contains("Frame21439") -> commonTabs + listOf(
            Hotzone(0.05, 0.04, 0.10, 0.08, "back"),
            Hotzone(0.84, 0.04, 0.10, 0.08, "ai"),
            Hotzone(0.05, 0.12, 0.84, 0.07, "search-food"),
            Hotzone(0.05, 0.20, 0.84, 0.06, "food"),
            Hotzone(0.05, 0.285, 0.84, 0.20, "food"),
            Hotzone(0.05, 0.495, 0.84, 0.20, "food"),
            Hotzone(0.05, 0.705, 0.84, 0.20, "food"),
            Hotzone(0.67, 0.435, 0.22, 0.06, "map"),
            Hotzone(0.67, 0.645, 0.22, 0.06, "map"),
            Hotzone(0.67, 0.855, 0.22, 0.06, "map"),
            Hotzone(0.05, 0.93, 0.84, 0.06, "ai"),
        )

        assetPath.contains("Frame21805") -> commonTabs + listOf(
            Hotzone(0.05, 0.04, 0.10, 0.08, "back"),
            Hotzone(0.84, 0.04, 0.10, 0.08, "ai"),
            Hotzone(0.05, 0.12, 0.84, 0.07, "search-activity"),
            Hotzone(0.05, 0.20, 0.84, 0.06, "activity"),
            Hotzone(0.70, 0.255, 0.19, 0.05, "activity"),
            Hotzone(0.05, 0.29, 0.84, 0.18, "activity"),
            Hotzone(0.05, 0.48, 0.84, 0.18, "activity"),
            Hotzone(0.05, 0.67, 0.84, 0.18, "activity"),
            Hotzone(0.05, 0.86, 0.84, 0.11, "ai"),
            Hotzone(0.79, 0.29, 0.10, 0.08, "favorite"),
            Hotzone(0.79, 0.48, 0.10, 0.08, "favorite"),
            Hotzone(0.79, 0.67, 0.10, 0.08, "favorite"),
        )

        assetPath.contains("Frame22283") -> commonTabs + listOf(
            Hotzone(0.05, 0.06, 0.84, 0.18, "stats"),
            Hotzone(0.84, 0.04, 0.10, 0.08, "settings"),
            Hotzone(0.05, 0.28, 0.84, 0.08, "itinerary"),
            Hotzone(0.05, 0.36, 0.42, 0.05, "history"),
            Hotzone(0.47, 0.36, 0.42, 0.05, "stats"),
            Hotzone(0.05, 0.42, 0.84, 0.09, "history"),
            Hotzone(0.05, 0.51, 0.84, 0.09, "history"),
            Hotzone(0.05, 0.60, 0.84, 0.09, "history"),
            Hotzone(0.05, 0.69, 0.84, 0.07, "favorite"),
            Hotzone(0.05, 0.76, 0.84, 0.07, "history"),
            Hotzone(0.05, 0.83, 0.84, 0.07, "profile"),
            Hotzone(0.05, 0.90, 0.84, 0.07, "settings"),
        )

        assetPath.contains("Frame22699") -> commonTabs + listOf(
            Hotzone(0.05, 0.04, 0.10, 0.08, "back"),
            Hotzone(0.84, 0.04, 0.10, 0.08, "ai"),
            Hotzone(0.13, 0.18, 0.62, 0.22, "ai"),
            Hotzone(0.04, 0.67, 0.22, 0.05, "map"),
            Hotzone(0.27, 0.67, 0.22, 0.05, "food"),
            Hotzone(0.50, 0.67, 0.22, 0.05, "hotel"),
            Hotzone(0.73, 0.67, 0.22, 0.05, "itinerary"),
            Hotzone(0.04, 0.73, 0.22, 0.05, "map"),
            Hotzone(0.27, 0.73, 0.22, 0.05, "itinerary"),
            Hotzone(0.50, 0.73, 0.22, 0.05, "food"),
            Hotzone(0.73, 0.73, 0.22, 0.05, "profile"),
            Hotzone(0.05, 0.80, 0.84, 0.08, "ai"),
        )

        assetPath.contains("Frame2343") -> commonTabs + listOf(
            Hotzone(0.05, 0.06, 0.72, 0.07, "search-map"),
            Hotzone(0.83, 0.05, 0.10, 0.08, "settings"),
            Hotzone(0.80, 0.22, 0.10, 0.09, "map"),
            Hotzone(0.80, 0.31, 0.10, 0.09, "favorite"),
            Hotzone(0.80, 0.40, 0.10, 0.09, "profile"),
            Hotzone(0.80, 0.49, 0.10, 0.09, "settings"),
            Hotzone(0.05, 0.16, 0.70, 0.58, "map"),
            Hotzone(0.08, 0.22, 0.20, 0.16, "food"),
            Hotzone(0.55, 0.22, 0.20, 0.16, "food"),
            Hotzone(0.32, 0.48, 0.20, 0.14, "activity"),
            Hotzone(0.55, 0.52, 0.22, 0.14, "hotel"),
        )

        assetPath.contains("Frame2643") -> commonTabs + listOf(
            Hotzone(0.05, 0.04, 0.10, 0.08, "back"),
            Hotzone(0.78, 0.04, 0.08, 0.08, "favorite"),
            Hotzone(0.87, 0.04, 0.08, 0.08, "ai"),
            Hotzone(0.05, 0.12, 0.84, 0.12, "itinerary"),
            Hotzone(0.05, 0.28, 0.18, 0.07, "itinerary"),
            Hotzone(0.25, 0.28, 0.18, 0.07, "itinerary"),
            Hotzone(0.45, 0.28, 0.18, 0.07, "itinerary"),
            Hotzone(0.05, 0.41, 0.84, 0.08, "hotel"),
            Hotzone(0.05, 0.49, 0.84, 0.08, "activity"),
            Hotzone(0.05, 0.57, 0.84, 0.08, "food"),
            Hotzone(0.05, 0.66, 0.84, 0.10, "itinerary"),
            Hotzone(0.05, 0.83, 0.84, 0.11, "ai"),
        )

        else -> commonTabs
    }
}

private fun textBindings(): List<Pair<String, String>> = listOf(
    "\u9996\u9875" to "home",
    "\u5730\u56fe" to "map",
    "\u67e5\u770b\u5730\u56fe" to "map",
    "\u63a2\u7d22\u5730\u56fe" to "map",
    "\u70ed\u95e8\u63a8\u8350" to "itinerary",
    "\u98ce\u666f\u5982\u753b" to "itinerary",
    "\u6842\u6797\u5c71\u6c34\u4e94\u65e5" to "itinerary",
    "\u6211\u7684\u884c\u7a0b" to "itinerary",
    "\u884c\u7a0b" to "itinerary",
    "Day1" to "itinerary",
    "Day2" to "itinerary",
    "Day3" to "itinerary",
    "\u4e91\u5357\u79d8\u5883\u4e03\u65e5\u6e38" to "itinerary",
    "\u5f53\u524d\u884c\u7a0b" to "itinerary",
    "\u62b5\u8fbe\u4e3d\u6c5f" to "itinerary",
    "\u62b5\u8fbe\u4e3d\u6c5f\u53e4\u57ce" to "itinerary",
    "\u53e4\u57ce\u6f2b\u6b65" to "itinerary",
    "\u665a\u9910\uff1a\u767e\u5e74\u7eb3\u897f\u7f8e\u98df" to "food",
    "\u672c\u5468\u5929\u6c14" to "itinerary",
    "AI" to "ai",
    "AI\u5efa\u8bae" to "ai",
    "AI\u884c\u7a0b\u52a9\u624b\u51c6\u5907\u5c31\u7eea" to "ai",
    "\u8be2\u95eeAI\u4f18\u5316\u884c\u7a0b" to "ai",
    "\u804a\u804a" to "ai",
    "\u95ee\u95ee\u5c0f\u5409\u5427..." to "ai",
    "\u5c0f\u5409" to "ai",
    "\ud83d\udccd \u63a8\u8350\u666f\u70b9" to "map",
    "\ud83c\udfe8 \u4f4f\u5bbf\u5efa\u8bae" to "hotel",
    "\ud83d\uddd3\ufe0f \u884c\u7a0b\u89c4\u5212" to "itinerary",
    "\u5feb\u901f\u5165\u53e3" to "home",
    "\u4f4f\u5bbf\u63a8\u8350" to "hotel",
    "\u4f4f\u5bbf\u9884\u8ba2" to "hotel",
    "\ud83c\udfe8 \u4f4f\u5bbf\u63a8\u8350" to "hotel",
    "\u7cbe\u54c1\u6c11\u5bbf" to "hotel",
    "\u9ad8\u7aef\u9152\u5e97" to "hotel",
    "\u7279\u8272\u6c11\u5bbf" to "hotel",
    "\u7279\u8272\u9152\u5e97" to "hotel",
    "\u4e13\u5c5e\u63a8\u8350" to "hotel",
    "\u6c5f\u666f\u623f" to "hotel",
    "\u6e05\u6668\u9192\u6765\u5373\u662f\u6f13\u6c5f\u7edd\u8272\u98ce\u5149" to "hotel",
    "\u7af9\u6797\u95f4\u00b7\u7985\u610f\u6c11\u5bbf" to "hotel",
    "\u6708\u4eae\u6e7e\u00b7\u6d1e\u7a9f\u9152\u5e97" to "hotel",
    "AI\u6839\u636e\u4f60\u7684\u504f\u597d\u7b5b\u9009\u4e864\u5bb6\u6700\u9002\u5408\u7684\u4f4f\u5bbf" to "hotel",
    "\u7f8e\u98df\u63a8\u8350" to "food",
    "\ud83c\udf5c \u7f8e\u98df\u63a8\u8350" to "food",
    "\u672c\u5730\u70ed\u95e8\u63a8\u8350" to "food",
    "\u5bfc\u822a\u524d\u5f80" to "map",
    "\u6c5f\u8fb9\u9732\u53f0" to "food",
    "\u767e\u5e74\u8001\u94fa\u00b7\u6842\u6797\u7c73\u7c89" to "food",
    "\u767e\u5e74\u8001\u5e97" to "food",
    "\u4e91\u96fe\u5c71\u5c45\u00b7\u8336\u98df\u5c0f\u9986" to "food",
    "\u6587\u827a\u8303\u513f" to "food",
    "\u7d20\u98df\u53cb\u597d" to "food",
    "\u6c5f\u7554\u591c\u5e02\u00b7\u5c0f\u5403\u96c6\u5e02" to "food",
    "\u591c\u5e02\u5fc5\u53bb" to "food",
    "\u54c1\u79cd\u591a\u6837" to "food",
    "\u6d3b\u52a8\u4f53\u9a8c" to "activity",
    "\ud83c\udfaf \u6d3b\u52a8\u4f53\u9a8c" to "activity",
    "\u6237\u5916\u63a2\u9669" to "activity",
    "\u6587\u5316\u63a2\u7d22" to "activity",
    "\u4f11\u95f2\u8fd0\u52a8" to "activity",
    "\u591c\u6e38\u89c2\u5149" to "activity",
    "\u5217\u8868" to "activity",
    "\u5361\u7247" to "activity",
    "\u6f13\u6c5f\u7af9\u7b4f\u6f02\u6d41" to "activity",
    "\u9f99\u810a\u68af\u7530\u5f92\u6b65" to "activity",
    "\u6444\u5f71\u5723\u5730" to "activity",
    "\u58ee\u65cf\u6587\u5316" to "activity",
    "\u65e5\u51fa\u63a8\u8350" to "activity",
    "\u9047\u9f99\u6cb3\u9a91\u884c\u534a\u65e5\u6e38" to "activity",
    "\u6842\u6797\u591c\u6e38\u4e24\u6c5f\u56db\u6e56" to "activity",
    "\u94f6\u5b50\u5ca9\u6eb6\u6d1e\u63a2\u79d8" to "activity",
    "\u83b7\u53d6\u5b9a\u5236\u6d3b\u52a8\u65b9\u6848" to "activity",
    "\ud83d\udccd \u9644\u8fd1\u53d1\u73b0" to "map",
    "\u53e4\u57ce\u8336\u9986" to "food",
    "\u60a6\u5c45\u6c11\u5bbf" to "hotel",
    "\u6f13\u6c5f\u7af9\u7b4f\u4f53\u9a8c" to "activity",
    "\u6f13\u6c5f\u6c11\u5bbf" to "hotel",
    "\u7af9\u7b4f\u6f02\u6d41" to "activity",
    "\u6842\u6797\u8001\u5b57\u53f7" to "food",
    "\u5c71\u666f\u7cbe\u54c1\u9152\u5e97" to "hotel",
    "\u8c61\u9f3b\u5c71\u666f\u533a" to "map",
    "\u591c\u5e02\u5c0f\u5403\u8857" to "food",
    "\u9f99\u810a\u68af\u7530" to "activity",
    "\u70b9\u51fb\u5730\u56fe \u63a2\u7d22\u5468\u8fb9\uff01" to "map",
    "\u6211\u7684" to "profile",
    "\u4e2a\u4eba\u4e2d\u5fc3" to "profile",
    "\u65c5\u884c\u6b21\u6570" to "stats",
    "\u53bb\u8fc7\u57ce\u5e02" to "stats",
    "\u83b7\u5f97\u52cb\u7ae0" to "stats",
    "\u65c5\u884c\u7167\u7247" to "profile",
    "\u5386\u53f2\u65c5\u884c" to "history",
    "\u6211\u7684\u52cb\u7ae0" to "stats",
    "\u6211\u7684\u6536\u85cf" to "favorite",
    "\u5386\u53f2\u884c\u7a0b" to "history",
    "\u65c5\u884c\u76f8\u518c" to "profile",
    "\u6d88\u606f\u901a\u77e5" to "profile",
    "\u5e2e\u52a9\u4e2d\u5fc3" to "about",
    "\u7cfb\u7edf\u8bbe\u7f6e" to "settings",
    "\u9000\u51fa\u767b\u5f55" to "settings",
    "\u6210\u90fd\u718a\u732b\u4e4b\u65c5" to "history",
    "\u897f\u85cf\u5723\u5730\u63a2\u79d8" to "history",
    "\u53a6\u95e8\u6d77\u8fb9\u6162\u884c" to "history",
)

private fun jsString(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (ch.code in 32..126) append(ch) else append("\\u%04x".format(ch.code))
            }
        }
    }
    append('"')
}
