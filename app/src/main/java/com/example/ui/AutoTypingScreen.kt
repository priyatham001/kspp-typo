package com.example.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth.HidConnectionState
import com.example.typing.TypingState
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusYellow
import com.example.usb.UsbConnectionState
import com.example.usb.UsbTypingProgress

@Composable
fun AutoTypingScreen(
    viewModel: MainViewModel,
    onNavigateToConnectivity: () -> Unit,
    onNavigateToScripts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val editorText by viewModel.editorText.collectAsState()
    val editorTitle by viewModel.editorTitle.collectAsState()
    val typingDelayMs by viewModel.typingDelayMs.collectAsState()

    val btState by viewModel.connectionState.collectAsState()
    val usbState by viewModel.usbConnectionState.collectAsState()
    val btTypingState by viewModel.typingState.collectAsState()
    val usbTypingProgress by viewModel.usbTypingProgress.collectAsState()

    val isBtConnected = btState is HidConnectionState.Connected
    val isNetSynced = usbState is UsbConnectionState.Synced
    val isConnected = isBtConnected || isNetSynced

    var showSaveDialog by remember { mutableStateOf(false) }
    var saveTitleInput by remember { mutableStateOf(editorTitle) }

    // Derive active typing status & progress across both transports
    val isTypingActive = (btTypingState is TypingState.Typing || btTypingState is TypingState.Preparing || usbTypingProgress is UsbTypingProgress.Typing)
    val isPaused = (btTypingState is TypingState.Paused || usbTypingProgress is UsbTypingProgress.Paused)

    val currentTypedIndex = when {
        usbTypingProgress is UsbTypingProgress.Typing -> (usbTypingProgress as UsbTypingProgress.Typing).currentIndex
        usbTypingProgress is UsbTypingProgress.Paused -> (usbTypingProgress as UsbTypingProgress.Paused).currentIndex
        btTypingState is TypingState.Typing -> (btTypingState as TypingState.Typing).currentIndex
        btTypingState is TypingState.Paused -> (btTypingState as TypingState.Paused).currentIndex
        btTypingState is TypingState.Completed || usbTypingProgress is UsbTypingProgress.Completed -> editorText.length
        else -> 0
    }

    val totalChars = editorText.length
    val progressPercent = if (totalChars > 0) (currentTypedIndex.toFloat() / totalChars).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Connection Gate Banner
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isConnected) StatusGreen.copy(alpha = 0.12f) else StatusYellow.copy(alpha = 0.15f)
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(if (isConnected) StatusGreen.copy(alpha = 0.5f) else StatusYellow.copy(alpha = 0.5f))
            ),
            modifier = Modifier.fillMaxWidth().testTag("auto_typing_conn_banner")
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) StatusGreen else StatusYellow)
                    )
                    Column {
                        Text(
                            text = when {
                                isNetSynced -> "🟢 Ready to Type via Wi-Fi / USB"
                                isBtConnected -> "🟢 Ready to Type via Bluetooth HID"
                                else -> "⚠️ Host Disconnected"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isConnected) StatusGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                isNetSynced -> {
                                    val synced = usbState as UsbConnectionState.Synced
                                    "Connected: ${synced.host} (${synced.latencyMs}ms)"
                                }
                                isBtConnected -> {
                                    val dev = (btState as HidConnectionState.Connected).deviceName
                                    "Connected: $dev"
                                }
                                else -> "Auto-typing is disabled until connected to PC"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!isConnected) {
                    Button(
                        onClick = onNavigateToConnectivity,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("connect_host_prompt_button")
                    ) {
                        Text("Connect", fontSize = 11.sp)
                    }
                }
            }
        }

        // 2. Multiline Text Input Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth().testTag("editor_input_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Message to Auto-Type",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val text = clip.getItemAt(0).text?.toString() ?: ""
                                    viewModel.updateEditorText(text)
                                }
                            },
                            modifier = Modifier.size(34.dp).testTag("paste_button")
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = { onNavigateToScripts() },
                            modifier = Modifier.size(34.dp).testTag("load_macro_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Load Macro", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = {
                                saveTitleInput = editorTitle
                                showSaveDialog = true
                            },
                            modifier = Modifier.size(34.dp).testTag("save_macro_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save Macro", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = { viewModel.clearEditor() },
                            modifier = Modifier.size(34.dp).testTag("clear_text_button")
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                OutlinedTextField(
                    value = editorText,
                    onValueChange = { viewModel.updateEditorText(it) },
                    placeholder = { Text("Type or paste message here to send to PC...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("auto_type_text_field"),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isTypingActive
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${editorText.length} characters • ${editorText.lines().size} lines",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Est. duration: ${(editorText.length * typingDelayMs) / 1000}s",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 3. Exact Queued Text & Real-Time Sync Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth().testTag("sync_status_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Exact Stream & Synchronized Status",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Surface(
                        shape = CircleShape,
                        color = when {
                            isTypingActive -> MaterialTheme.colorScheme.primaryContainer
                            isPaused -> StatusYellow.copy(alpha = 0.2f)
                            progressPercent == 1f && totalChars > 0 -> StatusGreen.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ) {
                        Text(
                            text = when {
                                isTypingActive -> "⚡ Typing in progress..."
                                isPaused -> "⏸️ Paused"
                                progressPercent == 1f && totalChars > 0 -> "✅ Completed"
                                else -> "Idle"
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isTypingActive -> MaterialTheme.colorScheme.primary
                                isPaused -> StatusYellow
                                progressPercent == 1f && totalChars > 0 -> StatusGreen
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Progress Bar
                LinearProgressIndicator(
                    progress = { progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$currentTypedIndex / $totalChars chars (${(progressPercent * 100).toInt()}%)",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isTypingActive) "Streaming to PC..." else "Exact match preview",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Highlighted exact text preview (confirmed in sync)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(10.dp)
                ) {
                    val annotatedString = buildAnnotatedString {
                        if (editorText.isNotEmpty()) {
                            val typedLength = currentTypedIndex.coerceIn(0, editorText.length)
                            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)) {
                                append(editorText.take(typedLength))
                            }
                            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))) {
                                append(editorText.drop(typedLength))
                            }
                        } else {
                            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))) {
                                append("(No text queued. Enter message above.)")
                            }
                        }
                    }

                    Text(
                        text = annotatedString,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // 4. Execution Controls (Start, Pause, Resume, Stop)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth().testTag("execution_controls_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Main Start Auto Typing button (Strictly disabled if not connected or text is empty)
                Button(
                    onClick = { viewModel.startTyping() },
                    enabled = isConnected && editorText.isNotBlank() && !isTypingActive,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_auto_typing_button")
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start", modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = when {
                            !isConnected -> "Connect Host to Auto-Type"
                            editorText.isBlank() -> "Enter Message to Type"
                            else -> "Start Auto Typing"
                        },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Pause / Resume / Stop Controls when active
                AnimatedVisibility(visible = isTypingActive || isPaused) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isPaused) {
                            Button(
                                onClick = { viewModel.resumeTyping() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                modifier = Modifier.weight(1f).testTag("resume_typing_button")
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                                Spacer(Modifier.width(6.dp))
                                Text("Resume")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewModel.pauseTyping() },
                                modifier = Modifier.weight(1f).testTag("pause_typing_button")
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause")
                                Spacer(Modifier.width(6.dp))
                                Text("Pause")
                            }
                        }

                        Button(
                            onClick = { viewModel.stopTyping() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f).testTag("stop_typing_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop")
                            Spacer(Modifier.width(6.dp))
                            Text("Stop")
                        }
                    }
                }

                // Speed / Delay Slider
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Speed, contentDescription = "Speed", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Keystroke Delay:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = "${typingDelayMs}ms per character",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Slider(
                        value = typingDelayMs.toFloat(),
                        onValueChange = { viewModel.setTypingDelay(it.toLong()) },
                        valueRange = 0f..200f,
                        steps = 19,
                        modifier = Modifier.testTag("typing_speed_slider")
                    )

                    // Speed presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PresetDelayChip("Turbo (5ms)", 5L, typingDelayMs) { viewModel.setTypingDelay(it) }
                        PresetDelayChip("Fast (20ms)", 20L, typingDelayMs) { viewModel.setTypingDelay(it) }
                        PresetDelayChip("Normal (35ms)", 35L, typingDelayMs) { viewModel.setTypingDelay(it) }
                        PresetDelayChip("Safe (70ms)", 70L, typingDelayMs) { viewModel.setTypingDelay(it) }
                    }
                }
            }
        }
    }

    // Save Macro Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save as Macro") },
            text = {
                OutlinedTextField(
                    value = saveTitleInput,
                    onValueChange = { saveTitleInput = it },
                    label = { Text("Macro Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("macro_title_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSaveDialog = false
                        viewModel.saveCurrentScript(saveTitleInput)
                    },
                    modifier = Modifier.testTag("confirm_save_macro_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PresetDelayChip(
    label: String,
    delay: Long,
    currentDelay: Long,
    onClick: (Long) -> Unit
) {
    FilterChip(
        selected = currentDelay == delay,
        onClick = { onClick(delay) },
        label = { Text(label, fontSize = 10.sp) },
        modifier = Modifier.testTag("preset_${delay}ms")
    )
}
