package dev.codecell.app.data

data class ProviderInfo(
    val id: String,
    val name: String,
)

data class ProviderPrompt(
    val key: String,
    val message: String,
    val placeholder: String?,
    val kind: String,
)

data class ProviderAuthMethod(
    val type: String,
    val label: String,
    val prompts: List<ProviderPrompt>,
)

data class ProviderCatalog(
    val all: List<ProviderInfo>,
    val connected: Set<String>,
)

data class ChatPart(
    val id: String = "",
    val type: String,
    val text: String = "",
    val tool: String = "",
    val status: String = "",
    val title: String = "",
    val input: String = "",
    val output: String = "",
    val error: String = "",
    val files: List<String> = emptyList(),
    val detail: String = "",
)

data class SentMessage(
    val text: String,
    val assistantID: String?,
    val userID: String?,
    val parts: List<ChatPart> = emptyList(),
)

data class Attachment(
    val id: Long,
    val name: String,
    val mime: String,
    val bytes: ByteArray,
)
