package me.rerere.rikkahub.ui.pages.travel

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.TravelItineraryDay
import me.rerere.rikkahub.data.model.TravelItemCategory
import me.rerere.rikkahub.data.model.TravelPlanningBrief
import me.rerere.rikkahub.data.model.TravelPlanningState
import me.rerere.rikkahub.data.model.TravelPoi
import me.rerere.rikkahub.data.model.TravelRecommendationCategory
import me.rerere.rikkahub.data.model.TravelRecommendationItem
import me.rerere.rikkahub.data.model.TravelSearchSuggestion
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.Navigator
import me.rerere.rikkahub.ui.hooks.ImeLazyListAutoScroller
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.pages.chat.ChatMapDrawerContent
import me.rerere.rikkahub.ui.pages.chat.ChatVM
import me.rerere.rikkahub.ui.pages.chat.TravelHubUiState
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.base64Encode
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

private val TravelBg = Color(0xFFF1FBF8)
private val TravelLine = Color(0xFFD5EEE7)
private val TravelTextMuted = Color(0xFF6D8F88)
private val TravelTeal = Color(0xFF43B8A7)
private val TravelTealDeep = Color(0xFF258C80)
private val TravelBlue = Color(0xFF69B7E9)
private val TravelBlueDeep = Color(0xFF4D92D4)
private val TravelOrange = Color(0xFFFF9750)
private val TravelOrangeDeep = Color(0xFFFF7A2E)
private val TravelMint = Color(0xFF79D8BE)
private val TravelLavender = Color(0xFFDCCFF6)
private val TravelGold = Color(0xFFFFD67A)

private data class TravelPalette(
    val start: Color,
    val end: Color,
    val soft: Color,
    val accent: Color,
)

private enum class TravelTab(val route: String, val label: String, val icon: String) {
    Home("home", "首页", "🏠"),
    Map("map", "地图", "🗺"),
    Itinerary("itinerary", "行程", "🗓"),
    Ai("ai", "AI", "✨"),
    Mine("mine", "我的", "👤"),
}

