package dev.codecell.app.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.codecell.app.AppState
import dev.codecell.app.ServerPhase
import dev.codecell.app.data.AgentOption
import dev.codecell.app.data.ModelOption
import dev.codecell.app.data.OpencodeClient
import dev.codecell.app.data.ProviderAuthMethod
import dev.codecell.app.data.ProviderCatalog
import dev.codecell.app.data.ProviderInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    state: AppState,
    onDismiss: () -> Unit,
    onManageProviders: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(state.userName) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Cs.Sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = Cs.Text)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name (optional)") },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("PROVIDERS", style = MaterialTheme.typography.labelSmall, color = Cs.Dim)
            val cardShape = RoundedCornerShape(16.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(Cs.Input.copy(alpha = 0.45f))
                    .border(1.dp, Cs.LineSoft, cardShape)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("AI providers", style = MaterialTheme.typography.titleSmall, color = Cs.Text)
                    Text(
                        "Add or change API keys for OpenAI, Anthropic, Google and more.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Cs.Muted,
                    )
                }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = onManageProviders, shape = RoundedCornerShape(12.dp)) {
                    Text("Manage", style = MaterialTheme.typography.labelLarge)
                }
            }

            Text("EMBEDDED SERVER", style = MaterialTheme.typography.labelSmall, color = Cs.Dim)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(Cs.Input.copy(alpha = 0.45f))
                    .border(1.dp, Cs.LineSoft, cardShape)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = when (state.serverPhase) {
                                ServerPhase.Running -> "Running"
                                ServerPhase.Starting -> "Starting"
                                ServerPhase.Failed -> "Failed"
                                ServerPhase.Stopped -> "Stopped"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = if (state.serverPhase == ServerPhase.Failed) Cs.Error else Cs.Text,
                        )
                        Text(
                            text = state.serverStatus.ifBlank { "Not started" },
                            style = MaterialTheme.typography.bodySmall,
                            color = Cs.Muted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(
                        onClick = { state.restartServer(scope) },
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Restart", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Text(
                    text = "Workspace: ${state.workspacePath}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cs.Dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "opencode 1.19.4 is built into this app. No Termux or extra server needed.",
                style = MaterialTheme.typography.bodySmall,
                color = Cs.Dim,
            )
            Button(
                onClick = {
                    state.saveSettings(name)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cs.Text,
                    contentColor = Cs.Bg,
                ),
            ) {
                Text("Save", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSheet(
    state: AppState,
    onDismiss: () -> Unit,
    onAddCustom: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Cs.Sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Agent",
                    style = MaterialTheme.typography.titleLarge,
                    color = Cs.Text,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onAddCustom, shape = RoundedCornerShape(10.dp)) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = Cs.Muted,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text("Custom", color = Cs.Muted, style = MaterialTheme.typography.labelLarge)
                }
            }

            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(state.agentOptions(), key = { "${it.name}-${it.isCustom}" }) { agent ->
                    AgentRow(
                        agent = agent,
                        selected = state.agentLabel == agent.name,
                        onClick = {
                            state.selectAgent(agent)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentRow(agent: AgentOption, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Cs.Input else Color.Transparent)
            .border(1.dp, if (selected) Cs.LineSoft else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = agent.name,
                    color = Cs.Text,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (agent.isCustom) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "CUSTOM",
                        color = Cs.Dim,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = agent.description,
                color = Cs.Muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Cs.Text,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
fun CustomAgentDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val valid = name.isNotBlank() && prompt.isNotBlank()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val displayName = readDisplayName(context, uri)
                    val raw = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: throw IllegalStateException("Could not read that file")
                    Triple(displayName, raw, parseAgentMarkdown(displayName, raw))
                }
            }
            result.onSuccess { (display, _, parsed) ->
                fileName = display
                name = parsed.first
                description = parsed.second
                prompt = parsed.third
                if (prompt.isBlank()) error = "That file has no agent instructions."
            }.onFailure {
                error = it.message ?: "Could not read that file"
            }
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Cs.SheetTop,
        titleContentColor = Cs.Text,
        textContentColor = Cs.Muted,
        title = { Text("Custom agent", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Upload an .md agent file. Its body becomes the agent instructions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cs.Muted,
                )
                OutlinedButton(
                    onClick = {
                        picker.launch(arrayOf("*/*"))
                    },
                    enabled = !loading,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(15.dp),
                            strokeWidth = 1.5.dp,
                            color = Cs.Muted,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("Reading...")
                    } else {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = Cs.Muted,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(fileName ?: "Choose .md file")
                    }
                }
                if (fileName != null) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (description.isNotBlank()) {
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = Cs.Muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        prompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = Cs.Dim,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                error?.let {
                    Text(it, color = Cs.Error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onSave(name, description, prompt) },
                enabled = valid,
            ) {
                Text("Save", color = if (valid) Cs.Text else Cs.Dim)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Cs.Muted)
            }
        },
    )
}

