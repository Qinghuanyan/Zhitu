package me.rerere.rikkahub.ui.pages.travel

import android.content.Intent
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.FavoriteEntity
import me.rerere.rikkahub.data.favorite.NodeFavoriteAdapter
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.FavoriteType
import me.rerere.rikkahub.data.model.TravelItineraryDay
import me.rerere.rikkahub.data.model.TravelItemCategory
import me.rerere.rikkahub.data.model.TravelPlanningBrief
import me.rerere.rikkahub.data.model.TravelPlanningState
import me.rerere.rikkahub.data.model.TravelPlan
import me.rerere.rikkahub.data.model.TravelPoi
import me.rerere.rikkahub.data.model.TravelRecommendationCategory
import me.rerere.rikkahub.data.model.TravelRecommendationItem
import me.rerere.rikkahub.data.model.TravelSearchSuggestion
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
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
import java.io.File
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

private data class TravelHistoryConversationSummary(
    val id: String,
    val title: String,
    val subtitle: String,
    val preview: String,
)

private data class TravelHistoryTripSummary(
    val conversationId: String,
    val title: String,
    val summary: String,
    val status: String,
)

private data class TravelFavoriteSummary(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val conversationId: String? = null,
    val nodeId: String? = null,
)

private data class TravelCurrentTripSummary(
    val conversationId: String?,
    val title: String,
    val summary: String,
)

private enum class TravelTab(val route: String, val label: String, val icon: String) {
    Home("home", "Home", "H"),
    Map("map", "Map", "M"),
    Itinerary("itinerary", "Trip", "T"),
    Ai("ai", "AI", "A"),
    Mine("mine", "Me", "P"),
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
    val conversationRepo: ConversationRepository = koinInject()
    val favoriteRepository: FavoriteRepository = koinInject()
    val nav = LocalNavController.current
    val context = LocalContext.current
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val historyConversationCount by vm.historyConversationCount.collectAsStateWithLifecycle()
    val assistantConversations by conversationRepo
        .getConversationsOfAssistant(conversation.assistantId)
        .collectAsStateWithLifecycle(emptyList())
    val favoriteEntities by favoriteRepository
        .listByType(FavoriteType.NODE)
        .collectAsStateWithLifecycle(emptyList())
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
    val historyConversations = remember(assistantConversations) {
        assistantConversations
            .sortedByDescending { it.updateAt }
            .take(4)
            .map(Conversation::toTravelHistoryConversationSummary)
    }
    val historyTrips = remember(assistantConversations) {
        assistantConversations
            .sortedByDescending { it.updateAt }
            .mapNotNull(Conversation::toTravelHistoryTripSummary)
            .take(4)
    }
    val favoriteItems = remember(favoriteEntities, conversation) {
        favoriteEntities
            .mapNotNull(FavoriteEntity::toTravelFavoriteSummary)
            .ifEmpty { conversation.travelPlan.toFallbackFavoriteSummaries() }
            .take(6)
    }
    val currentTripSummary = remember(conversation) {
        conversation.toTravelCurrentTripSummary()
    }
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

    Box(Modifier.fillMaxSize()) {
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
                TravelTab.Home -> IntegratedHomeTab(
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
                    onOpenItinerary = { tab = TravelTab.Itinerary },
                    onOpenMine = { tab = TravelTab.Mine },
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

                TravelTab.Itinerary -> IntegratedItineraryTab(
                    modifier = Modifier.padding(inner),
                    conversation = conversation,
                    onStartPlanning = { showSheet = true },
                    onOpenAi = { tab = TravelTab.Ai },
                    onOpenMap = { tab = TravelTab.Map },
                    onExportTrip = {
                        val file = exportTripMarkdownFile(context, conversation)
                        Toast.makeText(context, "宸插鍑哄埌 ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    },
                    onShareTrip = {
                        shareTripMarkdownFile(context, conversation)
                    },
                )

                TravelTab.Ai -> IntegratedAiTab(
                    modifier = Modifier.padding(inner),
                    vm = vm,
                    conversation = conversation,
                    nodeId = nodeId,
                    onOpenItinerary = { tab = TravelTab.Itinerary },
                    onOpenMap = { tab = TravelTab.Map },
                    onOpenHotels = { nav.navigate(Screen.TravelHotels(id.toString())) },
                    onOpenFoods = { nav.navigate(Screen.TravelFoods(id.toString())) },
                    onOpenActivities = { nav.navigate(Screen.TravelActivities(id.toString())) },
                )

                TravelTab.Mine -> ProfileIntegratedMineTab(
                    modifier = Modifier.padding(inner),
                    conversation = conversation,
                    historyConversationCount = historyConversationCount,
                    historyConversations = historyConversations,
                    historyTrips = historyTrips,
                    favoriteItems = favoriteItems,
                    currentTripSummary = currentTripSummary,
                    onOpenCurrentTrip = { tab = TravelTab.Itinerary },
                    onOpenHistoryConversation = { conversationId ->
                        nav.navigate(Screen.TravelHub(conversationId, startTab = "ai")) { launchSingleTop = true }
                    },
                    onOpenHistoryTrip = { conversationId ->
                        nav.navigate(Screen.TravelHub(conversationId, startTab = "itinerary")) { launchSingleTop = true }
                    },
                    onOpenFavoriteItem = { favorite ->
                        when {
                            !favorite.conversationId.isNullOrBlank() && !favorite.nodeId.isNullOrBlank() -> {
                                nav.navigate(Screen.Chat(favorite.conversationId, nodeId = favorite.nodeId)) {
                                    launchSingleTop = true
                                }
                            }

                            favorite.category == "hotel" -> nav.navigate(Screen.TravelHotels(id.toString())) { launchSingleTop = true }
                            favorite.category == "food" -> nav.navigate(Screen.TravelFoods(id.toString())) { launchSingleTop = true }
                            favorite.category == "activity" -> nav.navigate(Screen.TravelActivities(id.toString())) { launchSingleTop = true }
                            else -> nav.navigate(Screen.Favorite) { launchSingleTop = true }
                        }
                    },
                )
            }
        }

        if (nav.canPop) {
            BackButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 12.dp)
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
                icon = "馃搶",
                title = "鍏辨壘鍒?${items.size} 鏉?{categoryDisplayName(category)}",
            )
        }

