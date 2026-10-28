package dev.codecell.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codecell.app.AppState
import dev.codecell.app.ChatMessage
import dev.codecell.app.R
import dev.codecell.app.Role
import dev.codecell.app.ServerPhase
import dev.codecell.app.data.Attachment
import dev.codecell.app.data.ChatPart
import dev.codecell.app.data.SessionInfo
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CodeCellApp(state: AppState) {
    var showSettings by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var showAgents by remember { mutableStateOf(false) }
    var showCustomAgent by remember { mutableStateOf(false) }
    var showProviders by remember { mutableStateOf(false) }
    var showAttachments by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<SessionInfo?>(null) }
    var renameTarget by remember { mutableStateOf<SessionInfo?>(null) }
    var revertTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    fun hideKeyboard() {
        focus.clearFocus()
        keyboard?.hide()
    }

    fun addAttachment(uri: Uri) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { readAttachment(context, uri) }
            }
            result.onSuccess { attachment ->
                val total = attachments.sumOf { it.bytes.size.toLong() } + attachment.bytes.size
                if (attachment.bytes.size > MAX_ATTACHMENT_BYTES) {
                    Toast.makeText(context, "File is too large (max 12 MB)", Toast.LENGTH_SHORT).show()
                } else if (total > MAX_TOTAL_ATTACHMENT_BYTES) {
                    Toast.makeText(context, "Attachments are too large together (max 25 MB)", Toast.LENGTH_SHORT).show()
                } else {
                    attachments = attachments + attachment
                }
            }.onFailure {
                Toast.makeText(context, it.message ?: "Could not open file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun pasteClipboard() {
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = runCatching { manager?.primaryClip }.getOrNull()
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }
        for (i in 0 until clip.itemCount) {
            val item = clip.getItemAt(i)
            val uri = item.uri
            if (uri != null) {
                addAttachment(uri)
                return
            }
            val text = item.text?.toString()
            if (!text.isNullOrBlank()) {
                draft = if (draft.isBlank()) text else draft.trimEnd() + " " + text
                return
            }
        }
        Toast.makeText(context, "Clipboard has no image or text", Toast.LENGTH_SHORT).show()
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri -> uri?.let { addAttachment(it) } }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { addAttachment(it) } }

    LaunchedEffect(Unit) {
        state.ensureServer(scope)
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) {
            keyboard?.hide()
            state.refreshSessions(scope)
        }
    }

    CodeCellTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            scrimColor = Color.Black.copy(alpha = 0.5f),
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(312.dp),
                    drawerContainerColor = Cs.Sheet,
                    drawerContentColor = Cs.Text,
                ) {
                    DrawerContent(
                        state = state,
                        onClose = { scope.launch { drawerState.close() } },
                        onNewChat = {
                            state.newChat()
                            draft = ""
                            attachments = emptyList()
                            hideKeyboard()
                            scope.launch { drawerState.close() }
                        },
                        onOpenSession = { session ->
                            state.openSession(session, scope)
                            draft = ""
                            attachments = emptyList()
                            hideKeyboard()
                            scope.launch { drawerState.close() }
                        },
                        onDeleteSession = { target -> deleteTarget = target },
                        onPinSession = { state.togglePin(it, scope) },
                        onRenameSession = { renameTarget = it },
                        onArchiveSession = { target ->
                            state.archiveSession(target, scope)
                            scope.launch { drawerState.close() }
                        },
                        onUnarchiveSession = { state.unarchiveSession(it, scope) },
                        onSettings = {
                            hideKeyboard()
                            showSettings = true
                            scope.launch { drawerState.close() }
                        },
                    )
                }
            },
        ) {
            Surface(
                color = Color.Transparent,
                modifier = Modifier.fillMaxSize().background(Cs.PageGradient),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    TopBar(
                        onMenu = {
                            hideKeyboard()
                            scope.launch { drawerState.open() }
                        },
                        agentLabel = state.agentLabel,
                        onAgent = {
                            hideKeyboard()
                            showAgents = true
                        },
                        onNewChat = {
                            state.newChat()
                            draft = ""
                            attachments = emptyList()
                            hideKeyboard()
                        },
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when {
                            state.loadingHistory -> Box(
                                Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = Cs.Muted,
                                )
                            }

                            state.messages.isEmpty() && !state.sending -> EmptyState(
                                name = state.userName,
                                phase = state.serverPhase,
                                status = state.serverStatus,
                                onRetry = { state.retryServer(scope) },
                            )
                            else -> MessageList(
                                state = state,
                                scope = scope,
                                onSearch = { showSearch = true },
                                onRevert = { revertTarget = it },
                            )
                        }
                    }
                    Composer(
                        value = draft,
                        onValueChange = { draft = it },
                        onSend = {
                            if ((draft.isNotBlank() || attachments.isNotEmpty()) && !state.sending) {
                                val text = draft
                                val files = attachments
                                draft = ""
                                attachments = emptyList()
                                state.send(text, files, scope)
                            }
                        },
                        attachments = attachments,
                        onRemoveAttachment = { attachment ->
                            attachments = attachments.filterNot { it.id == attachment.id }
                        },
                        onAttach = {
                            hideKeyboard()
                            showAttachments = true
                        },
                        modelLabel = state.modelLabel,
                        onModels = {
                            hideKeyboard()
                            showModels = true
                        },
                    )
                }
            }
        }

        if (showSettings) {
            SettingsSheet(
                state = state,
                onDismiss = {
                    hideKeyboard()
                    showSettings = false
                },
                onManageProviders = {
                    hideKeyboard()
                    showSettings = false
                    showProviders = true
                },
            )
        }
        if (showModels) {
            ModelsSheet(
                state = state,
                onDismiss = {
                    hideKeyboard()
                    showModels = false
                },
                onProviders = {
                    hideKeyboard()
                    showModels = false
                    showProviders = true
                },
            )
        }
        if (showAgents) {
            AgentSheet(
                state = state,
                onDismiss = {
                    hideKeyboard()
                    showAgents = false
                },
                onAddCustom = {
                    showAgents = false
                    showCustomAgent = true
                },
            )
        }
        if (showCustomAgent) {
            CustomAgentDialog(
                onDismiss = { showCustomAgent = false },
                onSave = { name, description, prompt ->
                    state.addCustomAgent(name, prompt, description)
                    showCustomAgent = false
                },
            )
        }
        if (showProviders) {
            ProvidersSheet(
                state = state,
                onDismiss = {
                    hideKeyboard()
                    showProviders = false
                },
            )
        }
        if (showAttachments) {
            AttachmentSheet(
                onDismiss = {
                    hideKeyboard()
                    showAttachments = false
                },
                onPickImage = {
                    showAttachments = false
                    imagePicker.launch("image/*")
                },
                onPickFile = {
                    showAttachments = false
                    filePicker.launch(arrayOf("*/*"))
                },
                onPasteClipboard = {
                    showAttachments = false
                    pasteClipboard()
                },
            )
        }

        renameTarget?.let { target ->
            RenameSessionDialog(
                currentTitle = target.title,
                onDismiss = { renameTarget = null },
                onSave = { title ->
                    state.renameSession(target, title, scope)
                    renameTarget = null
                },
            )
        }

        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                shape = RoundedCornerShape(24.dp),
                containerColor = Cs.SheetTop,
                titleContentColor = Cs.Text,
                textContentColor = Cs.Muted,
                title = { Text("Delete chat?", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Text(
                        "\"${target.title}\" will be removed from this device.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            state.deleteSession(target, scope)
                            deleteTarget = null
                        },
                    ) {
                        Text("Delete", color = Cs.Error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) {
                        Text("Cancel", color = Cs.Muted)
                    }
                },
            )
        }

        revertTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { revertTarget = null },
                shape = RoundedCornerShape(24.dp),
                containerColor = Cs.SheetTop,
                titleContentColor = Cs.Text,
                textContentColor = Cs.Muted,
                title = { Text("Revert to here?", style = MaterialTheme.typography.titleLarge) },
                text = {
                    Text(
                        "This undoes later messages and file changes in this chat.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            state.revertTo(target, scope)
                            revertTarget = null
                        },
                    ) {
                        Text("Revert", color = Cs.Error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { revertTarget = null }) {
                        Text("Cancel", color = Cs.Muted)
                    }
                },
            )
        }

        if (showSearch) {
            SearchMessagesDialog(
                messages = state.messages,
                onDismiss = { showSearch = false },
            )
        }
    }
}

