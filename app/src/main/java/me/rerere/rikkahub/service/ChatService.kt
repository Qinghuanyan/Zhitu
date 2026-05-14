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
import me.rerere.rikkahub.utils.sendNotification
import me.rerere.rikkahub.utils.cancelNotification
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.Uuid

private const val TAG = "ChatService"

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
        getOrCreateSession(conversationId) // 确保 session 存在
        val conversation = conversationRepo.getConversationById(conversationId)
        if (conversation != null) {
            updateConversation(conversationId, conversation)
            settingsStore.updateAssistant(conversation.assistantId)
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
                updateConversation(conversationId, updatedConversation)

                // Show notification if app is not in foreground
                if (!isForeground.value && settings.displaySetting.enableNotificationOnMessageGeneration) {
                    sendGenerationDoneNotification(conversationId, senderName)
                }
            }.collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        val updatedConversation = getConversationFlow(conversationId).value
                            .updateCurrentMessages(chunk.messages)
                        updateConversation(conversationId, updatedConversation)

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
            val provider = model.findProvider(settings.providers) ?: return

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        prompt = settings.titlePrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(4).joinToString("\n\n") { it.summaryAsText() })
                    ),
                ),
                params = TextGenerationParams(
                    model = model,
                    thinkingBudget = 0,
                ),
            )

            // 生成完，conversation可能不是最新了，因此需要重新获取
            conversationRepo.getConversationById(conversation.id)?.let {
                saveConversation(
                    conversationId,
                    it.copy(title = result.choices[0].message?.toText()?.trim() ?: "")
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
            val provider = model.findProvider(settings.providers) ?: return

            sessions[conversationId]?.let { session ->
                updateConversation(
                    conversationId,
                    session.state.value.copy(chatSuggestions = emptyList())
                )
            }

            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        settings.suggestionPrompt.applyPlaceholders(
                            "locale" to Locale.getDefault().displayName,
                            "content" to conversation.currentMessages
                                .takeLast(8).joinToString("\n\n") { it.summaryAsText() }),
                    )
                ),
                params = TextGenerationParams(
                    model = model,
                    thinkingBudget = 0,
                ),
            )
            val suggestions =
                result.choices[0].message?.toText()?.split("\n")?.map { it.trim() }
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
        updateConversationState(conversationId) { current ->
            current.copy(travelPlanningState = TravelPlanningState.ExtractingBrief)
        }
        val content = conversation.currentMessages
            .takeLast(12)
            .joinToString("\n\n") { it.summaryAsText() }
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
            val settings = settingsStore.settingsFlow.first()
            val model = settings.getCurrentChatModel()
                ?: throw IllegalStateException("No chat model available")
            val provider = model.findProvider(settings.providers)
                ?: throw IllegalStateException("Provider not found")
            val providerHandler = providerManager.getProviderByType(provider)
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        DEFAULT_TRAVEL_BRIEF_PROMPT.applyPlaceholders(
                            "content" to content,
                        )
                    )
                ),
                params = TextGenerationParams(
                    model = model,
                    thinkingBudget = 0,
                ),
            )
            val brief = decodeJsonPayload<TravelPlanningBrief>(
                result.choices[0].message?.toText().orEmpty()
            )
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

        updateConversationState(conversationId) { current ->
            current.copy(
                travelPlanningState = TravelPlanningState.GeneratingPlan,
                travelPlan = (current.travelPlan ?: TravelPlan(conversationId = conversationId.toString())).copy(
                    brief = ensuredBrief,
                    status = TravelPlanStatus.ready_to_generate
                )
            )
        }

        val planningFacts = travelPlanningDataRepository.buildPlanningFacts(
            destination = ensuredBrief.destination,
            days = ensuredBrief.days,
            origin = ensuredBrief.origin,
            transportPreferences = ensuredBrief.transportPreferences,
        )

        runCatching {
            val settings = settingsStore.settingsFlow.first()
            val model = settings.getCurrentChatModel()
                ?: throw IllegalStateException("No chat model available")
            val provider = model.findProvider(settings.providers)
                ?: throw IllegalStateException("Provider not found")
            val providerHandler = providerManager.getProviderByType(provider)
            val content = conversation.currentMessages
                .takeLast(16)
                .joinToString("\n\n") { it.summaryAsText() }
            val result = providerHandler.generateText(
                providerSetting = provider,
                messages = listOf(
                    UIMessage.user(
                        buildTravelItineraryPrompt(
                            brief = JsonInstant.encodeToString(ensuredBrief),
                            facts = JsonInstant.encodeToString(planningFacts),
                            content = content,
                        )
                    )
                ),
                params = TextGenerationParams(
                    model = model,
                    thinkingBudget = 0,
                ),
            )
            val payload = decodeJsonPayload<TravelGeneratedPayload>(
                result.choices[0].message?.toText().orEmpty()
            )
            val latestConversation = conversationRepo.getConversationById(conversationId)
                ?: getConversationFlow(conversationId).value
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
        val hotels = payload.hotels.ifEmpty {
            planningFacts.commercialHotels.ifEmpty { planningFacts.nearbyHotels }.take(6)
        }
        val foods = payload.foods.ifEmpty { planningFacts.nearbyFoods.take(6) }
        val activities = payload.activities.ifEmpty {
            planningFacts.commercialActivities.ifEmpty { planningFacts.nearbyActivities }.take(8)
        }

        val mergedPois = mergePois(
            payloadPois = payload.pois,
            hotels = hotels,
            foods = foods,
            activities = activities,
            planningFacts = planningFacts,
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
    ): List<TravelPoi> {
        val factPois = buildList {
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

    suspend fun saveConversation(conversationId: Uuid, conversation: Conversation) {
        val exists = conversationRepo.existsConversationById(conversation.id)
        if (!exists && conversation.title.isBlank() && conversation.messageNodes.isEmpty()) {
            return // 新会话且为空时不保存
        }

        val updatedConversation = conversation.copy()
        updateConversation(conversationId, updatedConversation)

        if (!exists) {
            conversationRepo.insertConversation(updatedConversation)
        } else {
            conversationRepo.updateConversation(updatedConversation)
        }
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