        if (items.isEmpty()) {
            item {
                EmptyStateCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = "No ${categoryDisplayName(category)} data yet",
                    text = if (ui.isLoadingDetails) "Loading destination facts..." else "Choose a destination first, then load recommendations or generate a plan.",
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
    appendLine("Please merge this ${categoryDisplayName(category)} suggestion into my travel plan.")
    appendLine("Title: ${item.title}")
    if (item.subtitle.isNotBlank()) appendLine("Address: ${item.subtitle}")
    if (item.area.isNotBlank()) appendLine("Area: ${item.area}")
    if (item.priceHint.isNotBlank()) appendLine("Price: ${item.priceHint}")
    if (item.ratingText.isNotBlank()) appendLine("Rating: ${item.ratingText}")
    if (item.inventoryHint.isNotBlank()) appendLine("Availability: ${item.inventoryHint}")
    if (item.bookingUrl.isNotBlank()) appendLine("Link: ${item.bookingUrl}")
    append("Update the itinerary and explain the best day and time slot for this item.")
}

private fun buildBriefSummary(brief: TravelPlanningBrief?): String {
    if (brief == null) return "Tell me destination, dates, traveler count, budget, and preferences."
    return listOf(
        brief.destination.takeIf { it.isNotBlank() },
        brief.origin.takeIf { it.isNotBlank() }?.let { "From $it" },
        brief.dateRange.takeIf { it.isNotBlank() },
        brief.days?.takeIf { it > 0 }?.let { "$it days" },
        brief.travelerCount?.let { "$it travelers" },
        brief.budgetText.takeIf { it.isNotBlank() } ?: brief.budgetLevel.takeIf { it.isNotBlank() },
        brief.travelStyleTags.takeIf { it.isNotEmpty() }?.joinToString(" / "),
        brief.transportPreferences.takeIf { it.isNotEmpty() }?.joinToString(" / ")?.let { "Transport $it" },
    ).filterNotNull().joinToString(" | ").ifBlank { brief.userIntentSummary.ifBlank { "Trip brief is ready." } }
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
    onOpenItinerary: () -> Unit,
    onOpenMine: () -> Unit,
) {
    IntegratedHomeTab(
        modifier = modifier,
        conversation = conversation,
        ui = ui,
        weatherSummary = weatherSummary,
        onQueryChange = onQueryChange,
        onSelectSuggestion = onSelectSuggestion,
        onStartPlanning = onStartPlanning,
        onGeneratePlan = onGeneratePlan,
        onOpenHotels = onOpenHotels,
        onOpenFoods = onOpenFoods,
        onOpenActivities = onOpenActivities,
        onOpenMap = onOpenMap,
        onOpenAi = onOpenAi,
        onOpenItinerary = onOpenItinerary,
        onOpenMine = onOpenMine,
    )
}

@Composable
private fun IntegratedHomeTab(
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
    onOpenItinerary: () -> Unit,
    onOpenMine: () -> Unit,
) {
    val destination = conversation.travelPlan?.brief?.destination?.ifBlank { "Travel Planner" } ?: "Travel Planner"
    val days = conversation.travelPlan?.itineraryDays.orEmpty()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TravelBg),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GradientHeader(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                palette = TravelPalette(TravelTealDeep, TravelTeal, Color(0xFFE0F8F1), TravelMint),
                smallTitle = "HOME",
                title = destination,
                subtitle = weatherSummary.ifBlank { buildBriefSummary(conversation.travelPlan?.brief) },
            ) {
                HeaderActionRow(
                    leftLabel = "Me",
                    rightLabel = "Map",
                    onLeft = onOpenMine,
                    onRight = onOpenMap,
                )
                Spacer(Modifier.height(12.dp))
                SearchBox(
                    value = ui.searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = "Search destination, place, or food",
                )
                if (ui.suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    SuggestionCard(
                        suggestions = ui.suggestions,
                        onSelectSuggestion = onSelectSuggestion,
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HomeShortcutChip(label = "Trip", onClick = onOpenItinerary)
                HomeShortcutChip(label = "Hotels", onClick = onOpenHotels)
                HomeShortcutChip(label = "Food", onClick = onOpenFoods)
                HomeShortcutChip(label = "Explore", onClick = onOpenActivities)
                HomeShortcutChip(label = "AI", onClick = onOpenAi)
            }
        }

        item {
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color.White,
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Weather", fontWeight = FontWeight.ExtraBold)
                    Text(weatherSummary.ifBlank { "Weather is syncing." }, color = TravelTextMuted)
                }
            }
        }