@Composable
private fun TopBar(
    onMenu: () -> Unit,
    agentLabel: String,
    onAgent: () -> Unit,
    onNewChat: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(horizontal = 6.dp),
    ) {
        IconButton(onClick = onMenu, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.Filled.Menu, contentDescription = "Sessions", tint = Cs.Text)
        }
        AgentChip(
            label = agentLabel,
            onClick = onAgent,
            modifier = Modifier.align(Alignment.Center),
        )
        IconButton(onClick = onNewChat, modifier = Modifier.align(Alignment.CenterEnd)) {
            Icon(Icons.Filled.Add, contentDescription = "New chat", tint = Cs.Text)
        }
    }
}

@Composable
private fun AgentChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .widthIn(max = 190.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1F1F26), Color(0xFF15151A))),
            )
            .border(1.dp, Cs.Line, shape)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 7.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.SmartToy,
            contentDescription = null,
            tint = Cs.Muted,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.size(7.dp))
        Text(
            text = label,
            color = Cs.Text,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 124.dp),
        )
        Spacer(Modifier.size(2.dp))
        Icon(
            Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = Cs.Dim,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun DrawerContent(
    state: AppState,
    onClose: () -> Unit,
    onNewChat: () -> Unit,
    onOpenSession: (SessionInfo) -> Unit,
    onDeleteSession: (SessionInfo) -> Unit,
    onPinSession: (SessionInfo) -> Unit,
    onRenameSession: (SessionInfo) -> Unit,
    onArchiveSession: (SessionInfo) -> Unit,
    onUnarchiveSession: (SessionInfo) -> Unit,
    onSettings: () -> Unit,
) {
    var showArchived by remember { mutableStateOf(false) }
    val active = state.sessions.filter { !it.archived }
    val archived = state.sessions.filter { it.archived }
    val visible = if (showArchived) archived else active

    Column(
        Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 12.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF23232B), Color(0xFF16161B))))
                    .border(1.dp, Cs.Line, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.size(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "CodeCell",
                    style = MaterialTheme.typography.titleLarge,
                    color = Cs.Text,
                )
                Text(
                    text = "opencode client",
                    style = MaterialTheme.typography.labelSmall,
                    color = Cs.Dim,
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Cs.Dim)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(15.dp), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(15.dp))
                .background(Cs.ButtonGradient)
                .clickable(onClick = onNewChat)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Cs.Bg, modifier = Modifier.size(19.dp))
            Spacer(Modifier.size(9.dp))
            Text(
                "New chat",
                color = Cs.Bg,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(22.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (showArchived) "ARCHIVED" else "RECENT",
                style = MaterialTheme.typography.labelSmall,
                color = Cs.Dim,
                modifier = Modifier.weight(1f),
            )
            if (showArchived || archived.isNotEmpty()) {
                TextButton(
                    onClick = { showArchived = !showArchived },
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    Text(
                        text = if (showArchived) "Back" else "Archived (${archived.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = Cs.Dim,
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.sessionsLoading -> Box(
                    Modifier.fillMaxWidth().height(56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 1.5.dp,
                        color = Cs.Muted,
                    )
                }

                visible.isEmpty() -> Text(
                    text = if (showArchived) "No archived chats" else "No sessions yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cs.Dim,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(visible, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            selected = session.id == state.sessionId,
                            archived = showArchived,
                            onClick = { onOpenSession(session) },
                            onPin = { onPinSession(session) },
                            onRename = { onRenameSession(session) },
                            onArchive = { onArchiveSession(session) },
                            onUnarchive = { onUnarchiveSession(session) },
                            onDelete = { onDeleteSession(session) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onSettings,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Cs.Input.copy(alpha = 0.45f)),
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = Cs.Muted,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: SessionInfo,
    selected: Boolean,
    archived: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) Cs.Input else Color.Transparent)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menu = true },
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (selected) Cs.Text else Color.Transparent),
            )
            Spacer(Modifier.size(9.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (session.pinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = null,
                            tint = Cs.Dim,
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                    }
                    Text(
                        text = session.title,
                        color = if (selected) Cs.Text else Cs.Body,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (session.updated > 0) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = relativeTime(session.updated),
                        color = Cs.Dim,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menu,
            onDismissRequest = { menu = false },
            offset = DpOffset(x = 40.dp, y = 0.dp),
            shape = RoundedCornerShape(20.dp),
            containerColor = Cs.SheetTop,
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (session.pinned) "Unpin" else "Pin",
                        color = Cs.Text,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = null,
                        tint = Cs.Muted,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    menu = false
                    onPin()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        "Rename",
                        color = Cs.Text,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = Cs.Muted,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    menu = false
                    onRename()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = if (archived) "Unarchive" else "Archive",
                        color = Cs.Text,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = if (archived) {
                            Icons.Filled.Unarchive
                        } else {
                            Icons.Filled.Archive
                        },
                        contentDescription = null,
                        tint = Cs.Muted,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    menu = false
                    if (archived) onUnarchive() else onArchive()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        "Delete",
                        color = Cs.Error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = Cs.Error,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = {
                    menu = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun EmptyState(
    name: String,
    phase: ServerPhase,
    status: String,
    onRetry: () -> Unit,
) {
    val glow = rememberInfiniteTransition(label = "emptyGlow")
    val glowAlpha by glow.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
        label = "glowAlpha",
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(300.dp)
                .graphicsLayer { alpha = glowAlpha }
                .background(
                    Brush.radialGradient(listOf(Cs.Glow, Color.Transparent)),
                    CircleShape,
                ),
        )
        Column(
            Modifier.padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(68.dp)
                    .shadow(24.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF202027), Color(0xFF141419))),
                    )
                    .border(1.dp, Cs.Line, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = "${greeting()}, ${name.ifBlank { "there" }}",
                style = MaterialTheme.typography.headlineMedium,
                color = Cs.Text,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "What are we building today?",
                style = MaterialTheme.typography.bodyMedium,
                color = Cs.Muted,
            )
            Spacer(Modifier.height(18.dp))
            ServerStatus(phase = phase, status = status, onRetry = onRetry)
        }
    }
}

