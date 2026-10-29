package dev.codecell.app

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.codecell.app.data.AgentOption
import dev.codecell.app.data.AppConfig
import dev.codecell.app.data.Attachment
import dev.codecell.app.data.ChatPart
import dev.codecell.app.data.LocalServer
import dev.codecell.app.data.ModelOption
import dev.codecell.app.data.OpencodeClient
import dev.codecell.app.data.QuestionRequest
import dev.codecell.app.data.SessionInfo
import dev.codecell.app.data.SessionMessage
import dev.codecell.app.data.TermuxEnvironment
import dev.codecell.app.data.Vault
import dev.codecell.app.service.ServerService
import java.io.File
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Role { User, Assistant }

enum class ServerPhase { Stopped, Starting, Running, Failed }

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val isError: Boolean = false,
    val serverId: String? = null,
    val parts: List<ChatPart> = emptyList(),
    val queued: Boolean = false,
)

class AppState(context: Context) {

    private val appContext = context.applicationContext
    private val config = AppConfig(appContext)
    private val vault = Vault(appContext, config)
    private var nextId = 1L
    private val seenAssistantIds = mutableSetOf<String>()

    var serverUrl by mutableStateOf(AppConfig.DEFAULT_SERVER)
    var serverPhase by mutableStateOf(ServerPhase.Stopped)
        private set
    var serverStatus by mutableStateOf("")
        private set
    var workspacePath by mutableStateOf(config.workspacePath)
    var userName by mutableStateOf(config.userName)
    var model by mutableStateOf(config.model)
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
    var sessionId by mutableStateOf<String?>(null)
    var sessionTitle by mutableStateOf("New chat")
    var sending by mutableStateOf(false)
    var loadingHistory by mutableStateOf(false)
    var liveMessageId by mutableStateOf<Long?>(null)
        private set
    var revertMessageID by mutableStateOf<String?>(null)
        private set

    var folderLabel by mutableStateOf(vault.label())
    var hasFolder by mutableStateOf(vault.hasFolder())
    var folderPrompted by mutableStateOf(config.folderPrompted)
    var onboarded by mutableStateOf(config.onboarded)
        private set
    var configuredProviders by mutableStateOf(config.configuredProviders)
        private set
    var pendingQuestions by mutableStateOf<List<QuestionRequest>>(emptyList())
        private set

    private var sendJob: Job? = null
    private var activePollJob: Job? = null
    private var userStopped = false

    var builtinAgents by mutableStateOf(DEFAULT_AGENTS)
    var customAgents by mutableStateOf(vault.loadAgents())
    var agentName by mutableStateOf(config.agentName)
    var agentSystem by mutableStateOf(config.agentSystem)

    var sessions by mutableStateOf<List<SessionInfo>>(emptyList())
    var sessionsLoading by mutableStateOf(false)

    init {
        config.serverUrl = AppConfig.DEFAULT_SERVER
        vault.workspacePath()?.let { selected ->
            config.workspacePath = selected
            workspacePath = selected
        }
    }

    val modelLabel: String
        get() = model?.let { option ->
            val name = option.name.takeIf { it.isNotBlank() } ?: option.modelID
            option.variant?.takeIf { it.isNotBlank() }?.let { "$name · $it" } ?: name
        } ?: "Server default"

    val agentLabel: String
        get() = agentName.takeIf { it.isNotBlank() } ?: "build"

    fun agentOptions(): List<AgentOption> = builtinAgents + customAgents

    fun saveSettings(name: String) {
        userName = name.trim()
        config.userName = userName
    }