@Composable
fun TravelHubPage(
    id: Uuid,
    text: String? = null,
    files: List<Uri> = emptyList(),
    nodeId: Uuid? = null,
    startTab: String = "home",
    highlightPoiId: String? = null,
    preferredMapFilter: String? = null,
) {
    val vm: ChatVM = koinViewModel(parameters = { parametersOf(id.toString()) })
    val filesManager: FilesManager = koinInject()
    val nav = LocalNavController.current
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val ui = vm.travelHubUiState
    var tab by rememberSaveable(id.toString()) {
        mutableStateOf(TravelTab.entries.firstOrNull { it.route == startTab } ?: TravelTab.Home)
    }
    var handled by rememberSaveable(id.toString()) { mutableStateOf(false) }
    var showSheet by rememberSaveable(id.toString()) { mutableStateOf(false) }
    var appliedPreferredMapFilter by rememberSaveable(id.toString(), preferredMapFilter, highlightPoiId) {
        mutableStateOf(false)
    }
    val displayedWeatherSummary = vm.homeWeatherSummary(conversation)
    val visibleMapPois = vm.visibleMapPois(conversation, highlightPoiId)
    val imeVisible = WindowInsets.isImeVisible

    LaunchedEffect(conversation.travelPlanningState) {
        if (conversation.travelPlanningState == TravelPlanningState.Generated && tab != TravelTab.Map) {
            tab = TravelTab.Itinerary
        }
    }
    LaunchedEffect(conversation.travelPlan?.brief?.destination) {
        conversation.travelPlan?.brief?.destination?.takeIf { it.isNotBlank() }?.let(vm::ensureTravelDestinationContext)
    }
    LaunchedEffect(tab, preferredMapFilter) {
        if (tab == TravelTab.Map && !appliedPreferredMapFilter && !preferredMapFilter.isNullOrBlank()) {
            vm.selectTravelMapFilter(preferredMapFilter)
            appliedPreferredMapFilter = true
        }
    }
    LaunchedEffect(text, files, conversation.messageNodes.size) {
        if (handled || conversation.messageNodes.isNotEmpty()) return@LaunchedEffect
        val decoded = runCatching { text?.base64Decode().orEmpty() }.getOrDefault(text.orEmpty())
        val localFiles = if (files.isNotEmpty()) filesManager.createChatFilesByContents(files) else emptyList()
        val contentTypes = files.mapNotNull { filesManager.getFileMimeType(it) }
        val parts = buildList {
            localFiles.forEachIndexed { index, file ->
                when {
                    contentTypes.getOrNull(index)?.startsWith("image/") == true -> add(UIMessagePart.Image(file.toString()))
                    contentTypes.getOrNull(index)?.startsWith("video/") == true -> add(UIMessagePart.Video(file.toString()))
                    contentTypes.getOrNull(index)?.startsWith("audio/") == true -> add(UIMessagePart.Audio(file.toString()))
                    else -> add(
                        UIMessagePart.Document(
                            url = file.toString(),
                            fileName = "attachment",
                            mime = contentTypes.getOrNull(index) ?: "application/octet-stream",
                        ),
                    )
                }
            }
            if (decoded.isNotBlank()) add(UIMessagePart.Text(decoded))
        }
        if (parts.isNotEmpty()) {
            if (localFiles.isEmpty() && decoded.isNotBlank()) {
                vm.startTravelPlanning(decoded)
            } else {
                vm.ensureTravelAssistantSelected()
                vm.handleMessageSend(parts)
            }
            tab = TravelTab.Ai
        }
        handled = true
    }

    Scaffold(
        containerColor = TravelBg,
        bottomBar = {
            if (!(tab == TravelTab.Ai && imeVisible)) {
                NavigationBar(containerColor = Color.White) {
                    TravelTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TravelTealDeep,
                                selectedTextColor = TravelTealDeep,
                                indicatorColor = Color(0xFFDDF7F1),
                                unselectedIconColor = TravelTextMuted,
                                unselectedTextColor = TravelTextMuted,
                            ),
                            icon = { Text(item.icon) },
                            label = { Text(item.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        when (tab) {
            TravelTab.Home -> HomeTab(
                modifier = Modifier.padding(inner),
                conversation = conversation,
                ui = ui,
                weatherSummary = displayedWeatherSummary,
                onQueryChange = vm::onTravelSearchQueryChange,
                onSelectSuggestion = vm::selectTravelDestination,
                onStartPlanning = { showSheet = true },
                onGeneratePlan = vm::generateTravelPlan,
                onOpenHotels = { nav.navigate(Screen.TravelHotels(id.toString())) },
                onOpenFoods = { nav.navigate(Screen.TravelFoods(id.toString())) },
                onOpenActivities = { nav.navigate(Screen.TravelActivities(id.toString())) },
                onOpenMap = { tab = TravelTab.Map },
                onOpenAi = { tab = TravelTab.Ai },
            )

            TravelTab.Map -> MapTab(
                modifier = Modifier.padding(inner),
                conversation = conversation,
                ui = ui,
                mapPois = visibleMapPois,
                highlightPoiId = highlightPoiId,
                onQueryChange = vm::onTravelSearchQueryChange,
                onSelectSuggestion = vm::selectTravelDestination,
                onSelectFilter = vm::selectTravelMapFilter,
            )

            TravelTab.Itinerary -> ItineraryTab(
                modifier = Modifier.padding(inner),
                conversation = conversation,
                onStartPlanning = { showSheet = true },
                onOpenAi = { tab = TravelTab.Ai },
            )

            TravelTab.Ai -> AiTab(
                modifier = Modifier.padding(inner),
                vm = vm,
                conversation = conversation,
                nodeId = nodeId,
                onOpenItinerary = { tab = TravelTab.Itinerary },
            )

            TravelTab.Mine -> MineTab(
                modifier = Modifier.padding(inner),
                conversation = conversation,
                onOpenCurrentTrip = { tab = TravelTab.Itinerary },
            )
        }
    }

    if (showSheet) {
        StartPlanningSheet(
            onDismiss = { showSheet = false },
            onSubmit = { prompt ->
                showSheet = false
                if (conversation.messageNodes.isEmpty()) {
                    vm.startTravelPlanning(prompt)
                    tab = TravelTab.Ai
                } else {
                    nav.clearAndNavigate(
                        Screen.TravelHub(
                            id = Uuid.random().toString(),
                            text = prompt.base64Encode(),
                            startTab = "ai",
                        ),
                    )
                }
            },
        )
    }
}

@Composable
fun TravelRecommendationPage(id: Uuid, title: String, category: TravelRecommendationCategory) {
    val vm: ChatVM = koinViewModel(parameters = { parametersOf(id.toString()) })
    val nav = LocalNavController.current
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val ui = vm.travelHubUiState
    val palette = paletteForCategory(category)
    val items = recommendationItemsFor(category, conversation, ui)

    LaunchedEffect(conversation.travelPlan?.brief?.destination) {
        conversation.travelPlan?.brief?.destination?.takeIf { it.isNotBlank() }?.let(vm::ensureTravelDestinationContext)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(TravelBg),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            GradientHeader(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                palette = palette,
                smallTitle = categoryEmoji(category),
                title = title,
                subtitle = buildRecommendationSubtitle(category, conversation, ui),
            ) {
                HeaderActionRow(
                    leftLabel = "返回",
                    rightLabel = "AI",
                    onLeft = { nav.popBackStack() },
                    onRight = { nav.navigate(Screen.TravelHub(id.toString(), startTab = "ai")) },
                )
                Spacer(Modifier.height(12.dp))
                SearchBox(
                    value = ui.searchQuery,
                    onValueChange = vm::onTravelSearchQueryChange,
                    placeholder = categorySearchPlaceholder(category),
                )
                if (ui.suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    SuggestionCard(
                        suggestions = ui.suggestions,
                        onSelectSuggestion = vm::selectTravelDestination,
                    )
                }
                Spacer(Modifier.height(12.dp))
                ChipRow(buildRecommendationFilterChips(category))
            }
        }

        item {
            SectionTitle(
                modifier = Modifier.padding(horizontal = 16.dp),
                icon = "📌",
                title = "共找到 ${items.size} 条${categoryDisplayName(category)}",
            )
        }

        if (items.isEmpty()) {
            item {
                EmptyStateCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = "还没有真实${categoryDisplayName(category)}数据",
                    text = if (ui.isLoadingDetails) "正在加载目的地事实数据…" else "先选择目的地，再加载推荐或生成行程。",
                )
            }
        } else {
            items(items, key = { it.id }) { item ->
                RecommendationListingCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    item = item,
                    palette = palette,
                    category = category,
                    onAskAi = {
                        sendTravelAiPrompt(vm, conversation, buildRecommendationPrompt(item, category))
                        nav.popBackStack()
                    },
                    onOpenMap = {
                        nav.navigate(
                            Screen.TravelHub(
                                id = id.toString(),
                                startTab = "map",
                                highlightPoiId = item.id,
                                preferredMapFilter = when (category) {
                                    TravelRecommendationCategory.hotel -> "hotel"
                                    TravelRecommendationCategory.food -> "food"
                                    TravelRecommendationCategory.activity -> "activity"
                                },
                            )
                        )
                    },
                )
            }
        }
    }
}

private fun recommendationItemsFor(
    category: TravelRecommendationCategory,
    conversation: Conversation,
    ui: TravelHubUiState,
): List<TravelRecommendationItem> {
    val plan = conversation.travelPlan
    return when (category) {
        TravelRecommendationCategory.hotel -> plan?.hotels.orEmpty().ifEmpty { ui.hotels }
        TravelRecommendationCategory.food -> plan?.foods.orEmpty().ifEmpty { ui.foods }
        TravelRecommendationCategory.activity -> plan?.activities.orEmpty().ifEmpty { ui.activities }
    }
}

private fun buildRecommendationPrompt(item: TravelRecommendationItem, category: TravelRecommendationCategory): String = buildString {
    appendLine("请把这条${categoryDisplayName(category)}建议融入我的旅行计划。")
    appendLine("名称: ${item.title}")
    if (item.subtitle.isNotBlank()) appendLine("地址: ${item.subtitle}")
    if (item.area.isNotBlank()) appendLine("片区: ${item.area}")
    if (item.priceHint.isNotBlank()) appendLine("价格: ${item.priceHint}")
    if (item.ratingText.isNotBlank()) appendLine("评分: ${item.ratingText}")
    if (item.inventoryHint.isNotBlank()) appendLine("营业/库存: ${item.inventoryHint}")
    if (item.bookingUrl.isNotBlank()) appendLine("链接: ${item.bookingUrl}")
    append("请更新行程，并说明最适合插入哪一天、哪个时间段。")
}

private fun buildBriefSummary(brief: TravelPlanningBrief?): String {
    if (brief == null) return "告诉我目的地、日期、人数、预算和偏好，我会把真实天气、路线和推荐联动起来。"
    return listOf(
        brief.destination.takeIf { it.isNotBlank() },
        brief.origin.takeIf { it.isNotBlank() }?.let { "从 $it 出发" },
        brief.dateRange.takeIf { it.isNotBlank() },
        brief.days?.takeIf { it > 0 }?.let { "$it 天" },
        brief.travelerCount?.let { "$it 人" },
        brief.budgetText.takeIf { it.isNotBlank() } ?: brief.budgetLevel.takeIf { it.isNotBlank() },
        brief.travelStyleTags.takeIf { it.isNotEmpty() }?.joinToString(" / "),
        brief.transportPreferences.takeIf { it.isNotEmpty() }?.joinToString(" / ")?.let { "交通偏好: $it" },
    ).filterNotNull().joinToString(" · ").ifBlank { brief.userIntentSummary.ifBlank { "出行信息已准备好。" } }
}

@Composable
private fun HomeTab(
    modifier: Modifier,
    conversation: Conversation,
    ui: TravelHubUiState,
    weatherSummary: String,
    onQueryChange: (String) -> Unit,
    onSelectSuggestion: (TravelSearchSuggestion) -> Unit,
    onStartPlanning: () -> Unit,
    onGeneratePlan: () -> Unit,
    onOpenHotels: () -> Unit,
    onOpenFoods: () -> Unit,
    onOpenActivities: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenAi: () -> Unit,
) {
    val plan = conversation.travelPlan
    val destination = plan?.brief?.destination?.ifBlank { ui.selectedDestination?.name.orEmpty() }
        ?.ifBlank { ui.selectedDestination?.name.orEmpty() }
        ?: ui.selectedDestination?.name.orEmpty()
    val highlightItems = remember(plan, ui.hotels, ui.foods, ui.activities) {
        buildList {
            addAll((plan?.hotels ?: ui.hotels).take(1))
            addAll((plan?.foods ?: ui.foods).take(1))
            addAll((plan?.activities ?: ui.activities).take(2))
        }
    }
    val nearbyItems = remember(plan, ui.hotels, ui.foods, ui.activities) {
        ((plan?.activities ?: ui.activities) + (plan?.foods ?: ui.foods) + (plan?.hotels ?: ui.hotels)).distinctBy { it.id }.take(6)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(TravelBg),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            GradientHeader(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                palette = TravelPalette(TravelTeal, Color(0xFF7DDBC7), Color(0xFFE5FAF4), TravelGold),
                smallTitle = "AI旅行规划师",
                title = "早上好，旅行家！",
                subtitle = if (destination.isBlank()) "适合出行 / 先选目的地开始规划" else "${destination.ifBlank { "未选择目的地" }} · ${weatherSummary.ifBlank { "天气联动待刷新" }}",
            ) {
                HeaderHintBadge("今天适合出发！")
                Spacer(Modifier.height(14.dp))
                SearchBox(
                    value = ui.searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = "搜索目的地、景点、美食…",
                )
                if (ui.suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    SuggestionCard(
                        suggestions = ui.suggestions,
                        onSelectSuggestion = onSelectSuggestion,
                    )
                }
                Spacer(Modifier.height(12.dp))
                HeroStatsRow(
                    items = listOf(
                        if (weatherSummary.isBlank()) "天气待同步" else weatherSummary,
                        if (destination.isBlank()) "目的地未锁定" else destination,
                        buildBriefSummary(plan?.brief),
                    ),
                )
            }
        }

        item {
            SectionTitle(
                modifier = Modifier.padding(horizontal = 16.dp),
                icon = "⚡",
                title = "快速入口",
            )
        }

        item {
            QuickActionGrid(
                modifier = Modifier.padding(horizontal = 16.dp),
                actions = listOf(
                    QuickAction("🍜", "美食推荐", onOpenFoods),
                    QuickAction("🏨", "住宿预订", onOpenHotels),
                    QuickAction("🎯", "活动体验", onOpenActivities),
                    QuickAction("🗺", "探索地图", onOpenMap),
                ),
            )
        }

        item {
            SectionTitle(
                modifier = Modifier.padding(horizontal = 16.dp),
                icon = "📈",
                title = "热门行程推荐",
                action = if (highlightItems.isNotEmpty()) "查看地图" else null,
                onAction = if (highlightItems.isNotEmpty()) onOpenMap else null,
            )
        }

        if (highlightItems.isEmpty()) {
            item {
                EmptyStateCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = "还没有真实推荐",
                    text = if (ui.isLoadingDetails) "正在加载目的地天气、POI 和路线…" else "先搜索目的地，系统会把天气、推荐和地图联动起来。",
                )
            }
        } else {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(highlightItems, key = { it.id }) { item ->
                        FeaturedRecommendationCard(
                            item = item,
                            modifier = Modifier.width(258.dp),
                        )
                    }
                }
            }
        }

        item {
            SectionTitle(
                modifier = Modifier.padding(horizontal = 16.dp),
                icon = "📍",
                title = "附近发现",
            )
        }

        if (ui.errorMessage != null) {
            item {
                InfoBanner(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = "外部数据加载异常",
                    text = ui.errorMessage,
                    tone = Color(0xFFFFF2E8),
                )
            }
        }

        items(nearbyItems, key = { it.id }) { item ->
            NearbyRecommendationRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                item = item,
            )
        }

        item {
            AssistantCtaCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = "AI行程助手准备就绪",
                text = "告诉我你的旅行梦想，我来帮你规划完整行程！",
                primaryLabel = "生成计划",
                secondaryLabel = "开始填写",
                onPrimary = onGeneratePlan,
                onSecondary = onStartPlanning,
            )
        }

        item {
            ActionStrip(
                modifier = Modifier.padding(horizontal = 16.dp),
                actions = listOf(
                    "打开地图" to onOpenMap,
                    "进入 AI" to onOpenAi,
                    "重填需求" to onStartPlanning,
                ),
            )
        }
    }
}