@Composable
private fun ServerStatus(phase: ServerPhase, status: String, onRetry: () -> Unit) {
    when (phase) {
        ServerPhase.Starting -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = Cs.Muted,
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = "Starting opencode...",
                style = MaterialTheme.typography.bodySmall,
                color = Cs.Muted,
            )
        }

        ServerPhase.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "opencode isn't ready",
                style = MaterialTheme.typography.bodySmall,
                color = Cs.Error,
            )
            if (status.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = status.take(400),
                    style = MaterialTheme.typography.bodySmall,
                    color = Cs.Dim,
                )
            }
            TextButton(onClick = onRetry) {
                Text("Retry", color = Cs.Muted, style = MaterialTheme.typography.labelLarge)
            }
        }

        else -> Unit
    }
}

@Composable
private fun MessageList(
    state: AppState,
    scope: CoroutineScope,
    onSearch: () -> Unit,
    onRevert: (ChatMessage) -> Unit,
) {
    val listState = rememberLazyListState()
    val showTyping = state.sending && state.liveMessageId == null
    val itemCount = state.messages.size + if (showTyping) 1 else 0

    LaunchedEffect(
        state.messages.size,
        state.sending,
        state.liveMessageId,
        state.messages.lastOrNull()?.parts?.size,
    ) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.messages, key = { it.id }) { message ->
            MessageRow(
                message = message,
                onSearch = onSearch,
                onFork = { state.forkFrom(message, scope) },
                onRevert = { onRevert(message) },
            )
        }
        if (showTyping) {
            item(key = "typing") { TypingRow() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: ChatMessage,
    onSearch: () -> Unit,
    onFork: () -> Unit,
    onRevert: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val canModify = message.serverId != null

    if (message.role == Role.User) {
        val shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = message.text,
                    color = Cs.Text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .widthIn(max = 304.dp)
                        .clip(shape)
                        .background(Cs.BubbleGradient)
                        .border(1.dp, Color(0xFF3A3A44), shape)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { menu = true },
                        )
                        .padding(horizontal = 15.dp, vertical = 11.dp),
                )
            }
            MessageActionsMenu(
                expanded = menu,
                canModify = canModify,
                onDismiss = { menu = false },
                onSearch = onSearch,
                onCopy = { clipboard.setText(AnnotatedString(message.text)) },
                onFork = onFork,
                onRevert = onRevert,
            )
        }
    } else {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { menu = true },
                    ),
            ) {
                if (message.parts.isNotEmpty()) {
                    MessageParts(message.parts, isError = message.isError)
                } else if (message.text.isBlank()) {
                    TypingRow()
                } else {
                    Text(
                        text = message.text,
                        color = if (message.isError) Cs.Error else Cs.Body,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (!message.isError) {
                    Spacer(Modifier.height(4.dp))
                    AssistantActions(
                        onCopy = { clipboard.setText(AnnotatedString(message.text)) },
                        onShare = { share(context, message.text) },
                    )
                }
            }
            MessageActionsMenu(
                expanded = menu,
                canModify = canModify,
                onDismiss = { menu = false },
                onSearch = onSearch,
                onCopy = { clipboard.setText(AnnotatedString(message.text)) },
                onFork = onFork,
                onRevert = onRevert,
            )
        }
    }
}