    fun ensureServer(scope: CoroutineScope) {
        if (serverPhase == ServerPhase.Starting) return
        if (serverPhase == ServerPhase.Running) {
            val embedded = LocalServer.workspacePath
            if (!LocalServer.isRunning() || embedded == config.workspacePath) return
            ServerService.stop(appContext)
            LocalServer.stop()
            serverPhase = ServerPhase.Stopped
        }

        val password = config.serverPassword
        OpencodeClient.setCredentials("opencode", password)
        serverPhase = ServerPhase.Starting
        serverStatus = "Starting opencode..."

        scope.launch {
            val alreadyRunning = withContext(Dispatchers.IO) {
                runCatching { OpencodeClient.health(serverUrl) }.isSuccess
            }
            if (alreadyRunning) {
                onServerReady(scope)
                return@launch
            }

            val workspace = File(config.workspacePath).apply { mkdirs() }
            if (!workspace.exists()) {
                serverPhase = ServerPhase.Failed
                serverStatus = "The selected workspace is not accessible."
                return@launch
            }
            workspacePath = workspace.absolutePath
            ServerService.start(appContext, workspace, password, portOf(serverUrl))

            val deadline = System.currentTimeMillis() + 90_000
            while (System.currentTimeMillis() < deadline) {
                delay(700)
                val healthy = withContext(Dispatchers.IO) {
                    runCatching { OpencodeClient.health(serverUrl) }.isSuccess
                }
                if (healthy) {
                    onServerReady(scope)
                    return@launch
                }
                if (LocalServer.state == LocalServer.State.Failed) break
            }

            serverPhase = ServerPhase.Failed
            val tail = withContext(Dispatchers.IO) { LocalServer.logTail(appContext, 8) }
            val detail = tail.ifBlank { LocalServer.message }
            serverStatus = if (detail.isBlank()) {
                "opencode did not start."
            } else {
                "opencode did not start:\n$detail"
            }
        }
    }

    fun retryServer(scope: CoroutineScope) {
        ServerService.stop(appContext)
        LocalServer.stop()
        serverPhase = ServerPhase.Stopped
        serverStatus = ""
        ensureServer(scope)
    }

    fun restartServer(scope: CoroutineScope) {
        retryServer(scope)
    }

    private suspend fun onServerReady(scope: CoroutineScope) {
        markRunning()
        refreshAgents(scope)
        refreshSessions(scope)
    }

    private fun markRunning() {
        if (serverPhase != ServerPhase.Running) {
            serverPhase = ServerPhase.Running
            serverStatus = "Connected"
        }
    }

    private fun isLocalUrl(url: String): Boolean = runCatching {
        val host = URI(url.trim()).host?.lowercase()
        host == "127.0.0.1" || host == "localhost" || host == "::1"
    }.getOrDefault(false)

    private fun portOf(url: String): Int = runCatching {
        URI(url.trim()).port.takeIf { it > 0 } ?: 4096
    }.getOrDefault(4096)

    fun attachFolder(uri: Uri, scope: CoroutineScope) {
        val ok = vault.attach(uri)
        hasFolder = vault.hasFolder()
        folderLabel = vault.label()
        config.folderPrompted = true
        folderPrompted = true
        if (ok) {
            workspacePath = config.workspacePath
            File(workspacePath).mkdirs()
            customAgents = vault.loadAgents()
            restartServer(scope)
        }
    }

    fun markFolderPrompted() {
        config.folderPrompted = true
        folderPrompted = true
    }

    fun completeOnboarding() {
        if (!TermuxEnvironment.isInstalled(appContext)) return
        config.onboarded = true
        onboarded = true
    }

    fun markProviderConfigured(providerID: String) {
        if (providerID.isBlank()) return
        configuredProviders = configuredProviders + providerID
        config.configuredProviders = configuredProviders
    }

    fun unmarkProviderConfigured(providerID: String) {
        configuredProviders = configuredProviders - providerID
        config.configuredProviders = configuredProviders
    }

    fun selectModel(option: ModelOption?) {
        model = option
        config.model = option
    }

    fun selectAgent(option: AgentOption) {
        agentName = option.name
        agentSystem = option.system
        config.agentName = option.name
        config.agentSystem = option.system
    }

    fun addCustomAgent(name: String, system: String, description: String = "Custom agent") {
        val cleanName = name.trim()
        val cleanSystem = system.trim()
        if (cleanName.isBlank() || cleanSystem.isBlank()) return
        val option = AgentOption(cleanName, description.ifBlank { "Custom agent" }, cleanSystem)
        customAgents = customAgents.filterNot { it.name.equals(cleanName, ignoreCase = true) } + option
        selectAgent(option)
        val snapshot = customAgents
        Thread {
            vault.saveAgents(snapshot)
        }.start()
    }

