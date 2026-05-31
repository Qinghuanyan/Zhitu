package me.rerere.rikkahub.service

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.ToolApprovalState
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.finishReasoning
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.common.android.Logging
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.RouteActivity
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationFallbackNotice
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.tools.LocalTools
import me.rerere.rikkahub.data.ai.tools.createSearchTools
import me.rerere.rikkahub.data.ai.tools.createSkillTools
import me.rerere.rikkahub.data.files.SkillManager
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.transformers.OcrTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.PromptInjectionTransformer
import me.rerere.rikkahub.data.ai.transformers.RegexOutputTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.ai.transformers.TimeReminderTransformer
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRAVEL_BRIEF_PROMPT
import me.rerere.rikkahub.data.ai.prompts.DEFAULT_TRAVEL_ITINERARY_PROMPT
import me.rerere.rikkahub.data.ai.prompts.buildTravelBriefRefinerPrompt
import me.rerere.rikkahub.data.ai.prompts.buildTravelItineraryAuditorPrompt
import me.rerere.rikkahub.data.ai.prompts.buildTravelItineraryPrompt
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.datastore.TRAVEL_PLANNER_ASSISTANT_ID
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.TravelGeneratedPayload
import me.rerere.rikkahub.data.model.TravelItineraryDay
import me.rerere.rikkahub.data.model.TravelItineraryItem
import me.rerere.rikkahub.data.model.TravelItemCategory
import me.rerere.rikkahub.data.model.TravelPlan
import me.rerere.rikkahub.data.model.TravelPlanStatus
import me.rerere.rikkahub.data.model.TravelPlanningBrief
import me.rerere.rikkahub.data.model.TravelPlanningFacts
import me.rerere.rikkahub.data.model.TravelPlanningState
import me.rerere.rikkahub.data.model.TravelPoi
import me.rerere.rikkahub.data.model.TravelRecommendationCategory
import me.rerere.rikkahub.data.model.TravelRecommendationItem
import me.rerere.rikkahub.data.model.replaceRegexes
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.TravelPlanningDataRepository
import me.rerere.rikkahub.web.BadRequestException
import me.rerere.rikkahub.web.NotFoundException
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.applyPlaceholders
import me.rerere.rikkahub.utils.cancelNotification
import me.rerere.rikkahub.utils.sendNotification
import me.rerere.rikkahub.utils.stripMarkdown
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val TAG = "ChatService"
private const val GENERATION_PROGRESS_PERSIST_INTERVAL_MS = 1_500L
private const val GENERATION_PROGRESS_PERSIST_DELAY_MS = 400L

data class ChatError(
    val id: Uuid = Uuid.random(),
    val title: String? = null,
    val error: Throwable,
    val conversationId: Uuid? = null,
    val timestamp: Long = System.currentTimeMillis()
)

private val inputTransformers by lazy {
    listOf(
        TimeReminderTransformer,
        PromptInjectionTransformer,
        PlaceholderTransformer,
        DocumentAsPromptTransformer,
        OcrTransformer,
    )
}

private val outputTransformers by lazy {
    listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        RegexOutputTransformer,
    )
}