        if (days.isNotEmpty()) {
            item {
                CurrentTripCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = destination,
                    subtitle = conversation.travelPlan?.toTravelSummaryText().orEmpty(),
                    onClick = onOpenItinerary,
                )
            }
        }

        item {
            AssistantCtaCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = if (days.isEmpty()) "Start a new trip" else "Keep refining this trip",
                text = "Use AI to build itinerary, weather-aware routes, hotels, food, and exploration suggestions.",
                primaryLabel = if (days.isEmpty()) "Generate" else "Replan",
                secondaryLabel = "Open AI",
                onPrimary = onGeneratePlan,
                onSecondary = onOpenAi,
            )
        }

        item {
            ActionStrip(
                modifier = Modifier.padding(horizontal = 16.dp),
                actions = listOf(
                    "Map" to onOpenMap,
                    "Trip" to onOpenItinerary,
                    "Planner" to onStartPlanning,
                    "AI" to onOpenAi,
                ),
            )
        }
    }
}

@Composable
private fun HomeShortcutChip(
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = Color(0xFF4B5563),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
        )
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TravelBg),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Surface(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Map", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            conversation.travelPlan?.brief?.destination?.ifBlank { "Travel map" } ?: "Travel map",
                            color = TravelTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    SmallStatusPill(text = "Live", color = TravelTealDeep)
                }
                MapSearchControls(
                    ui = ui,
                    onQueryChange = onQueryChange,
                    onSelectSuggestion = onSelectSuggestion,
                    onSelectFilter = onSelectFilter,
                )
            }
        }

        MapViewport(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            nav = nav,
            mapPois = mapPois,
            activePoiId = activePoiId,
            showBottomPanel = false,
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
        placeholder = "Search place, POI, or food",
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
        chips = listOf("Hotel", "Food", "Activity", "Route"),
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
    Box(modifier = modifier.fillMaxWidth()) {
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
                title = "No POI on map yet",
                text = "Search a destination or generate an itinerary first. POIs will sync to the map here.",
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
                    Text(text = poi.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = listOf(poi.category, poi.address).filter { it.isNotBlank() }.joinToString(" | ").ifBlank { "Waiting for more map details" },
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
private fun IntegratedItineraryTab(
    modifier: Modifier,
    conversation: Conversation,
    onStartPlanning: () -> Unit,
    onOpenAi: () -> Unit,
    onOpenMap: () -> Unit,
    onExportTrip: () -> Unit,
    onShareTrip: () -> Unit,
) {
    val plan = conversation.travelPlan
    val days = plan?.itineraryDays.orEmpty()
    val destination = plan?.brief?.destination?.ifBlank { "My Trip" } ?: "My Trip"
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
        modifier = modifier
            .fillMaxSize()
            .background(TravelBg),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GradientHeader(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                palette = TravelPalette(TravelTealDeep, TravelTeal, Color(0xFFE1F8F2), TravelGold),
                smallTitle = "ITINERARY",
                title = destination,
                subtitle = buildBriefSummary(plan?.brief),
            ) {
                HeroStatsRow(
                    listOf(
                        "${(plan?.brief?.days ?: days.size).coerceAtLeast(days.size)} days",
                        if (plan?.pois.isNullOrEmpty()) "POI pending" else "${plan?.pois.orEmpty().size} POIs",
                        travelPlanningStateLabel(conversation.travelPlanningState),
                    ),
                )
                Spacer(Modifier.height(12.dp))
                HeaderActionRow(
                    leftLabel = "Ask AI",
                    rightLabel = "Map",
                    onLeft = onOpenAi,
                    onRight = onOpenMap,
                )
            }
        }

        if (days.isEmpty()) {
            item {
                AssistantCtaCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = "No detailed itinerary yet",
                    text = "Add destination and preferences first, then generate daily schedule, weather hints, and route suggestions.",
                    primaryLabel = "Generate now",
                    secondaryLabel = "Open AI",
                    onPrimary = onStartPlanning,
                    onSecondary = onOpenAi,
                )
            }
        } else {
            item {
                ItineraryPreviewCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    destination = destination,
                    day = selectedDay,
                    planningState = travelPlanningStateLabel(conversation.travelPlanningState),
                    onOpenMap = onOpenMap,
                    onStartPlanning = onStartPlanning,
                )
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(days, key = { it.dayIndex }) { day ->
                        DayChip(
                            title = "Day ${day.dayIndex}",
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

            item {
                ActionStrip(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    actions = listOf(
                        "Replan" to onStartPlanning,
                        "Export" to onExportTrip,
                        "Share" to onShareTrip,
                        "Map" to onOpenMap,
                        "AI" to onOpenAi,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ItineraryPreviewCard(
    modifier: Modifier = Modifier,
    destination: String,
    day: TravelItineraryDay?,
    planningState: String,
    onOpenMap: () -> Unit,
    onStartPlanning: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(26.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = destination,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = day?.title?.ifBlank { "Day ${day.dayIndex}" } ?: "Waiting for itinerary preview",
                        color = TravelTextMuted,
                    )
                }
                SmallStatusPill(text = planningState, color = TravelTealDeep)
            }

            Surface(
                color = Color(0xFFF8FCFB),
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TravelLine),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = day?.dateText?.ifBlank { "Weather and timing aligned" }
                            ?: "Generated plan will automatically include weather and timing guidance.",
                        color = TravelTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    day?.items?.take(4)?.forEachIndexed { index, item ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${index + 1}. ${item.title}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            val detail = listOf(
                                item.timeSlot.takeIf { it.isNotBlank() },
                                item.description.takeIf { it.isNotBlank() },
                            ).filterNotNull().joinToString(" | ")
                            if (detail.isNotBlank()) {
                                Text(
                                    text = detail,
                                    color = TravelTextMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (day?.items.isNullOrEmpty()) {
                        Text(
                            text = "No detailed activities for this day yet. Replan or continue filling details in AI.",
                            color = TravelTextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStartPlanning,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TravelTeal),
                ) {
                    Text("Replan")
                }
                Button(
                    onClick = onOpenMap,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TravelOrange),
                ) {
                    Text("View map")
                }
            }
        }
    }
}

@Composable
private fun IntegratedAiTab(
    modifier: Modifier,
    vm: ChatVM,
    conversation: Conversation,
    nodeId: Uuid?,
    onOpenItinerary: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenHotels: () -> Unit,
    onOpenFoods: () -> Unit,
    onOpenActivities: () -> Unit,
) {
    var input by rememberSaveable(conversation.id.toString()) { mutableStateOf("") }
    val listState = rememberLazyListState()
    ImeLazyListAutoScroller(listState)
    val focusNode = nodeId?.let { target -> conversation.currentMessages.firstOrNull { it.id == target } }
    val shortcutPrompts = listOf(
        "Spots" to "Recommend the best places to visit today for my current destination.",
        "Food" to "Recommend nearby food options that fit my current destination and plan.",
        "Stay" to "Suggest a better accommodation option based on my current itinerary.",
        "Optimize today" to "Optimize today's itinerary with a more reasonable route and schedule.",
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 1.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(TravelTeal, TravelBlue, Color(0xFFA855F7)),
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("AI", color = Color.White, fontWeight = FontWeight.ExtraBold)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("OASIS AI", fontWeight = FontWeight.ExtraBold)
                            Text(
                                text = buildBriefSummary(conversation.travelPlan?.brief),
                                color = TravelTextMuted,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    SmallStatusPill(
                        text = travelPlanningStateLabel(conversation.travelPlanningState),
                        color = TravelTealDeep,
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HomeShortcutChip(label = "Itinerary", onClick = onOpenItinerary)
                    HomeShortcutChip(label = "Map", onClick = onOpenMap)
                    HomeShortcutChip(label = "Hotels", onClick = onOpenHotels)
                    HomeShortcutChip(label = "Food", onClick = onOpenFoods)
                    HomeShortcutChip(label = "Explore", onClick = onOpenActivities)
                }
            }
        }

        if (focusNode != null) {
            InfoBanner(
                title = "Focused message",
                text = focusNode.toText(),
                tone = Color(0xFFE8F8F3),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            shortcutPrompts.forEach { (label, prompt) ->
                HomeShortcutChip(
                    label = label,
                    onClick = { sendTravelAiPrompt(vm, conversation, prompt) },
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(conversation.currentMessages, key = { it.id.toString() }) { message ->
                val isUser = message.role == MessageRole.USER
                val textContent = message.toText().ifBlank {
                    if (isUser) "(empty message)" else "(non-text message)"
                }
                if (isUser) {
                    UserChatBubble(text = textContent)
                } else {
                    AssistantChatBubble(text = textContent)
                }
            }
        }

        Surface(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding(),
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
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
                    minLines = 2,
                    placeholder = { Text("Enter destination, constraints, or what you want to adjust...") },
                    shape = RoundedCornerShape(20.dp),
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
                        Text("Send")
                    }
                    Button(
                        onClick = { vm.generateTravelPlan() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TravelOrange),
                    ) {
                        Text("Generate plan")
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantChatBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp, end = 8.dp)
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(TravelTeal, TravelBlue, Color(0xFFA855F7)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "AI",
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        Surface(
            modifier = Modifier.width(292.dp),
            color = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp),
            tonalElevation = 1.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDDF0EA)),
        ) {
            MarkdownBlock(
                content = text,
                modifier = Modifier.padding(14.dp),
            )
        }
    }
}

@Composable
private fun UserChatBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Box(
            modifier = Modifier
                .width(292.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(Brush.linearGradient(listOf(TravelTeal, TravelBlueDeep)))
                .padding(14.dp),
        ) {
            Text(text = text, color = Color.White)
        }
    }
}

@Composable
private fun ProfileIntegratedMineTab(
    modifier: Modifier,
    conversation: Conversation,
    historyConversationCount: Int,
    historyConversations: List<TravelHistoryConversationSummary>,
    historyTrips: List<TravelHistoryTripSummary>,
    favoriteItems: List<TravelFavoriteSummary>,
    currentTripSummary: TravelCurrentTripSummary?,
    onOpenCurrentTrip: () -> Unit,
    onOpenHistoryConversation: (String) -> Unit,
    onOpenHistoryTrip: (String) -> Unit,
    onOpenFavoriteItem: (TravelFavoriteSummary) -> Unit,
) {
    val plan = conversation.travelPlan
    val nav = LocalNavController.current
    val context = LocalContext.current
    val destination = plan?.brief?.destination?.ifBlank { "Traveling" } ?: "Traveling"
    val stats = listOf(
        "History" to historyConversationCount.toString(),
        "Favorites" to favoriteItems.size.toString(),
        "Days" to (plan?.brief?.days ?: plan?.itineraryDays.orEmpty().size).toString(),
    )
    var profileTab by rememberSaveable(conversation.id.toString()) { mutableStateOf("history") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TravelBg),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            GradientHeader(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                palette = TravelPalette(TravelTealDeep, TravelTeal, Color(0xFFE0F7F1), TravelGold),
                smallTitle = "PROFILE",
                title = "Traveler",
                subtitle = "$destination | native integrated page",
            ) {
                HeroStatsRow(stats.map { "${it.first} ${it.second}" })
                Spacer(Modifier.height(12.dp))
                HeaderActionRow(
                    leftLabel = "Settings",
                    rightLabel = "More",
                    onLeft = { nav.navigate(Screen.Setting) },
                    onRight = { nav.navigate(Screen.SettingAbout) },
                )
            }
        }

        if (currentTripSummary != null) {
            item {
                CurrentTripCard(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = currentTripSummary.title,
                    subtitle = currentTripSummary.summary,
                    onClick = onOpenCurrentTrip,
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color.White,
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 1.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProfileTabButton(
                        modifier = Modifier.weight(1f),
                        label = "History",
                        selected = profileTab == "history",
                        onClick = { profileTab = "history" },
                    )
                    ProfileTabButton(
                        modifier = Modifier.weight(1f),
                        label = "Favorites",
                        selected = profileTab == "favorites",
                        onClick = { profileTab = "favorites" },
                    )
                }
            }
        }

        if (profileTab == "history") {
            item {
                SectionTitle(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    icon = "H",
                    title = "History conversations",
                    action = "All",
                    onAction = { nav.navigate(Screen.History) },
                )
            }

            if (historyConversations.isEmpty()) {
                item {
                    EmptyStateCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        title = "No history conversations yet",
                        text = "After you finish a travel plan, recent sessions, resumable itineraries, and favorite entries will appear here.",
                    )
                }
            } else {
                historyConversations.forEach { historyConversation ->
                    item {
                        HistoryConversationCard(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            item = historyConversation,
                            onClick = { onOpenHistoryConversation(historyConversation.id) },
                        )
                    }
                }
            }

            if (historyTrips.isNotEmpty()) {
                item {
                    SectionTitle(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        icon = "T",
                        title = "History trips",
                    )
                }
                historyTrips.forEach { historyTrip ->
                    item {
                        HistoryTripCard(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            item = historyTrip,
                            onClick = { onOpenHistoryTrip(historyTrip.conversationId) },
                        )
                    }
                }
            }

            item {
                ProfileActionEntry(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    title = "Travel preferences",
                    subtitle = "Account, preferences, and default rules",
                    onClick = { nav.navigate(Screen.Setting) },
                )
            }
        } else {
            item {
                SectionTitle(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    icon = "F",
                    title = "Favorites",
                    action = "Manage",
                    onAction = { nav.navigate(Screen.Favorite) },
                )
            }

            if (favoriteItems.isEmpty()) {
                item {
                    EmptyStateCard(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        title = "No favorites yet",
                        text = "After generating an itinerary, you can keep favorites from hotels, food, activities, or message nodes.",
                    )
                }
            } else {
                favoriteItems.groupBy { it.category }.forEach { (category, itemsForCategory) ->
                    item {
                        SectionTitle(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            icon = when (category) {
                                "hotel" -> "H"
                                "food" -> "F"
                                "activity" -> "A"
                                else -> "*"
                            },
                            title = category.ifBlank { "other" },
                        )
                    }
                    itemsForCategory.forEach { favoriteItem ->
                        item {
                            FavoriteSummaryCard(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                item = favoriteItem,
                                onClick = { onOpenFavoriteItem(favoriteItem) },
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Color.White,
                    shape = RoundedCornerShape(26.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column {
                        MineRow("My favorites", "${favoriteItems.size} items", enabled = true, onClick = { nav.navigate(Screen.Favorite) })
                        MineRow("History sessions", "$historyConversationCount items", enabled = true, onClick = { nav.navigate(Screen.History) })
                        MineRow(
                            "System settings",
                            "Account and privacy",
                            enabled = true,
                            onClick = { nav.navigate(Screen.Setting) },
                        )
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable { Toast.makeText(context, "Logout is not connected yet", Toast.LENGTH_SHORT).show() },
                color = Color(0xFFEFF9F5),
                shape = RoundedCornerShape(22.dp),
            ) {
                Text(
                    text = "Logout",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    color = TravelTealDeep,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ProfileTabButton(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) TravelTeal else Color(0xFFF8FAFC),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = if (selected) 1.dp else 0.dp,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 12.dp),
            color = if (selected) Color.White else Color(0xFF4B5563),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ProfileActionEntry(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, fontWeight = FontWeight.Bold)
            Text(text = subtitle, color = TravelTextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable
private fun LegacyItineraryTab(
    modifier: Modifier,
    conversation: Conversation,
    onStartPlanning: () -> Unit,
    onOpenAi: () -> Unit,
    onExportTrip: () -> Unit,
    onShareTrip: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize())
}

@Composable
private fun LegacyAiTab(
    modifier: Modifier,
    vm: ChatVM,
    conversation: Conversation,
    nodeId: Uuid?,
    onOpenItinerary: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize())
}

@Composable
private fun LegacyIntegratedMineTab(
    modifier: Modifier,
    conversation: Conversation,
    historyConversationCount: Int,
    historyConversations: List<TravelHistoryConversationSummary>,
    historyTrips: List<TravelHistoryTripSummary>,
    favoriteItems: List<TravelFavoriteSummary>,
    currentTripSummary: TravelCurrentTripSummary?,
    onOpenCurrentTrip: () -> Unit,
    onOpenHistoryConversation: (String) -> Unit,
    onOpenHistoryTrip: (String) -> Unit,
    onOpenFavoriteItem: (TravelFavoriteSummary) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize())
}

@Composable
private fun MineTab(
    modifier: Modifier,
    conversation: Conversation,
    onOpenCurrentTrip: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize())
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
                        text = suggestion.name.ifBlank { "Unnamed place" },
                        fontWeight = FontWeight.Bold,
                    )
                    val subtitle = listOf(suggestion.district, suggestion.address)
                        .filter { it.isNotBlank() }
                        .joinToString(" 路 ")
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
                    .joinToString(" 路 ")
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
                    ).filterNotNull().joinToString(" 路 ").ifBlank { item.subtitle.ifBlank { "绛夊緟鏇村鍦扮偣璇︽儏" } },
                    color = TravelTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(">", style = MaterialTheme.typography.headlineSmall, color = TravelTextMuted)
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
                Text("馃")
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
                    Text("Fav", modifier = Modifier.padding(10.dp))
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
                        Text("鍦板浘鏌ョ湅")
                    }
                    Button(
                        onClick = onAskAi,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TravelTeal),
                    ) {
                        Text("铻嶅叆琛岀▼")
                    }
                    if (item.bookingUrl.isNotBlank()) {
                        Button(
                            onClick = { nav.navigate(Screen.WebView(url = item.bookingUrl)) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = TravelOrange),
                        ) {
                            Text("Open")
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
    ).filterNotNull().joinToString(" 路 ")
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
                    .joinToString(" 路 ")
                    .ifBlank { "Weather details will appear here." },
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
            Text("鏈懆澶╂皵", fontWeight = FontWeight.Bold)
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
        if (day.weatherHint.isNotBlank()) append("澶╂皵锛?{day.weatherHint}")
        if (transportHint != null) {
            if (isNotBlank()) append(" | ")
            append("璺嚎锛?transportHint")
        }
        if (isBlank()) append("Weather and route hints are ready for a second AI pass.")
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
            Text("馃 AI寤鸿", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(summary, color = TravelTextMuted)
            TextButton(onClick = onOpenAi) { Text("Ask AI to optimize") }
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
                Text("馃彅")
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("褰撳墠琛岀▼", color = TravelTextMuted, style = MaterialTheme.typography.bodySmall)
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = TravelTextMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = if (onClick != null) "Continue" else ">",
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
            text = if (enabled) ">" else "Pending",
            color = if (enabled) TravelTextMuted else TravelOrangeDeep,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HistoryConversationCard(
    modifier: Modifier = Modifier,
    item: TravelHistoryConversationSummary,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (item.subtitle.isNotBlank()) {
                Text(item.subtitle, color = TravelTealDeep, style = MaterialTheme.typography.bodySmall)
            }
            if (item.preview.isNotBlank()) {
                Text(
                    item.preview,
                    color = TravelTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HistoryTripCard(
    modifier: Modifier = Modifier,
    item: TravelHistoryTripSummary,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color(0xFFFFF7DF),
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFE3A4)),
                contentAlignment = Alignment.Center,
            ) {
                Text("馃Л")
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    item.summary,
                    color = TravelTextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SmallStatusPill(text = item.status.ifBlank { "trip" }, color = TravelOrangeDeep)
        }
    }
}

@Composable
private fun FavoriteSummaryCard(
    modifier: Modifier = Modifier,
    item: TravelFavoriteSummary,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
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
                Text(
                    when (item.category) {
                        "hotel" -> "馃彣"
                        "food" -> "馃崪"
                        "activity" -> "馃帿"
                        else -> "馃挰"
                    }
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (item.subtitle.isNotBlank()) {
                    Text(
                        item.subtitle,
                        color = TravelTextMuted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(">", color = TravelTextMuted, style = MaterialTheme.typography.titleLarge)
        }
    }
}

private fun Conversation.toTravelHistoryConversationSummary(): TravelHistoryConversationSummary {
    val preview = currentMessages.lastOrNull()?.toText().orEmpty().trim()
    return TravelHistoryConversationSummary(
        id = id.toString(),
        title = title.ifBlank { travelPlan?.brief?.destination?.ifBlank { "鍘嗗彶浼氳瘽" } ?: "鍘嗗彶浼氳瘽" },
        subtitle = travelPlan.toTravelSummaryText(),
        preview = preview,
    )
}

private fun Conversation.toTravelHistoryTripSummary(): TravelHistoryTripSummary? {
    val plan = travelPlan ?: return null
    return TravelHistoryTripSummary(
        conversationId = id.toString(),
        title = title.ifBlank { plan.brief?.destination?.ifBlank { "鍘嗗彶琛岀▼" } ?: "鍘嗗彶琛岀▼" },
        summary = plan.toTravelSummaryText(),
        status = travelPlanningState.name,
    )
}

private fun FavoriteEntity.toTravelFavoriteSummary(): TravelFavoriteSummary? {
    val ref = NodeFavoriteAdapter.decodeRef(this) ?: return null
    val meta = NodeFavoriteAdapter.decodeMeta(this)
    return TravelFavoriteSummary(
        id = id,
        title = meta?.title?.ifBlank { "鏀惰棌娑堟伅" } ?: "鏀惰棌娑堟伅",
        subtitle = meta?.previewText.orEmpty(),
        category = "favorite",
        conversationId = ref.conversationId.toString(),
        nodeId = ref.nodeId.toString(),
    )
}

private fun TravelPlan?.toFallbackFavoriteSummaries(): List<TravelFavoriteSummary> {
    val plan = this ?: return emptyList()

    fun items(category: String, list: List<TravelRecommendationItem>): List<TravelFavoriteSummary> {
        return list.take(2).map { item ->
            TravelFavoriteSummary(
                id = "$category-${item.id}",
                title = item.title,
                subtitle = item.subtitle.ifBlank { item.area.ifBlank { item.priceHint.ifBlank { item.ratingText } } },
                category = category,
                conversationId = plan.conversationId,
            )
        }
    }

    return buildList {
        addAll(items("hotel", plan.hotels))
        addAll(items("food", plan.foods))
        addAll(items("activity", plan.activities))
    }
}

private fun Conversation.toTravelCurrentTripSummary(): TravelCurrentTripSummary? {
    val plan = travelPlan ?: return null
    return TravelCurrentTripSummary(
        conversationId = id.toString(),
        title = title.ifBlank { plan.brief?.destination?.ifBlank { "Current trip" } ?: "Current trip" },
        summary = plan.toTravelSummaryText(),
    )
}

private fun TravelPlan?.toTravelSummaryText(): String {
    val plan = this ?: return ""
    val brief = plan.brief
    val parts = buildList {
        brief?.destination?.takeIf { it.isNotBlank() }?.let(::add)
        brief?.dateRange?.takeIf { it.isNotBlank() }?.let(::add)
        (brief?.days ?: plan.itineraryDays.size).takeIf { it > 0 }?.let { add("${it} days") }
    }
    return if (parts.isNotEmpty()) parts.joinToString(" | ") else "Trip summary pending"
}

private fun exportTripMarkdownFile(
    context: android.content.Context,
    conversation: Conversation,
): File {
    val exportsDir = File(context.cacheDir, "zhitu-exports").apply { mkdirs() }
    val safeName = (conversation.title.ifBlank { conversation.travelPlan?.brief?.destination ?: "travel-plan" })
        .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-")
        .trim('-')
        .ifBlank { "travel-plan" }
    val file = File(exportsDir, "$safeName.md")
    file.writeText(buildTripMarkdown(conversation))
    return file
}

private fun shareTripMarkdownFile(
    context: android.content.Context,
    conversation: Conversation,
) {
    val file = exportTripMarkdownFile(context, conversation)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, buildTripMarkdown(conversation))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share itinerary"))
}

private fun buildTripMarkdown(conversation: Conversation): String {
    val plan = conversation.travelPlan
    val brief = plan?.brief
    return buildString {
        appendLine("# ${conversation.title.ifBlank { brief?.destination ?: "Travel plan" }}")
        appendLine()
        if (brief != null) {
            appendLine("## Summary")
            brief.destination.takeIf { it.isNotBlank() }?.let { appendLine("- Destination: $it") }
            brief.origin.takeIf { it.isNotBlank() }?.let { appendLine("- Origin: $it") }
            brief.dateRange.takeIf { it.isNotBlank() }?.let { appendLine("- Dates: $it") }
            brief.days?.let { appendLine("- Days: $it") }
            brief.budgetText.takeIf { it.isNotBlank() }?.let { appendLine("- Budget: $it") }
            brief.userIntentSummary.takeIf { it.isNotBlank() }?.let { appendLine("- Intent: $it") }
            appendLine()
        }

        plan?.itineraryDays?.takeIf { it.isNotEmpty() }?.let { days ->
            appendLine("## Daily itinerary")
            days.forEach { day ->
                appendLine("### Day ${day.dayIndex}: ${day.title}")
                day.dateText.takeIf { it.isNotBlank() }?.let { appendLine("- Date: $it") }
                day.weatherHint.takeIf { it.isNotBlank() }?.let { appendLine("- Weather: $it") }
                day.items.forEach { item ->
                    appendLine("- ${item.timeSlot.ifBlank { "TBD" }}: ${item.title}")
                    item.description.takeIf { it.isNotBlank() }?.let { appendLine("  - Note: $it") }
                    item.transportHint.takeIf { it.isNotBlank() }?.let { appendLine("  - Transport: $it") }
                    item.estimatedCost.takeIf { it.isNotBlank() }?.let { appendLine("  - Cost: $it") }
                }
                appendLine()
            }
        }

        fun appendRecommendationBlock(title: String, items: List<TravelRecommendationItem>) {
            if (items.isEmpty()) return
            appendLine("## $title")
            items.forEach { item ->
                appendLine("- ${item.title}")
                item.subtitle.takeIf { it.isNotBlank() }?.let { appendLine("  - Location: $it") }
                item.reason.takeIf { it.isNotBlank() }?.let { appendLine("  - Why: $it") }
                item.priceHint.takeIf { it.isNotBlank() }?.let { appendLine("  - Price: $it") }
                item.ratingText.takeIf { it.isNotBlank() }?.let { appendLine("  - Rating: $it") }
            }
            appendLine()
        }

        appendRecommendationBlock("Hotel recommendations", plan?.hotels.orEmpty())
        appendRecommendationBlock("Food recommendations", plan?.foods.orEmpty())
        appendRecommendationBlock("Activity recommendations", plan?.activities.orEmpty())
    }
}

private fun categoryLabel(category: TravelItemCategory): String = when (category) {
    TravelItemCategory.transport -> "Transport"
    TravelItemCategory.sightseeing -> "Sight"
    TravelItemCategory.food -> "Food"
    TravelItemCategory.hotel -> "Hotel"
    TravelItemCategory.activity -> "Activity"
    TravelItemCategory.free_time -> "Free"
    TravelItemCategory.shopping -> "Shopping"
    TravelItemCategory.other -> "Other"
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
    TravelRecommendationCategory.hotel -> "hotel"
    TravelRecommendationCategory.food -> "food"
    TravelRecommendationCategory.activity -> "activity"
}

private fun categoryEmoji(category: TravelRecommendationCategory): String = when (category) {
    TravelRecommendationCategory.hotel -> "H"
    TravelRecommendationCategory.food -> "F"
    TravelRecommendationCategory.activity -> "A"
}

private fun paletteForCategory(category: TravelRecommendationCategory): TravelPalette = when (category) {
    TravelRecommendationCategory.hotel -> TravelPalette(TravelBlue, Color(0xFF9CD8FF), Color(0xFFE8F6FF), TravelBlueDeep)
    TravelRecommendationCategory.food -> TravelPalette(TravelOrange, Color(0xFFFFC16B), Color(0xFFFFF0DC), TravelOrangeDeep)
    TravelRecommendationCategory.activity -> TravelPalette(TravelTeal, Color(0xFF9AE7D1), Color(0xFFE7FAF3), TravelTealDeep)
}

private fun categorySearchPlaceholder(category: TravelRecommendationCategory): String = when (category) {
    TravelRecommendationCategory.hotel -> "Search hotel or stay"
    TravelRecommendationCategory.food -> "Search restaurant or cuisine"
    TravelRecommendationCategory.activity -> "Search activity or experience"
}

private fun buildRecommendationSubtitle(
    category: TravelRecommendationCategory,
    conversation: Conversation,
    ui: TravelHubUiState,
): String {
    val destination = conversation.travelPlan?.brief?.destination?.ifBlank { ui.selectedDestination?.name.orEmpty() }
        ?.ifBlank { ui.selectedDestination?.name.orEmpty() }
        ?: "Destination TBD"
    return when (category) {
        TravelRecommendationCategory.hotel -> "$destination | comfortable stays"
        TravelRecommendationCategory.food -> "$destination | local flavors"
        TravelRecommendationCategory.activity -> "$destination | unique experiences"
    }
}

private fun buildRecommendationFilterChips(category: TravelRecommendationCategory): List<String> = when (category) {
    TravelRecommendationCategory.hotel -> listOf("Recommended", "Price", "Rating", "Distance", "Homestay", "Hotel")
    TravelRecommendationCategory.food -> listOf("All", "Local", "Cafe", "Late-night", "Dessert")
    TravelRecommendationCategory.activity -> listOf("All", "Outdoor", "Culture", "Leisure", "Night")
}

private fun mapFilterLabel(value: String): String = when (value) {
    "hotel" -> "Hotel"
    "food" -> "Food"
    "route" -> "Route"
    else -> "Activity"
}

private fun mapFilterValue(label: String): String = when (label) {
    "Hotel" -> "hotel"
    "Food" -> "food"
    "Route" -> "route"
    else -> "activity"
}

private fun travelPlanningStateLabel(state: TravelPlanningState): String = when (state) {
    TravelPlanningState.ExtractingBrief -> "Extracting brief"
    TravelPlanningState.GeneratingPlan -> "Generating"
    TravelPlanningState.Generated -> "Generated"
    TravelPlanningState.Failed -> "Failed"
    else -> "Idle"
}

private fun weatherEmoji(text: String): String {
    val lower = text.lowercase()
    return when {
        "rain" in lower -> "rain"
        "cloud" in lower -> "cloud"
        "snow" in lower -> "snow"
        "wind" in lower -> "wind"
        else -> "sun"
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
    var destination by rememberSaveable { mutableStateOf("") }
    var dates by rememberSaveable { mutableStateOf("") }
    var travelers by rememberSaveable { mutableStateOf("") }
    var budget by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Start planning", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text("Keep the current entry points, but enrich them with destination, schedule, and budget.", color = TravelTextMuted)
            PlannerField(destination, { destination = it }, "Destination")
            PlannerField(dates, { dates = it }, "Dates")
            PlannerField(travelers, { travelers = it }, "Travelers")
            PlannerField(budget, { budget = it }, "Budget")
            Button(
                onClick = {
                    onSubmit(
                        buildString {
                            appendLine("Create a travel plan.")
                            appendLine("Destination: ${destination.ifBlank { "TBD" }}")
                            appendLine("Dates: ${dates.ifBlank { "TBD" }}")
                            appendLine("Travelers: ${travelers.ifBlank { "TBD" }}")
                            appendLine("Budget: ${budget.ifBlank { "TBD" }}")
                            append("Extract a brief first, then generate recommendations and a detailed itinerary.")
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TravelTeal),
            ) {
                Text("Continue with AI")
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
