package dev.codecell.app.data

import android.util.Base64
import android.util.JsonReader
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.URLEncoder

class OpencodeException(message: String) : Exception(message)

/**
 * Minimal client for the opencode headless server (opencode serve).
 * Uses only HttpURLConnection + org.json so the APK stays tiny.
 */
object OpencodeClient {

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 300_000
    private const val HEALTH_TIMEOUT_MS = 4_000

    @Volatile
    private var authHeader: String? = null

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    fun cancelActiveRequest() {
        activeConnection?.disconnect()
    }

    fun setCredentials(username: String?, password: String?) {
        authHeader = if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            val raw = "$username:$password".toByteArray(Charsets.UTF_8)
            "Basic " + Base64.encodeToString(raw, Base64.NO_WRAP)
        } else {
            null
        }
    }

    fun health(baseUrl: String, timeoutMs: Int = HEALTH_TIMEOUT_MS): String {
        val raw = request("GET", url(baseUrl, "/global/health"), null, timeoutMs)
        val version = runCatching { JSONObject(raw).optString("version") }.getOrDefault("")
        return if (version.isBlank()) "Connected" else "Connected - v$version"
    }

    fun createSession(baseUrl: String, title: String): String {
        val body = JSONObject().put("title", title.ifBlank { "New chat" }).toString()
        val raw = request("POST", url(baseUrl, "/session"), body)
        val id = JSONObject(raw).optString("id")
        if (id.isBlank()) throw OpencodeException("Server did not return a session id")
        return id
    }

    fun listSessions(baseUrl: String): List<SessionInfo> {
        val raw = request("GET", url(baseUrl, "/session"), null)
        val array = JSONArray(raw)
        val result = mutableListOf<SessionInfo>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            val title = item.optString("title").takeIf { it.isNotBlank() } ?: "New chat"
            val time = item.optJSONObject("time")
            val updated = time?.optLong("updated") ?: 0L
            val archived = (time?.optLong("archived") ?: 0L) > 0L
            val pinned = item.optJSONObject("metadata")?.optBoolean("pinned") ?: false
            result += SessionInfo(id, title, updated, pinned, archived)
        }
        return result
    }

    fun loadMessages(baseUrl: String, sessionId: String): List<SessionMessage> {
        val raw = request("GET", url(baseUrl, "/session/$sessionId/message"), null)
        val array = JSONArray(raw)
        val result = mutableListOf<SessionMessage>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val role = item.optJSONObject("info")?.optString("role")
                ?.takeIf { it == "user" || it == "assistant" } ?: continue
            val parts = parseParts(item.optJSONArray("parts"))
            val text = joinText(item.optJSONArray("parts"))
            val id = item.optJSONObject("info")?.optString("id").orEmpty()
            if (parts.isNotEmpty() || text.isNotBlank()) {
                result += SessionMessage(role, text, id, parts)
            }
        }
        return result
    }

    fun listAgents(baseUrl: String): List<AgentOption> {
        val raw = request("GET", url(baseUrl, "/agent"), null)
        val array = JSONArray(raw)
        val result = linkedMapOf<String, AgentOption>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val name = item.optString("name").takeIf { it.isNotBlank() } ?: continue
            if (item.optString("mode") == "subagent") continue
            val description = item.optString("description")
                .takeIf { it.isNotBlank() } ?: "Agent"
            result[name] = AgentOption(name, description)
        }
        return result.values.toList()
    }

    fun sendMessage(
        baseUrl: String,
        sessionId: String,
        text: String,
        providerID: String?,
        modelID: String?,
        variant: String? = null,
        agent: String?,
        system: String?,
        attachments: List<Attachment> = emptyList(),
    ): SentMessage {
        val payload = JSONObject().apply {
            if (!providerID.isNullOrBlank() && !modelID.isNullOrBlank()) {
                put(
                    "model",
                    JSONObject()
                        .put("providerID", providerID)
                        .put("modelID", modelID),
                )
            }
            if (!variant.isNullOrBlank()) put("variant", variant)
            if (!agent.isNullOrBlank()) put("agent", agent)
            if (!system.isNullOrBlank()) put("system", system)
            val parts = JSONArray()
            if (text.isNotBlank()) {
                parts.put(JSONObject().put("type", "text").put("text", text))
            }
            attachments.forEach { attachment ->
                parts.put(
                    JSONObject()
                        .put("type", "file")
                        .put("mime", attachment.mime)
                        .put("filename", attachment.name)
                        .put(
                            "url",
                            "data:${attachment.mime};base64," +
                                Base64.encodeToString(attachment.bytes, Base64.NO_WRAP),
                        ),
                )
            }
            put("parts", parts)
        }
        val raw = request(
            "POST",
            url(baseUrl, "/session/$sessionId/message"),
            payload.toString(),
        )
        val info = runCatching { JSONObject(raw).optJSONObject("info") }.getOrNull()
        val root = runCatching { JSONObject(raw) }.getOrNull()
        return SentMessage(
            text = extractReply(raw),
            assistantID = info?.optString("id")?.takeIf { it.isNotBlank() },
            userID = info?.optString("parentID")?.takeIf { it.isNotBlank() },
            parts = parseParts(root?.optJSONArray("parts")),
        )
    }

    fun forkSession(baseUrl: String, sessionId: String, messageID: String): String {
        val body = JSONObject().put("messageID", messageID).toString()
        val raw = request("POST", url(baseUrl, "/session/$sessionId/fork"), body)
        val id = JSONObject(raw).optString("id")
        if (id.isBlank()) throw OpencodeException("Server did not return a forked session id")
        return id
    }

    fun revertSession(baseUrl: String, sessionId: String, messageID: String) {
        val body = JSONObject().put("messageID", messageID).toString()
        request("POST", url(baseUrl, "/session/$sessionId/revert"), body)
    }

    fun listProviders(baseUrl: String): ProviderCatalog {
        val target = url(baseUrl, "/provider")
        val connection = openConnection(target, 30_000)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val text = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw OpencodeException(errorMessage(code, text))
            }
            val all = linkedMapOf<String, ProviderInfo>()
            val connected = mutableSetOf<String>()
            JsonReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "all" -> {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                reader.beginObject()
                                var id = ""
                                var name = ""
                                while (reader.hasNext()) {
                                    when (reader.nextName()) {
                                        "id" -> id = reader.nextString()
                                        "name" -> name = reader.nextString()
                                        else -> reader.skipValue()
                                    }
                                }
                                reader.endObject()
                                if (id.isNotBlank()) {
                                    all[id] = ProviderInfo(id, name.ifBlank { id })
                                }
                            }
                            reader.endArray()
                        }

                        "connected" -> {
                            reader.beginArray()
                            while (reader.hasNext()) connected += reader.nextString()
                            reader.endArray()
                        }

                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
            return ProviderCatalog(
                all = all.values.sortedBy { it.name.lowercase() },
                connected = connected,
            )
        } catch (e: OpencodeException) {
            throw e
        } catch (e: IOException) {
            throw OpencodeException("Could not load providers. Is the server running?")
        } finally {
            connection.disconnect()
        }
    }

    fun listAuthMethods(baseUrl: String): Map<String, List<ProviderAuthMethod>> {
        val raw = request("GET", url(baseUrl, "/provider/auth"), null, 15_000)
        val root = JSONObject(raw)
        val result = linkedMapOf<String, List<ProviderAuthMethod>>()
        val providers = root.keys()
        while (providers.hasNext()) {
            val providerID = providers.next()
            val array = root.optJSONArray(providerID) ?: continue
            val methods = mutableListOf<ProviderAuthMethod>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val prompts = mutableListOf<ProviderPrompt>()
                val promptArray = item.optJSONArray("prompts")
                if (promptArray != null) {
                    for (p in 0 until promptArray.length()) {
                        val prompt = promptArray.optJSONObject(p) ?: continue
                        val key = prompt.optString("key")
                        if (key.isBlank()) continue
                        prompts += ProviderPrompt(
                            key = key,
                            message = prompt.optString("message").ifBlank { key },
                            placeholder = prompt.optString("placeholder").takeIf { it.isNotBlank() },
                            kind = prompt.optString("type").ifBlank { "text" },
                        )
                    }
                }
                methods += ProviderAuthMethod(
                    type = item.optString("type"),
                    label = item.optString("label").ifBlank { item.optString("type") },
                    prompts = prompts,
                )
            }
            if (methods.isNotEmpty()) result[providerID] = methods
        }
        return result
    }

    fun setApiKey(
        baseUrl: String,
        providerID: String,
        key: String,
        extra: Map<String, String> = emptyMap(),
    ) {
        val body = JSONObject().put("type", "api").put("key", key.trim())
        extra.forEach { (k, v) -> if (v.isNotBlank()) body.put(k, v.trim()) }
        request("PUT", url(baseUrl, "/auth/${encode(providerID)}"), body.toString(), 20_000)
    }

    fun deleteProviderAuth(baseUrl: String, providerID: String) {
        request("DELETE", url(baseUrl, "/auth/${encode(providerID)}"), null, 20_000)
    }

    fun disposeInstances(baseUrl: String) {
        request("POST", url(baseUrl, "/instance/dispose"), null, 20_000)
    }

    fun deleteSession(baseUrl: String, sessionId: String) {
        request("DELETE", url(baseUrl, "/session/${encode(sessionId)}"), null, 20_000)
    }

    fun abortSession(baseUrl: String, sessionId: String) {
        request("POST", url(baseUrl, "/session/${encode(sessionId)}/abort"), null, 20_000)
    }

    fun listQuestions(baseUrl: String): List<QuestionRequest> {
        val raw = request("GET", url(baseUrl, "/question"), null, 15_000)
        val array = JSONArray(raw)
        val result = mutableListOf<QuestionRequest>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val sessionID = item.optString("sessionID")
            if (id.isBlank() || sessionID.isBlank()) continue
            val questions = parseQuestions(item.optJSONArray("questions"))
            if (questions.isNotEmpty()) result += QuestionRequest(id, sessionID, questions)
        }
        return result
    }

    fun replyQuestion(
        baseUrl: String,
        requestId: String,
        answers: List<List<String>>,
    ) {
        val payload = JSONArray()
        answers.forEach { answer -> payload.put(JSONArray(answer)) }
        val body = JSONObject().put("answers", payload).toString()
        request("POST", url(baseUrl, "/question/${encode(requestId)}/reply"), body, 20_000)
    }

    fun rejectQuestion(baseUrl: String, requestId: String) {
        request("POST", url(baseUrl, "/question/${encode(requestId)}/reject"), null, 20_000)
    }

    fun updateSessionTitle(baseUrl: String, sessionId: String, title: String) {
        patchSession(baseUrl, sessionId, JSONObject().put("title", title.trim()))
    }

    fun setSessionPinned(baseUrl: String, sessionId: String, pinned: Boolean) {
        patchSession(
            baseUrl,
            sessionId,
            JSONObject().put("metadata", JSONObject().put("pinned", pinned)),
        )
    }

    fun setSessionArchived(baseUrl: String, sessionId: String, archived: Boolean) {
        patchSession(
            baseUrl,
            sessionId,
            JSONObject().put(
                "time",
                JSONObject().put(
                    "archived",
                    if (archived) System.currentTimeMillis() else 0L,
                ),
            ),
        )
    }

    private fun patchSession(baseUrl: String, sessionId: String, body: JSONObject) {
        val uri = try {
            URI(baseUrl.trim())
        } catch (e: Exception) {
            throw OpencodeException("Invalid server URL")
        }
        val host = uri.host ?: throw OpencodeException("Invalid server URL")
        val port = if (uri.port > 0) uri.port else 80
        val path = "/session/${encode(sessionId)}"
        val payload = body.toString().toByteArray(Charsets.UTF_8)
        val header = buildString {
            append("PATCH $path HTTP/1.1\r\n")
            append("Host: $host:$port\r\n")
            authHeader?.let { append("Authorization: $it\r\n") }
            append("Content-Type: application/json; charset=utf-8\r\n")
            append("Content-Length: ${payload.size}\r\n")
            append("Connection: close\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)

        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = 20_000
                val out = socket.getOutputStream()
                out.write(header)
                out.write(payload)
                out.flush()
                val status = socket.getInputStream().bufferedReader().readLine().orEmpty()
                val code = status.split(' ').getOrNull(1)?.toIntOrNull() ?: 0
                if (code !in 200..299) {
                    throw OpencodeException("Could not update chat (HTTP $code)")
                }
            }
        } catch (e: OpencodeException) {
            throw e
        } catch (e: IOException) {
            throw OpencodeException("Cannot reach the opencode server. Try restarting it in Settings.")
        }
    }

    fun listModels(baseUrl: String): List<ModelOption> {
        val raw = request("GET", url(baseUrl, "/config/providers"), null)
        val root = JSONObject(raw)
        val providers = root.optJSONArray("providers") ?: return emptyList()
        val result = mutableListOf<ModelOption>()
        for (i in 0 until providers.length()) {
            val provider = providers.optJSONObject(i) ?: continue
            val providerID = provider.optString("id")
            if (providerID.isBlank()) continue
            val models = provider.optJSONObject("models") ?: continue
            val keys = models.keys()
            while (keys.hasNext()) {
                val modelID = keys.next()
                val model = models.optJSONObject(modelID)
                val label = model?.optString("name")?.takeIf { it.isNotBlank() } ?: modelID
                val variants = model?.optJSONObject("variants")
                    ?.keys()?.asSequence()?.toList().orEmpty()
                val cost = model?.optJSONObject("cost")
                val free = (cost != null &&
                    cost.optDouble("input", -1.0) == 0.0 &&
                    cost.optDouble("output", -1.0) == 0.0) ||
                    modelID.contains("free", ignoreCase = true)
                result += ModelOption(
                    providerID = providerID,
                    modelID = modelID,
                    name = label,
                    variants = variants,
                    free = free,
                )
            }
        }
        return result.sortedWith(
            compareByDescending<ModelOption> { it.free }
                .thenBy { it.name.lowercase() },
        )
    }

    private fun parseQuestions(array: JSONArray?): List<QuestionInfo> {
        if (array == null) return emptyList()
        val result = mutableListOf<QuestionInfo>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val question = item.optString("question")
            if (question.isBlank()) continue
            val options = mutableListOf<QuestionOption>()
            val optionArray = item.optJSONArray("options")
            if (optionArray != null) {
                for (o in 0 until optionArray.length()) {
                    val option = optionArray.optJSONObject(o) ?: continue
                    val label = option.optString("label")
                    if (label.isBlank()) continue
                    options += QuestionOption(label, option.optString("description"))
                }
            }
            result += QuestionInfo(
                question = question,
                header = item.optString("header").ifBlank { "Question" },
                options = options,
                multiple = item.optBoolean("multiple", false),
                custom = item.optBoolean("custom", true),
            )
        }
        return result
    }

    private fun parseAnswers(array: JSONArray?): List<List<String>> {
        if (array == null) return emptyList()
        val result = mutableListOf<List<String>>()
        for (i in 0 until array.length()) {
            val answer = array.optJSONArray(i)
            if (answer == null) {
                result.add(emptyList())
                continue
            }
            val labels = mutableListOf<String>()
            for (j in 0 until answer.length()) {
                val label = answer.optString(j)
                if (label.isNotBlank()) labels += label
            }
            result.add(labels)
        }
        return result
    }

    private fun parseParts(parts: JSONArray?): List<ChatPart> {
        if (parts == null) return emptyList()
        val result = mutableListOf<ChatPart>()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val type = part.optString("type")
            val id = part.optString("id")
            when (type) {
                "text" -> {
                    if (part.optBoolean("ignored")) continue
                    val text = part.optString("text")
                    if (text.isNotBlank()) result += ChatPart(id = id, type = type, text = text)
                }

                "reasoning" -> {
                    val text = part.optString("text")
                    if (text.isNotBlank()) result += ChatPart(id = id, type = type, text = text)
                }

                "tool" -> {
                    val toolName = part.optString("tool")
                    val state = part.optJSONObject("state")
                    val input = state?.opt("input")
                    val inputObject = when (input) {
                        is JSONObject -> input
                        is String -> runCatching { JSONObject(input) }.getOrNull()
                        else -> null
                    }
                    val inputText = when (input) {
                        is JSONObject -> input.toString(2)
                        null, JSONObject.NULL -> ""
                        else -> input.toString()
                    }
                    val metadataObject = state?.optJSONObject("metadata")
                    val output = state?.optString("output").orEmpty()
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?: metadataObject?.optString("output").orEmpty()
                    val error = state?.optString("error").orEmpty()
                    val metadata = metadataObject?.toString(2).orEmpty()
                    val progress = if (toolName == "bash" || toolName == "shell") {
                        parseToolProgress(output)
                    } else {
                        null
                    }
                    result += ChatPart(
                        id = id,
                        type = type,
                        tool = toolName,
                        status = state?.optString("status").orEmpty(),
                        title = state?.optString("title").orEmpty(),
                        input = inputText,
                        output = output,
                        error = error,
                        detail = metadata,
                        progress = progress?.fraction,
                        progressLabel = progress?.label.orEmpty(),
                        questions = if (toolName == "question") {
                            parseQuestions(inputObject?.optJSONArray("questions"))
                        } else {
                            emptyList()
                        },
                        answers = if (toolName == "question") {
                            parseAnswers(metadataObject?.optJSONArray("answers"))
                        } else {
                            emptyList()
                        },
                    )
                }

                "patch" -> {
                    val files = mutableListOf<String>()
                    val array = part.optJSONArray("files")
                    if (array != null) {
                        for (f in 0 until array.length()) {
                            val file = array.optString(f)
                            if (file.isNotBlank()) files += file
                        }
                    }
                    result += ChatPart(id = id, type = type, files = files)
                }

                "step-finish" -> {
                    val tokens = part.optJSONObject("tokens")
                    val total = tokens?.optInt("total")?.takeIf { it > 0 }
                        ?: ((tokens?.optInt("input") ?: 0) + (tokens?.optInt("output") ?: 0))
                    val cost = part.optDouble("cost", 0.0)
                    val reason = part.optString("reason")
                    val bits = mutableListOf<String>()
                    if (reason.isNotBlank()) bits += reason
                    if (total > 0) bits += "$total tokens"
                    if (cost > 0) bits += "$" + String.format(java.util.Locale.US, "%.4f", cost)
                    result += ChatPart(id = id, type = type, text = bits.joinToString(" · "))
                }

                "retry" -> {
                    val attempt = part.optInt("attempt")
                    val error = part.optJSONObject("error")
                    val message = error?.optJSONObject("data")?.optString("message")
                        ?.takeIf { it.isNotBlank() }
                        ?: error?.optString("message").orEmpty()
                    result += ChatPart(
                        id = id,
                        type = type,
                        text = message,
                        detail = if (attempt > 0) "Attempt $attempt" else "",
                    )
                }

                "file" -> {
                    result += ChatPart(
                        id = id,
                        type = type,
                        title = part.optString("filename").ifBlank { "File" },
                        text = part.optString("mime"),
                    )
                }

                "subtask" -> {
                    result += ChatPart(
                        id = id,
                        type = type,
                        title = part.optString("description"),
                        text = part.optString("prompt"),
                        detail = part.optString("agent"),
                    )
                }

                "agent" -> {
                    result += ChatPart(id = id, type = type, title = part.optString("name"))
                }

                "compaction" -> {
                    result += ChatPart(id = id, type = type, text = "Context compacted")
                }
            }
        }
        return result
    }

    private fun joinText(parts: JSONArray?): String {
        if (parts == null) return ""
        val builder = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.optString("type") == "text") {
                val chunk = part.optString("text")
                if (chunk.isNotBlank()) {
                    if (builder.isNotEmpty()) builder.append("\n\n")
                    builder.append(chunk)
                }
            }
        }
        return builder.toString()
    }

    private fun extractReply(raw: String): String {
        val root = runCatching { JSONObject(raw) }
            .getOrElse { throw OpencodeException(raw.take(300).ifBlank { "Empty response" }) }

        val text = joinText(root.optJSONArray("parts"))
        if (text.isNotBlank()) return text

        val info = root.optJSONObject("info")
        val error = info?.optJSONObject("error")
        if (error != null) {
            val message = error.optJSONObject("data")?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: error.optString("message").takeIf { it.isNotBlank() }
                ?: error.optString("name")
            if (message.contains("file part media type", ignoreCase = true)) {
                throw OpencodeException(
                    "This model can't read that file type. Remove the attachment or pick another model.",
                )
            }
            throw OpencodeException(message.ifBlank { "The model returned an error" })
        }
        return "Done."
    }

    private data class ToolProgress(val fraction: Float?, val label: String)

    private val ansiEscape = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
    private val percentValue = Regex("""(\d{1,3}(?:\.\d+)?)\s*%""")

    private fun parseToolProgress(output: String): ToolProgress {
        val lines = output
            .replace('\r', '\n')
            .replace(ansiEscape, "")
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val line = lines.lastOrNull().orEmpty()
        val lower = line.lowercase()
        val installing = lower.contains("setting up") ||
            lower.contains("unpacking") ||
            lower.contains("selecting previously") ||
            lower.contains("preparing to unpack") ||
            lower.contains("reading database") ||
            lower.contains("fetched")
        val downloading = lower.contains("get:") ||
            lower.contains("downloading")
        val building = lower.contains("executing") ||
            lower.contains("building") ||
            lower.contains("configuring") ||
            lower.contains("> task")
        val label = when {
            installing -> "Installing"
            downloading -> "Downloading"
            building -> "Building"
            else -> "Working"
        }
        val fraction = if (installing) {
            null
        } else {
            percentValue.find(line)?.groupValues?.get(1)
                ?.toFloatOrNull()
                ?.div(100f)
                ?.coerceIn(0f, 1f)
        }
        return ToolProgress(fraction, label)
    }

    private fun url(baseUrl: String, path: String): String =
        baseUrl.trim().trimEnd('/') + path

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun openConnection(target: String, readTimeoutMs: Int): HttpURLConnection {
        val connection = try {
            (URL(target).openConnection() as HttpURLConnection)
        } catch (e: Exception) {
            throw OpencodeException("Invalid server URL: $target")
        }
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("Accept", "application/json")
        authHeader?.let { connection.setRequestProperty("Authorization", it) }
        return connection
    }

    private fun request(
        method: String,
        target: String,
        body: String?,
        readTimeoutMs: Int = READ_TIMEOUT_MS,
    ): String {
        val connection = openConnection(target, readTimeoutMs)
        activeConnection = connection

        try {
            connection.requestMethod = method
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                throw OpencodeException(errorMessage(code, text))
            }
            return text
        } catch (e: OpencodeException) {
            throw e
        } catch (e: IOException) {
            throw OpencodeException(
                "Cannot reach the opencode server. Try restarting it in Settings.",
            )
        } finally {
            if (activeConnection === connection) activeConnection = null
            connection.disconnect()
        }
    }

    private fun errorMessage(code: Int, raw: String): String {
        if (raw.contains("file part media type", ignoreCase = true)) {
            return "This model can't read that file type. Remove the attachment or pick another model."
        }
        val fallback = if (raw.isBlank()) "HTTP $code" else raw.take(300)
        val parsed = runCatching { JSONObject(raw) }.getOrNull() ?: return fallback
        val error = parsed.optJSONObject("error")
        val message = error?.optJSONObject("data")?.optString("message")
            ?.takeIf { it.isNotBlank() }
            ?: error?.optString("message")?.takeIf { it.isNotBlank() }
            ?: parsed.optString("message").takeIf { it.isNotBlank() }
        return message?.let { "HTTP $code: $it" } ?: fallback
    }
}
