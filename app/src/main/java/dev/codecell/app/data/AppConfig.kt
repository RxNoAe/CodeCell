package dev.codecell.app.data

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.SecureRandom

data class ModelOption(
    val providerID: String,
    val modelID: String,
    val name: String,
    val variant: String? = null,
    val variants: List<String> = emptyList(),
    val free: Boolean = false,
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
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val revertMessageID: String? = null,
)

data class SessionMessage(
    val role: String,
    val text: String,
    val id: String = "",
    val parts: List<ChatPart> = emptyList(),
)

class AppConfig(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("codecell", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, DEFAULT_SERVER)?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER
        set(value) = prefs.edit().putString(KEY_SERVER, value).apply()

    var userName: String
        get() = prefs.getString(KEY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_NAME, value).apply()

    var serverPassword: String
        get() = prefs.getString(KEY_PASSWORD, null)?.takeIf { it.isNotBlank() }
            ?: newPassword().also { prefs.edit().putString(KEY_PASSWORD, it).apply() }
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var workspacePath: String
        get() = prefs.getString(KEY_WORKSPACE, null)?.takeIf { it.isNotBlank() }
            ?: defaultWorkspace()
        set(value) = prefs.edit().putString(KEY_WORKSPACE, value).apply()

    var treeUri: String?
        get() = prefs.getString(KEY_TREE, null)
        set(value) = prefs.edit().putString(KEY_TREE, value).apply()

    var folderPrompted: Boolean
        get() = prefs.getBoolean(KEY_PROMPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROMPTED, value).apply()

    var onboarded: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    var configuredProviders: Set<String>
        get() = prefs.getStringSet(KEY_PROVIDERS, emptySet()).orEmpty()
        set(value) = prefs.edit().putStringSet(KEY_PROVIDERS, value).apply()

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
            val variant = prefs.getString(KEY_MODEL_VARIANT, null)?.takeIf { it.isNotBlank() }
            return ModelOption(provider, id, label, variant = variant)
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) {
                    remove(KEY_PROVIDER)
                    remove(KEY_MODEL)
                    remove(KEY_MODEL_NAME)
                    remove(KEY_MODEL_VARIANT)
                } else {
                    putString(KEY_PROVIDER, value.providerID)
                    putString(KEY_MODEL, value.modelID)
                    putString(KEY_MODEL_NAME, value.name)
                    if (value.variant.isNullOrBlank()) remove(KEY_MODEL_VARIANT)
                    else putString(KEY_MODEL_VARIANT, value.variant)
                }
            }.apply()
        }

    private fun defaultWorkspace(): String =
        File(appContext.getExternalFilesDir(null) ?: appContext.filesDir, "workspace")
            .absolutePath

    private fun newPassword(): String {
        val bytes = ByteArray(18)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    companion object {
        const val DEFAULT_SERVER = "http://127.0.0.1:4096"
        private const val KEY_SERVER = "server_url"
        private const val KEY_PASSWORD = "server_password"
        private const val KEY_WORKSPACE = "workspace_path"
        private const val KEY_NAME = "user_name"
        private const val KEY_TREE = "tree_uri"
        private const val KEY_PROMPTED = "folder_prompted"
        private const val KEY_ONBOARDED = "onboarded"
        private const val KEY_PROVIDERS = "configured_providers"
        private const val KEY_AGENT = "agent_name"
        private const val KEY_AGENT_SYSTEM = "agent_system"
        private const val KEY_PROVIDER = "provider_id"
        private const val KEY_MODEL = "model_id"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_MODEL_VARIANT = "model_variant"
    }
}