    fun newChat() {
        sessionId = null
        sessionTitle = "New chat"
        messages = emptyList()
        liveMessageId = null
        revertMessageID = null
        queuedPrompts.clear()
        pendingQuestions = emptyList()
        seenAssistantIds.clear()
    }

    fun refreshAgents(scope: CoroutineScope) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { OpencodeClient.listAgents(serverUrl) }
            }.onSuccess { fetched ->
                if (fetched.isNotEmpty()) builtinAgents = fetched
                markRunning()
            }
        }
    }

    fun refreshSessions(scope: CoroutineScope) {
        sessionsLoading = true
        scope.launch {
            try {
                val fetched = withContext(Dispatchers.IO) { OpencodeClient.listSessions(serverUrl) }
                sessions = fetched.sortedWith(
                    compareByDescending<SessionInfo> { it.pinned }
                        .thenByDescending { it.updated },
                )
                markRunning()
            } catch (e: Exception) {
                sessions = emptyList()
            } finally {
                sessionsLoading = false
            }
        }
    }

    fun openSession(target: SessionInfo, scope: CoroutineScope) {
        sessionId = target.id
        sessionTitle = target.title
        messages = emptyList()
        liveMessageId = null
        revertMessageID = target.revertMessageID
        queuedPrompts.clear()
        seenAssistantIds.clear()
        loadingHistory = true
        scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    OpencodeClient.loadMessages(serverUrl, target.id)
                }
                rememberAssistantIds(loaded)
                messages = loaded.visibleUpTo(revertMessageID).map { message ->
                    ChatMessage(
                        id = nextId++,
                        role = if (message.role == "assistant") Role.Assistant else Role.User,
                        text = message.text,
                        serverId = message.id.takeIf { it.isNotBlank() },
                        parts = message.parts,
                    )
                }
                pendingQuestions = withContext(Dispatchers.IO) {
                    runCatching {
                        OpencodeClient.listQuestions(serverUrl)
                            .filter { it.sessionID == target.id }
                    }.getOrDefault(emptyList())
                }
                markRunning()
            } catch (e: Exception) {
                messages = listOf(
                    ChatMessage(nextId++, Role.Assistant, e.message ?: "Could not load session", true),
                )
            } finally {
                loadingHistory = false
            }
        }
    }

    fun answerQuestion(
        request: QuestionRequest,
        answers: List<List<String>>,
        scope: CoroutineScope,
    ) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    OpencodeClient.replyQuestion(serverUrl, request.id, answers)
                }
                pendingQuestions = pendingQuestions.filterNot { it.id == request.id }
                if (!sending) refreshSessionMessages(scope)
            } catch (e: Exception) {
                pendingQuestions = pendingQuestions.filterNot { it.id == request.id }
                append(Role.Assistant, e.message ?: "Could not send that answer", isError = true)
            }
        }
    }

    fun rejectQuestion(request: QuestionRequest, scope: CoroutineScope) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    OpencodeClient.rejectQuestion(serverUrl, request.id)
                }
            }
            pendingQuestions = pendingQuestions.filterNot { it.id == request.id }
        }
    }

    private fun refreshSessionMessages(scope: CoroutineScope) {
        val id = sessionId ?: return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    OpencodeClient.loadMessages(serverUrl, id)
                }
            }.onSuccess { loaded ->
                rememberAssistantIds(loaded)
                messages = loaded.visibleUpTo(revertMessageID).map { item ->
                    ChatMessage(
                        id = nextId++,
                        role = if (item.role == "assistant") Role.Assistant else Role.User,
                        text = item.text,
                        serverId = item.id.takeIf { it.isNotBlank() },
                        parts = item.parts,
                    )
                }
            }
        }
    }

    private data class QueuedPrompt(
        val id: Long,
        val text: String,
        val attachments: List<Attachment>,
    )

    private val queuedPrompts = ArrayDeque<QueuedPrompt>()

    fun send(text: String, attachments: List<Attachment> = emptyList(), scope: CoroutineScope) {
        val content = text.trim()
        if (content.isEmpty() && attachments.isEmpty()) return
        if (sending) {
            val parts = attachments.map {
                ChatPart(type = "file", title = it.name, text = it.mime, preview = it.preview)
            }
            val queued = append(Role.User, content, parts = parts, queued = true)
            queuedPrompts.addLast(QueuedPrompt(queued.id, content, attachments))
            return
        }
        startSend(content, attachments, scope, queuedID = null)
    }

    private fun startSend(
        content: String,
        attachments: List<Attachment>,
        scope: CoroutineScope,
        queuedID: Long?,
    ) {
        val isNew = sessionId == null
        val title = content.lineSequence().firstOrNull()?.trim()?.take(60)
            ?.takeIf { it.isNotBlank() }
            ?: attachments.firstOrNull()?.name
            ?: "New chat"
        val display = content
        val userParts = attachments.map {
            ChatPart(type = "file", title = it.name, text = it.mime, preview = it.preview)
        }

        val baselineAssistantIds = seenAssistantIds + messages
            .filter { it.role == Role.Assistant }
            .mapNotNull { it.serverId }
        val userMessage = if (queuedID == null) {
            append(Role.User, display, parts = userParts)
        } else {
            messages = messages.map {
                if (it.id == queuedID) it.copy(queued = false) else it
            }
            messages.firstOrNull { it.id == queuedID } ?: return
        }
        sending = true
        userStopped = false
        var sessionForCleanup: String? = null

        sendJob = scope.launch {
            var pollJob: Job? = null
            try {
                val activeSession = sessionId ?: withContext(Dispatchers.IO) {
                    OpencodeClient.createSession(serverUrl, title)
                }.also {
                    sessionId = it
                    sessionTitle = title
                }
                sessionForCleanup = activeSession

                pollJob = scope.launch(Dispatchers.IO) {
                    while (isActive) {
                        val loaded = runCatching {
                            OpencodeClient.loadMessages(serverUrl, activeSession)
                        }.getOrNull()
                        if (loaded != null) {
                            val fresh = loaded.filter {
                                it.role == "assistant" &&
                                    it.id.isNotBlank() &&
                                    it.id !in baselineAssistantIds
                            }
                            if (fresh.isNotEmpty()) {
                                val text = fresh.mapNotNull { it.text.takeIf(String::isNotBlank) }
                                    .joinToString("\n\n")
                                val parts = fresh.flatMap { it.parts }
                                val serverId = fresh.last().id
                                withContext(Dispatchers.Main) {
                                    seenAssistantIds += fresh.map { it.id }
                                    val live = liveMessageId
                                    if (live == null) {
                                        val message = ChatMessage(
                                            id = nextId++,
                                            role = Role.Assistant,
                                            text = text,
                                            serverId = serverId,
                                            parts = parts,
                                        )
                                        appendMessage(message)
                                        liveMessageId = message.id
                                    } else {
                                        messages = messages.map {
                                            if (it.id == live) {
                                                it.copy(text = text, serverId = serverId, parts = parts)
                                            } else {
                                                it
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        val questions = runCatching {
                            OpencodeClient.listQuestions(serverUrl)
                        }.getOrNull()
                        if (questions != null) {
                            val mine = questions.filter { it.sessionID == activeSession }
                            withContext(Dispatchers.Main) {
                                pendingQuestions = pendingQuestions
                                    .filterNot { it.sessionID == activeSession } + mine
                            }
                        }
                        delay(700)
                    }
                }
                activePollJob = pollJob

                val sent = withContext(Dispatchers.IO) {
                    OpencodeClient.sendMessage(
                        baseUrl = serverUrl,
                        sessionId = activeSession,
                        text = content,
                        providerID = model?.providerID,
                        modelID = model?.modelID,
                        variant = model?.variant,
                        agent = if (agentSystem == null) agentName else null,
                        system = agentSystem,
                        attachments = attachments,
                    )
                }
                revertMessageID = null
                markRunning()
                pollJob?.cancelAndJoin()
                pollJob = null
                activePollJob = null
                val finalLoaded = runCatching {
                    withContext(Dispatchers.IO) {
                        OpencodeClient.loadMessages(serverUrl, activeSession)
                    }
                }.getOrNull()
                val fresh = finalLoaded.orEmpty().filter {
                    it.role == "assistant" &&
                        it.id.isNotBlank() &&
                        it.id !in baselineAssistantIds
                }
                val finalText = fresh.mapNotNull { it.text.takeIf(String::isNotBlank) }
                    .joinToString("\n\n")
                    .ifBlank { sent.text }
                val finalParts = fresh.flatMap { it.parts }.ifEmpty { sent.parts }
                val finalServerId = fresh.lastOrNull()?.id ?: sent.assistantID
                seenAssistantIds += fresh.map { it.id }
                sent.userID?.let { userID ->
                    messages = messages.map {
                        if (it.id == userMessage.id) it.copy(serverId = userID) else it
                    }
                }
                val live = liveMessageId
                if (live != null) {
                    messages = messages.map {
                        if (it.id == live) {
                            it.copy(text = finalText, serverId = finalServerId, parts = finalParts)
                        } else {
                            it
                        }
                    }
                } else {
                    append(
                        Role.Assistant,
                        finalText,
                        serverId = finalServerId,
                        parts = finalParts,
                    )
                }
                pendingQuestions = pendingQuestions.filterNot { it.sessionID == activeSession }
                persistSession(activeSession, scope)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pollJob?.cancel()
                pollJob = null
                activePollJob = null
                if (userStopped) {
                    if (liveMessageId == null) {
                        append(Role.Assistant, "Stopped.")
                    }
                } else {
                    val message = e.message ?: "Request failed"
                    val live = liveMessageId
                    if (live != null) {
                        messages = messages.map {
                            if (it.id == live) it.copy(text = message, isError = true) else it
                        }
                    } else {
                        append(Role.Assistant, message, isError = true)
                    }
                }
            } finally {
                pollJob?.cancel()
                activePollJob = null
                liveMessageId = null
                sending = false
                userStopped = false
                sendJob = null
                sessionForCleanup?.let { active ->
                    pendingQuestions = pendingQuestions.filterNot { it.sessionID == active }
                }
                drainQueue(scope)
            }
        }

        if (isNew) sessionTitle = title
    }

    private fun drainQueue(scope: CoroutineScope) {
        if (sending) return
        while (true) {
            val next = queuedPrompts.removeFirstOrNull() ?: return
            if (messages.none { it.id == next.id }) continue
            startSend(next.text, next.attachments, scope, next.id)
            return
        }
    }

    fun stop(scope: CoroutineScope) {
        if (!sending) return
        val activeSession = sessionId
        userStopped = true
        activePollJob?.cancel()
        activePollJob = null
        OpencodeClient.cancelActiveRequest()
        if (activeSession != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching { OpencodeClient.abortSession(serverUrl, activeSession) }
                }
            }
        }
        scope.launch {
            delay(4_000)
            if (userStopped && sending) sendJob?.cancel()
        }
    }

    fun deleteSession(target: SessionInfo, scope: CoroutineScope) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { OpencodeClient.deleteSession(serverUrl, target.id) }
            }
            if (sessionId == target.id) newChat()
            refreshSessions(scope)
        }
    }

    fun renameSession(target: SessionInfo, title: String, scope: CoroutineScope) {
        val clean = title.trim()
        if (clean.isBlank()) return
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    OpencodeClient.updateSessionTitle(serverUrl, target.id, clean)
                }
            }
            if (sessionId == target.id) sessionTitle = clean
            refreshSessions(scope)
        }
    }

    fun togglePin(target: SessionInfo, scope: CoroutineScope) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    OpencodeClient.setSessionPinned(serverUrl, target.id, !target.pinned)
                }
            }
            refreshSessions(scope)
        }
    }

    fun archiveSession(target: SessionInfo, scope: CoroutineScope) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    OpencodeClient.setSessionArchived(serverUrl, target.id, true)
                }
            }
            if (sessionId == target.id) newChat()
            refreshSessions(scope)
        }
    }

    fun unarchiveSession(target: SessionInfo, scope: CoroutineScope) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    OpencodeClient.setSessionArchived(serverUrl, target.id, false)
                }
            }
            refreshSessions(scope)
        }
    }

    suspend fun reloadAfterProviderChange() {
        withContext(Dispatchers.IO) {
            runCatching { OpencodeClient.disposeInstances(serverUrl) }
        }
        val available = withContext(Dispatchers.IO) {
            runCatching { OpencodeClient.listModels(serverUrl) }.getOrDefault(emptyList())
        }
        val current = model
        if (available.isNotEmpty() && current != null && available.none {
                it.providerID == current.providerID && it.modelID == current.modelID
            }
        ) {
            selectModel(null)
        }
    }

    private fun persistSession(id: String, scope: CoroutineScope) {
        if (!hasFolder) return
        val snapshot = messages.filterNot { it.queued }.map {
            SessionMessage(
                role = if (it.role == Role.User) "user" else "assistant",
                text = it.text,
            )
        }
        val title = sessionTitle
        scope.launch(Dispatchers.IO) {
            runCatching { vault.saveSession(id, title, snapshot) }
        }
    }

    private fun rememberAssistantIds(items: List<SessionMessage>) {
        seenAssistantIds.clear()
        seenAssistantIds += items.asSequence()
            .filter { it.role == "assistant" && it.id.isNotBlank() }
            .map { it.id }
    }

    private fun append(
        role: Role,
        text: String,
        isError: Boolean = false,
        serverId: String? = null,
        parts: List<ChatPart> = emptyList(),
        queued: Boolean = false,
    ): ChatMessage {
        val message = ChatMessage(
            id = nextId++,
            role = role,
            text = text,
            isError = isError,
            serverId = serverId,
            parts = parts,
            queued = queued,
        )
        appendMessage(message)
        return message
    }

    private fun appendMessage(message: ChatMessage) {
        val queueIndex = if (message.role == Role.Assistant) {
            messages.indexOfFirst { it.queued }
        } else {
            -1
        }
        messages = if (queueIndex >= 0) {
            messages.toMutableList().apply { add(queueIndex, message) }
        } else {
            messages + message
        }
    }

    fun forkFrom(message: ChatMessage, scope: CoroutineScope) {
        val session = sessionId ?: return
        val messageID = message.serverId ?: return
        scope.launch {
            try {
                val newSession = withContext(Dispatchers.IO) {
                    OpencodeClient.forkSession(serverUrl, session, messageID)
                }
                refreshSessions(scope)
                openSession(
                    SessionInfo(newSession, "$sessionTitle (fork)", System.currentTimeMillis()),
                    scope,
                )
            } catch (e: Exception) {
                append(Role.Assistant, e.message ?: "Could not fork this chat", isError = true)
            }
        }
    }

    fun revertTo(message: ChatMessage, scope: CoroutineScope) {
        val session = sessionId ?: return
        val messageID = message.serverId ?: return
        scope.launch {
            try {
                val revertedID = withContext(Dispatchers.IO) {
                    OpencodeClient.revertSession(serverUrl, session, messageID)
                }
                revertMessageID = revertedID?.takeIf { it.isNotBlank() } ?: messageID
                queuedPrompts.clear()
                loadingHistory = true
                liveMessageId = null
                val loaded = withContext(Dispatchers.IO) {
                    OpencodeClient.loadMessages(serverUrl, session)
                }
                rememberAssistantIds(loaded)
                messages = loaded.visibleUpTo(revertMessageID).map { item ->
                    ChatMessage(
                        id = nextId++,
                        role = if (item.role == "assistant") Role.Assistant else Role.User,
                        text = item.text,
                        serverId = item.id.takeIf { it.isNotBlank() },
                        parts = item.parts,
                    )
                }
                markRunning()
                refreshSessions(scope)
            } catch (e: Exception) {
                append(Role.Assistant, e.message ?: "Could not revert this chat", isError = true)
            } finally {
                loadingHistory = false
            }
        }
    }

    private fun List<SessionMessage>.visibleUpTo(revertID: String?): List<SessionMessage> {
        if (revertID.isNullOrBlank()) return this
        val index = indexOfFirst { it.id == revertID }
        return if (index < 0) this else take(index)
    }

    companion object {
        private val DEFAULT_AGENTS = listOf(
            AgentOption("build", "Build and edit code"),
            AgentOption("plan", "Plan before changing code"),
        )
    }
}
