package dev.codecell.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Everything the app owns lives in a user-picked folder on shared storage.
 * A "CodeCell" directory is created inside the picked tree.
 */
class Vault(private val context: Context, private val config: AppConfig) {

    fun hasFolder(): Boolean {
        val tree = config.treeUri ?: return false
        return runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(tree))?.canWrite() == true
        }.getOrDefault(false)
    }

    fun label(): String {
        val tree = config.treeUri ?: return "Not set"
        val name = runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(tree))?.name
        }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: "CodeCell"
    }

    fun attach(tree: Uri): Boolean {
        return runCatching {
            context.contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            val root = DocumentFile.fromTreeUri(context, tree) ?: return false
            val target = if (root.name.equals(NAME, ignoreCase = true)) {
                root
            } else {
                root.findFile(NAME)?.takeIf { it.isDirectory }
                    ?: root.createDirectory(NAME)
                    ?: return false
            }
            config.treeUri = target.uri.toString()
            true
        }.getOrDefault(false)
    }

    fun saveAgents(agents: List<AgentOption>): Boolean {
        val array = JSONArray()
        agents.forEach { agent ->
            array.put(
                JSONObject()
                    .put("name", agent.name)
                    .put("description", agent.description)
                    .put("system", agent.system.orEmpty()),
            )
        }
        return runCatching {
            agentsFile().writeText(array.toString())
            true
        }.getOrDefault(false)
    }

    fun loadAgents(): List<AgentOption> {
        val raw = runCatching { agentsFile().takeIf { it.isFile }?.readText() }.getOrNull()
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val name = item.optString("name").trim()
                    val system = item.optString("system")
                    val description = item.optString("description")
                        .takeIf { it.isNotBlank() } ?: "Custom agent"
                    if (name.isNotBlank() && system.isNotBlank()) {
                        add(AgentOption(name = name, description = description, system = system))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveSession(sessionId: String, title: String, messages: List<SessionMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("role", message.role)
                    .put("text", message.text),
            )
        }
        val json = JSONObject()
            .put("id", sessionId)
            .put("title", title)
            .put("messages", array)
            .toString()
        write("sessions/$sessionId.json", json)
    }

    private fun agentsFile(): File = File(context.filesDir, "agents.json")

    private fun write(relativePath: String, text: String): Boolean {
        val dir = directoryOf(relativePath.substringBeforeLast('/', "")) ?: return false
        val name = relativePath.substringAfterLast('/')
        val file = dir.findFile(name) ?: dir.createFile("application/json", name) ?: return false
        return runCatching {
            context.contentResolver.openOutputStream(file.uri, "wt")?.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            }
            true
        }.getOrDefault(false)
    }

    private fun read(relativePath: String): String? {
        val dir = directoryOf(relativePath.substringBeforeLast('/', "")) ?: return null
        val file = dir.findFile(relativePath.substringAfterLast('/')) ?: return null
        return runCatching {
            context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
    }

    private fun directoryOf(relative: String): DocumentFile? {
        val tree = config.treeUri ?: return null
        var current = runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(tree))
        }.getOrNull() ?: return null
        if (relative.isBlank()) return current
        for (part in relative.split('/')) {
            if (part.isBlank()) continue
            current = current.findFile(part)?.takeIf { it.isDirectory }
                ?: current.createDirectory(part)
                ?: return null
        }
        return current
    }

    companion object {
        const val NAME = "CodeCell"
    }
}
