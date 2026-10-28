package dev.codecell.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class OpencodeException(message: String) : Exception(message)

/**
 * Minimal client for the opencode headless server (opencode serve).
 * Uses only HttpURLConnection + org.json so the APK stays tiny.
 */
object OpencodeClient {

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 300_000

    fun health(baseUrl: String): String {
        val raw = request("GET", url(baseUrl, "/global/health"), null)
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
            val updated = item.optJSONObject("time")?.optLong("updated") ?: 0L
            result += SessionInfo(id, title, updated)
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
            val text = joinText(item.optJSONArray("parts"))
            if (text.isNotBlank()) result += SessionMessage(role, text)
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
        agent: String?,
        system: String?,
    ): String {
        val payload = JSONObject().apply {
            if (!providerID.isNullOrBlank() && !modelID.isNullOrBlank()) {
                put(
                    "model",
                    JSONObject()
                        .put("providerID", providerID)
                        .put("modelID", modelID),
                )
            }
            if (!agent.isNullOrBlank()) put("agent", agent)
            if (!system.isNullOrBlank()) put("system", system)
            put(
                "parts",
                JSONArray().put(JSONObject().put("type", "text").put("text", text)),
            )
        }
        val raw = request(
            "POST",
            url(baseUrl, "/session/$sessionId/message"),
            payload.toString(),
        )
        return extractReply(raw)
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
                result += ModelOption(providerID, modelID, label)
            }
        }
        return result.sortedBy { it.name.lowercase() }
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
            throw OpencodeException(message.ifBlank { "The model returned an error" })
        }
        return "Done."
    }

    private fun url(baseUrl: String, path: String): String =
        baseUrl.trim().trimEnd('/') + path

    private fun request(method: String, target: String, body: String?): String {
        val connection = try {
            (URL(target).openConnection() as HttpURLConnection)
        } catch (e: Exception) {
            throw OpencodeException("Invalid server URL: $target")
        }

        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
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
                "Cannot reach $target. Is `opencode serve` running and reachable from this device?",
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(code: Int, raw: String): String {
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