@Composable
private fun MapTab(
    modifier: Modifier,
    conversation: Conversation,
    ui: TravelHubUiState,
    mapPois: List<TravelPoi>,
    highlightPoiId: String?,
    onQueryChange: (String) -> Unit,
    onSelectSuggestion: (TravelSearchSuggestion) -> Unit,
    onSelectFilter: (String) -> Unit,
) {
    val nav = LocalNavController.current
    var isMapExpanded by rememberSaveable(conversation.id.toString()) {
        mutableStateOf(false)
    }
    var activePoiId by rememberSaveable(conversation.id.toString(), highlightPoiId) {
        mutableStateOf(highlightPoiId)
    }
    LaunchedEffect(mapPois.map { it.id }, activePoiId, highlightPoiId) {
        if (mapPois.isEmpty()) {
            activePoiId = null
        } else if (activePoiId == null || mapPois.none { it.id == activePoiId }) {
            activePoiId = highlightPoiId?.takeIf { target -> mapPois.any { it.id == target } } ?: mapPois.first().id
        }
    }
    if (isMapExpanded) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(TravelBg)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TinyActionButton("← 返回", onClick = { isMapExpanded = false }, enabled = true)
                Text(
                    text = conversation.travelPlan?.brief?.destination?.ifBlank { "地图" } ?: "地图",
                    style = MaterialTheme.typography.titleMedium,
                    color = TravelTealDeep,
                    fontWeight = FontWeight.Bold,
                )
                SmallStatusPill(text = "全屏", color = TravelTealDeep)
            }
            MapSearchControls(
                ui = ui,
                onQueryChange = onQueryChange,
                onSelectSuggestion = onSelectSuggestion,
                onSelectFilter = onSelectFilter,
            )
            MapViewport(
                modifier = Modifier.weight(1f),
                nav = nav,
                mapPois = mapPois,
                activePoiId = activePoiId,
                expandLabel = null,
                onExpandToggle = null,
            )
            if (mapPois.isNotEmpty()) {
                MapPoiCarousel(
                    mapPois = mapPois,
                    activePoiId = activePoiId,
                    onSelectPoi = { activePoiId = it },
                )
            }
        }
    } else {
    Column(
        modifier = modifier.fillMaxSize().background(TravelBg),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (false) GradientHeader(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            palette = TravelPalette(Color(0xFF9EDFD2), Color(0xFFD9F8EE), Color(0xFFEFFCF7), TravelTealDeep),
            smallTitle = "探索地图",
            title = conversation.travelPlan?.brief?.destination?.ifBlank { "旅行地图" } ?: "旅行地图",
            subtitle = "真实 POI、路线与行程联动",
        ) {
            SearchBox(
                value = ui.searchQuery,
                onValueChange = onQueryChange,
                placeholder = "搜索地点…",
            )
            if (ui.suggestions.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                SuggestionCard(
                    suggestions = ui.suggestions,
                    onSelectSuggestion = onSelectSuggestion,
                )
            }
            Spacer(Modifier.height(6.dp))
            ChipRow(
                chips = listOf("住宿", "美食", "活动", "路线"),
                selected = mapFilterLabel(ui.selectedMapFilter),
                onSelect = { label -> onSelectFilter(mapFilterValue(label)) },
            )
        }

        Surface(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = Color.White.copy(alpha = 0.96f),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 2.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = "探索地图",
                            style = MaterialTheme.typography.titleMedium,
                            color = TravelTealDeep,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = conversation.travelPlan?.brief?.destination?.ifBlank { "旅行地图" } ?: "旅行地图",
                            style = MaterialTheme.typography.bodySmall,
                            color = TravelTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TinyActionButton("全屏", onClick = { isMapExpanded = true }, enabled = true)
                }
                MapSearchControls(
                    ui = ui,
                    onQueryChange = onQueryChange,
                    onSelectSuggestion = onSelectSuggestion,
                    onSelectFilter = onSelectFilter,
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1.8f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.White,
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 2.dp,
            ) {
                ChatMapDrawerContent(
                    modifier = Modifier.fillMaxSize(),
                    travelPois = mapPois,
                    highlightPoiId = activePoiId,
                    showBottomPanel = false,
                    onOpenInternalWebView = { url -> nav.navigate(Screen.WebView(url = url)) },
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 14.dp),
            ) {
                TinyActionButton("放大", onClick = { isMapExpanded = true }, enabled = true)
            }

            if (mapPois.isNotEmpty()) {
                MapPoiCarousel(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 6.dp),
                    mapPois = mapPois,
                    activePoiId = activePoiId,
                    onSelectPoi = { activePoiId = it },
                )
            }

            if (mapPois.isEmpty()) {
                EmptyStateCard(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    title = "地图上还没有 POI",
                    text = "搜索地点或先生成行程，系统会把推荐点位同步到地图。",
                )
            }
        }

        if (false && mapPois.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(mapPois, key = { it.id }) { poi ->
                    Surface(
                        modifier = Modifier
                            .width(210.dp)
                            .clickable { activePoiId = poi.id },
                        color = Color.White,
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 1.dp,
                        border = if (activePoiId == poi.id) androidx.compose.foundation.BorderStroke(1.5.dp, TravelTeal) else null,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(poi.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = listOf(poi.category, poi.address).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "等待更多地图详情" },
                                style = MaterialTheme.typography.bodySmall,
                                color = TravelTextMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun MapSearchControls(
    ui: TravelHubUiState,
    onQueryChange: (String) -> Unit,
    onSelectSuggestion: (TravelSearchSuggestion) -> Unit,
    onSelectFilter: (String) -> Unit,
) {
    SearchBox(
        value = ui.searchQuery,
        onValueChange = onQueryChange,
        placeholder = "搜索地点、景点或美食",
    )
    if (ui.suggestions.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        SuggestionCard(
            suggestions = ui.suggestions,
            onSelectSuggestion = onSelectSuggestion,
        )
    }
    Spacer(Modifier.height(12.dp))
    ChipRow(
        chips = listOf("住宿", "美食", "活动", "路线"),
        selected = mapFilterLabel(ui.selectedMapFilter),
        onSelect = { label -> onSelectFilter(mapFilterValue(label)) },
    )
}

@Composable
private fun MapViewport(
    modifier: Modifier = Modifier,
    nav: Navigator,
    mapPois: List<TravelPoi>,
    activePoiId: String?,
    showBottomPanel: Boolean = false,
    expandLabel: String? = null,
    onExpandToggle: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 2.dp,
        ) {
            ChatMapDrawerContent(
                modifier = Modifier.fillMaxSize(),
                travelPois = mapPois,
                highlightPoiId = activePoiId,
                showBottomPanel = showBottomPanel,
                onOpenInternalWebView = { url -> nav.navigate(Screen.WebView(url = url)) },
            )
        }

        if (!expandLabel.isNullOrBlank() && onExpandToggle != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 14.dp),
            ) {
                TinyActionButton(expandLabel, onClick = onExpandToggle, enabled = true)
            }
        }

        if (mapPois.isEmpty()) {
            EmptyStateCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                title = "地图上还没有 POI",
                text = "先搜索目的地或生成行程，地图会同步展示当前可用点位。",
            )
        }
    }
}

