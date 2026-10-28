package dev.codecell.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codecell.app.AppState
import dev.codecell.app.R
import dev.codecell.app.ServerPhase
import dev.codecell.app.data.OpencodeClient
import dev.codecell.app.data.ProviderAuthMethod
import dev.codecell.app.data.ProviderCatalog
import dev.codecell.app.data.ProviderInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OnboardingScreen(state: AppState, scope: CoroutineScope) {
    var page by rememberSaveable { mutableStateOf(0) }

    Surface(Modifier.fillMaxSize(), color = Cs.Bg) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Cs.PageGradient),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 16.dp),
            ) {
                OnboardingHeader(page)
                Spacer(Modifier.height(22.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (page == 0) {
                        ProviderSetup(state = state, scope = scope)
                    } else {
                        FolderSetup(state = state, scope = scope)
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (page == 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "STEP 1 OF 2",
                            color = Cs.Dim,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { page = 1 },
                            enabled = state.configuredProviders.isNotEmpty(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Cs.Text,
                                contentColor = Cs.Bg,
                                disabledContainerColor = Cs.Input,
                                disabledContentColor = Cs.Dim,
                            ),
                        ) {
                            Text("Next", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { page = 0 }) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = null,
                                tint = Cs.Muted,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text("Back", color = Cs.Muted)
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { state.completeOnboarding() },
                            enabled = state.hasFolder,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Cs.Text,
                                contentColor = Cs.Bg,
                                disabledContainerColor = Cs.Input,
                                disabledContentColor = Cs.Dim,
                            ),
                        ) {
                            Text("Start chatting", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingHeader(page: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Cs.Input)
                .border(1.dp, Cs.Line, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(25.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("CodeCell", style = MaterialTheme.typography.titleLarge, color = Cs.Text)
            Text(
                text = if (page == 0) "Connect your AI provider" else "Choose your workspace",
                style = MaterialTheme.typography.bodySmall,
                color = Cs.Muted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(2) { index ->
                Box(
                    Modifier
                        .size(width = if (index == page) 20.dp else 7.dp, height = 7.dp)
                        .clip(CircleShape)
                        .background(if (index == page) Cs.Text else Cs.Line),
                )
            }
        }
    }
}

@Composable
private fun ProviderSetup(state: AppState, scope: CoroutineScope) {
    var loading by remember { mutableStateOf(true) }
    var catalog by remember { mutableStateOf<ProviderCatalog?>(null) }
    var methods by remember {
        mutableStateOf<Map<String, List<ProviderAuthMethod>>>(emptyMap())
    }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<ProviderInfo?>(null) }
    var working by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reload, state.serverPhase) {
        if (state.serverPhase != ServerPhase.Running) {
            loading = false
            return@LaunchedEffect
        }
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

    Column(Modifier.fillMaxSize()) {
        Text("Pick a provider", style = MaterialTheme.typography.headlineMedium, color = Cs.Text)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Every provider starts empty. Connect one with an API key to begin. " +
                "You can add more later in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = Cs.Muted,
        )
        Spacer(Modifier.height(14.dp))

        if (state.serverPhase != ServerPhase.Running) {
            ServerSetupStatus(state = state, scope = scope)
            return@Column
        }

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
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Cs.Text,
                unfocusedTextColor = Cs.Text,
                focusedContainerColor = Cs.Input,
                unfocusedContainerColor = Cs.Input,
                focusedBorderColor = Cs.Line,
                unfocusedBorderColor = Cs.Line,
                focusedLabelColor = Cs.Muted,
                unfocusedLabelColor = Cs.Muted,
                cursorColor = Cs.Text,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        when {
            loading -> Box(
                Modifier.fillMaxWidth().height(200.dp),
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
                TextButton(onClick = { reload++ }) {
                    Text("Try again", color = Cs.Muted)
                }
            }

            catalog != null -> {
                val all = catalog!!.all.filter {
                    query.isBlank() ||
                        it.name.contains(query, ignoreCase = true) ||
                        it.id.contains(query, ignoreCase = true)
                }
                if (all.isEmpty()) {
                    Text(
                        "No providers match that search.",
                        color = Cs.Dim,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 440.dp)) {
                        items(all, key = { it.id }) { provider ->
                            ProviderRow(
                                provider = provider,
                                connected = provider.id in state.configuredProviders,
                                onClick = {
                                    if (supportsApiKey(provider, methods)) {
                                        editing = provider
                                    } else {
                                        notice = "This provider uses a browser sign-in, " +
                                            "which isn't supported in this build yet."
                                    }
                                },
                                onRemove = null,
                            )
                        }
                    }
                }
            }
        }

        if (working) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = Cs.Muted,
                )
                Spacer(Modifier.width(8.dp))
                Text("Saving...", color = Cs.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        notice?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(message, color = Cs.Warn, style = MaterialTheme.typography.bodySmall)
        }
        if (state.configuredProviders.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Cs.Success,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${state.configuredProviders.size} provider connected",
                    color = Cs.Success,
                    style = MaterialTheme.typography.bodySmall,
                )
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
                        state.markProviderConfigured(provider.id)
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
}

@Composable
private fun ServerSetupStatus(state: AppState, scope: CoroutineScope) {
    Spacer(Modifier.height(26.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Cs.Input.copy(alpha = 0.5f))
            .border(1.dp, Cs.LineSoft, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.serverPhase == ServerPhase.Starting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 1.5.dp,
                    color = Cs.Muted,
                )
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text = when (state.serverPhase) {
                    ServerPhase.Starting -> "Starting opencode..."
                    ServerPhase.Failed -> "opencode isn't ready"
                    else -> "opencode is stopped"
                },
                color = if (state.serverPhase == ServerPhase.Failed) Cs.Error else Cs.Text,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { state.retryServer(scope) }) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Retry",
                    tint = Cs.Muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (state.serverStatus.isNotBlank()) {
            Text(
                text = state.serverStatus,
                color = Cs.Dim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "Connect the server to load the provider list.",
            color = Cs.Muted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun FolderSetup(state: AppState, scope: CoroutineScope) {
    val context = LocalContext.current
    val allFiles = hasAllFilesAccess(context)
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            state.attachFolder(uri, scope)
            if (!state.hasFolder) {
                Toast.makeText(context, "Choose a folder on this device", Toast.LENGTH_SHORT).show()
            } else if (!hasAllFilesAccess(context)) {
                openAllFilesSettings(context)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text("Set up CodeCell", style = MaterialTheme.typography.headlineMedium, color = Cs.Text)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Choose where CodeCell keeps your chats and workspace files. " +
                "A CodeCell folder will be created inside the folder you pick.",
            style = MaterialTheme.typography.bodySmall,
            color = Cs.Muted,
        )
        Spacer(Modifier.height(20.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Cs.Input.copy(alpha = 0.5f))
                .border(
                    1.dp,
                    if (state.hasFolder) Cs.Line else Cs.LineSoft,
                    RoundedCornerShape(20.dp),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Cs.SheetTop),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        tint = if (state.hasFolder) Cs.Success else Cs.Muted,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (state.hasFolder) state.folderLabel else "No folder selected",
                        color = Cs.Text,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = if (state.hasFolder) {
                            state.workspacePath
                        } else {
                            "Sessions and workspace files"
                        },
                        color = Cs.Muted,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (state.hasFolder) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Cs.Success,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            OutlinedButton(
                onClick = { picker.launch(null) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    if (state.hasFolder) Icons.Filled.Refresh else Icons.Filled.Add,
                    contentDescription = null,
                    tint = Cs.Muted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (state.hasFolder) "Change folder" else "Choose folder")
            }
            if (state.hasFolder && !allFiles) {
                OutlinedButton(
                    onClick = { openAllFilesSettings(context) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Allow workspace file access")
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "Your API keys stay in the opencode server on this device. " +
                "CodeCell never uploads them.",
            style = MaterialTheme.typography.labelSmall,
            color = Cs.Dim,
        )
    }
}

internal fun hasAllFilesAccess(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

internal fun openAllFilesSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val appSettings = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )
    runCatching { context.startActivity(appSettings) }.onFailure {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}