private fun ChatMessage.fullText(): String {
    if (parts.isEmpty()) return text
    return parts.joinToString("\n\n") { part ->
        when (part.type) {
            "text", "reasoning", "step-finish", "retry", "compaction" -> part.text
            "tool" -> listOf(part.tool, part.title, part.input, part.output, part.error)
                .filter { it.isNotBlank() }
                .joinToString("\n")

            "patch" -> part.files.joinToString("\n")
            "file" -> listOf(part.title, part.text).filter { it.isNotBlank() }.joinToString("\n")
            "subtask" -> listOf(part.title, part.detail, part.text)
                .filter { it.isNotBlank() }
                .joinToString("\n")

            "agent" -> part.title
            else -> ""
        }
    }
}

private fun ChatMessage.previewText(): String {
    if (text.isNotBlank()) return text
    val part = parts.firstOrNull { it.type == "tool" }
        ?: parts.firstOrNull { it.text.isNotBlank() || it.title.isNotBlank() }
        ?: return ""
    return listOf(part.title, part.text, part.tool).firstOrNull { it.isNotBlank() }.orEmpty()
}

@Composable
private fun MessageActionsMenu(
    expanded: Boolean,
    canModify: Boolean,
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
    onCopy: () -> Unit,
    onFork: () -> Unit,
    onRevert: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = Cs.SheetTop,
    ) {
        MessageAction(
            title = "Search",
            description = "Find in this chat",
            icon = Icons.Filled.Search,
            onClick = {
                onDismiss()
                onSearch()
            },
        )
        if (canModify) {
            MessageAction(
                title = "Revert",
                description = "Undo later messages and file changes",
                icon = Icons.Filled.Undo,
                onClick = {
                    onDismiss()
                    onRevert()
                },
            )
        }
        MessageAction(
            title = "Copy",
            description = "Copy message text",
            icon = Icons.Filled.ContentCopy,
            onClick = {
                onDismiss()
                onCopy()
            },
        )
        if (canModify) {
            MessageAction(
                title = "Fork",
                description = "Start a new chat from here",
                icon = Icons.Filled.ForkRight,
                onClick = {
                    onDismiss()
                    onFork()
                },
            )
        }
    }
}