@Composable
private fun MapPoiCarousel(
    modifier: Modifier = Modifier,
    mapPois: List<TravelPoi>,
    activePoiId: String?,
    onSelectPoi: (String) -> Unit,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(mapPois, key = { it.id }) { poi ->
            Surface(
                modifier = Modifier
                    .width(210.dp)
                    .clickable { onSelectPoi(poi.id) },
                color = Color.White,
                shape = RoundedCornerShape(20.dp),
                tonalElevation = 1.dp,
                border = if (activePoiId == poi.id) androidx.compose.foundation.BorderStroke(1.5.dp, TravelTeal) else null,
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = poi.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOf(poi.category, poi.address)
                            .filter { it.isNotBlank() }
                            .joinToString(" 路 ")
                            .ifBlank { "等待更多地图详情" },
                        style = MaterialTheme.typography.bodySmall,
                        color = TravelTextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ItineraryTab(
    modifier: Modifier,
    conversation: Conversation,
    onStartPlanning: () -> Unit,
    onOpenAi: () -> Unit,
) {
    val plan = conversation.travelPlan
    val days = plan?.itineraryDays.orEmpty()
    var selectedDayIndex by rememberSaveable(conversation.id.toString()) {
        mutableStateOf(days.firstOrNull()?.dayIndex ?: 1)
    }

    LaunchedEffect(days.map { it.dayIndex }) {
        if (days.isNotEmpty() && days.none { it.dayIndex == selectedDayIndex }) {
            selectedDayIndex = days.first().dayIndex
        }
    }

    val selectedDay = days.firstOrNull { it.dayIndex == selectedDayIndex } ?: days.firstOrNull()

    LazyColumn(
        modifier = modifier.fillMaxSize().background(TravelBg),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GradientHeader(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                palette = TravelPalette(TravelTealDeep, TravelTeal, Color(0xFFE1F8F2), TravelGold),
                smallTitle = "行程规划",
                title = plan?.brief?.destination?.ifBlank { "我的行程" } ?: "我的行程",
                subtitle = buildBriefSummary(plan?.brief),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = selectedDay?.title?.ifBlank { "Day ${selectedDay?.dayIndex ?: 1}" } ?: "等待生成行程",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = selectedDay?.dateText?.ifBlank { "真实天气与路线会同步写入" }
                                ?: "生成完成后会显示真实天气与路线约束",
                            color = Color.White.copy(alpha = 0.92f),
                        )
                    }
                    HeaderHintBadge(
                        text = travelPlanningStateLabel(conversation.travelPlanningState),
                    )
                }
            }
        }

        if (days.isEmpty()) {
            item {
                AssistantCtaCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = "还没有详细行程",
                    text = "选好目的地后点击生成，系统会把真实天气和路线提示写进每一天。",
                    primaryLabel = "立即生成",
                    secondaryLabel = "打开 AI",
                    onPrimary = onStartPlanning,
                    onSecondary = onOpenAi,
                )
            }
        } else {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(days, key = { it.dayIndex }) { day ->
                        DayChip(
                            title = "Day${day.dayIndex}",
                            selected = day.dayIndex == selectedDayIndex,
                            onClick = { selectedDayIndex = day.dayIndex },
                        )
                    }
                }
            }

            selectedDay?.let { day ->
                item {
                    DayTimelineCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        day = day,
                    )
                }

                item {
                    WeatherStrip(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        days = days,
                    )
                }

                item {
                    AiAdviceCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        day = day,
                        onOpenAi = onOpenAi,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiTab(
    modifier: Modifier,
    vm: ChatVM,
    conversation: Conversation,
    nodeId: Uuid?,
    onOpenItinerary: () -> Unit,
) {
    var input by rememberSaveable(conversation.id.toString()) { mutableStateOf("") }
    val listState = rememberLazyListState()
    ImeLazyListAutoScroller(listState)
    val focusNode = nodeId?.let { target -> conversation.currentMessages.firstOrNull { it.id == target } }
    val shortcutPrompts = listOf(
        "推荐景点",
        "美食推荐",
        "住宿建议",
        "优化今天行程",
    )

    LaunchedEffect(conversation.currentMessages.size) {
        val lastIndex = conversation.currentMessages.lastIndex
        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TravelBg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        GradientHeader(
            modifier = Modifier.fillMaxWidth(),
            palette = TravelPalette(TravelTealDeep, TravelTeal, Color(0xFFE0F8F1), TravelMint),
            smallTitle = "🦒 小吉",
            title = "AI旅行助手",
            subtitle = buildBriefSummary(conversation.travelPlan?.brief),
        ) {
            HeaderActionRow(
                leftLabel = "返回行程",
                rightLabel = travelPlanningStateLabel(conversation.travelPlanningState),
                onLeft = onOpenItinerary,
                onRight = {},
                rightEnabled = false,
            )
        }

        if (focusNode != null) {
            InfoBanner(
                title = "当前聚焦消息",
                text = focusNode.toText(),
                tone = Color(0xFFE8F8F3),
            )
        }

        ChipRow(
            chips = shortcutPrompts,
            selected = null,
            onSelect = { prompt ->
                val realPrompt = when (prompt) {
                    "推荐景点" -> "请根据我当前目的地推荐今天最值得去的景点。"
                    "美食推荐" -> "请根据我当前目的地推荐附近最值得吃的美食。"
                    "住宿建议" -> "请根据我当前目的地推荐更适合我的住宿方案。"
                    else -> "请优化今天的旅行行程，补充更合理的路线和时间安排。"
                }
                sendTravelAiPrompt(vm, conversation, realPrompt)
            },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(conversation.currentMessages, key = { it.id.toString() }) { message ->
                val isUser = message.role == MessageRole.USER
                val textContent = message.toText().ifBlank {
                    if (isUser) "(空消息)" else "(非文本消息)"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                ) {
                    Surface(
                        modifier = Modifier.width(280.dp),
                        color = if (isUser) Color(0xFFDFF7F1) else Color.White,
                        shape = RoundedCornerShape(24.dp),
                        tonalElevation = 1.dp,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = if (isUser) "你" else "小吉",
                                color = TravelTealDeep,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            if (isUser) {
                                Text(text = textContent)
                            } else {
                                MarkdownBlock(
                                    content = textContent,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding(),
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    placeholder = { Text("问问小吉吧…") },
                    shape = RoundedCornerShape(18.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            if (input.isBlank()) return@Button
                            sendTravelAiPrompt(vm, conversation, input)
                            input = ""
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TravelTeal),
                    ) {
                        Text("发送")
                    }
                    Button(
                        onClick = { vm.generateTravelPlan() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TravelOrange),
                    ) {
                        Text("生成行程")
                    }
                }
            }
        }
    }
}

@Composable
private fun MineTab(
    modifier: Modifier,
    conversation: Conversation,
    onOpenCurrentTrip: () -> Unit,
) {
    val plan = conversation.travelPlan
    val nav = LocalNavController.current
    val context = LocalContext.current
    val destination = plan?.brief?.destination?.ifBlank { "旅行中" } ?: "旅行中"
    val stats = listOf(
        "旅行次数" to "${plan?.itineraryDays.orEmpty().size.coerceAtLeast(1)}",
        "去过城市" to if (destination.isBlank()) "0" else "1",
        "获得勋章" to "${plan?.foods.orEmpty().size + plan?.activities.orEmpty().size}",
        "旅行照片" to "${plan?.pois.orEmpty().size}",
    )

    LazyColumn(
        modifier = modifier.fillMaxSize().background(TravelBg),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GradientHeader(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                palette = TravelPalette(TravelTealDeep, TravelTeal, Color(0xFFE0F7F1), TravelGold),
                smallTitle = "个人中心",
                title = "旅行者小明",
                subtitle = "$destination · 旅行达人",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    stats.forEach { (label, value) ->
                        MiniStatCard(
                            modifier = Modifier.weight(1f),
                            label = label,
                            value = value,
                        )
                    }
                }
            }
        }

        if (plan != null) {
            item {
                CurrentTripCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = plan.brief?.destination?.ifBlank { "当前行程" } ?: "当前行程",
                    subtitle = buildBriefSummary(plan.brief),
                    onClick = onOpenCurrentTrip,
                )
            }
        }

        item {
            SectionTitle(
                modifier = Modifier.padding(horizontal = 16.dp),
                icon = "🧳",
                title = "旅行资产",
            )
        }

        item {
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color.White,
                shape = RoundedCornerShape(26.dp),
                tonalElevation = 1.dp,
            ) {
                Column {
                    MineRow("我的收藏", "${plan?.pois.orEmpty().size} 个地点", enabled = true, onClick = { nav.navigate(Screen.Favorite) })
                    MineRow("历史行程", "${plan?.itineraryDays.orEmpty().size} 天", enabled = true, onClick = { nav.navigate(Screen.History) })
                    MineRow(
                        "旅行相册",
                        "${(plan?.hotels.orEmpty().size + plan?.foods.orEmpty().size + plan?.activities.orEmpty().size)} 条记录",
                        enabled = false,
                        onClick = { Toast.makeText(context, "旅行相册功能待接入", Toast.LENGTH_SHORT).show() },
                    )
                    MineRow("系统设置", "账号与隐私", enabled = true, onClick = { nav.navigate(Screen.Setting) })
                    MineRow("帮助中心", "常见问题", enabled = true, onClick = { nav.navigate(Screen.SettingAbout) })
                }
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable { Toast.makeText(context, "退出登录功能待接入", Toast.LENGTH_SHORT).show() },
                color = Color(0xFFEFF9F5),
                shape = RoundedCornerShape(22.dp),
            ) {
                Text(
                    text = "退出登录",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    color = TravelTealDeep,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun GradientHeader(
    modifier: Modifier = Modifier,
    palette: TravelPalette,
    smallTitle: String,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .background(Brush.linearGradient(listOf(palette.start, palette.end)))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(smallTitle, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Color.White.copy(alpha = 0.92f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            content()
        }
    }
}

@Composable
private fun HeaderActionRow(
    leftLabel: String,
    rightLabel: String,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    rightEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TinyActionButton(leftLabel, onLeft, enabled = true)
        TinyActionButton(rightLabel, onRight, enabled = rightEnabled)
    }
}

@Composable
private fun TinyActionButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    Surface(
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        color = Color.White.copy(alpha = if (enabled) 0.18f else 0.12f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color.White.copy(alpha = if (enabled) 0.98f else 0.72f),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun HeaderHintBadge(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(18.dp),
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = TravelTealDeep,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HeroStatsRow(items: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.filter { it.isNotBlank() }.forEach { item ->
            Surface(
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    text = item,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SearchBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(placeholder) },
        shape = RoundedCornerShape(22.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun SuggestionCard(
    suggestions: List<TravelSearchSuggestion>,
    onSelectSuggestion: (TravelSearchSuggestion) -> Unit,
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            suggestions.take(6).forEach { suggestion ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSuggestion(suggestion) }
                        .padding(vertical = 10.dp),
                ) {
                    Text(
                        text = suggestion.name.ifBlank { "未命名地点" },
                        fontWeight = FontWeight.Bold,
                    )
                    val subtitle = listOf(suggestion.district, suggestion.address)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            color = TravelTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private data class QuickAction(val icon: String, val label: String, val onClick: () -> Unit)

@Composable
private fun QuickActionGrid(modifier: Modifier = Modifier, actions: List<QuickAction>) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        actions.forEach { action ->
            Surface(
                modifier = Modifier.weight(1f).clickable(onClick = action.onClick),
                color = Color.White,
                shape = RoundedCornerShape(22.dp),
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 18.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF1FAF8)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(action.icon)
                    }
                    Text(action.label, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(
    modifier: Modifier = Modifier,
    icon: String,
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(icon)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        }
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun EmptyStateCard(modifier: Modifier = Modifier, title: String, text: String) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(text, color = TravelTextMuted)
        }
    }
}

@Composable
private fun InfoBanner(modifier: Modifier = Modifier, title: String, text: String, tone: Color) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = tone,
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(text, color = TravelTextMuted)
        }
    }
}

@Composable
private fun FeaturedRecommendationCard(item: TravelRecommendationItem, modifier: Modifier = Modifier) {
    val palette = paletteForCategory(item.category)
    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(26.dp),
        tonalElevation = 1.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Brush.linearGradient(listOf(palette.start, palette.end))),
            ) {
                SmallHeaderChip(
                    modifier = Modifier.padding(12.dp),
                    text = categoryDisplayName(item.category),
                )
                Text(
                    text = categoryEmoji(item.category),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.displaySmall,
                )
                if (item.priceHint.isNotBlank()) {
                    SmallHeaderChip(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                        text = item.priceHint,
                    )
                }
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val subtitle = listOf(item.area.takeIf { it.isNotBlank() }, item.subtitle.takeIf { it.isNotBlank() })
                    .filterNotNull()
                    .joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = TravelTextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                RecommendationMetaText(item)
            }
        }
    }
}

@Composable
private fun NearbyRecommendationRow(modifier: Modifier = Modifier, item: TravelRecommendationItem) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF1FAF8)),
                contentAlignment = Alignment.Center,
            ) {
                Text(categoryEmoji(item.category))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = listOf(
                        item.area.takeIf { it.isNotBlank() },
                        item.priceHint.takeIf { it.isNotBlank() },
                        item.ratingText.takeIf { it.isNotBlank() },
                        item.inventoryHint.takeIf { it.isNotBlank() },
                    ).filterNotNull().joinToString(" · ").ifBlank { item.subtitle.ifBlank { "等待更多地点详情" } },
                    color = TravelTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = TravelTextMuted)
        }
    }
}