class ChatService(
    private val context: Application,
    private val appScope: AppScope,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val generationHandler: GenerationHandler,
    private val templateTransformer: TemplateTransformer,
    private val providerManager: ProviderManager,
    private val localTools: LocalTools,
    val mcpManager: McpManager,
    private val filesManager: FilesManager,
    private val skillManager: SkillManager,
    private val travelPlanningDataRepository: TravelPlanningDataRepository,
) {
    // 统一会话管理
    private val sessions = ConcurrentHashMap<Uuid, ConversationSession>()
    private val _sessionsVersion = MutableStateFlow(0L)
    private val progressPersistJobs = ConcurrentHashMap<Uuid, Job>()
    private val progressPersistAt = ConcurrentHashMap<Uuid, Long>()

    // 错误状态
    private val _errors = MutableStateFlow<List<ChatError>>(emptyList())
    val errors: StateFlow<List<ChatError>> = _errors.asStateFlow()

    fun addError(error: Throwable, conversationId: Uuid? = null, title: String? = null) {
        if (error is CancellationException) return
        _errors.update { it + ChatError(title = title, error = error, conversationId = conversationId) }
    }

    fun dismissError(id: Uuid) {
        _errors.update { list -> list.filter { it.id != id } }
    }

    fun clearAllErrors() {
        _errors.value = emptyList()
    }

    // 生成完成流
    private val _generationDoneFlow = MutableSharedFlow<Uuid>()
    val generationDoneFlow: SharedFlow<Uuid> = _generationDoneFlow.asSharedFlow()

    // 前台状态管理
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> _isForeground.value = true
            Lifecycle.Event.ON_STOP -> _isForeground.value = false
            else -> {}
        }
    }

    init {
        // 添加生命周期观察者
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }

    fun cleanup() = runCatching {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        sessions.values.forEach { it.cleanup() }
        sessions.clear()
    }

    // ---- Session 管理 ----

    private fun getOrCreateSession(conversationId: Uuid): ConversationSession {
        return sessions.computeIfAbsent(conversationId) { id ->
            val settings = settingsStore.settingsFlow.value
            ConversationSession(
                id = id,
                initial = Conversation.ofId(
                    id = id,
                    assistantId = settings.getCurrentAssistant().id
                ),
                scope = appScope,
                onIdle = { removeSession(it) }
            ).also {
                _sessionsVersion.value++
                Log.i(TAG, "createSession: $id (total: ${sessions.size + 1})")
            }
        }
    }

    private fun removeSession(conversationId: Uuid) {
        val session = sessions[conversationId] ?: return
        if (session.isInUse) {
            Log.d(TAG, "removeSession: skipped $conversationId (still in use)")
            return
        }
        if (sessions.remove(conversationId, session)) {
            session.cleanup()
            _sessionsVersion.value++
            Log.i(TAG, "removeSession: $conversationId (remaining: ${sessions.size})")
        }
    }

    // ---- 引用管理 ----

    fun addConversationReference(conversationId: Uuid) {
        getOrCreateSession(conversationId).acquire()
    }

    fun removeConversationReference(conversationId: Uuid) {
        sessions[conversationId]?.release()
    }

    private fun launchWithConversationReference(
        conversationId: Uuid,
        block: suspend () -> Unit
    ): Job = appScope.launch {
        addConversationReference(conversationId)
        try {
            block()
        } finally {
            removeConversationReference(conversationId)
        }
    }

    // ---- 对话状态访问 ----

    fun getConversationFlow(conversationId: Uuid): StateFlow<Conversation> {
        return getOrCreateSession(conversationId).state
    }

    fun getGenerationJobStateFlow(conversationId: Uuid): Flow<Job?> {
        val session = sessions[conversationId] ?: return flowOf(null)
        return session.generationJob
    }

    private fun Conversation.hasSessionState(): Boolean {
        return messageNodes.isNotEmpty() || travelPlan != null || chatSuggestions.isNotEmpty()
    }

    private fun Conversation.hasInterruptedGenerationState(): Boolean {
        val hasUnfinishedAssistantMessage = currentMessages.any { message ->
            message.role == MessageRole.ASSISTANT && message.finishedAt == null
        }
        return hasUnfinishedAssistantMessage ||
            travelPlanningState == TravelPlanningState.ExtractingBrief ||
            travelPlanningState == TravelPlanningState.GeneratingPlan
    }

    private fun Conversation.finishInterruptedGeneration(): Conversation {
        val finishedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val nextState = when (travelPlanningState) {
            TravelPlanningState.ExtractingBrief,
            TravelPlanningState.GeneratingPlan -> if (travelPlan?.brief?.canGenerate() == true) {
                TravelPlanningState.ReadyToGenerate
            } else {
                TravelPlanningState.DraftBrief
            }

            else -> travelPlanningState
        }
        val nextPlan = travelPlan?.let { plan ->
            when (travelPlanningState) {
                TravelPlanningState.ExtractingBrief,
                TravelPlanningState.GeneratingPlan -> plan.copy(
                    status = if (plan.brief?.canGenerate() == true) {
                        TravelPlanStatus.ready_to_generate
                    } else {
                        TravelPlanStatus.draft_brief
                    }
                )

                else -> plan
            }
        }

        return copy(
            messageNodes = messageNodes.map { node ->
                node.copy(
                    messages = node.messages.map { message ->
                        val normalized = message.finishReasoning()
                        if (normalized.role == MessageRole.ASSISTANT && normalized.finishedAt == null) {
                            normalized.copy(finishedAt = finishedAt)
                        } else {
                            normalized
                        }
                    }
                )
            },
            travelPlanningState = nextState,
            travelPlan = nextPlan,
        )
    }

    private suspend fun recoverInterruptedConversationIfNeeded(
        conversationId: Uuid,
        conversation: Conversation,
    ): Conversation {
        if (!conversation.hasInterruptedGenerationState()) return conversation
        val recoveredConversation = conversation.finishInterruptedGeneration()
        saveConversation(conversationId, recoveredConversation)
        addError(
            error = IllegalStateException("检测到上次生成未完成，已恢复已保存内容，可继续重试。"),
            conversationId = conversationId,
            title = "已恢复未完成会话"
        )
        return recoveredConversation
    }

    fun getConversationJobs(): Flow<Map<Uuid, Job?>> {
        return _sessionsVersion.flatMapLatest {
            val currentSessions = sessions.values.toList()
            if (currentSessions.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(currentSessions.map { s ->
                    s.generationJob.map { job -> s.id to job }
                }) { pairs ->
                    pairs.filter { it.second != null }.toMap()
                }
            }
        }
    }

    // ---- 初始化对话 ----

    suspend fun initializeConversation(conversationId: Uuid) {
        val session = getOrCreateSession(conversationId)
        val sessionConversation = session.state.value
        val conversation = conversationRepo.getConversationById(conversationId)
        if (session.isGenerating && sessionConversation.hasSessionState()) {
            settingsStore.updateAssistant(sessionConversation.assistantId)
            return
        }

        if (conversation != null) {
            val preferredConversation = if (
                sessionConversation.hasSessionState() &&
                sessionConversation.updateAt.isAfter(conversation.updateAt)
            ) {
                sessionConversation
            } else {
                recoverInterruptedConversationIfNeeded(conversationId, conversation)
            }
            updateConversation(conversationId, preferredConversation)
            settingsStore.updateAssistant(preferredConversation.assistantId)
        } else if (sessionConversation.hasSessionState()) {
            settingsStore.updateAssistant(sessionConversation.assistantId)
        } else {
            // 新建对话, 并添加预设消息
            val currentSettings = settingsStore.settingsFlowRaw.first()
            val assistant = currentSettings.getCurrentAssistant()
            val newConversation = Conversation.ofId(
                id = conversationId,
                assistantId = assistant.id,
                newConversation = true
            ).updateCurrentMessages(assistant.presetMessages)
            updateConversation(conversationId, newConversation)
        }
    }

    // ---- 发送消息 ----

    fun sendMessage(conversationId: Uuid, content: List<UIMessagePart>, answer: Boolean = true) {
        if (content.isEmptyInputMessage()) return

        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()
        val processedContent = preprocessUserInputParts(content)

        val job = appScope.launch {
            try {
                val currentConversation = session.state.value

                // 添加消息到列表
                val newConversation = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes + UIMessage(
                        role = MessageRole.USER,
                        parts = processedContent,
                    ).toMessageNode(),
                )
                saveConversation(conversationId, newConversation)

                // 开始补全
                if (answer) {
                    handleMessageComplete(conversationId)
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                e.printStackTrace()
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            }
        }
        session.setJob(job)
    }

    fun submitTravelPlanningRequest(conversationId: Uuid, content: String) {
        val normalized = content.trim()
        if (normalized.isBlank()) return

        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()

        val job = appScope.launch {
            try {
                val currentConversation = session.state.value
                val processedContent = preprocessUserInputParts(listOf(UIMessagePart.Text(normalized)))
                val newConversation = currentConversation.copy(
                    messageNodes = currentConversation.messageNodes + UIMessage(
                        role = MessageRole.USER,
                        parts = processedContent,
                    ).toMessageNode()
                )
                saveConversation(conversationId, newConversation)
                generateTravelBrief(conversationId, newConversation)
                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                e.printStackTrace()
                addError(e, conversationId, title = context.getString(R.string.error_title_send_message))
            }
        }
        session.setJob(job)
    }

    private fun preprocessUserInputParts(parts: List<UIMessagePart>): List<UIMessagePart> {
        val assistant = settingsStore.settingsFlow.value.getCurrentAssistant()
        return parts.map { part ->
            when (part) {
                is UIMessagePart.Text -> {
                    part.copy(
                        text = part.text.replaceRegexes(
                            assistant = assistant,
                            scope = AssistantAffectScope.USER,
                            visual = false
                        )
                    )
                }

                else -> part
            }
        }
    }

    private fun buildFallbackTitle(notice: GenerationFallbackNotice): String {
        return if (notice.cause.javaClass.simpleName.contains("Timeout")) {
            "当前模型超过 3 分钟未完成，已切换备用模型"
        } else if (notice.cause is CancellationException) {
            "当前模型已切换"
        } else {
            "当前模型不可用，已切换备用模型"
        }
    }

    private fun formatModelSwitchNotice(notice: GenerationFallbackNotice): String {
        val fromName = notice.fromModel.displayName.ifBlank { notice.fromModel.modelId }
        val toName = notice.toModel.displayName.ifBlank { notice.toModel.modelId }
        return "已从 $fromName 切换到 $toName"
    }

    private suspend fun generateSingleTextWithFallback(
        conversationId: Uuid?,
        settings: me.rerere.rikkahub.data.datastore.Settings,
        model: me.rerere.ai.provider.Model,
        messages: List<UIMessage>,
        thinkingBudget: Int = 0,
        title: String? = null,
    ): String {
        val result = generationHandler.generateSingleTextWithFallback(
            settings = settings,
            model = model,
            messages = messages,
            paramsBuilder = { targetModel ->
                TextGenerationParams(
                    model = targetModel,
                    thinkingBudget = thinkingBudget,
                )
            },
            onFallback = { notice ->
                addError(
                    error = IllegalStateException(formatModelSwitchNotice(notice)),
                    conversationId = conversationId,
                    title = title ?: buildFallbackTitle(notice)
                )
            }
        )
        return result.choices.firstOrNull()?.message?.toText().orEmpty()
    }

    // ---- 重新生成消息 ----

    fun regenerateAtMessage(
        conversationId: Uuid,
        message: UIMessage,
        regenerateAssistantMsg: Boolean = true
    ) {
        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()

        val job = appScope.launch {
            try {
                val conversation = session.state.value

                if (message.role == MessageRole.USER) {
                    // 如果是用户消息，则截止到当前消息
                    val node = conversation.getMessageNodeByMessage(message)
                    val indexAt = conversation.messageNodes.indexOf(node)
                    val newConversation = conversation.copy(
                        messageNodes = conversation.messageNodes.subList(0, indexAt + 1)
                    )
                    saveConversation(conversationId, newConversation)
                    handleMessageComplete(conversationId)
                } else {
                    if (regenerateAssistantMsg) {
                        val node = conversation.getMessageNodeByMessage(message)
                        val nodeIndex = conversation.messageNodes.indexOf(node)
                        handleMessageComplete(conversationId, messageRange = 0..<nodeIndex)
                    } else {
                        saveConversation(conversationId, conversation)
                    }
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                addError(e, conversationId, title = context.getString(R.string.error_title_regenerate_message))
            }
        }

        session.setJob(job)
    }

    // ---- 处理工具调用审批 ----

    fun handleToolApproval(
        conversationId: Uuid,
        toolCallId: String,
        approved: Boolean,
        reason: String = "",
        answer: String? = null,
    ) {
        val session = getOrCreateSession(conversationId)
        session.getJob()?.cancel()

        val job = appScope.launch {
            try {
                val conversation = session.state.value
                val newApprovalState = when {
                    answer != null -> ToolApprovalState.Answered(answer)
                    approved -> ToolApprovalState.Approved
                    else -> ToolApprovalState.Denied(reason)
                }

                // Update the tool approval state
                val updatedNodes = conversation.messageNodes.map { node ->
                    node.copy(
                        messages = node.messages.map { msg ->
                            msg.copy(
                                parts = msg.parts.map { part ->
                                    when {
                                        part is UIMessagePart.Tool && part.toolCallId == toolCallId -> {
                                            part.copy(approvalState = newApprovalState)
                                        }

                                        else -> part
                                    }
                                }
                            )
                        }
                    )
                }
                val updatedConversation = conversation.copy(messageNodes = updatedNodes)
                saveConversation(conversationId, updatedConversation)

                // Check if there are still pending tools
                val hasPendingTools = updatedNodes.any { node ->
                    node.currentMessage.parts.any { part ->
                        part is UIMessagePart.Tool && part.isPending
                    }
                }

                // Only continue generation when all pending tools are handled
                if (!hasPendingTools) {
                    handleMessageComplete(conversationId)
                }

                _generationDoneFlow.emit(conversationId)
            } catch (e: Exception) {
                addError(e, conversationId, title = context.getString(R.string.error_title_tool_approval))
            }
        }

        session.setJob(job)
    }

    // ---- 处理消息补全 ----

    private suspend fun handleMessageComplete(
        conversationId: Uuid,
        messageRange: ClosedRange<Int>? = null
    ) {
        val settings = settingsStore.settingsFlow.first()
        val model = settings.getCurrentChatModel() ?: return

        val assistant = settings.getCurrentAssistant()
        val senderName = if (assistant.useAssistantAvatar) {
            assistant.name.ifEmpty { context.getString(R.string.assistant_page_default_assistant) }
        } else {
            model.displayName
        }

        runCatching {
            val conversation = getConversationFlow(conversationId).value

            // reset suggestions
            updateConversation(conversationId, conversation.copy(chatSuggestions = emptyList()))

            // memory tool
            if (!model.abilities.contains(ModelAbility.TOOL)) {
                if (settings.enableWebSearch || mcpManager.getAllAvailableTools().isNotEmpty()) {
                    addError(
                        IllegalStateException(context.getString(R.string.tools_warning)),
                        conversationId,
                        title = context.getString(R.string.error_title_tool_unavailable)
                    )
                }
            }

            // check invalid messages
            checkInvalidMessages(conversationId)

            // start generating
            generationHandler.generateText(
                settings = settings,
                model = model,
                messages = conversation.currentMessages.let {
                    if (messageRange != null) {
                        it.subList(messageRange.start, messageRange.endInclusive + 1)
                    } else {
                        it
                    }
                },
                assistant = settings.getCurrentAssistant(),
                memories = if (settings.getCurrentAssistant().useGlobalMemory) {
                    memoryRepository.getGlobalMemories()
                } else {
                    memoryRepository.getMemoriesOfAssistant(settings.assistantId.toString())
                },
                inputTransformers = buildList {
                    addAll(inputTransformers)
                    add(templateTransformer)
                },
                outputTransformers = outputTransformers,
                tools = buildList {
                    if (settings.enableWebSearch) {
                        addAll(createSearchTools(settings))
                    }
                    addAll(localTools.getTools(settings.getCurrentAssistant().localTools))
                    val assistant = settings.getCurrentAssistant()
                    if (assistant.enabledSkills.isNotEmpty()) {
                        addAll(
                            createSkillTools(
                                enabledSkills = assistant.enabledSkills,
                                allSkills = skillManager.listSkills(),
                                skillManager = skillManager,
                            )
                        )
                    }
                    mcpManager.getAllAvailableTools().forEach { tool ->
                        add(
                            Tool(
                                name = "mcp__" + tool.name,
                                description = tool.description ?: "",
                                parameters = { tool.inputSchema },
                                needsApproval = tool.needsApproval,
                                execute = {
                                    mcpManager.callTool(tool.name, it.jsonObject)
                                },
                            )
                        )
                    }
                },
                onFallback = { notice ->
                    addError(
                        error = IllegalStateException(formatModelSwitchNotice(notice)),
                        conversationId = conversationId,
                        title = buildFallbackTitle(notice)
                    )
                },
            ).onCompletion {
                // 取消 Live Update 通知
                cancelLiveUpdateNotification(conversationId)

                // 可能被取消了，或者意外结束，兜底更新
                val updatedConversation = getConversationFlow(conversationId).value.copy(
                    messageNodes = getConversationFlow(conversationId).value.messageNodes.map { node ->
                        node.copy(messages = node.messages.map { it.finishReasoning() })
                    },
                    updateAt = Instant.now()
                )
                saveConversation(conversationId, updatedConversation)

                // Show notification if app is not in foreground
                if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration) {
                    sendGenerationDoneNotification(conversationId, senderName)
                }
            }.collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        val updatedConversation = getConversationFlow(conversationId).value
                            .updateCurrentMessages(chunk.messages)
                            .copy(updateAt = Instant.now())
                        updateConversation(conversationId, updatedConversation)
                        persistConversationProgress(conversationId, updatedConversation)

                        // 如果应用不在前台，发送 Live Update 通知
                        if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration && settings.displaySetting.enableLiveUpdateNotification) {
                            sendLiveUpdateNotification(conversationId, chunk.messages, senderName)
                        }
                    }
                }
            }
        }.onFailure {
            // 取消 Live Update 通知
            cancelLiveUpdateNotification(conversationId)

            it.printStackTrace()
            val recoveredConversation = getConversationFlow(conversationId).value
                .finishInterruptedGeneration()
            saveConversation(conversationId, recoveredConversation)
            addError(it, conversationId, title = context.getString(R.string.error_title_generation))
            Logging.log(TAG, "handleMessageComplete: $it")
            Logging.log(TAG, it.stackTraceToString())
        }.onSuccess {
            val finalConversation = getConversationFlow(conversationId).value
            saveConversation(conversationId, finalConversation)

            launchWithConversationReference(conversationId) {
                if (finalConversation.assistantId == TRAVEL_PLANNER_ASSISTANT_ID) {
                    generateTravelBrief(conversationId, finalConversation)
                } else {
                    launchWithConversationReference(conversationId) {
                        generateTitle(conversationId, finalConversation)
                    }
                    launchWithConversationReference(conversationId) {
                        generateSuggestion(conversationId, finalConversation)
                    }
                }
            }
        }
    }

    // ---- 检查无效消息 ----

    private fun checkInvalidMessages(conversationId: Uuid) {
        val conversation = getConversationFlow(conversationId).value
        var messagesNodes = conversation.messageNodes

        // 移除无效 tool (未执行的 Tool)
        messagesNodes = messagesNodes.mapIndexed { index, node ->
            // Check for Tool type with non-executed tools
            val hasPendingTools = node.currentMessage.getTools().any { !it.isExecuted }

            if (hasPendingTools) {
                // Skip removal if any tool is Approved (waiting to be executed)
                val hasApprovedTool = node.currentMessage.getTools().any {
                    it.approvalState is ToolApprovalState.Approved
                }
                if (hasApprovedTool) {
                    return@mapIndexed node
                }

                // If all tools are executed, it's valid
                val allToolsExecuted = node.currentMessage.getTools().all { it.isExecuted }
                if (allToolsExecuted && node.currentMessage.getTools().isNotEmpty()) {
                    return@mapIndexed node
                }

                // Remove message with pending non-approved tools
                return@mapIndexed node.copy(
                    messages = node.messages.filter { it.id != node.currentMessage.id },
                    selectIndex = node.selectIndex - 1
                )
            }
            node
        }

        // 更新index
        messagesNodes = messagesNodes.map { node ->
            if (node.messages.isNotEmpty() && node.selectIndex !in node.messages.indices) {
                node.copy(selectIndex = 0)
            } else {
                node
            }
        }

        // 移除无效消息
        messagesNodes = messagesNodes.filter { it.messages.isNotEmpty() }

        updateConversation(conversationId, conversation.copy(messageNodes = messagesNodes))
    }

    // ---- 生成标题 ----

    suspend fun generateTitle(
        conversationId: Uuid,
        conversation: Conversation,
        force: Boolean = false
    ) {
        val shouldGenerate = when {
            force -> true
            conversation.title.isBlank() -> true
            else -> false
        }
        if (!shouldGenerate) return

        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.titleModelId) ?: return
            val generatedTitle = generateSingleTextWithFallback(
                conversationId = conversationId,
                settings = settings,
                model = model,
                messages = listOf(
                    UIMessage.user(
                        prompt = settings.titlePrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(4).joinToString("\n\n") { it.summaryAsText() })
                    ),
                ),
                title = "标题模型已切换"
            )

            // 生成完，conversation可能不是最新了，因此需要重新获取
            conversationRepo.getConversationById(conversation.id)?.let {
                saveConversation(
                    conversationId,
                    it.copy(title = generatedTitle.trim())
                )
            }
        }.onFailure {
            it.printStackTrace()
            addError(it, conversationId, title = context.getString(R.string.error_title_generate_title))
        }
    }

    // ---- 生成建议 ----

    suspend fun generateSuggestion(conversationId: Uuid, conversation: Conversation) {
        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.findModelById(settings.suggestionModelId) ?: return

            sessions[conversationId]?.let { session ->
                updateConversation(
                    conversationId,
                    session.state.value.copy(chatSuggestions = emptyList())
                )
            }

            val suggestionText = generateSingleTextWithFallback(
                conversationId = conversationId,
                settings = settings,
                model = model,
                messages = listOf(
                    UIMessage.user(
                        settings.suggestionPrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(8).joinToString("\n\n") { it.summaryAsText() }),
                    )
                ),
                title = "建议模型已切换"
            )
            val suggestions =
                suggestionText.split("\n").map { it.trim() }
                    ?.filter { it.isNotBlank() } ?: emptyList()

            val latestConversation = conversationRepo.getConversationById(conversationId)
                ?: sessions[conversationId]?.state?.value
                ?: conversation
            saveConversation(
                conversationId,
                latestConversation.copy(
                    chatSuggestions = suggestions.take(
                        10
                    )
                )
            )
        }.onFailure {
            it.printStackTrace()
        }
    }

    // ---- 压缩对话历史 ----

    suspend fun generateTravelBrief(
        conversationId: Uuid,
        conversation: Conversation,
        autoGeneratePlan: Boolean = true,
    ) {
        val extractingConversation = getConversationFlow(conversationId).value.let { current ->
            current.copy(travelPlanningState = TravelPlanningState.ExtractingBrief)
        }
<<<<<<< HEAD
        updateConversation(conversationId, extractingConversation)
        persistConversationProgress(conversationId, extractingConversation, immediate = true)
=======
>>>>>>> 42ac734b7fb804f86cc77117caef4a05fcde92db
        val content = buildTravelConversationContent(conversation.currentMessages)
        if (content.isBlank()) {
            val latestConversation = conversationRepo.getConversationById(conversationId)
                ?: getConversationFlow(conversationId).value
            saveConversation(
                conversationId,
                latestConversation.copy(
                    travelPlan = (latestConversation.travelPlan
                        ?: TravelPlan(conversationId = conversationId.toString())).copy(
                        status = TravelPlanStatus.draft_brief,
                    ),
                    travelPlanningState = TravelPlanningState.DraftBrief,
                )
            )
            addError(
                IllegalStateException("Please describe your destination, dates, budget, and preferences first."),
                conversationId,
                title = "Travel brief generation skipped"
            )
            return
        }
        runCatching {
            val draftBrief = decodeJsonPayload<TravelPlanningBrief>(
                runTravelPlannerTextAgent(
                    DEFAULT_TRAVEL_BRIEF_PROMPT.applyPlaceholders(
                        "content" to content,
                    )
                )
            )
            val brief = refineTravelBriefWithAgent(content, draftBrief)
            val latestConversation = conversationRepo.getConversationById(conversationId)
                ?: getConversationFlow(conversationId).value
            val nextPlan = (latestConversation.travelPlan ?: TravelPlan(conversationId = conversationId.toString()))
                .withBrief(
                    newBrief = brief,
                    status = if (brief.canGenerate()) {
                        TravelPlanStatus.ready_to_generate
                    } else {
                        TravelPlanStatus.draft_brief
                    }
                )
            saveConversation(
                conversationId,
                latestConversation.copy(
                    travelPlan = nextPlan,
                    travelPlanningState = if (brief.canGenerate()) {
                        TravelPlanningState.ReadyToGenerate
                    } else {
                        TravelPlanningState.DraftBrief
                    }
                )
            )
            if (autoGeneratePlan && brief.canGenerate()) {
                generateTravelPlan(
                    conversationId = conversationId,
                    conversation = latestConversation.copy(
                        travelPlan = nextPlan,
                        travelPlanningState = TravelPlanningState.ReadyToGenerate,
                    ),
                    brief = brief,
                    refreshBrief = false,
                )
            }
        }.onFailure {
            it.printStackTrace()
            val fallbackBrief = buildFallbackTravelBrief(content)
            if (fallbackBrief != null) {
                val latestConversation = conversationRepo.getConversationById(conversationId)
                    ?: getConversationFlow(conversationId).value
                val nextPlan = (latestConversation.travelPlan ?: TravelPlan(conversationId = conversationId.toString()))
                    .withBrief(
                        newBrief = fallbackBrief,
                        status = if (fallbackBrief.canGenerate()) {
                            TravelPlanStatus.ready_to_generate
                        } else {
                            TravelPlanStatus.draft_brief
                        }
                    )
                saveConversation(
                    conversationId,
                    latestConversation.copy(
                        travelPlan = nextPlan,
                        travelPlanningState = if (fallbackBrief.canGenerate()) {
                            TravelPlanningState.ReadyToGenerate
                        } else {
                            TravelPlanningState.DraftBrief
                        }
                    )
                )
                if (autoGeneratePlan && fallbackBrief.canGenerate()) {
                    generateTravelPlan(
                        conversationId = conversationId,
                        conversation = latestConversation.copy(
                            travelPlan = nextPlan,
                            travelPlanningState = TravelPlanningState.ReadyToGenerate,
                        ),
                        brief = fallbackBrief,
                        refreshBrief = false,
                    )
                }
                return
            }
            addError(it, conversationId, title = "Travel brief generation failed")
            val latestConversation = conversationRepo.getConversationById(conversationId)
                ?: getConversationFlow(conversationId).value
            saveConversation(
                conversationId,
                latestConversation.copy(travelPlanningState = TravelPlanningState.Failed)
            )
        }
    }

    suspend fun generateTravelPlan(
        conversationId: Uuid,
        conversation: Conversation,
        brief: TravelPlanningBrief? = conversation.travelPlan?.brief,
        refreshBrief: Boolean = true,
    ) {
        val ensuredBrief = if (refreshBrief) {
            generateTravelBrief(conversationId, conversation, autoGeneratePlan = false)
            (conversationRepo.getConversationById(conversationId)
                ?: getConversationFlow(conversationId).value).travelPlan?.brief
        } else brief ?: run {
            generateTravelBrief(conversationId, conversation, autoGeneratePlan = false)
            (conversationRepo.getConversationById(conversationId)
                ?: getConversationFlow(conversationId).value).travelPlan?.brief
        }
        if (ensuredBrief == null || !ensuredBrief.canGenerate()) {
            val latestConversation = conversationRepo.getConversationById(conversationId)
                ?: getConversationFlow(conversationId).value
            saveConversation(
                conversationId,
                latestConversation.copy(
                    travelPlan = (latestConversation.travelPlan
                        ?: TravelPlan(conversationId = conversationId.toString())).copy(
                        brief = ensuredBrief,
                        status = TravelPlanStatus.draft_brief,
                    ),
                    travelPlanningState = TravelPlanningState.DraftBrief,
                )
            )
            addError(
                IllegalStateException("Travel brief is incomplete. Please provide destination, dates, and preferences first."),
                conversationId,
                title = "Travel plan generation skipped"
            )
            return
        }

        val generatingConversation = getConversationFlow(conversationId).value.let { current ->
            current.copy(
                travelPlanningState = TravelPlanningState.GeneratingPlan,
                travelPlan = (current.travelPlan ?: TravelPlan(conversationId = conversationId.toString())).copy(
                    brief = ensuredBrief,
                    status = TravelPlanStatus.ready_to_generate
                )
            )
        }
        updateConversation(conversationId, generatingConversation)
        persistConversationProgress(conversationId, generatingConversation, immediate = true)

        val planningFacts = travelPlanningDataRepository.buildPlanningFacts(
            destination = ensuredBrief.destination,
            days = ensuredBrief.days,
            origin = ensuredBrief.origin,
            transportPreferences = ensuredBrief.transportPreferences,
        )

        runCatching {
            val content = buildTravelConversationContent(conversation.currentMessages)
            val latestConversation = conversationRepo.getConversationById(conversationId)
                ?: getConversationFlow(conversationId).value
            val payload = repairTravelGeneratedPayloadWithAgent(
                ensuredBrief = ensuredBrief,
                planningFacts = planningFacts,
                content = content,
                currentPlan = latestConversation.travelPlan ?: TravelPlan(conversationId = conversationId.toString()),
            )
            val newPlan = enrichGeneratedTravelPlan(
                currentPlan = latestConversation.travelPlan ?: TravelPlan(conversationId = conversationId.toString()),
                ensuredBrief = ensuredBrief,
                payload = payload,
                planningFacts = planningFacts,
            ).copy(
                generatedAt = System.currentTimeMillis(),
                generationVersion = (latestConversation.travelPlan?.generationVersion ?: 0) + 1,
                status = TravelPlanStatus.generated,
            )
            saveConversation(
                conversationId,
                latestConversation.copy(
                    travelPlan = newPlan,
                    travelPlanningState = TravelPlanningState.Generated,
                )
            )
        }.onFailure {
            it.printStackTrace()
            addError(it, conversationId, title = "Travel plan generation failed")
            val latestConversation = conversationRepo.getConversationById(conversationId)
                ?: getConversationFlow(conversationId).value
            val fallbackPlan = enrichGeneratedTravelPlan(
                currentPlan = latestConversation.travelPlan ?: TravelPlan(conversationId = conversationId.toString()),
                ensuredBrief = ensuredBrief,
                payload = TravelGeneratedPayload(),
                planningFacts = planningFacts,
            ).copy(
                generatedAt = System.currentTimeMillis(),
                generationVersion = (latestConversation.travelPlan?.generationVersion ?: 0) + 1,
                status = TravelPlanStatus.generated,
            )
            saveConversation(
                conversationId,
                latestConversation.copy(
                    travelPlan = fallbackPlan,
                    travelPlanningState = TravelPlanningState.Generated,
                )
            )
        }
    }

    suspend fun retryTravelPlanGeneration(conversationId: Uuid) {
        val conversation = conversationRepo.getConversationById(conversationId)
            ?: getConversationFlow(conversationId).value
        generateTravelPlan(conversationId, conversation, conversation.travelPlan?.brief)
    }

    private inline fun <reified T> decodeJsonPayload(text: String): T {
        val payload = text
            .trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .removePrefix("json")
            .trim()
        val normalizedPayload = extractJsonObject(payload) ?: payload
        return JsonInstant.decodeFromString(normalizedPayload)
    }

    private suspend fun runTravelPlannerTextAgent(prompt: String): String {
        val settings = settingsStore.settingsFlow.first()
        val model = settings.getCurrentChatModel()
            ?: throw IllegalStateException("No chat model available")
<<<<<<< HEAD
        return generateSingleTextWithFallback(
            conversationId = null,
            settings = settings,
            model = model,
            messages = listOf(UIMessage.user(prompt)),
            title = "规划模型已切换"
        )
=======
        val provider = model.findProvider(settings.providers)
            ?: throw IllegalStateException("Provider not found")
        val providerHandler = providerManager.getProviderByType(provider)
        val result = providerHandler.generateText(
            providerSetting = provider,
            messages = listOf(UIMessage.user(prompt)),
            params = TextGenerationParams(
                model = model,
                thinkingBudget = 0,
            ),
        )
        return result.choices[0].message?.toText().orEmpty()
>>>>>>> 42ac734b7fb804f86cc77117caef4a05fcde92db
    }

    private fun buildTravelConversationContent(messages: List<UIMessage>): String {
        val preferredMessages = messages
            .takeLast(16)
            .filter { it.role == MessageRole.USER }
            .ifEmpty { messages.takeLast(12) }
        return preferredMessages.joinToString("\n\n") { message ->
            when (message.role) {
                MessageRole.USER -> "[USER] ${sanitizeTravelUiText(message.toText())}"
                MessageRole.ASSISTANT -> "[ASSISTANT] ${sanitizeTravelUiText(message.toText())}"
                else -> sanitizeTravelUiText(message.toText())
            }
        }
    }

    private suspend fun refineTravelBriefWithAgent(
        content: String,
        draftBrief: TravelPlanningBrief,
    ): TravelPlanningBrief {
        val normalizedDraft = normalizeTravelBrief(draftBrief, content)
        val refinedByModel = runCatching {
            decodeJsonPayload<TravelPlanningBrief>(
                runTravelPlannerTextAgent(
                    buildTravelBriefRefinerPrompt(
                        brief = JsonInstant.encodeToString(normalizedDraft),
                        content = content,
                    )
                )
            )
        }.getOrNull()
        return normalizeTravelBrief(refinedByModel ?: normalizedDraft, content)
    }

    private suspend fun repairTravelGeneratedPayloadWithAgent(
        ensuredBrief: TravelPlanningBrief,
        planningFacts: TravelPlanningFacts,
        content: String,
        currentPlan: TravelPlan,
    ): TravelGeneratedPayload {
        val draftPayload = decodeJsonPayload<TravelGeneratedPayload>(
            runTravelPlannerTextAgent(
                buildTravelItineraryPrompt(
                    brief = JsonInstant.encodeToString(ensuredBrief),
                    currentPlan = JsonInstant.encodeToString(currentPlan),
                    facts = JsonInstant.encodeToString(planningFacts),
                    content = content,
                )
            )
        )
        val normalizedDraft = normalizeTravelGeneratedPayload(
            payload = draftPayload,
            brief = ensuredBrief,
            planningFacts = planningFacts,
        )
        val repairedByModel = runCatching {
            decodeJsonPayload<TravelGeneratedPayload>(
                runTravelPlannerTextAgent(
                    buildTravelItineraryAuditorPrompt(
                        brief = JsonInstant.encodeToString(ensuredBrief),
                        facts = JsonInstant.encodeToString(planningFacts),
                        content = content,
                        payload = JsonInstant.encodeToString(normalizedDraft),
                    )
                )
            )
        }.getOrNull()
        return normalizeTravelGeneratedPayload(
            payload = repairedByModel ?: normalizedDraft,
            brief = ensuredBrief,
            planningFacts = planningFacts,
        )
    }

    private fun normalizeTravelBrief(
        brief: TravelPlanningBrief,
        content: String,
    ): TravelPlanningBrief {
        val explicitDays = extractDaysGuess(content)
        val explicitTravelers = extractTravelerCountGuess(content)
        val destination = brief.destination.ifBlank { extractDestinationGuess(content).orEmpty() }
        val budgetText = brief.budgetText.ifBlank { extractBudgetTextGuess(content) }
        val styleTags = (brief.travelStyleTags + extractTravelStyleTags(content)).distinct()
        val transportPreferences = (brief.transportPreferences + extractTransportPreferences(content)).distinct()
        val cleanedSummary = sanitizeTravelUiText(brief.userIntentSummary)
        val fallbackSummary = buildString {
            if (destination.isNotBlank()) append(destination)
            explicitDays?.let {
                if (isNotBlank()) append("，")
                append("${it}天行程")
            }
            explicitTravelers?.let {
                if (isNotBlank()) append("，")
                append("${it}人出行")
            }
            budgetText.takeIf { it.isNotBlank() }?.let {
                if (isNotBlank()) append("，")
                append(it)
            }
        }.ifBlank { sanitizeTravelUiText(content).take(120) }

        return brief.copy(
            destination = destination,
            days = (explicitDays ?: brief.days)?.coerceIn(1, 14),
            travelerCount = (explicitTravelers ?: brief.travelerCount)?.coerceIn(1, 20),
            budgetText = sanitizeTravelUiText(budgetText),
            budgetLevel = sanitizeTravelUiText(brief.budgetLevel),
            travelStyleTags = styleTags.map(::sanitizeTravelUiText).filter { it.isNotBlank() }.distinct(),
            transportPreferences = transportPreferences.map(::sanitizeTravelUiText).filter { it.isNotBlank() }.distinct(),
            hardConstraints = brief.hardConstraints.map(::sanitizeTravelUiText).filter { it.isNotBlank() }.distinct(),
            userIntentSummary = cleanedSummary.ifBlank { fallbackSummary },
        )
    }

    private fun normalizeTravelGeneratedPayload(
        payload: TravelGeneratedPayload,
        brief: TravelPlanningBrief,
        planningFacts: TravelPlanningFacts,
    ): TravelGeneratedPayload {
        val targetDays = (brief.days ?: payload.itineraryDays.size.takeIf { it > 0 } ?: planningFacts.dailyWeather.size)
            .coerceIn(1, 14)
        val normalizedDays = payload.itineraryDays
            .sortedBy { it.dayIndex }
            .take(targetDays)
            .mapIndexed { index, day ->
                day.copy(
                    dayIndex = index + 1,
                    title = sanitizeTravelUiText(day.title).ifBlank { "Day ${index + 1}" },
                    dateText = sanitizeTravelUiText(day.dateText).ifBlank {
                        planningFacts.dailyWeather.getOrNull(index)?.date.orEmpty()
                    },
                    weatherHint = sanitizeTravelUiText(day.weatherHint).ifBlank {
                        planningFacts.dailyWeather.getOrNull(index)?.summary.orEmpty()
                    },
                    items = day.items.map { item ->
                        item.copy(
                            title = sanitizeTravelUiText(item.title),
                            description = sanitizeTravelUiText(item.description),
                            estimatedCost = sanitizeTravelUiText(item.estimatedCost),
                            transportHint = sanitizeTravelUiText(item.transportHint),
                        )
                    }.filter { it.title.isNotBlank() }
                )
            }
            .toMutableList()

        while (normalizedDays.size < targetDays) {
            val index = normalizedDays.size
            normalizedDays += TravelItineraryDay(
                dayIndex = index + 1,
                title = "Day ${index + 1}",
                dateText = planningFacts.dailyWeather.getOrNull(index)?.date.orEmpty(),
                weatherHint = planningFacts.dailyWeather.getOrNull(index)?.summary.orEmpty(),
                items = emptyList(),
            )
        }

        return payload.copy(
            hotels = payload.hotels.map { recommendation ->
                recommendation.copy(
                    title = sanitizeTravelUiText(recommendation.title),
                    subtitle = sanitizeTravelUiText(recommendation.subtitle),
                    tags = recommendation.tags.map(::sanitizeTravelUiText).filter { it.isNotBlank() },
                    reason = sanitizeTravelUiText(recommendation.reason),
                    priceHint = sanitizeTravelUiText(recommendation.priceHint),
                    ratingText = sanitizeTravelUiText(recommendation.ratingText),
                    area = sanitizeTravelUiText(recommendation.area),
                    inventoryHint = sanitizeTravelUiText(recommendation.inventoryHint),
                    bookingUrl = recommendation.bookingUrl.trim(),
                    source = sanitizeTravelUiText(recommendation.source),
                )
            },
            foods = payload.foods.map { recommendation ->
                recommendation.copy(
                    title = sanitizeTravelUiText(recommendation.title),
                    subtitle = sanitizeTravelUiText(recommendation.subtitle),
                    tags = recommendation.tags.map(::sanitizeTravelUiText).filter { it.isNotBlank() },
                    reason = sanitizeTravelUiText(recommendation.reason),
                    priceHint = sanitizeTravelUiText(recommendation.priceHint),
                    ratingText = sanitizeTravelUiText(recommendation.ratingText),
                    area = sanitizeTravelUiText(recommendation.area),
                    inventoryHint = sanitizeTravelUiText(recommendation.inventoryHint),
                    bookingUrl = recommendation.bookingUrl.trim(),
                    source = sanitizeTravelUiText(recommendation.source),
                )
            },
            activities = payload.activities.map { recommendation ->
                recommendation.copy(
                    title = sanitizeTravelUiText(recommendation.title),
                    subtitle = sanitizeTravelUiText(recommendation.subtitle),
                    tags = recommendation.tags.map(::sanitizeTravelUiText).filter { it.isNotBlank() },
                    reason = sanitizeTravelUiText(recommendation.reason),
                    priceHint = sanitizeTravelUiText(recommendation.priceHint),
                    ratingText = sanitizeTravelUiText(recommendation.ratingText),
                    area = sanitizeTravelUiText(recommendation.area),
                    inventoryHint = sanitizeTravelUiText(recommendation.inventoryHint),
                    bookingUrl = recommendation.bookingUrl.trim(),
                    source = sanitizeTravelUiText(recommendation.source),
                )
            },
            pois = payload.pois.map { poi ->
                poi.copy(
                    name = sanitizeTravelUiText(poi.name),
                    category = sanitizeTravelUiText(poi.category),
                    address = sanitizeTravelUiText(poi.address),
                )
            },
            itineraryDays = normalizedDays,
        )
    }

    private fun sanitizeTravelUiText(text: String): String {
        return text
            .replace(Regex("""(?i)\[(user|assistant|system)\]\s*:?\s*"""), "")
            .replace(Regex("""(?im)^(user|assistant|system)\s*:?\s*"""), "")
            .replace(Regex("""(?is)<think>.*?</think>"""), " ")
            .replace(Regex("""(?im)^thinking\s*:?\s*"""), "")
            .replace("```json", "")
            .replace("```JSON", "")
            .replace("```", "")
            .stripMarkdown()
            .replace(Regex("""\\([`*_#>\-\[\]\(\)])"""), "$1")
            .replace(Regex("""[*#>`~_]+"""), " ")
            .replace(Regex("""<[^>]+>"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private suspend fun buildFallbackTravelBrief(content: String): TravelPlanningBrief? {
        val normalized = content.trim()
        if (normalized.isBlank()) return null

        val destinationGuess = extractDestinationGuess(normalized)
            ?: travelPlanningDataRepository.searchDestinationSuggestions(normalized).firstOrNull()?.name.orEmpty()
        val daysGuess = extractDaysGuess(normalized)
        val travelerCountGuess = extractTravelerCountGuess(normalized)
        val budgetTextGuess = extractBudgetTextGuess(normalized)
        val styleTags = extractTravelStyleTags(normalized)
        val transportPreferences = extractTransportPreferences(normalized)

        val brief = TravelPlanningBrief(
            destination = destinationGuess,
            days = daysGuess,
            travelerCount = travelerCountGuess,
            budgetText = budgetTextGuess,
            budgetLevel = extractBudgetLevel(normalized, budgetTextGuess),
            travelStyleTags = styleTags,
            transportPreferences = transportPreferences,
            userIntentSummary = normalized.take(160),
        )
        return brief.takeIf { it.canGenerate() }
    }

    private fun extractDestinationGuess(text: String): String? {
        val patterns = listOf(
            Regex("(?:去|到|前往|奔赴|飞去|来一场|安排到)\\s*([\\u4E00-\\u9FFFA-Za-z0-9]{2,20})"),
            Regex("([\\u4E00-\\u9FFFA-Za-z0-9]{2,20})(?:\\s*(?:两日|2日|三日|3日|四日|4日|五日|5日|六日|6日|七日|7日|八日|8日|九日|9日|十日|10日)?\\s*游)"),
        )
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val candidate = match.groupValues.getOrNull(1).orEmpty().trim().trimEnd('游', '玩', '行', '程')
            if (candidate.isNotBlank()) return candidate
        }
        return null
    }

    private fun extractDaysGuess(text: String): Int? {
        val digitMatch = Regex("(\\d{1,2})\\s*(?:天|日|晚)").find(text)
        if (digitMatch != null) {
            return digitMatch.groupValues.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 14)
        }
        val chineseMatch = Regex("([一二三四五六七八九十两]+)\\s*(?:天|日|晚|日游)").find(text)
        if (chineseMatch != null) {
            return chineseNumberToInt(chineseMatch.groupValues.getOrNull(1).orEmpty())?.coerceIn(1, 14)
        }
        return null
    }

    private fun extractTravelerCountGuess(text: String): Int? {
        val digitMatch = Regex("(\\d{1,2})\\s*人").find(text)
        if (digitMatch != null) {
            return digitMatch.groupValues.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 20)
        }
        val chineseMatch = Regex("([一二三四五六七八九十两]+)\\s*人").find(text)
        if (chineseMatch != null) {
            return chineseNumberToInt(chineseMatch.groupValues.getOrNull(1).orEmpty())?.coerceIn(1, 20)
        }
        return null
    }

    private fun extractBudgetTextGuess(text: String): String {
        val digitMatch = Regex("预算\\s*([\\d,.]+)\\s*(?:元|块|rmb|RMB)?").find(text)
        if (digitMatch != null) {
            return "预算 ${digitMatch.groupValues.getOrNull(1).orEmpty()}"
        }
        val budgetKeywords = listOf("高预算", "中等预算", "低预算", "省钱", "经济型", "奢华")
        val keyword = budgetKeywords.firstOrNull { text.contains(it) }
        return keyword?.let { "预算偏好：$it" }.orEmpty()
    }

    private fun extractBudgetLevel(text: String, budgetText: String): String {
        val normalized = (budgetText + " " + text)
        return when {
            normalized.contains("高预算") || normalized.contains("奢华") -> "high"
            normalized.contains("低预算") || normalized.contains("省钱") || normalized.contains("经济") -> "low"
            normalized.contains("中等") || normalized.contains("均衡") -> "mid"
            else -> "mid"
        }
    }

    private fun extractTravelStyleTags(text: String): List<String> {
        val keywords = linkedMapOf(
            "美食" to "美食",
            "吃" to "美食",
            "夜景" to "夜景",
            "拍照" to "拍照",
            "亲子" to "亲子",
            "购物" to "购物",
            "自然" to "自然",
            "海边" to "海边",
            "博物馆" to "文化",
            "文化" to "文化",
            "温泉" to "温泉",
            "休闲" to "休闲",
            "徒步" to "徒步",
            "轻松" to "轻松",
            "商务" to "商务",
        )
        return keywords.filter { (key, _) -> text.contains(key) }.map { it.value }.distinct()
    }

    private fun extractTransportPreferences(text: String): List<String> {
        val keywords = linkedMapOf(
            "高铁" to "高铁",
            "火车" to "火车",
            "动车" to "动车",
            "飞机" to "飞机",
            "自驾" to "自驾",
            "打车" to "打车",
            "地铁" to "地铁",
            "公交" to "公交",
            "步行" to "步行",
        )
        return keywords.filter { (key, _) -> text.contains(key) }.map { it.value }.distinct()
    }

    private fun chineseNumberToInt(value: String): Int? {
        val normalized = value.trim()
        if (normalized.isBlank()) return null
        if (normalized == "十") return 10
        if (normalized == "两") return 2
        if (normalized.length == 1) {
            return when (normalized) {
                "一" -> 1
                "二" -> 2
                "三" -> 3
                "四" -> 4
                "五" -> 5
                "六" -> 6
                "七" -> 7
                "八" -> 8
                "九" -> 9
                else -> null
            }
        }
        return normalized
            .replace("两", "二")
            .takeIf { it.length == 2 && it.startsWith("十") }
            ?.let { 10 + chineseNumberToInt(it.substring(1)).orZero() }
            ?: normalized.takeIf { it.length == 2 && it.endsWith("十") }
                ?.let { chineseNumberToInt(it.first().toString()).orZero() * 10 }
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val ch = text[index]
            when {
                escaped -> escaped = false
                ch == '\\' && inString -> escaped = true
                ch == '"' -> inString = !inString
                !inString && ch == '{' -> depth++
                !inString && ch == '}' -> {
                    depth--
                    if (depth == 0) {
                        return text.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    private suspend fun enrichGeneratedTravelPlan(
        currentPlan: TravelPlan,
        ensuredBrief: TravelPlanningBrief,
        payload: TravelGeneratedPayload,
        planningFacts: TravelPlanningFacts,
    ): TravelPlan {
        val poiFallbackSource = payload.pois + currentPlan.pois
        val hotels = payload.hotels.ifEmpty {
            planningFacts.commercialHotels
                .ifEmpty { planningFacts.nearbyHotels }
                .ifEmpty { currentPlan.hotels }
                .ifEmpty {
                    fallbackRecommendationsFromPois(
                        pois = poiFallbackSource,
                        category = TravelRecommendationCategory.hotel,
                        destination = ensuredBrief.destination,
                    )
                }
                .take(6)
        }
        val foods = payload.foods.ifEmpty {
            planningFacts.nearbyFoods
                .ifEmpty { currentPlan.foods }
                .ifEmpty {
                    fallbackRecommendationsFromPois(
                        pois = poiFallbackSource,
                        category = TravelRecommendationCategory.food,
                        destination = ensuredBrief.destination,
                    )
                }
                .take(6)
        }
        val activities = payload.activities.ifEmpty {
            planningFacts.commercialActivities
                .ifEmpty { planningFacts.nearbyActivities }
                .ifEmpty { currentPlan.activities }
                .ifEmpty {
                    fallbackRecommendationsFromPois(
                        pois = poiFallbackSource,
                        category = TravelRecommendationCategory.activity,
                        destination = ensuredBrief.destination,
                    )
                }
                .take(8)
        }

        val mergedPois = mergePois(
            payloadPois = payload.pois,
            hotels = hotels,
            foods = foods,
            activities = activities,
            planningFacts = planningFacts,
            existingPois = currentPlan.pois,
        )
        val baseItineraryDays = payload.itineraryDays.ifEmpty {
            buildFallbackItineraryDays(
                ensuredBrief = ensuredBrief,
                planningFacts = planningFacts,
                hotels = hotels,
                foods = foods,
                activities = activities,
            )
        }
        val hydratedItineraryDays = baseItineraryDays.mapIndexed { index, day ->
            val seedItems = if (day.items.isEmpty()) {
                buildFallbackDayItems(index, hotels, foods, activities)
            } else {
                day.items
            }
            ensureConcreteItemsForDay(
                day = day.copy(
                    items = seedItems.map { item ->
                        item.copy(
                            poiRefId = matchPoiRefId(
                                item = item,
                                mergedPois = mergedPois,
                                hotels = hotels,
                                foods = foods,
                                activities = activities,
                            ) ?: item.poiRefId
                        )
                    }
                ),
                dayIndex = index,
                hotels = hotels,
                foods = foods,
                activities = activities,
            )
        }
        val routeHints = travelPlanningDataRepository.buildRouteHints(
            poiSequence = orderedPoisForRoutes(hydratedItineraryDays, mergedPois),
            city = ensuredBrief.destination,
        )
        val routeHintMap = routeHints.associateBy { "${it.fromPoiId}->${it.toPoiId}" }
        val weatherByDay = planningFacts.dailyWeather.associateBy { it.date }
        val firstTransportHint = planningFacts.intercityTransportHints.firstOrNull().orEmpty()

        val itineraryDays = hydratedItineraryDays.mapIndexed { index, day ->
            val fallbackWeather = planningFacts.dailyWeather.getOrNull(index)?.summary
                ?: weatherByDay[day.dateText]?.summary
                ?: if (planningFacts.dailyWeather.isEmpty()) "天气数据暂不可用" else ""
            val items = day.items.mapIndexed { itemIndex, item ->
                if (item.transportHint.isNotBlank()) {
                    item
                } else {
                    val previousPoiId = day.items.subList(0, itemIndex).lastOrNull { it.poiRefId != null }?.poiRefId
                    val currentPoiId = item.poiRefId
                    val routeHint = if (previousPoiId != null && currentPoiId != null) {
                        routeHintMap["$previousPoiId->$currentPoiId"]?.summary
                    } else null
                    item.copy(
                        transportHint = routeHint.orEmpty(),
                    )
                }
            }
            day.copy(
                weatherHint = day.weatherHint.ifBlank { fallbackWeather.orEmpty() },
                items = items.mapIndexed { itemIndex, item ->
                    if (itemIndex == 0 && item.transportHint.isBlank() && firstTransportHint.isNotBlank()) {
                        item.copy(transportHint = firstTransportHint)
                    } else {
                        item
                    }
                },
            )
        }

        return currentPlan.copy(
            brief = ensuredBrief,
            hotels = hotels,
            foods = foods,
            activities = activities,
            pois = mergedPois,
            itineraryDays = itineraryDays,
        )
    }

    private fun mergePois(
        payloadPois: List<TravelPoi>,
        hotels: List<me.rerere.rikkahub.data.model.TravelRecommendationItem>,
        foods: List<me.rerere.rikkahub.data.model.TravelRecommendationItem>,
        activities: List<me.rerere.rikkahub.data.model.TravelRecommendationItem>,
        planningFacts: TravelPlanningFacts,
        existingPois: List<TravelPoi>,
    ): List<TravelPoi> {
        val factPois = buildList {
            addAll(existingPois)
            addAll(planningFacts.candidatePois)
            addAll(hotels.map { recommendation ->
                TravelPoi(
                    id = recommendation.id,
                    name = recommendation.title,
                    category = recommendation.category.name,
                    lat = recommendation.lat,
                    lon = recommendation.lon,
                    address = recommendation.subtitle,
                    linkedRecommendationId = recommendation.id,
                )
            })
            addAll(foods.map { recommendation ->
                TravelPoi(
                    id = recommendation.id,
                    name = recommendation.title,
                    category = recommendation.category.name,
                    lat = recommendation.lat,
                    lon = recommendation.lon,
                    address = recommendation.subtitle,
                    linkedRecommendationId = recommendation.id,
                )
            })
            addAll(activities.map { recommendation ->
                TravelPoi(
                    id = recommendation.id,
                    name = recommendation.title,
                    category = recommendation.category.name,
                    lat = recommendation.lat,
                    lon = recommendation.lon,
                    address = recommendation.subtitle,
                    linkedRecommendationId = recommendation.id,
                )
            })
        }
        return (payloadPois + factPois)
            .groupBy { it.id }
            .values
            .map { duplicates ->
                duplicates.reduce { acc, item ->
                    acc.copy(
                        name = acc.name.ifBlank { item.name },
                        category = acc.category.ifBlank { item.category },
                        lat = acc.lat ?: item.lat,
                        lon = acc.lon ?: item.lon,
                        address = acc.address.ifBlank { item.address },
                        linkedRecommendationId = acc.linkedRecommendationId ?: item.linkedRecommendationId,
                        linkedItineraryItemId = acc.linkedItineraryItemId ?: item.linkedItineraryItemId,
                    )
                }
            }
    }

    private fun buildFallbackItineraryDays(
        ensuredBrief: TravelPlanningBrief,
        planningFacts: TravelPlanningFacts,
        hotels: List<TravelRecommendationItem>,
        foods: List<TravelRecommendationItem>,
        activities: List<TravelRecommendationItem>,
    ): List<TravelItineraryDay> {
        val requestedDays = ensuredBrief.days ?: planningFacts.dailyWeather.size
        val dayCount = requestedDays.takeIf { it > 0 } ?: 1
        return (0 until dayCount).map { index ->
            TravelItineraryDay(
                dayIndex = index + 1,
                title = "Day ${index + 1}",
                dateText = planningFacts.dailyWeather.getOrNull(index)?.date.orEmpty(),
                weatherHint = planningFacts.dailyWeather.getOrNull(index)?.summary.orEmpty(),
                items = buildFallbackDayItems(index, hotels, foods, activities),
            )
        }
    }

    private fun buildFallbackDayItems(
        dayIndex: Int,
        hotels: List<TravelRecommendationItem>,
        foods: List<TravelRecommendationItem>,
        activities: List<TravelRecommendationItem>,
    ): List<TravelItineraryItem> {
        val activityPrimary = activities.pickForDay(dayIndex)
        val activitySecondary = activities.pickForDay(dayIndex + 1)?.takeIf { it.id != activityPrimary?.id }
        val food = foods.pickForDay(dayIndex)
        val hotel = hotels.firstOrNull()
        return buildList {
            activityPrimary?.let { recommendation ->
                add(
                    recommendation.toItineraryItem(
                        id = "fallback-day${dayIndex + 1}-activity-1",
                        timeSlot = "09:00-11:30",
                        category = TravelItemCategory.activity,
                    )
                )
            }
            food?.let { recommendation ->
                add(
                    recommendation.toItineraryItem(
                        id = "fallback-day${dayIndex + 1}-food-1",
                        timeSlot = "12:00-13:30",
                        category = TravelItemCategory.food,
                    )
                )
            }
            activitySecondary?.let { recommendation ->
                add(
                    recommendation.toItineraryItem(
                        id = "fallback-day${dayIndex + 1}-activity-2",
                        timeSlot = "14:00-17:00",
                        category = TravelItemCategory.activity,
                    )
                )
            }
            if (dayIndex == 0) {
                hotel?.let { recommendation ->
                    add(
                        recommendation.toItineraryItem(
                            id = "fallback-day${dayIndex + 1}-hotel",
                            timeSlot = "19:00-20:00",
                            category = TravelItemCategory.hotel,
                        )
                    )
                }
            }
        }
    }

    private fun ensureConcreteItemsForDay(
        day: TravelItineraryDay,
        dayIndex: Int,
        hotels: List<TravelRecommendationItem>,
        foods: List<TravelRecommendationItem>,
        activities: List<TravelRecommendationItem>,
    ): TravelItineraryDay {
        val items = day.items.toMutableList()
        val hasConcreteActivity = items.any {
            it.poiRefId != null && (it.category == TravelItemCategory.activity || it.category == TravelItemCategory.sightseeing)
        }
        val hasConcreteFood = items.any { it.poiRefId != null && it.category == TravelItemCategory.food }
        val hasConcreteHotel = items.any { it.poiRefId != null && it.category == TravelItemCategory.hotel }

        if (!hasConcreteActivity) {
            activities.pickForDay(dayIndex)?.let { recommendation ->
                items += recommendation.toItineraryItem(
                    id = "supplement-day${dayIndex + 1}-activity",
                    timeSlot = if (items.isEmpty()) "09:00-11:30" else "14:00-17:00",
                    category = TravelItemCategory.activity,
                )
            }
        }
        if (!hasConcreteFood) {
            foods.pickForDay(dayIndex)?.let { recommendation ->
                items += recommendation.toItineraryItem(
                    id = "supplement-day${dayIndex + 1}-food",
                    timeSlot = "12:00-13:30",
                    category = TravelItemCategory.food,
                )
            }
        }
        if (dayIndex == 0 && !hasConcreteHotel) {
            hotels.firstOrNull()?.let { recommendation ->
                items += recommendation.toItineraryItem(
                    id = "supplement-day${dayIndex + 1}-hotel",
                    timeSlot = "19:00-20:00",
                    category = TravelItemCategory.hotel,
                )
            }
        }
        return day.copy(items = items.distinctBy { it.id })
    }

    private fun matchPoiRefId(
        item: TravelItineraryItem,
        mergedPois: List<TravelPoi>,
        hotels: List<TravelRecommendationItem>,
        foods: List<TravelRecommendationItem>,
        activities: List<TravelRecommendationItem>,
    ): String? {
        item.poiRefId?.takeIf { poiId -> mergedPois.any { it.id == poiId } }?.let { return it }
        val queries = listOf(item.title, item.description)
            .map(::normalizeTravelText)
            .filter { it.length >= 2 }
        if (queries.isEmpty()) return null

        val typedCandidates = when (item.category) {
            TravelItemCategory.hotel -> hotels.map(::recommendationToPoi)
            TravelItemCategory.food -> foods.map(::recommendationToPoi)
            TravelItemCategory.activity,
            TravelItemCategory.sightseeing -> activities.map(::recommendationToPoi)
            else -> emptyList()
        }
        return findUniquePoiMatch(queries, typedCandidates, mergedPois)
            ?: findUniquePoiMatch(queries, mergedPois, mergedPois)
    }

    private fun findUniquePoiMatch(
        queries: List<String>,
        primaryCandidates: List<TravelPoi>,
        mergedPois: List<TravelPoi>,
    ): String? {
        val enrichedCandidates = primaryCandidates.map { candidate ->
            mergedPois.firstOrNull { it.id == candidate.id } ?: candidate
        }.distinctBy { it.id }
        val matches = enrichedCandidates.filter { poi ->
            val normalizedName = normalizeTravelText(poi.name)
            normalizedName.length >= 2 && queries.any { query ->
                query == normalizedName || query.contains(normalizedName) || normalizedName.contains(query)
            }
        }
        return matches.singleOrNull()?.id
    }

    private fun recommendationToPoi(recommendation: TravelRecommendationItem): TravelPoi {
        return TravelPoi(
            id = recommendation.id,
            name = recommendation.title,
            category = recommendation.category.name,
            lat = recommendation.lat,
            lon = recommendation.lon,
            address = recommendation.subtitle,
            linkedRecommendationId = recommendation.id,
        )
    }

    private fun fallbackRecommendationsFromPois(
        pois: List<TravelPoi>,
        category: TravelRecommendationCategory,
        destination: String,
    ): List<TravelRecommendationItem> {
        val categoryName = category.name
        return pois
            .asSequence()
            .filter { poi ->
                poi.lat != null &&
                    poi.lon != null &&
                    poi.name.isNotBlank() &&
                    poi.category.equals(categoryName, ignoreCase = true)
            }
            .distinctBy { it.linkedRecommendationId ?: it.id }
            .map { poi ->
                TravelRecommendationItem(
                    id = poi.linkedRecommendationId ?: poi.id,
                    category = category,
                    title = poi.name,
                    subtitle = poi.address,
                    reason = when (category) {
                        TravelRecommendationCategory.hotel -> "$destination 可继续使用的住宿点位"
                        TravelRecommendationCategory.food -> "$destination 可继续使用的餐饮点位"
                        TravelRecommendationCategory.activity -> "$destination 可继续使用的活动/景点点位"
                    },
                    area = poi.address,
                    source = "poi-fallback",
                    lat = poi.lat,
                    lon = poi.lon,
                )
            }
            .toList()
    }

    private fun TravelRecommendationItem.toItineraryItem(
        id: String,
        timeSlot: String,
        category: TravelItemCategory,
    ): TravelItineraryItem {
        return TravelItineraryItem(
            id = id,
            timeSlot = timeSlot,
            title = title,
            description = reason.ifBlank { subtitle }.ifBlank { area },
            category = category,
            poiRefId = this.id,
            estimatedCost = priceHint,
        )
    }

    private fun List<TravelRecommendationItem>.pickForDay(dayIndex: Int): TravelRecommendationItem? {
        if (isEmpty()) return null
        return get(dayIndex % size)
    }

    private fun normalizeTravelText(value: String): String {
        return value
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9\\u4e00-\\u9fa5]"), "")
    }

    private fun orderedPoisForRoutes(
        itineraryDays: List<me.rerere.rikkahub.data.model.TravelItineraryDay>,
        pois: List<TravelPoi>,
    ): List<TravelPoi> {
        val poiIndex = pois.associateBy { it.id }
        return buildList {
            itineraryDays.forEach { day ->
                day.items.forEach { item ->
                    val poi = item.poiRefId?.let(poiIndex::get)
                    if (poi != null && lastOrNull()?.id != poi.id) {
                        add(poi)
                    }
                }
            }
        }
    }

    suspend fun compressConversation(
        conversationId: Uuid,
        conversation: Conversation,
        additionalPrompt: String,
        targetTokens: Int,
        keepRecentMessages: Int = 32
    ): Result<Unit> = runCatching {
        val settings = settingsStore.settingsFlow.first()
        val model = settings.findModelById(settings.compressModelId)
            ?: settings.getCurrentChatModel()
            ?: throw IllegalStateException("No model available for compression")
        val provider = model.findProvider(settings.providers)
            ?: throw IllegalStateException("Provider not found")

        val providerHandler = providerManager.getProviderByType(provider)

        val maxMessagesPerChunk = 256
        val allMessages = conversation.currentMessages

        // Split messages into those to compress and those to keep
        val messagesToCompress: List<UIMessage>
        val messagesToKeep: List<UIMessage>

        if (keepRecentMessages > 0 && allMessages.size > keepRecentMessages) {
            messagesToCompress = allMessages.dropLast(keepRecentMessages)
            messagesToKeep = allMessages.takeLast(keepRecentMessages)
        } else if (keepRecentMessages > 0) {
            // Not enough messages to compress while keeping recent ones
            throw IllegalStateException(context.getString(R.string.chat_page_compress_not_enough_messages))
        } else {
            messagesToCompress = allMessages
            messagesToKeep = emptyList()
        }

        fun splitMessages(messages: List<UIMessage>): List<List<UIMessage>> {
            if (messages.size <= maxMessagesPerChunk) return listOf(messages)
            val mid = messages.size / 2
            val left = splitMessages(messages.subList(0, mid))
            val right = splitMessages(messages.subList(mid, messages.size))
            return left + right
        }

        suspend fun compressMessages(messages: List<UIMessage>): String {
            val contentToCompress = messages.joinToString("\n\n") { it.summaryAsText() }
            val prompt = settings.compressPrompt.applyPlaceholders(
                "content" to contentToCompress,
                "target_tokens" to targetTokens.toString(),
                "additional_context" to if (additionalPrompt.isNotBlank()) {
                    "Additional instructions from user: $additionalPrompt"
                } else "",
                "locale" to Locale.getDefault().displayName
            )

            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(UIMessage.user(prompt)),
                params = TextGenerationParams(
                    model = model,
                ),
            )

            return result.choices[0].message?.toText()?.trim()
                ?: throw IllegalStateException("Failed to generate compressed summary")
        }

        val compressedSummaries = coroutineScope {
            splitMessages(messagesToCompress)
                .map { chunk -> async { compressMessages(chunk) } }
                .awaitAll()
        }

        // Create new conversation with compressed history as multiple user messages + kept messages
        val newMessageNodes = buildList {
            compressedSummaries.forEach { summary ->
                add(UIMessage.user(summary).toMessageNode())
            }
            addAll(messagesToKeep.map { it.toMessageNode() })
        }
        val newConversation = conversation.copy(
            messageNodes = newMessageNodes,
            chatSuggestions = emptyList(),
        )

        saveConversation(conversationId, newConversation)
    }

    // ---- 通知 ----

    private fun sendGenerationDoneNotification(conversationId: Uuid, senderName: String) {
        // 先取消 Live Update 通知
        cancelLiveUpdateNotification(conversationId)

        val conversation = getConversationFlow(conversationId).value
        context.sendNotification(
            channelId = CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID,
            notificationId = 1
        ) {
            title = senderName
            content = conversation.currentMessages.lastOrNull()?.toText()?.take(50)?.trim() ?: ""
            autoCancel = true
            useDefaults = true
            category = NotificationCompat.CATEGORY_MESSAGE
            contentIntent = getPendingIntent(context, conversationId)
        }
    }

    private fun getLiveUpdateNotificationId(conversationId: Uuid): Int {
        return conversationId.hashCode() + 10000
    }

    private fun sendLiveUpdateNotification(
        conversationId: Uuid,
        messages: List<UIMessage>,
        senderName: String
    ) {
        val lastMessage = messages.lastOrNull() ?: return
        val parts = lastMessage.parts

        // 确定当前状态
        val (chipText, statusText, contentText) = determineNotificationContent(parts)

        context.sendNotification(
            channelId = CHAT_LIVE_UPDATE_NOTIFICATION_CHANNEL_ID,
            notificationId = getLiveUpdateNotificationId(conversationId)
        ) {
            title = senderName
            content = contentText
            subText = statusText
            ongoing = true
            onlyAlertOnce = true
            category = NotificationCompat.CATEGORY_PROGRESS
            useBigTextStyle = true
            contentIntent = getPendingIntent(context, conversationId)
            requestPromotedOngoing = true
            shortCriticalText = chipText
        }
    }

    private fun determineNotificationContent(parts: List<UIMessagePart>): Triple<String, String, String> {
        // 检查最近的 part 来确定状态
        val lastReasoning = parts.filterIsInstance<UIMessagePart.Reasoning>().lastOrNull()
        val lastTool = parts.filterIsInstance<UIMessagePart.Tool>().lastOrNull()
        val lastText = parts.filterIsInstance<UIMessagePart.Text>().lastOrNull()

        return when {
            // 正在执行工具
            lastTool != null && !lastTool.isExecuted -> {
                val toolName = lastTool.toolName.removePrefix("mcp__")
                Triple(
                    context.getString(R.string.notification_live_update_chip_tool),
                    context.getString(R.string.notification_live_update_tool, toolName),
                    lastTool.input.take(100)
                )
            }
            // 正在思考（Reasoning 未结束）
            lastReasoning != null && lastReasoning.finishedAt == null -> {
                Triple(
                    context.getString(R.string.notification_live_update_chip_thinking),
                    context.getString(R.string.notification_live_update_thinking),
                    lastReasoning.reasoning.takeLast(200)
                )
            }
            // 正在写回复
            lastText != null -> {
                Triple(
                    context.getString(R.string.notification_live_update_chip_writing),
                    context.getString(R.string.notification_live_update_writing),
                    lastText.text.takeLast(200)
                )
            }
            // 默认状态
            else -> {
                Triple(
                    context.getString(R.string.notification_live_update_chip_writing),
                    context.getString(R.string.notification_live_update_title),
                    ""
                )
            }
        }
    }

    private fun cancelLiveUpdateNotification(conversationId: Uuid) {
        context.cancelNotification(getLiveUpdateNotificationId(conversationId))
    }

    private fun getPendingIntent(context: Context, conversationId: Uuid): PendingIntent {
        val intent = Intent(context, RouteActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId.toString())
        }
        return PendingIntent.getActivity(
            context,
            conversationId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    // ---- 对话状态更新 ----

    private fun updateConversation(conversationId: Uuid, conversation: Conversation) {
        if (conversation.id != conversationId) return
        val session = getOrCreateSession(conversationId)
        checkFilesDelete(conversation, session.state.value)
        session.state.value = conversation
    }

    fun updateConversationState(conversationId: Uuid, update: (Conversation) -> Conversation) {
        val current = getConversationFlow(conversationId).value
        updateConversation(conversationId, update(current))
    }

    private fun checkFilesDelete(newConversation: Conversation, oldConversation: Conversation) {
        val newFiles = newConversation.files
        val oldFiles = oldConversation.files
        val deletedFiles = oldFiles.filter { file ->
            newFiles.none { it == file }
        }
        if (deletedFiles.isNotEmpty()) {
            filesManager.deleteChatFiles(deletedFiles)
            Log.w(TAG, "checkFilesDelete: $deletedFiles")
        }
    }

    private suspend fun persistConversationToRepository(conversationId: Uuid, conversation: Conversation): Conversation {
        val exists = conversationRepo.existsConversationById(conversation.id)
        if (!exists && conversation.title.isBlank() && conversation.messageNodes.isEmpty()) {
            return conversation
        }

        val updatedConversation = conversation.copy(updateAt = Instant.now())
        updateConversation(conversationId, updatedConversation)

        if (!exists) {
            conversationRepo.insertConversation(updatedConversation)
        } else {
            conversationRepo.updateConversation(updatedConversation)
        }
        return updatedConversation
    }

    private fun clearProgressPersistState(conversationId: Uuid) {
        progressPersistJobs.remove(conversationId)?.cancel()
        progressPersistAt.remove(conversationId)
    }

    private fun persistConversationProgress(
        conversationId: Uuid,
        conversation: Conversation,
        immediate: Boolean = false,
    ) {
        val now = System.currentTimeMillis()
        val lastPersistAt = progressPersistAt[conversationId] ?: 0L
        val shouldPersistNow = immediate || now - lastPersistAt >= GENERATION_PROGRESS_PERSIST_INTERVAL_MS

        progressPersistJobs.remove(conversationId)?.cancel()

        if (shouldPersistNow) {
            progressPersistAt[conversationId] = now
            appScope.launch {
                persistConversationToRepository(conversationId, conversation)
            }
            return
        }

        val delayedJob = appScope.launch {
            delay(GENERATION_PROGRESS_PERSIST_DELAY_MS)
            progressPersistAt[conversationId] = System.currentTimeMillis()
            persistConversationToRepository(conversationId, conversation)
        }
        progressPersistJobs[conversationId] = delayedJob
    }

    suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        clearProgressPersistState(conversationId)
        persistConversationToRepository(conversationId, conversation)
    }

    // ---- 翻译消息 ----

    fun translateMessage(
        conversationId: Uuid,
        message: UIMessage,
        targetLanguage: Locale
    ) {
        appScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsStore.settingsFlow.first()

                val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()

                if (messageText.isBlank()) return@launch

                // Set loading state for translation
                val loadingText = context.getString(R.string.translating)
                updateTranslationField(conversationId, message.id, loadingText)

                generationHandler.translateText(
                    settings = settings,
                    sourceText = messageText,
                    targetLanguage = targetLanguage
                ) { translatedText ->
                    // Update translation field in real-time
                    updateTranslationField(conversationId, message.id, translatedText)
                }.collect { /* Final translation already handled in onStreamUpdate */ }

                // Save the conversation after translation is complete
                saveConversation(conversationId, getConversationFlow(conversationId).value)
            } catch (e: Exception) {
                // Clear translation field on error
                clearTranslationField(conversationId, message.id)
                addError(e, conversationId, title = context.getString(R.string.error_title_translate_message))
            }
        }
    }

    private fun updateTranslationField(
        conversationId: Uuid,
        messageId: Uuid,
        translationText: String
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = translationText)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // ---- 消息操作 ----

    suspend fun editMessage(
        conversationId: Uuid,
        messageId: Uuid,
        parts: List<UIMessagePart>
    ) {
        if (parts.isEmptyInputMessage()) return
        val processedParts = preprocessUserInputParts(parts)

        val currentConversation = getConversationFlow(conversationId).value
        var edited = false

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (!node.messages.any { it.id == messageId }) {
                return@map node
            }
            edited = true

            node.copy(
                messages = node.messages + UIMessage(
                    role = node.role,
                    parts = processedParts,
                ),
                selectIndex = node.messages.size
            )
        }

        if (!edited) return

        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun forkConversationAtMessage(
        conversationId: Uuid,
        messageId: Uuid
    ): Conversation {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNodeIndex = currentConversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            throw NotFoundException("Message not found")
        }

        val copiedNodes = currentConversation.messageNodes
            .subList(0, targetNodeIndex + 1)
            .map { node ->
                node.copy(
                    id = Uuid.random(),
                    messages = node.messages.map { message ->
                        message.copy(
                            parts = message.parts.map { part ->
                                part.copyWithForkedFileUrl()
                            }
                        )
                    }
                )
            }

        val forkConversation = Conversation(
            id = Uuid.random(),
            assistantId = currentConversation.assistantId,
            messageNodes = copiedNodes,
        )

        saveConversation(forkConversation.id, forkConversation)
        return forkConversation
    }

    suspend fun selectMessageNode(
        conversationId: Uuid,
        nodeId: Uuid,
        selectIndex: Int
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val targetNode = currentConversation.messageNodes.firstOrNull { it.id == nodeId }
            ?: throw NotFoundException("Message node not found")

        if (selectIndex !in targetNode.messages.indices) {
            throw BadRequestException("Invalid selectIndex")
        }

        if (targetNode.selectIndex == selectIndex) {
            return
        }

        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.id == nodeId) {
                node.copy(selectIndex = selectIndex)
            } else {
                node
            }
        }

        saveConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        messageId: Uuid,
        failIfMissing: Boolean = true,
    ) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedConversation = buildConversationAfterMessageDelete(currentConversation, messageId)

        if (updatedConversation == null) {
            if (failIfMissing) {
                throw NotFoundException("Message not found")
            }
            return
        }

        saveConversation(conversationId, updatedConversation)
    }

    suspend fun deleteMessage(
        conversationId: Uuid,
        message: UIMessage,
    ) {
        deleteMessage(conversationId, message.id, failIfMissing = false)
    }

    private fun buildConversationAfterMessageDelete(
        conversation: Conversation,
        messageId: Uuid,
    ): Conversation? {
        val targetNodeIndex = conversation.messageNodes.indexOfFirst { node ->
            node.messages.any { it.id == messageId }
        }
        if (targetNodeIndex == -1) {
            return null
        }

        val updatedNodes = conversation.messageNodes.mapIndexedNotNull { index, node ->
            if (index != targetNodeIndex) {
                return@mapIndexedNotNull node
            }

            val nextMessages = node.messages.filterNot { it.id == messageId }
            if (nextMessages.isEmpty()) {
                return@mapIndexedNotNull null
            }

            val nextSelectIndex = node.selectIndex.coerceAtMost(nextMessages.lastIndex)
            node.copy(
                messages = nextMessages,
                selectIndex = nextSelectIndex,
            )
        }

        return conversation.copy(messageNodes = updatedNodes)
    }

    private fun UIMessagePart.copyWithForkedFileUrl(): UIMessagePart {
        fun copyLocalFileIfNeeded(url: String): String {
            if (!url.startsWith("file:")) return url
            val copied = filesManager.createChatFilesByContents(listOf(url.toUri())).firstOrNull()
            return copied?.toString() ?: url
        }

        return when (this) {
            is UIMessagePart.Image -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Document -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Video -> copy(url = copyLocalFileIfNeeded(url))
            is UIMessagePart.Audio -> copy(url = copyLocalFileIfNeeded(url))
            else -> this
        }
    }

    fun clearTranslationField(conversationId: Uuid, messageId: Uuid) {
        val currentConversation = getConversationFlow(conversationId).value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = null)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(conversationId, currentConversation.copy(messageNodes = updatedNodes))
    }

    // 停止当前会话生成任务（不清理会话缓存）
    fun stopGeneration(conversationId: Uuid) {
        sessions[conversationId]?.getJob()?.cancel()
    }
}