private fun readDisplayName(context: android.content.Context, uri: Uri): String {
    var name = "agent.md"
    runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && !cursor.isNull(index)) {
                    name = cursor.getString(index)?.takeIf { it.isNotBlank() } ?: name
                }
            }
        }
    }
    return name
}

private fun parseAgentMarkdown(fileName: String, raw: String): Triple<String, String, String> {
    val text = raw.replace("\r\n", "\n")
    var name = fileName.substringBeforeLast('.').replace('-', ' ').replace('_', ' ').trim()
        .ifBlank { "Custom agent" }
    var description = "Custom agent"
    var body = text.trim()

    if (text.startsWith("---\n")) {
        val end = text.indexOf("\n---", 3)
        if (end > 0) {
            val front = text.substring(4, end)
            front.lineSequence().forEach { line ->
                val split = line.indexOf(':')
                if (split <= 0) return@forEach
                val key = line.substring(0, split).trim().lowercase()
                val value = line.substring(split + 1).trim().trim('"', '\'')
                if (value.isBlank()) return@forEach
                when (key) {
                    "name" -> name = value
                    "description" -> description = value
                }
            }
            body = text.substring(end + 4).trimStart('\n').trim()
        }
    }
    return Triple(name, description, body)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsSheet(
    state: AppState,
    onDismiss: () -> Unit,
    onProviders: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var loading by remember { mutableStateOf(true) }
    var options by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var variantTarget by remember { mutableStateOf<ModelOption?>(null) }

    LaunchedEffect(reload) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching { OpencodeClient.listModels(state.serverUrl) }
        }
        result.onSuccess { options = it }.onFailure { error = it.message ?: "Failed to load models" }
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Cs.Sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Model",
                    style = MaterialTheme.typography.titleLarge,
                    color = Cs.Text,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onProviders) {
                    Text(
                        "Providers",
                        color = Cs.Muted,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                TextButton(onClick = { reload++ }, enabled = !loading) {
                    Text(
                        "Refresh",
                        color = Cs.Muted,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Text(
                "Free models appear by default. Connect a provider to unlock more.",
                style = MaterialTheme.typography.bodySmall,
                color = Cs.Dim,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search models") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = Cs.Dim,
                        modifier = Modifier.size(17.dp),
                    )
                },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            when {
                loading -> Box(
                    Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Cs.Muted,
                    )
                }

                error != null -> Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Text(error!!, color = Cs.Error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Try restarting the embedded server in Settings, then tap Refresh.",
                        color = Cs.Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    item(key = "default") {
                        ModelRow(
                            name = "Server default",
                            subtitle = "Let opencode pick the model",
                            selected = state.model == null,
                            onClick = {
                                state.selectModel(null)
                                onDismiss()
                            },
                        )
                    }
                    val filtered = options.filter {
                        query.isBlank() ||
                            it.name.contains(query, ignoreCase = true) ||
                            it.providerID.contains(query, ignoreCase = true) ||
                            it.modelID.contains(query, ignoreCase = true)
                    }
                    if (filtered.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "No models match that search.",
                                color = Cs.Dim,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 13.dp, vertical = 18.dp),
                            )
                        }
                    }
                    items(filtered, key = { "${it.providerID}/${it.modelID}" }) { option ->
                        val selected = state.model?.providerID == option.providerID &&
                            state.model?.modelID == option.modelID
                        val currentVariant = state.model?.variant
                        ModelRow(
                            name = option.name,
                            subtitle = buildString {
                                append(option.providerID)
                                append('/')
                                append(option.modelID)
                                if (selected && !currentVariant.isNullOrBlank()) {
                                    append(" · ")
                                    append(currentVariant)
                                } else if (option.variants.isNotEmpty()) {
                                    append(" · ")
                                    append(option.variants.size)
                                    append(" variants")
                                }
                            },
                            selected = selected,
                            hasVariants = option.variants.isNotEmpty(),
                            onClick = {
                                if (option.variants.isNotEmpty()) {
                                    variantTarget = option
                                } else {
                                    state.selectModel(option)
                                    onDismiss()
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    variantTarget?.let { target ->
        VariantDialog(
            model = target,
            current = if (state.model?.providerID == target.providerID &&
                state.model?.modelID == target.modelID
            ) {
                state.model?.variant
            } else {
                null
            },
            onDismiss = { variantTarget = null },
            onSelect = { variant ->
                state.selectModel(target.copy(variant = variant))
                variantTarget = null
                onDismiss()
            },
        )
    }
}

@Composable
private fun VariantDialog(
    model: ModelOption,
    current: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Cs.SheetTop,
        titleContentColor = Cs.Text,
        title = {
            Text("${model.name} variant", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                VariantRow(
                    label = "Default",
                    selected = current.isNullOrBlank(),
                    onClick = { onSelect(null) },
                )
                model.variants.forEach { variant ->
                    VariantRow(
                        label = variantLabel(variant),
                        selected = current == variant,
                        onClick = { onSelect(variant) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Cs.Muted)
            }
        },
    )
}

@Composable
private fun VariantRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Cs.Input else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Cs.Text,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Cs.Text,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun variantLabel(value: String): String = when (value.lowercase()) {
    "none" -> "Off"
    "minimal" -> "Minimal"
    "xhigh" -> "Extra high"
    "thinking" -> "Thinking"
    else -> value.replaceFirstChar { it.uppercaseChar() }
}

@Composable
private fun ModelRow(
    name: String,
    subtitle: String,
    selected: Boolean,
    hasVariants: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Cs.Input else Color.Transparent)
            .border(1.dp, if (selected) Cs.LineSoft else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                color = Cs.Text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Cs.Muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (hasVariants) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = "Model variants",
                tint = Cs.Dim,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Cs.Text,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersSheet(state: AppState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableStateOf(0) }
    var catalog by remember { mutableStateOf<ProviderCatalog?>(null) }
    var methods by remember {
        mutableStateOf<Map<String, List<ProviderAuthMethod>>>(emptyMap())
    }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ProviderInfo?>(null) }
    var removing by remember { mutableStateOf<ProviderInfo?>(null) }
    var working by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload) {
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val cat = OpencodeClient.listProviders(state.serverUrl)
                val auth = runCatching {
                    OpencodeClient.listAuthMethods(state.serverUrl)
                }.getOrDefault(emptyMap())
                cat to auth
            }
        }
        result.onSuccess { (cat, auth) ->
            catalog = cat
            methods = auth
        }.onFailure {
            error = it.message ?: "Could not load providers"
        }
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Cs.Sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Providers",
                    style = MaterialTheme.typography.titleLarge,
                    color = Cs.Text,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { reload++ }, enabled = !loading && !working) {
                    Text("Refresh", color = Cs.Muted, style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                "Connect your own AI accounts. API keys stay on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = Cs.Muted,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search providers") },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = Cs.Dim,
                        modifier = Modifier.size(17.dp),
                    )
                },
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (working) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 1.5.dp,
                        color = Cs.Muted,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Updating providers...",
                        style = MaterialTheme.typography.bodySmall,
                        color = Cs.Muted,
                    )
                }
            }

            when {
                loading -> Box(
                    Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Cs.Muted,
                    )
                }

                error != null -> Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Text(error!!, color = Cs.Error, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { reload++ }) {
                        Text("Try again", color = Cs.Muted)
                    }
                }

                catalog != null -> {
                    val cat = catalog!!
                    val connected = cat.all.filter { it.id in cat.connected }
                    val available = cat.all.filter {
                        it.id !in cat.connected &&
                            (query.isBlank() ||
                                it.name.contains(query, ignoreCase = true) ||
                                it.id.contains(query, ignoreCase = true))
                    }
                    LazyColumn(Modifier.heightIn(max = 440.dp)) {
                        if (connected.isNotEmpty()) {
                            item(key = "connected-title") {
                                SectionLabel("CONNECTED")
                            }
                            items(connected, key = { "c-${it.id}" }) { provider ->
                                ProviderRow(
                                    provider = provider,
                                    connected = true,
                                    onClick = {
                                        if (supportsApiKey(provider, methods)) {
                                            editing = provider
                                        } else {
                                            notice = "This provider uses a browser sign-in, " +
                                                "which isn't supported in this build yet. " +
                                                "Remove it to disconnect."
                                        }
                                    },
                                    onRemove = { removing = provider },
                                )
                            }
                        }
                        item(key = "available-title") {
                            SectionLabel("ADD A PROVIDER")
                        }
                        items(available, key = { "a-${it.id}" }) { provider ->
                            ProviderRow(
                                provider = provider,
                                connected = false,
                                onClick = {
                                    if (supportsApiKey(provider, methods)) {
                                        editing = provider
                                    } else {
                                        notice = "This provider needs a browser sign-in, " +
                                            "which isn't supported in this build yet."
                                    }
                                },
                                onRemove = null,
                            )
                        }
                        if (available.isEmpty()) {
                            item(key = "no-providers") {
                                Text(
                                    "No matching providers.",
                                    color = Cs.Dim,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { provider ->
        ProviderKeyDialog(
            provider = provider,
            method = methods[provider.id]?.firstOrNull { it.type == "api" },
            onDismiss = { editing = null },
            onSave = { key, extras ->
                scope.launch {
                    working = true
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            OpencodeClient.setApiKey(state.serverUrl, provider.id, key, extras)
                        }
                    }
                    if (result.isSuccess) {
                        runCatching { state.reloadAfterProviderChange() }
                        editing = null
                        reload++
                    } else {
                        notice = result.exceptionOrNull()?.message ?: "Could not save that key"
                    }
                    working = false
                }
            },
        )
    }

    removing?.let { provider ->
        AlertDialog(
            onDismissRequest = { removing = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = Cs.SheetTop,
            titleContentColor = Cs.Text,
            textContentColor = Cs.Muted,
            title = { Text("Remove provider?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    if (provider.id == "opencode") {
                        "The saved Zen key will be removed. Free models stay available."
                    } else {
                        "The API key for ${provider.name} will be deleted from this device."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            working = true
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    OpencodeClient.deleteProviderAuth(state.serverUrl, provider.id)
                                }
                            }
                            runCatching { state.reloadAfterProviderChange() }
                            removing = null
                            reload++
                            working = false
                        }
                    },
                ) {
                    Text("Remove", color = Cs.Error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) {
                    Text("Cancel", color = Cs.Muted)
                }
            },
        )
    }

    notice?.let { message ->
        AlertDialog(
            onDismissRequest = { notice = null },
            shape = RoundedCornerShape(24.dp),
            containerColor = Cs.SheetTop,
            titleContentColor = Cs.Text,
            textContentColor = Cs.Muted,
            title = { Text("Provider", style = MaterialTheme.typography.titleLarge) },
            text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { notice = null }) {
                    Text("OK", color = Cs.Text)
                }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Cs.Dim,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun ProviderRow(
    provider: ProviderInfo,
    connected: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (connected) Cs.Input else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 13.dp, end = 4.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = provider.name,
                color = Cs.Text,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (provider.id == "opencode") {
                    "Free models or your Zen key"
                } else {
                    provider.id
                },
                color = Cs.Muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (connected) {
            IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Key,
                    contentDescription = "Change API key",
                    tint = Cs.Dim,
                    modifier = Modifier.size(16.dp),
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove provider",
                        tint = Cs.Dim,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        } else {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = Cs.Dim,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
    }
}

private fun supportsApiKey(
    provider: ProviderInfo,
    methods: Map<String, List<ProviderAuthMethod>>,
): Boolean {
    val list = methods[provider.id] ?: return true
    return list.any { it.type == "api" }
}

@Composable
private fun ProviderKeyDialog(
    provider: ProviderInfo,
    method: ProviderAuthMethod?,
    onDismiss: () -> Unit,
    onSave: (String, Map<String, String>) -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var extras by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val prompts = method?.prompts?.filter { it.kind == "text" } ?: emptyList()
    val valid = key.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Cs.SheetTop,
        titleContentColor = Cs.Text,
        textContentColor = Cs.Muted,
        title = {
            Text(
                if (provider.id == "opencode") "OpenCode Zen" else provider.name,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (provider.id == "opencode") {
                        "Paste your Zen API key to unlock more models. It replaces any key already saved."
                    } else {
                        "Paste a new API key for ${provider.name}. It replaces any key already saved."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Cs.Muted,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                prompts.forEach { prompt ->
                    OutlinedTextField(
                        value = extras[prompt.key].orEmpty(),
                        onValueChange = { value -> extras = extras + (prompt.key to value) },
                        label = { Text(prompt.message) },
                        placeholder = { Text(prompt.placeholder.orEmpty()) },
                        singleLine = true,
                        colors = fieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onSave(key, extras) },
                enabled = valid,
            ) {
                Text("Save", color = if (valid) Cs.Text else Cs.Dim)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Cs.Muted)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onPasteClipboard: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Cs.Sheet,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { SheetHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Add to chat", style = MaterialTheme.typography.titleLarge, color = Cs.Text)
            Text(
                "Attach a photo, a file, or paste from the clipboard.",
                style = MaterialTheme.typography.bodySmall,
                color = Cs.Muted,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            AttachmentOption(
                icon = Icons.Filled.Image,
                title = "Photo",
                subtitle = "Images from your gallery",
                onClick = onPickImage,
            )
            AttachmentOption(
                icon = Icons.Filled.InsertDriveFile,
                title = "File",
                subtitle = "Code, documents, PDFs and more",
                onClick = onPickFile,
            )
            AttachmentOption(
                icon = Icons.Filled.ContentPaste,
                title = "Paste",
                subtitle = "Image or text from the clipboard",
                onClick = onPasteClipboard,
            )
            Text(
                "Images, PDFs, and text files work with most models.",
                style = MaterialTheme.typography.labelSmall,
                color = Cs.Dim,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )
            Text(
                "Hold the handle at the top and slide down to close.",
                style = MaterialTheme.typography.labelSmall,
                color = Cs.Dim,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp),
            )
        }
    }
}

@Composable
private fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Cs.Input.copy(alpha = 0.45f))
            .border(1.dp, Cs.LineSoft, shape)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Cs.SheetTop),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Cs.Muted, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Cs.Text, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = Cs.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = Cs.Dim,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
fun RenameSessionDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var title by remember(currentTitle) { mutableStateOf(currentTitle) }
    val valid = title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Cs.SheetTop,
        titleContentColor = Cs.Text,
        textContentColor = Cs.Muted,
        title = { Text("Rename chat", style = MaterialTheme.typography.titleLarge) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Chat name") },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onSave(title.trim()) },
                enabled = valid,
            ) {
                Text("Save", color = if (valid) Cs.Text else Cs.Dim)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Cs.Muted)
            }
        },
    )
}

@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .padding(top = 12.dp, bottom = 4.dp)
            .size(width = 38.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Cs.Line),
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Cs.Text,
    unfocusedTextColor = Cs.Text,
    focusedContainerColor = Cs.Input,
    unfocusedContainerColor = Cs.Input,
    focusedBorderColor = Cs.Line,
    unfocusedBorderColor = Cs.Line,
    focusedLabelColor = Cs.Muted,
    unfocusedLabelColor = Cs.Muted,
    cursorColor = Cs.Text,
)
