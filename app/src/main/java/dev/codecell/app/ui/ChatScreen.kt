package dev.codecell.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.codecell.app.AppState
import dev.codecell.app.ChatMessage
import dev.codecell.app.R
import dev.codecell.app.Role
import dev.codecell.app.data.SessionInfo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun CodeCellApp(state: AppState) {
    var showSettings by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var showAgents by remember { mutableStateOf(false) }
    var showCustomAgent by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            state.attachFolder(uri)
            state.refreshAgents(scope)
        } else {
            state.markFolderPrompted()
        }
    }

    LaunchedEffect(Unit) {
        state.refreshAgents(scope)
        if (!state.hasFolder && !state.folderPrompted) {
            folderPicker.launch(null)
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen) state.refreshSessions(scope)
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
                            scope.launch { drawerState.close() }
                        },
                        onOpenSession = { session ->
                            state.openSession(session, scope)
                            scope.launch { drawerState.close() }
                        },
                        onSettings = {
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
                        onMenu = { scope.launch { drawerState.open() } },
                        agentLabel = state.agentLabel,
                        onAgent = { showAgents = true },
                        onNewChat = {
                            state.newChat()
                            draft = ""
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

                            state.messages.isEmpty() && !state.sending -> EmptyState(state.userName)
                            else -> MessageList(state, scope)
                        }
                    }
                    Composer(
                        value = draft,
                        onValueChange = { draft = it },
                        onSend = {
                            if (draft.isNotBlank() && !state.sending) {
                                val text = draft
                                draft = ""
                                state.send(text, scope)
                            }
                        },
                        modelLabel = state.modelLabel,
                        onModels = { showModels = true },
                        onNewChat = {
                            state.newChat()
                            draft = ""
                        },
                    )
                }
            }
        }

        if (showSettings) {
            SettingsSheet(
                state = state,
                onDismiss = { showSettings = false },
                onChooseFolder = {
                    showSettings = false
                    folderPicker.launch(null)
                },
            )
        }
        if (showModels) {
            ModelsSheet(state, onDismiss = { showModels = false })
        }
        if (showAgents) {
            AgentSheet(
                state = state,
                onDismiss = { showAgents = false },
                onAddCustom = {
                    showAgents = false
                    showCustomAgent = true
                },
            )
        }
        if (showCustomAgent) {
            CustomAgentDialog(
                onDismiss = { showCustomAgent = false },
                onSave = { name, prompt ->
                    state.addCustomAgent(name, prompt)
                    showCustomAgent = false
                },
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
    onSettings: () -> Unit,
) {
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
        Text(
            text = "RECENT",
            style = MaterialTheme.typography.labelSmall,
            color = Cs.Dim,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
        )

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

                state.sessions.isEmpty() -> Text(
                    text = "No sessions yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cs.Dim,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp),
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.sessions, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            selected = session.id == state.sessionId,
                            onClick = { onOpenSession(session) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        val footerShape = RoundedCornerShape(14.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(footerShape)
                .background(Cs.Input.copy(alpha = 0.45f))
                .border(1.dp, Cs.LineSoft, footerShape)
                .clickable(onClick = onSettings)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Cs.SheetTop),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    tint = Cs.Muted,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Settings", color = Cs.Text, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (state.hasFolder) state.folderLabel else "Choose a CodeCell folder",
                    color = Cs.Dim,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionInfo, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Cs.Input else Color.Transparent)
            .clickable(onClick = onClick)
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
            Text(
                text = session.title,
                color = if (selected) Cs.Text else Cs.Body,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
}

@Composable
private fun EmptyState(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(300.dp)
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
        }
    }
}

@Composable
private fun MessageList(state: AppState, scope: CoroutineScope) {
    val listState = rememberLazyListState()
    val itemCount = state.messages.size + if (state.sending) 1 else 0

    LaunchedEffect(state.messages.size, state.sending) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.messages, key = { it.id }) { message ->
            MessageRow(message)
        }
        if (state.sending) {
            item(key = "typing") { TypingRow() }
        }
    }
}

@Composable
private fun MessageRow(message: ChatMessage) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    if (message.role == Role.User) {
        val shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
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
                    .padding(horizontal = 15.dp, vertical = 11.dp),
            )
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = message.text,
                color = if (message.isError) Cs.Error else Cs.Body,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!message.isError) {
                Spacer(Modifier.height(4.dp))
                AssistantActions(
                    onCopy = { clipboard.setText(AnnotatedString(message.text)) },
                    onShare = { share(context, message.text) },
                )
            }
        }
    }
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
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modelLabel: String,
    onModels: () -> Unit,
    onNewChat: () -> Unit,
) {
    val canSend = value.isNotBlank()
    val sendScale by animateFloatAsState(if (canSend) 1f else 0.9f, label = "sendScale")
    val shape = RoundedCornerShape(30.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .shadow(28.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(shape)
            .background(Cs.ComposerGradient)
            .border(1.dp, Cs.Line, shape)
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNewChat, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "New chat",
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
        IconButton(onClick = {}, enabled = false, modifier = Modifier.size(34.dp)) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = "Voice input is not available",
                tint = Cs.Dim,
                modifier = Modifier.size(19.dp),
            )
        }
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
