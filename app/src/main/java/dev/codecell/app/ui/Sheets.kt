package dev.codecell.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.codecell.app.AppState
import dev.codecell.app.data.ModelOption
import dev.codecell.app.data.OpencodeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(state: AppState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(state.serverUrl) }
    var name by remember { mutableStateOf(state.userName) }
    var testing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Cs.Sheet,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Settings", color = Cs.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "CodeCell talks to an opencode server. Start one with " +
                    "`opencode serve --hostname 0.0.0.0 --port 4096` " +
                    "and use this device's network address.",
                color = Cs.Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server URL") },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name (optional)") },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        testing = true
                        status = null
                        scope.launch {
                            status = try {
                                withContext(Dispatchers.IO) { OpencodeClient.health(url.trim()) }
                            } catch (e: Exception) {
                                e.message ?: "Connection failed"
                            }
                            testing = false
                        }
                    },
                    enabled = !testing,
                ) {
                    Text(if (testing) "Testing..." else "Test")
                }
                status?.let {
                    Text(
                        text = it,
                        color = if (it.startsWith("Connected")) Cs.Text else Cs.Error,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Button(
                onClick = {
                    state.saveSettings(url, name)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Cs.Text,
                    contentColor = Cs.Bg,
                ),
            ) {
                Text("Save")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsSheet(state: AppState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var loading by remember { mutableStateOf(true) }
    var options by remember { mutableStateOf<List<ModelOption>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

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
                    color = Cs.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { reload++ }, enabled = !loading) {
                    Text("Refresh", color = Cs.Muted, fontSize = 13.sp)
                }
            }

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
                    Text(error!!, color = Cs.Error, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Check the server URL in Settings and make sure the server is running.",
                        color = Cs.Muted,
                        fontSize = 12.sp,
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
                    items(options, key = { "${it.providerID}/${it.modelID}" }) { option ->
                        ModelRow(
                            name = option.name,
                            subtitle = "${option.providerID}/${option.modelID}",
                            selected = state.model?.providerID == option.providerID &&
                                state.model?.modelID == option.modelID,
                            onClick = {
                                state.selectModel(option)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    name: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                color = Cs.Text,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = Cs.Muted,
                fontSize = 11.sp,
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
