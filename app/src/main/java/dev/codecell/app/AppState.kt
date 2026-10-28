package dev.codecell.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.codecell.app.data.AppConfig
import dev.codecell.app.data.ModelOption
import dev.codecell.app.data.OpencodeClient
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
    private var nextId = 1L

    var serverUrl by mutableStateOf(config.serverUrl)
    var userName by mutableStateOf(config.userName)
    var model by mutableStateOf(config.model)
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
    var sessionId by mutableStateOf<String?>(null)
    var sending by mutableStateOf(false)

    val modelLabel: String
        get() = model?.name?.takeIf { it.isNotBlank() } ?: "Server default"

    fun saveSettings(url: String, name: String) {
        serverUrl = url.trim().ifBlank { AppConfig.DEFAULT_SERVER }
        userName = name.trim()
        config.serverUrl = serverUrl
        config.userName = userName
    }

    fun selectModel(option: ModelOption?) {
        model = option
        config.model = option
    }

    fun newChat() {
        sessionId = null
        messages = emptyList()
    }

    fun send(text: String, scope: CoroutineScope) {
        val content = text.trim()
        if (content.isEmpty() || sending) return

        append(Role.User, content)
        sending = true
        scope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    val id = sessionId ?: OpencodeClient
                        .createSession(serverUrl, content.take(40).replace('\n', ' '))
                        .also { sessionId = it }
                    OpencodeClient.sendMessage(
                        baseUrl = serverUrl,
                        sessionId = id,
                        text = content,
                        providerID = model?.providerID,
                        modelID = model?.modelID,
                    )
                }
                append(Role.Assistant, reply)
            } catch (e: Exception) {
                append(Role.Assistant, e.message ?: "Request failed", isError = true)
            } finally {
                sending = false
            }
        }
    }

    private fun append(role: Role, text: String, isError: Boolean = false) {
        messages = messages + ChatMessage(nextId++, role, text, isError)
    }
}