@Composable
private fun MessageAction(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    text = title,
                    color = Cs.Text,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = description,
                    color = Cs.Dim,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Cs.Muted,
                modifier = Modifier.size(18.dp),
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun SearchMessagesDialog(messages: List<ChatMessage>, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, messages) {
        if (query.isBlank()) {
            emptyList()
        } else {
            messages.filter { it.fullText().contains(query, ignoreCase = true) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = Cs.SheetTop,
        titleContentColor = Cs.Text,
        textContentColor = Cs.Muted,
        title = { Text("Search this chat", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search messages") },
                    singleLine = true,
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
                Spacer(Modifier.height(10.dp))
                when {
                    query.isBlank() -> Text(
                        "Type to search this chat.",
                        color = Cs.Dim,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    results.isEmpty() -> Text(
                        "No matches.",
                        color = Cs.Dim,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    else -> LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(results, key = { it.id }) { message ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(
                                    text = if (message.role == Role.User) "You" else "CodeCell",
                                    color = Cs.Dim,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = message.previewText(),
                                    color = Cs.Body,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Cs.Muted)
            }
        },
    )
}

@Composable
private fun AssistantActions(onCopy: () -> Unit, onShare: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StaticIcon(Icons.Filled.ThumbUp)
        StaticIcon(Icons.Filled.ThumbDown)
        IconButton(onClick = onCopy, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copy reply",
                tint = Cs.Dim,
                modifier = Modifier.size(15.dp),
            )
        }
        StaticIcon(Icons.Filled.Refresh)
        IconButton(onClick = onShare, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Filled.Share,
                contentDescription = "Share reply",
                tint = Cs.Dim,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun StaticIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = Cs.Dim,
        modifier = Modifier.padding(horizontal = 6.dp).size(15.dp),
    )
}

@Composable
private fun TypingRow() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Thinking",
            color = Cs.Muted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.size(9.dp))
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 520, delayMillis = index * 150),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .padding(horizontal = 1.5.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Cs.Muted.copy(alpha = alpha)),
            )
        }
    }
}

