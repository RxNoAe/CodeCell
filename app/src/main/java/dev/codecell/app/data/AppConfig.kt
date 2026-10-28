package dev.codecell.app.data

import android.content.Context

data class ModelOption(
    val providerID: String,
    val modelID: String,
    val name: String,
)

data class AgentOption(
    val name: String,
    val description: String,
    val system: String? = null,
) {
    val isCustom: Boolean get() = system != null
}

data class SessionInfo(
    val id: String,
    val title: String,
    val updated: Long,
)

data class SessionMessage(
    val role: String,
    val text: String,
)

class AppConfig(context: Context) {

    private val prefs = context.getSharedPreferences("codecell", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, DEFAULT_SERVER)?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER
        set(value) = prefs.edit().putString(KEY_SERVER, value).apply()

    var userName: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var treeUri: String?
        get() = prefs.getString(KEY_TREE, null)
        set(value) = prefs.edit().putString(KEY_TREE, value).apply()

    var folderPrompted: Boolean
        get() = prefs.getBoolean(KEY_PROMPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROMPTED, value).apply()

    var agentName: String
        get() = prefs.getString(KEY_AGENT, "build")?.takeIf { it.isNotBlank() } ?: "build"
        set(value) = prefs.edit().putString(KEY_AGENT, value).apply()

    var agentSystem: String?
        get() = prefs.getString(KEY_AGENT_SYSTEM, null)
        set(value) = prefs.edit().putString(KEY_AGENT_SYSTEM, value).apply()

    var model: ModelOption?
        get() {
            val provider = prefs.getString(KEY_PROVIDER, null)
            val id = prefs.getString(KEY_MODEL, null)
            if (provider.isNullOrBlank() || id.isNullOrBlank()) return null
            val label = prefs.getString(KEY_MODEL_NAME, null)?.takeIf { it.isNotBlank() } ?: id
            return ModelOption(provider, id, label)
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_PROVIDER)
                    remove(KEY_MODEL)
                    remove(KEY_MODEL_NAME)
                } else {
                    putString(KEY_PROVIDER, value.providerID)
                    putString(KEY_MODEL, value.modelID)
                    putString(KEY_MODEL_NAME, value.name)
                }
            }.apply()
        }

    companion object {
        const val DEFAULT_SERVER = "http://127.0.0.1:4096"
        private const val KEY_SERVER = "server_url"
        private const val KEY_NAME = "user_name"
        private const val KEY_TREE = "tree_uri"
        private const val KEY_PROMPTED = "folder_prompted"
        private const val KEY_AGENT = "agent_name"
        private const val KEY_AGENT_SYSTEM = "agent_system"
        private const val KEY_PROVIDER = "provider_id"
        private const val KEY_MODEL = "model_id"
        private const val KEY_MODEL_NAME = "model_name"
    }
}
