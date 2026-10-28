package dev.codecell.app.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codecell.app.AppState
import dev.codecell.app.ChatMessage
import dev.codecell.app.R
import dev.codecell.app.Role
import dev.codecell.app.data.SessionInfo
import java.util.Calendar
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
            Surface(color = Cs.Bg, modifier = Modifier.fillMaxSize()) {
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
    Row(
        modifier = modifier
            .widthIn(max = 180.dp)
            .clip(RoundedCornerShape(50))
            .background(Cs.Input)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.SmartToy,
            contentDescription = null,
            tint = Cs.Muted,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            color = Cs.Text,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
        Icon(
            Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = Cs.Muted,
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
                .padding(start = 12.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "CodeCell",
                color = Cs.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Cs.Muted)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onNewChat)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Cs.Text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            Text("New chat", color = Cs.Text, fontSize = 14.sp)
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "SESSIONS",
            color = Cs.Dim,
            fontSize = 10.sp,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
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
                    color = Cs.Dim,
                    fontSize = 12.sp,
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

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onSettings)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = Cs.Muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Settings", color = Cs.Text, fontSize = 13.sp)
                Text(state.folderLabel, color = Cs.Dim, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SessionRow(session: SessionInfo, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Cs.Input else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = session.title,
            color = if (selected) Cs.Text else Color(0xFFC2C2C8),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyState(name: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = null,
            modifier = Modifier.size(46.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "${greeting()}, ${name.ifBlank { "there" }}",
            color = Cs.Text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
        )
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = message.text,
                color = Cs.Text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .background(Cs.Bubble)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = message.text,
                color = if (message.isError) Cs.Error else Color(0xFFC9C9CE),
                fontSize = 14.sp,
                lineHeight = 21.sp,
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 1.5.dp,
            color = Cs.Muted,
        )
        Spacer(Modifier.size(8.dp))
        Text("Thinking...", color = Cs.Muted, fontSize = 14.sp)
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Cs.Input)
            .padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onNewChat, modifier = Modifier.size(40.dp)) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "New chat",
                tint = Cs.Text,
                modifier = Modifier.size(22.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).padding(vertical = 14.dp),
            textStyle = TextStyle(color = Cs.Text, fontSize = 15.sp, lineHeight = 20.sp),
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
                        Text("How can I help you today?", color = Cs.Muted, fontSize = 15.sp)
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
                .size(36.dp)
                .clip(CircleShape)
                .background(if (canSend) Cs.Text else Color(0xFF34343A)),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onSend, enabled = canSend, modifier = Modifier.size(36.dp)) {
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
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Cs.Bg.copy(alpha = 0.4f))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Cs.Text,
            fontSize = 12.sp,
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