@Composable
private fun MessageParts(parts: List<ChatPart>, isError: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        parts.forEach { part ->
            when (part.type) {
                "text" -> Text(
                    text = part.text,
                    color = if (isError) Cs.Error else Cs.Body,
                    style = MaterialTheme.typography.bodyLarge,
                )

                "reasoning" -> ReasoningCard(part.text)
                "tool" -> ToolCard(part)
                "patch" -> PatchCard(part.files)
                "step-finish" -> if (part.text.isNotBlank()) StepFinishRow(part.text)
                "retry" -> NoticeCard(
                    title = "RETRY",
                    text = part.text,
                    detail = part.detail,
                    color = Cs.Warn,
                )

                "file" -> FilePartCard(part.title, part.text)
                "subtask" -> SubtaskCard(part)
                "agent" -> AgentChip(part.title)
                "compaction" -> NoticeCard(
                    title = "CONTEXT",
                    text = part.text,
                    detail = "",
                    color = Cs.ToolRead,
                    icon = Icons.Filled.Description,
                )
            }
        }
    }
}

@Composable
private fun ReasoningCard(text: String) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Cs.Input.copy(alpha = 0.55f))
            .border(1.dp, Cs.LineSoft, shape)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Psychology,
                contentDescription = null,
                tint = Cs.Reasoning,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "THINKING",
                color = Cs.Reasoning,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(6.dp))
        ExpandablePlain(text, Cs.Muted, maxLines = 8)
    }
}

@Composable
private fun ToolCard(part: ChatPart) {
    val accent = toolAccent(part.tool)
    val shape = RoundedCornerShape(16.dp)
    val clipboard = LocalClipboardManager.current
    val copyText = listOf(part.title, part.input, part.output, part.error, part.detail)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = 0.06f))
            .border(1.dp, accent.copy(alpha = 0.32f), shape),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = toolIcon(part.tool),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = toolLabel(part.tool),
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (part.title.isNotBlank()) {
                    Text(
                        text = part.title,
                        color = Cs.Body,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ToolStatus(part.status, accent)
            if (copyText.isNotBlank()) {
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(copyText)) },
                    modifier = Modifier.size(30.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy activity",
                        tint = Cs.Dim,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        if (part.input.isNotBlank()) {
            SectionLabel("INPUT")
            ExpandableCode(part.input, Cs.Muted, maxLines = 10)
        }
        if (part.output.isNotBlank()) {
            SectionLabel("OUTPUT")
            ExpandableCode(part.output, Cs.Body, maxLines = 12)
        }
        if (part.error.isNotBlank()) {
            SectionLabel("ERROR")
            ExpandableCode(part.error, Cs.Error, maxLines = 8)
        }
        if (part.detail.isNotBlank() && part.detail != "{}") {
            SectionLabel("DETAILS")
            ExpandableCode(part.detail, Cs.Dim, maxLines = 6)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Cs.Dim,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
    )
}

@Composable
private fun ToolStatus(status: String, accent: Color) {
    val label: String
    val color: Color
    val icon: ImageVector?
    when (status) {
        "running" -> {
            label = "Running"
            color = accent
            icon = null
        }

        "pending" -> {
            label = "Queued"
            color = Cs.Muted
            icon = null
        }

        "completed" -> {
            label = "Done"
            color = Cs.Success
            icon = Icons.Filled.CheckCircle
        }

        "error" -> {
            label = "Error"
            color = Cs.Error
            icon = Icons.Filled.ErrorOutline
        }

        else -> {
            label = status.ifBlank { "Tool" }
            color = Cs.Dim
            icon = null
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp),
            )
        } else if (status == "running" || status == "pending") {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = color,
                strokeWidth = 1.5.dp,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ExpandableCode(text: String, color: Color, maxLines: Int) {
    var expanded by remember { mutableStateOf(false) }
    val lineCount = text.count { it == '\n' } + 1
    val canExpand = lineCount > maxLines || text.length > maxLines * 80
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 2.dp),
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = 17.sp,
            ),
            maxLines = if (expanded) Int.MAX_VALUE else maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (canExpand) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Text(
                    text = if (expanded) "Show less" else "Show all",
                    color = Cs.Muted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ExpandablePlain(text: String, color: Color, maxLines: Int) {
    var expanded by remember { mutableStateOf(false) }
    val lineCount = text.count { it == '\n' } + 1
    val canExpand = lineCount > maxLines || text.length > maxLines * 90
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            maxLines = if (expanded) Int.MAX_VALUE else maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (canExpand) {
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(28.dp),
            ) {
                Text(
                    text = if (expanded) "Show less" else "Show all",
                    color = Cs.Muted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun PatchCard(files: List<String>) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Cs.ToolCode.copy(alpha = 0.06f))
            .border(1.dp, Cs.ToolCode.copy(alpha = 0.32f), shape)
            .padding(vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Code,
                contentDescription = null,
                tint = Cs.ToolCode,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "FILES CHANGED",
                color = Cs.ToolCode,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(6.dp))
        val shown = files.ifEmpty { listOf("Working tree updated") }
        shown.forEach { file ->
            Text(
                text = file,
                color = Cs.Body,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp,
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun StepFinishRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Cs.LineSoft),
        )
        Text(
            text = text,
            color = Cs.Dim,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(Cs.LineSoft),
        )
    }
}

@Composable
private fun NoticeCard(
    title: String,
    text: String,
    detail: String,
    color: Color,
    icon: ImageVector = Icons.Filled.ErrorOutline,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.07f))
            .border(1.dp, color.copy(alpha = 0.30f), shape)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = title,
                color = color,
                style = MaterialTheme.typography.labelSmall,
            )
            if (detail.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = detail,
                    color = Cs.Dim,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (text.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            ExpandablePlain(text, Cs.Muted, maxLines = 6)
        }
    }
}