@Composable
private fun AssistantCtaCard(
    modifier: Modifier = Modifier,
    title: String,
    text: String,
    primaryLabel: String,
    secondaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFDFF7F1),
        shape = RoundedCornerShape(26.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFC9EFE5)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🦒")
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(text, color = TravelTextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPrimary,
                    colors = ButtonDefaults.buttonColors(containerColor = TravelTeal),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(primaryLabel)
                }
                TextButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        }
    }
}

@Composable
private fun ActionStrip(modifier: Modifier = Modifier, actions: List<Pair<String, () -> Unit>>) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEach { (label, action) ->
            Surface(
                modifier = Modifier.clickable(onClick = action),
                color = Color.White,
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 1.dp,
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = TravelTealDeep,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RecommendationListingCard(
    modifier: Modifier = Modifier,
    item: TravelRecommendationItem,
    palette: TravelPalette,
    category: TravelRecommendationCategory,
    onAskAi: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val nav = LocalNavController.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 1.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .background(Brush.linearGradient(listOf(palette.start, palette.end))),
            ) {
                SmallHeaderChip(
                    modifier = Modifier.padding(14.dp),
                    text = item.tags.firstOrNull().takeUnless { it.isNullOrBlank() } ?: categoryDisplayName(category),
                )
                Text(
                    text = categoryEmoji(category),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.displayMedium,
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp),
                    color = Color.White,
                    shape = CircleShape,
                ) {
                    Text("♡", modifier = Modifier.padding(10.dp))
                }
                if (item.reason.isNotBlank()) {
                    Text(
                        text = item.reason,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp),
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle, color = TravelTextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                RecommendationMetaText(item)
                if (item.tags.isNotEmpty()) {
                    ChipRow(item.tags.take(4))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onOpenMap,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                    ) {
                        Text("地图查看")
                    }
                    Button(
                        onClick = onAskAi,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TravelTeal),
                    ) {
                        Text("融入行程")
                    }
                    if (item.bookingUrl.isNotBlank()) {
                        Button(
                            onClick = { nav.navigate(Screen.WebView(url = item.bookingUrl)) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = TravelOrange),
                        ) {
                            Text("去查看")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationMetaText(item: TravelRecommendationItem) {
    val meta = listOf(
        item.area.takeIf { it.isNotBlank() },
        item.ratingText.takeIf { it.isNotBlank() },
        item.priceHint.takeIf { it.isNotBlank() },
        item.inventoryHint.takeIf { it.isNotBlank() },
        item.source.takeIf { it.isNotBlank() },
    ).filterNotNull().joinToString(" · ")
    if (meta.isNotBlank()) {
        Text(
            text = meta,
            color = TravelTealDeep,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SmallHeaderChip(modifier: Modifier = Modifier, text: String) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.9f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = TravelTealDeep,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ChipRow(
    chips: List<String>,
    selected: String? = null,
    onSelect: ((String) -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            val isSelected = chip == selected
            Surface(
                modifier = Modifier.clickable(enabled = onSelect != null, onClick = { onSelect?.invoke(chip) }),
                color = if (isSelected) TravelTeal else Color.White,
                shape = RoundedCornerShape(18.dp),
                tonalElevation = if (isSelected) 0.dp else 1.dp,
                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, TravelLine),
            ) {
                Text(
                    text = chip,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    color = if (isSelected) Color.White else TravelTealDeep,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun TinyMapBadge(text: String, selected: Boolean) {
    Surface(
        color = if (selected) TravelOrange else TravelTeal,
        shape = RoundedCornerShape(16.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FloatingCircle(text: String) {
    Surface(
        color = Color.White.copy(alpha = 0.92f),
        shape = CircleShape,
        tonalElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text)
        }
    }
}

@Composable
private fun DayChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) TravelTeal else Color.White,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = if (selected) 0.dp else 1.dp,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, TravelLine),
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            color = if (selected) Color.White else TravelTealDeep,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DayTimelineCard(modifier: Modifier = Modifier, day: TravelItineraryDay) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(day.title.ifBlank { "Day ${day.dayIndex}" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
            Text(
                text = listOf(day.dateText.takeIf { it.isNotBlank() }, day.weatherHint.takeIf { it.isNotBlank() })
                    .filterNotNull()
                    .joinToString(" · ")
                    .ifBlank { "真实天气会写在这里" },
                color = TravelTextMuted,
            )
            day.items.forEachIndexed { index, item ->
                TimelineItemRow(
                    item = item,
                    isLast = index == day.items.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun TimelineItemRow(item: me.rerere.rikkahub.data.model.TravelItineraryItem, isLast: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, TravelLine, CircleShape),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(2.dp)
                        .height(72.dp)
                        .background(TravelLine),
                )
            }
        }
        Surface(
            modifier = Modifier.weight(1f),
            color = Color(0xFFFCFFFE),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, TravelLine),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    SmallStatusPill(categoryLabel(item.category), categoryColor(item.category))
                }
                val details = listOf(
                    item.timeSlot.takeIf { it.isNotBlank() },
                    item.description.takeIf { it.isNotBlank() },
                    item.transportHint.takeIf { it.isNotBlank() },
                    item.estimatedCost.takeIf { it.isNotBlank() },
                ).filterNotNull()
                details.forEach { line ->
                    Text(line, color = TravelTextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SmallStatusPill(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(16.dp)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun WeatherStrip(modifier: Modifier = Modifier, days: List<TravelItineraryDay>) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFEAF8F4),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("本周天气", fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                days.take(7).forEach { day ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(day.dateText.ifBlank { "Day${day.dayIndex}" }, style = MaterialTheme.typography.bodySmall, color = TravelTextMuted)
                        Text(weatherEmoji(day.weatherHint))
                    }
                }
            }
        }
    }
}

@Composable
private fun AiAdviceCard(modifier: Modifier = Modifier, day: TravelItineraryDay, onOpenAi: () -> Unit) {
    val transportHint = day.items.firstNotNullOfOrNull { it.transportHint.takeIf(String::isNotBlank) }
    val summary = buildString {
        if (day.weatherHint.isNotBlank()) append("天气：${day.weatherHint}")
        if (transportHint != null) {
            if (isNotBlank()) append("；")
            append("路线：$transportHint")
        }
        if (isBlank()) append("今天的真实天气与路线提示已经可用于 AI 二次优化。")
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(26.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("🦒 AI建议", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(summary, color = TravelTextMuted)
            TextButton(onClick = onOpenAi) { Text("询问 AI 优化行程 ›") }
        }
    }
}

@Composable
private fun MiniStatCard(modifier: Modifier = Modifier, label: String, value: String) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.14f),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(value, color = Color.White, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
            Text(label, color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
        }
    }
}

@Composable
private fun CurrentTripCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = if (onClick != null) {
            modifier.fillMaxWidth().clickable(onClick = onClick)
        } else {
            modifier.fillMaxWidth()
        },
        color = Color(0xFFFFF0C7),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFFFE3A4)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🏔")
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("当前行程", color = TravelTextMuted, style = MaterialTheme.typography.bodySmall)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = TravelTextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = if (onClick != null) "继续" else "›",
                style = MaterialTheme.typography.titleMedium,
                color = TravelOrange,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MineRow(
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null, onClick = { onClick?.invoke() })
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = if (enabled) Color.Unspecified else TravelTextMuted,
            )
            Text(
                text = subtitle,
                color = TravelTextMuted.copy(alpha = if (enabled) 1f else 0.72f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = if (enabled) "›" else "待接入",
            color = if (enabled) TravelTextMuted else TravelOrangeDeep,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun categoryLabel(category: TravelItemCategory): String = when (category) {
    TravelItemCategory.transport -> "交通"
    TravelItemCategory.sightseeing -> "景点"
    TravelItemCategory.food -> "餐饮"
    TravelItemCategory.hotel -> "住宿"
    TravelItemCategory.activity -> "活动"
    TravelItemCategory.free_time -> "自由"
    TravelItemCategory.shopping -> "购物"
    TravelItemCategory.other -> "其他"
}

private fun categoryColor(category: TravelItemCategory): Color = when (category) {
    TravelItemCategory.transport -> TravelBlueDeep
    TravelItemCategory.sightseeing -> TravelTealDeep
    TravelItemCategory.food -> TravelOrangeDeep
    TravelItemCategory.hotel -> Color(0xFF5E9AE0)
    TravelItemCategory.activity -> TravelMint
    TravelItemCategory.free_time -> Color(0xFF8CA6A0)
    TravelItemCategory.shopping -> Color(0xFFA86DE1)
    TravelItemCategory.other -> TravelTextMuted
}

private fun categoryDisplayName(category: TravelRecommendationCategory): String = when (category) {
    TravelRecommendationCategory.hotel -> "住宿"
    TravelRecommendationCategory.food -> "美食"
    TravelRecommendationCategory.activity -> "活动"
}

private fun categoryEmoji(category: TravelRecommendationCategory): String = when (category) {
    TravelRecommendationCategory.hotel -> "🏨"
    TravelRecommendationCategory.food -> "🍜"
    TravelRecommendationCategory.activity -> "🎯"
}

private fun paletteForCategory(category: TravelRecommendationCategory): TravelPalette = when (category) {
    TravelRecommendationCategory.hotel -> TravelPalette(TravelBlue, Color(0xFF9CD8FF), Color(0xFFE8F6FF), TravelBlueDeep)
    TravelRecommendationCategory.food -> TravelPalette(TravelOrange, Color(0xFFFFC16B), Color(0xFFFFF0DC), TravelOrangeDeep)
    TravelRecommendationCategory.activity -> TravelPalette(TravelTeal, Color(0xFF9AE7D1), Color(0xFFE7FAF3), TravelTealDeep)
}

private fun categorySearchPlaceholder(category: TravelRecommendationCategory): String = when (category) {
    TravelRecommendationCategory.hotel -> "搜索民宿、酒店名称…"
    TravelRecommendationCategory.food -> "搜索餐厅、菜系…"
    TravelRecommendationCategory.activity -> "搜索活动体验…"
}

private fun buildRecommendationSubtitle(
    category: TravelRecommendationCategory,
    conversation: Conversation,
    ui: TravelHubUiState,
): String {
    val destination = conversation.travelPlan?.brief?.destination?.ifBlank { ui.selectedDestination?.name.orEmpty() }
        ?.ifBlank { ui.selectedDestination?.name.orEmpty() }
        ?: "待选择目的地"
    return when (category) {
        TravelRecommendationCategory.hotel -> "$destination · 发现舒适好去处"
        TravelRecommendationCategory.food -> "$destination · 发现地道美味"
        TravelRecommendationCategory.activity -> "$destination · 探索独特体验"
    }
}

private fun buildRecommendationFilterChips(category: TravelRecommendationCategory): List<String> = when (category) {
    TravelRecommendationCategory.hotel -> listOf("综合推荐", "价格升序", "评分最高", "距离最近", "民宿", "酒店", "江景")
    TravelRecommendationCategory.food -> listOf("全部", "米粉", "烧烤", "素食", "茶餐", "夜宵")
    TravelRecommendationCategory.activity -> listOf("全部", "户外探险", "文化探索", "休闲活动", "夜游")
}

private fun mapFilterLabel(value: String): String = when (value) {
    "hotel" -> "住宿"
    "food" -> "美食"
    "route" -> "路线"
    else -> "活动"
}

private fun mapFilterValue(label: String): String = when (label) {
    "住宿" -> "hotel"
    "美食" -> "food"
    "路线" -> "route"
    else -> "activity"
}

private fun travelPlanningStateLabel(state: TravelPlanningState): String = when (state) {
    TravelPlanningState.ExtractingBrief -> "提炼需求中"
    TravelPlanningState.GeneratingPlan -> "生成中"
    TravelPlanningState.Generated -> "已生成"
    TravelPlanningState.Failed -> "生成失败"
    else -> "待完善"
}

private fun weatherEmoji(text: String): String {
    val lower = text.lowercase()
    return when {
        "雨" in text || "rain" in lower -> "🌧"
        "云" in text || "cloud" in lower -> "☁️"
        "雪" in text || "snow" in lower -> "❄️"
        "风" in text || "wind" in lower -> "🌬"
        else -> "☀️"
    }
}

private fun sendTravelAiPrompt(vm: ChatVM, conversation: Conversation, prompt: String) {
    if (prompt.isBlank()) return
    if (conversation.messageNodes.isEmpty()) {
        vm.startTravelPlanning(prompt)
    } else {
        vm.ensureTravelAssistantSelected()
        vm.handleMessageSend(listOf(UIMessagePart.Text(prompt)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartPlanningSheet(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var origin by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var dates by rememberSaveable { mutableStateOf("") }
    var travelers by rememberSaveable { mutableStateOf("") }
    var budget by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var transport by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("开始填写旅行需求", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text("保持原有入口不变，只补充真实天气、路线和推荐联动。", color = TravelTextMuted)
            PlannerField(origin, { origin = it }, "出发地")
            PlannerField(destination, { destination = it }, "目的地")
            PlannerField(dates, { dates = it }, "日期")
            PlannerField(travelers, { travelers = it }, "人数")
            PlannerField(budget, { budget = it }, "预算")
            PlannerField(tags, { tags = it }, "风格偏好")
            PlannerField(transport, { transport = it }, "交通偏好")
            Button(
                onClick = {
                    onSubmit(
                        buildString {
                            appendLine("请帮我创建一个旅行规划。")
                            appendLine("出发地: ${origin.ifBlank { "待定" }}")
                            appendLine("目的地: ${destination.ifBlank { "待定" }}")
                            appendLine("日期: ${dates.ifBlank { "待定" }}")
                            appendLine("人数: ${travelers.ifBlank { "待定" }}")
                            appendLine("预算: ${budget.ifBlank { "待定" }}")
                            appendLine("风格偏好: ${tags.ifBlank { "轻松、美食、拍照" }}")
                            appendLine("交通偏好: ${transport.ifBlank { "高铁、航班、公共交通" }}")
                            append("请先提炼 brief，再生成推荐和详细行程。")
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TravelTeal),
            ) {
                Text("继续交给 AI")
            }
        }
    }
}

@Composable
private fun ColumnScope.PlannerField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    )
}
