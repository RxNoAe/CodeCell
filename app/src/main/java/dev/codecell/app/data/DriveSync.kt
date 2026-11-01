package dev.codecell.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backs up the embedded opencode sessions to the hidden Google Drive
 * app-data folder and restores them. Only the app can see these files, and
 * Drive encrypts them at rest. No backend is involved.
 */
object DriveSync {

    private const val API = "https://www.googleapis.com"
    private const val BACKUP_PREFIX = "codecell-backup-"
    private const val SETTINGS_ENTRY = "codecell-settings.json"
    private const val KEEP_BACKUPS = 5
    private val SKIP_DIRS = setOf("log", "shell", "repos", "node_modules")

    data class BackupInfo(
        val id: String,
        val name: String,
        val modified: String,
    )

    fun buildBackup(context: Context, config: AppConfig): File {
        val home = LocalServer.homeDir(context)
        val out = File(context.cacheDir, "codecell-backup.zip")
        if (out.exists()) out.delete()
        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            addTree(zip, home, File(home, ".local/share/opencode"))
            addTree(zip, home, File(home, ".config/opencode"))
            zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
            zip.write(settingsJson(config).toByteArray())
            zip.closeEntry()
        }
        return out
    }

    fun upload(token: String, file: File): BackupInfo {
        val name = "$BACKUP_PREFIX${System.currentTimeMillis()}.zip"
        val boundary = "codecell" + UUID.randomUUID().toString().replace("-", "")
        val metadata = JSONObject()
            .put("name", name)
            .put("parents", JSONArray().put("appDataFolder"))
        val connection = open(
            "$API/upload/drive/v3/files?uploadType=multipart&fields=id,name,modifiedTime",
            token,
        )
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "multipart/related; boundary=$boundary",
            )
            connection.outputStream.buffered().use { out ->
                fun write(text: String) = out.write(text.toByteArray())
                write("--$boundary\r\n")
                write("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                write(metadata.toString())
                write("\r\n--$boundary\r\n")
                write("Content-Type: application/zip\r\n\r\n")
                file.inputStream().use { it.copyTo(out) }
                write("\r\n--$boundary--\r\n")
            }
            val code = connection.responseCode
            val text = readBody(connection)
            if (code !in 200..299) {
                throw IllegalStateException("Google Drive upload failed ($code): $text")
            }
            val json = JSONObject(text)
            val info = BackupInfo(
                id = json.optString("id"),
                name = json.optString("name").ifBlank { name },
                modified = json.optString("modifiedTime"),
            )
            runCatching { prune(token) }
            return info
        } finally {
            connection.disconnect()
        }
    }

    fun findLatest(token: String): BackupInfo? = listAll(token).firstOrNull()

    fun download(token: String, id: String): ByteArray {
        val connection = open("$API/drive/v3/files/$id?alt=media", token)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Google Drive download failed ($code).")
            }
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    fun restore(context: Context, config: AppConfig, bytes: ByteArray) {
        val home = LocalServer.homeDir(context)
        val tmp = File(context.cacheDir, "restore-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val rootPath = tmp.canonicalPath + File.separator
            ZipInputStream(bytes.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val target = File(tmp, entry.name)
                    val canonical = target.canonicalPath
                    if (canonical != tmp.canonicalPath && !canonical.startsWith(rootPath)) {
                        entry = zip.nextEntry
                        continue
                    }
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { zip.copyTo(it) }
                    }
                    entry = zip.nextEntry
                }
            }
            val dataDir = File(home, ".local/share/opencode")
            dataDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".db-wal") || file.name.endsWith(".db-shm")) {
                    file.delete()
                }
            }
            applySettings(config, File(tmp, SETTINGS_ENTRY))
            tmp.listFiles()?.forEach { child ->
                if (child.name == SETTINGS_ENTRY) return@forEach
                copyTree(child, File(home, child.name))
            }
        } finally {
            tmp.deleteRecursively()
        }
    }

    private fun listAll(token: String): List<BackupInfo> {
        val connection = open(
            "$API/drive/v3/files?spaces=appDataFolder" +
                "&fields=files(id,name,modifiedTime)&orderBy=modifiedTime%20desc&pageSize=100",
            token,
        )
        try {
            val code = connection.responseCode
            val text = readBody(connection)
            if (code !in 200..299) {
                throw IllegalStateException("Google Drive list failed ($code): $text")
            }
            val files = JSONObject(text).optJSONArray("files") ?: return emptyList()
            val result = mutableListOf<BackupInfo>()
            for (i in 0 until files.length()) {
                val item = files.optJSONObject(i) ?: continue
                val name = item.optString("name")
                if (!name.startsWith(BACKUP_PREFIX)) continue
                result += BackupInfo(
                    id = item.optString("id"),
                    name = name,
                    modified = item.optString("modifiedTime"),
                )
            }
            return result
        } finally {
            connection.disconnect()
        }
    }

    private fun prune(token: String) {
        val backups = listAll(token)
        backups.drop(KEEP_BACKUPS).forEach { old ->
            val connection = open("$API/drive/v3/files/${old.id}", token)
            try {
                connection.requestMethod = "DELETE"
                connection.responseCode
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun addTree(zip: ZipOutputStream, home: File, current: File) {
        if (!current.exists()) return
        if (current.isDirectory) {
            if (current != home && current.name in SKIP_DIRS) return
            current.listFiles()?.forEach { addTree(zip, home, it) }
            return
        }
        if (current.name == "auth.json") return
        val name = current.relativeTo(home).invariantSeparatorsPath
        zip.putNextEntry(ZipEntry(name))
        current.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun copyTree(source: File, target: File) {
        if (source.isDirectory) {
            target.mkdirs()
            source.listFiles()?.forEach { copyTree(it, File(target, it.name)) }
        } else {
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
        }
    }

    private fun settingsJson(config: AppConfig): String {
        val json = JSONObject()
        json.put("userName", config.userName)
        json.put("googleSignedIn", config.googleSignedIn)
        json.put("googleEmail", config.googleEmail)
        json.put("googleAvatar", config.googleAvatar)
        json.put("agentName", config.agentName)
        json.put("agentSystem", config.agentSystem ?: "")
        val model = config.model
        if (model != null) {
            json.put("modelProvider", model.providerID)
            json.put("modelID", model.modelID)
            json.put("modelName", model.name)
            json.put("modelVariant", model.variant ?: "")
        }
        json.put("configuredProviders", JSONArray(config.configuredProviders.toList()))
        return json.toString()
    }

    private fun applySettings(config: AppConfig, file: File) {
        if (!file.isFile) return
        val json = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return
        if (json.has("userName")) config.userName = json.optString("userName")
        if (json.has("googleSignedIn")) config.googleSignedIn = json.optBoolean("googleSignedIn")
        if (json.has("googleEmail")) config.googleEmail = json.optString("googleEmail")
        if (json.has("googleAvatar")) config.googleAvatar = json.optString("googleAvatar")
        val agent = json.optString("agentName")
        if (agent.isNotBlank()) config.agentName = agent
        config.agentSystem = json.optString("agentSystem").ifBlank { null }
        val provider = json.optString("modelProvider")
        val modelId = json.optString("modelID")
        if (provider.isNotBlank() && modelId.isNotBlank()) {
            config.model = ModelOption(
                providerID = provider,
                modelID = modelId,
                name = json.optString("modelName").ifBlank { modelId },
                variant = json.optString("modelVariant").ifBlank { null },
            )
        }
        val providers = json.optJSONArray("configuredProviders")
        if (providers != null) {
            val set = mutableSetOf<String>()
            for (i in 0 until providers.length()) {
                val value = providers.optString(i)
                if (value.isNotBlank()) set += value
            }
            config.configuredProviders = set
        }
    }

    private fun open(url: String, token: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20_000
        connection.readTimeout = 120_000
        connection.setRequestProperty("Authorization", "Bearer $token")
        return connection
    }

    private fun readBody(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }
}