@Composable
private fun FilePartCard(name: String, mime: String) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Cs.ToolNet.copy(alpha = 0.07f))
            .border(1.dp, Cs.ToolNet.copy(alpha = 0.30f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint = Cs.ToolNet,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "ATTACHED FILE",
                color = Cs.ToolNet,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = name,
                color = Cs.Body,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (mime.isNotBlank()) {
                Text(
                    text = mime,
                    color = Cs.Dim,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun SubtaskCard(part: ChatPart) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Cs.ToolAgent.copy(alpha = 0.07f))
            .border(1.dp, Cs.ToolAgent.copy(alpha = 0.30f), shape)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = null,
                tint = Cs.ToolAgent,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "SUBAGENT",
                color = Cs.ToolAgent,
                style = MaterialTheme.typography.labelSmall,
            )
            if (part.detail.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = part.detail,
                    color = Cs.Dim,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (part.title.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = part.title,
                color = Cs.Body,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (part.text.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            ExpandableCode(part.text, Cs.Muted, maxLines = 8)
        }
    }
}

@Composable
private fun AgentChip(name: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Cs.ToolAgent.copy(alpha = 0.10f))
            .border(1.dp, Cs.ToolAgent.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.SmartToy,
            contentDescription = null,
            tint = Cs.ToolAgent,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "AGENT · $name",
            color = Cs.ToolAgent,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun toolAccent(tool: String): Color = when {
    tool == "bash" || tool == "shell" -> Cs.ToolShell
    tool == "edit" || tool == "write" -> Cs.ToolCode
    tool == "read" || tool == "grep" || tool == "glob" || tool == "list" -> Cs.ToolRead
    tool == "webfetch" -> Cs.ToolNet
    tool == "task" -> Cs.ToolAgent
    else -> Cs.Muted
}

private fun toolLabel(tool: String): String = when (tool) {
    "bash", "shell" -> "COMMAND"
    "edit", "write" -> "CODE EDIT"
    "read" -> "READ FILE"
    "grep", "glob", "list" -> "SEARCH"
    "webfetch" -> "DOWNLOAD"
    "task" -> "SUBAGENT"
    "todowrite", "todoread" -> "TASKS"
    else -> tool.uppercase().ifBlank { "TOOL" }
}

private fun toolIcon(tool: String): ImageVector = when (tool) {
    "bash", "shell" -> Icons.Filled.Terminal
    "edit", "write" -> Icons.Filled.Code
    "read", "grep", "glob", "list" -> Icons.Filled.Description
    "webfetch" -> Icons.Filled.Download
    "task" -> Icons.Filled.SmartToy
    "todowrite", "todoread" -> Icons.Filled.CheckCircle
    else -> Icons.Filled.Settings
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    attachments: List<Attachment>,
    onRemoveAttachment: (Attachment) -> Unit,
    onAttach: () -> Unit,
    modelLabel: String,
    onModels: () -> Unit,
) {
    val canSend = value.isNotBlank() || attachments.isNotEmpty()
    val sendScale by animateFloatAsState(if (canSend) 1f else 0.9f, label = "sendScale")
    val shape = RoundedCornerShape(30.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .animateContentSize(),
    ) {
        AnimatedVisibility(visible = attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                attachments.forEach { attachment ->
                    AttachmentChip(
                        attachment = attachment,
                        onRemove = { onRemoveAttachment(attachment) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(28.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape)
                .background(Cs.ComposerGradient)
                .border(1.dp, Cs.Line, shape)
                .padding(start = 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onAttach, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Attach file",
                    tint = Cs.Muted,
                    modifier = Modifier.size(21.dp),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).padding(vertical = 15.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Cs.Text),
                maxLines = 5,
                cursorBrush = SolidColor(Cs.Text),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    capitalization = KeyboardCapitalization.Sentences,
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                "Message CodeCell...",
                                color = Cs.Dim,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            ModelChip(label = modelLabel, onClick = onModels)
            Spacer(Modifier.size(6.dp))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .graphicsLayer {
                        scaleX = sendScale
                        scaleY = sendScale
                    }
                    .shadow(
                        elevation = if (canSend) 14.dp else 0.dp,
                        shape = CircleShape,
                        ambientColor = Color.White.copy(alpha = 0.2f),
                        spotColor = Color.White.copy(alpha = 0.2f),
                    )
                    .clip(CircleShape)
                    .background(
                        brush = if (canSend) {
                            Cs.ButtonGradient
                        } else {
                            Brush.verticalGradient(listOf(Color(0xFF2A2A31), Color(0xFF232329)))
                        },
                    )
                    .border(1.dp, if (canSend) Color.Transparent else Cs.Line, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onSend, enabled = canSend, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.Filled.ArrowUpward,
                        contentDescription = "Send",
                        tint = if (canSend) Cs.Bg else Cs.Dim,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(attachment: Attachment, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(Cs.Input.copy(alpha = 0.65f))
            .border(1.dp, Cs.LineSoft, shape)
            .padding(start = 10.dp, end = 2.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (attachment.mime.startsWith("image/")) {
                Icons.Filled.Image
            } else {
                Icons.Filled.InsertDriveFile
            },
            contentDescription = null,
            tint = Cs.Muted,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = attachment.name,
            color = Cs.Body,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(26.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove attachment",
                tint = Cs.Dim,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun ModelChip(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(Color(0xFF101014))
            .border(1.dp, Cs.LineSoft, shape)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Cs.Body,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 82.dp),
        )
        Icon(
            Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = Cs.Muted,
            modifier = Modifier.size(16.dp),
        )
    }
}

private fun relativeTime(updated: Long): String {
    val diff = System.currentTimeMillis() - updated
    if (diff < 60_000) return "Just now"
    val minutes = diff / 60_000
    if (minutes < 60) return "${minutes}m ago"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    return when {
        days == 1L -> "Yesterday"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(updated))
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}

private fun share(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share reply"))
}

private const val MAX_ATTACHMENT_BYTES = 12 * 1024 * 1024L
private const val MAX_TOTAL_ATTACHMENT_BYTES = 25 * 1024 * 1024L

private fun readAttachment(context: Context, uri: Uri): Attachment {
    val resolver = context.contentResolver
    var name = "file"
    var declaredSize = -1L
    resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: name
            }
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                declaredSize = cursor.getLong(sizeIndex)
            }
        }
    }
    if (declaredSize > MAX_ATTACHMENT_BYTES) {
        throw IllegalArgumentException("File is too large (max 12 MB)")
    }

    val mime = resolver.getType(uri) ?: "application/octet-stream"
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_ATTACHMENT_BYTES) {
                throw IllegalArgumentException("File is too large (max 12 MB)")
            }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    } ?: throw IllegalStateException("Could not read file")

    return Attachment(
        id = System.nanoTime(),
        name = name,
        mime = mime,
        bytes = bytes,
    )
}
