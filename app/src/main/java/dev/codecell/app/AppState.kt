package dev.codecell.app

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.codecell.app.data.AgentOption
import dev.codecell.app.data.AppConfig
import dev.codecell.app.data.ModelOption
import dev.codecell.app.data.OpencodeClient
import dev.codecell.app.data.SessionInfo
import dev.codecell.app.data.SessionMessage
import dev.codecell.app.data.Vault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Role { User, Assistant }

data class ChatMessage(
    val id: Long,
    val role: Role,
    val text: String,
    val isError: Boolean = false,
)

class AppState(context: Context) {

    private val config = AppConfig(context)
    private val vault = Vault(context, config)
    private var nextId = 1L

    var serverUrl by mutableStateOf(config.serverUrl)
    var userName by mutableStateOf(config.userName)
    var model by mutableStateOf(config.model)
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
    var sessionId by mutableStateOf<String?>(null)
    var sessionTitle by mutableStateOf("New chat")
    var sending by mutableStateOf(false)
    var loadingHistory by mutableStateOf(false)

    var folderLabel by mutableStateOf(vault.label())
    var hasFolder by mutableStateOf(vault.hasFolder())
    var folderPrompted by mutableStateOf(config.folderPrompted)

    var builtinAgents by mutableStateOf(DEFAULT_AGENTS)
    var customAgents by mutableStateOf(vault.loadAgents())
    var agentName by mutableStateOf(config.agentName)
    var agentSystem by mutableStateOf(config.agentSystem)

    var sessions by mutableStateOf<List<SessionInfo>>(emptyList())
    var sessionsLoading by mutableStateOf(false)

    val modelLabel: String
        get() = model?.name?.takeIf { it.isNotBlank() } ?: "Server default"

    val agentLabel: String
        get() = agentName.takeIf { it.isNotBlank() } ?: "build"

    fun agentOptions(): List<AgentOption> = builtinAgents + customAgents

    fun saveSettings(url: String, name: String) {
        serverUrl = url.trim().ifBlank { AppConfig.DEFAULT_SERVER }
        userName = name.trim()
        config.serverUrl = serverUrl
        config.userName = userName
    }

    fun attachFolder(uri: Uri) {
        val ok = vault.attach(uri)
        hasFolder = vault.hasFolder()
        folderLabel = vault.label()
        config.folderPrompted = true
        folderPrompted = true
        if (ok) customAgents = vault.loadAgents()
    }

    fun markFolderPrompted() {
        config.folderPrompted = true
        folderPrompted = true
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

    fun addCustomAgent(name: String, system: String) {
        val cleanName = name.trim()
        val cleanSystem = system.trim()
        if (cleanName.isBlank() || cleanSystem.isBlank()) return
        val option = AgentOption(cleanName, "Custom agent", cleanSystem)
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
    }

    fun refreshAgents(scope: CoroutineScope) {
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { OpencodeClient.listAgents(serverUrl) }
            }.onSuccess { fetched ->
                if (fetched.isNotEmpty()) builtinAgents = fetched
            }
        }
    }

    fun refreshSessions(scope: CoroutineScope) {
        sessionsLoading = true
        scope.launch {
            try {
                val fetched = withContext(Dispatchers.IO) { OpencodeClient.listSessions(serverUrl) }
                sessions = fetched.sortedByDescending { it.updated }
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
        loadingHistory = true
        scope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) {
                    OpencodeClient.loadMessages(serverUrl, target.id)
                }
                messages = loaded.map { message ->
                    ChatMessage(
                        id = nextId++,
                        role = if (message.role == "assistant") Role.Assistant else Role.User,
                        text = message.text,
                    )
                }
            } catch (e: Exception) {
                messages = listOf(
                    ChatMessage(nextId++, Role.Assistant, e.message ?: "Could not load session", true),
                )
            } finally {
                loadingHistory = false
            }
        }
    }

    fun send(text: String, scope: CoroutineScope) {
        val content = text.trim()
        if (content.isEmpty() || sending) return

        val isNew = sessionId == null
        val title = content.lineSequence().firstOrNull()?.trim()?.take(60)
            ?.takeIf { it.isNotBlank() } ?: "New chat"

        append(Role.User, content)
        sending = true
        scope.launch {
            try {
                val activeSession = sessionId ?: withContext(Dispatchers.IO) {
                    OpencodeClient.createSession(serverUrl, title)
                }.also {
                    sessionId = it
                    sessionTitle = title
                }

                val reply = withContext(Dispatchers.IO) {
                    OpencodeClient.sendMessage(
                        baseUrl = serverUrl,
                        sessionId = activeSession,
                        text = content,
                        providerID = model?.providerID,
                        modelID = model?.modelID,
                        agent = if (agentSystem == null) agentName else null,
                        system = agentSystem,
                    )
                }
                append(Role.Assistant, reply)
                persistSession(activeSession, scope)
            } catch (e: Exception) {
                append(Role.Assistant, e.message ?: "Request failed", isError = true)
            } finally {
                sending = false
            }
        }

        if (isNew) sessionTitle = title
    }

    private fun persistSession(id: String, scope: CoroutineScope) {
        if (!hasFolder) return
        val snapshot = messages.map {
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

    private fun append(role: Role, text: String, isError: Boolean = false) {
        messages = messages + ChatMessage(nextId++, role, text, isError)
    }

    companion object {
        private val DEFAULT_AGENTS = listOf(
            AgentOption("build", "Build and edit code"),
            AgentOption("plan", "Plan before changing code"),
        )
    }
}
