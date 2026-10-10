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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow
import com.example.usb.CompanionInfo
import com.example.usb.UsbConnectionState
import com.example.usb.UsbTypingProgress
import kotlinx.coroutines.launch

@Composable
fun UsbTypingScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val usbState by viewModel.usbConnectionState.collectAsState()
    val isUsbCableConnected by viewModel.isUsbCableConnected.collectAsState()
    val usbTypingProgress by viewModel.usbTypingProgress.collectAsState()
    val companionInfo by viewModel.companionInfo.collectAsState()
    val editorText by viewModel.editorText.collectAsState()

    var isCheckingConnection by remember { mutableStateOf(false) }
    var showHowToUse by remember { mutableStateOf(true) }
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var customHostInput by remember { mutableStateOf("127.0.0.1") }
    var customPortInput by remember { mutableStateOf("8989") }
    var usbDelayMs by remember { mutableFloatStateOf(25f) }

    val scrollState = rememberScrollState()

    val charCount = editorText.length
    val wordCount = remember(editorText) {
        if (editorText.isBlank()) 0 else editorText.trim().split("\\s+".toRegex()).size
    }
    val estimatedDurationSec = remember(charCount, usbDelayMs) {
        (charCount * usbDelayMs.toLong()) / 1000
    }

    val isSynced = usbState is UsbConnectionState.Synced

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "USB Typing",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "High-speed auto-typing into Windows via USB cable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = {
                    scope.launch {
                        isCheckingConnection = true
                        viewModel.checkUsbConnection()
                        isCheckingConnection = false
                    }
                },
                modifier = Modifier.testTag("refresh_usb_connection_btn")
            ) {
                if (isCheckingConnection) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Check Connection",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Connection & Sync Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("usb_connection_status_card"),
            colors = CardDefaults.cardColors(
                containerColor = when (usbState) {
                    is UsbConnectionState.Synced -> StatusGreen.copy(alpha = 0.08f)
                    is UsbConnectionState.Connecting, is UsbConnectionState.Connected -> StatusYellow.copy(alpha = 0.08f)
                    is UsbConnectionState.Incompatible -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.surface
                }
            ),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    when (usbState) {
                                        is UsbConnectionState.Synced -> StatusGreen
                                        is UsbConnectionState.Connecting, is UsbConnectionState.Connected -> StatusYellow
                                        is UsbConnectionState.Incompatible -> MaterialTheme.colorScheme.error
                                        else -> StatusRed
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when (val s = usbState) {
                                is UsbConnectionState.Synced -> "SYNCED (READY)"
                                is UsbConnectionState.Connecting -> "CONNECTING..."
                                is UsbConnectionState.Connected -> "CONNECTED (VERIFYING)"
                                is UsbConnectionState.Incompatible -> "INCOMPATIBLE COMPANION"
                                is UsbConnectionState.Error -> "CONNECTION ERROR"
                                is UsbConnectionState.Disconnected -> "DISCONNECTED"
                            },
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            fontSize = 14.sp,
                            color = when (usbState) {
                                is UsbConnectionState.Synced -> StatusGreen
                                is UsbConnectionState.Connecting, is UsbConnectionState.Connected -> StatusYellow
                                is UsbConnectionState.Incompatible -> MaterialTheme.colorScheme.error
                                else -> StatusRed
                            }
                        )
                    }

                    // Physical USB Cable indicator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cable,
                            contentDescription = "USB Cable",
                            modifier = Modifier.size(16.dp),
                            tint = if (isUsbCableConnected) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isUsbCableConnected) "Cable Plugged" else "Cable Unplugged",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Detail message
                Text(
                    text = when (val s = usbState) {
                        is UsbConnectionState.Synced -> "Windows Companion v${s.companionVersion} verified. Handshake confirmed (ping: ${s.latencyMs} ms). Ready to stream keystrokes into PC."
                        is UsbConnectionState.Connecting -> s.message
                        is UsbConnectionState.Connected -> "TCP connection established on ${s.host}:${s.port}. Waiting for protocol handshake..."
                        is UsbConnectionState.Incompatible -> "Incompatible Companion: ${s.reason}. Companion version is ${s.companionVersion}, requires ${s.requiredVersion}."
                        is UsbConnectionState.Error -> s.message
                        is UsbConnectionState.Disconnected -> "${s.reason}. Ensure USB Debugging is ON and replica-companion.exe is running on PC."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Action row: Check Connection & Share Companion
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                isCheckingConnection = true
                                val port = customPortInput.toIntOrNull() ?: 8989
                                viewModel.checkUsbConnection(customHostInput, port)
                                isCheckingConnection = false
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("check_usb_sync_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isCheckingConnection) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Check Connection")
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.shareCompanionFile(context) },
                        modifier = Modifier.testTag("share_companion_exe_btn")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share .exe")
                    }
                }

                // Advanced Connection Settings & ADB Command Toggle
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAdvancedSettings = !showAdvancedSettings },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showAdvancedSettings) "⚙️ Hide Advanced Connection Settings" else "⚙️ Advanced Port / Host Settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = showAdvancedSettings) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = customHostInput,
                                onValueChange = { customHostInput = it },
                                label = { Text("Host / IP", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(2f)
                            )
                            OutlinedTextField(
                                value = customPortInput,
                                onValueChange = { customPortInput = it },
                                label = { Text("Port", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // ADB Command Quick Copy Box
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "ADB Port Forwarding Command (Optional):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "adb reverse tcp:8989 tcp:8989",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("ADB Command", "adb reverse tcp:8989 tcp:8989")
                                            clipboard.setPrimaryClip(clip)
                                        },
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy Reverse Cmd", fontSize = 10.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("ADB Forward Command", "adb forward tcp:8989 tcp:8989")
                                            clipboard.setPrimaryClip(clip)
                                        },
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy Forward Cmd", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live Typing Progress Card (visible when typing, paused, or completed)
        AnimatedVisibility(visible = usbTypingProgress !is UsbTypingProgress.Idle) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("usb_typing_progress_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    when (val prog = usbTypingProgress) {
                        is UsbTypingProgress.Typing -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "TYPING IN PROGRESS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = StatusGreen
                                )
                                Text(
                                    text = "${(prog.percent * 100).toInt()}%",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { prog.percent },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Typed: ${prog.currentIndex} / ${prog.totalChars} chars",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Remaining: ~${prog.remainingMs / 1000}s",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is UsbTypingProgress.Paused -> {
                            Text(
                                text = "TYPING PAUSED (${(prog.percent * 100).toInt()}%)",
                                fontWeight = FontWeight.Bold,
                                color = StatusYellow,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap Resume to continue streaming to Windows.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        is UsbTypingProgress.Completed -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Completed! Transmitted ${prog.totalChars} chars in ${prog.elapsedMs / 1000}s.",
                                    fontWeight = FontWeight.Bold,
                                    color = StatusGreen
                                )
                            }
                        }

                        is UsbTypingProgress.Stopped -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Stop, contentDescription = null, tint = StatusRed)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Typing stopped (${prog.charsTyped} chars transmitted).",
                                    color = StatusRed
                                )
                            }
                        }

                        else -> Unit
                    }
                }
            }
        }

        // Text Editor for USB Typing
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Script / Text to Type",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                                    viewModel.updateEditorText(editorText + pasteText)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste", modifier = Modifier.size(18.dp))
                        }

                        IconButton(
                            onClick = { viewModel.updateEditorText("") },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = editorText,
                    onValueChange = { viewModel.updateEditorText(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 260.dp)
                        .testTag("usb_editor_input"),
                    placeholder = {
                        Text(
                            "Enter or paste text here...\nOpen Notepad on PC and click inside before starting.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$charCount chars • $wordCount words",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Est. duration: ~${estimatedDurationSec}s",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Speed Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Keystroke Delay", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = "${usbDelayMs.toInt()} ms",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = usbDelayMs,
                        onValueChange = { usbDelayMs = it },
                        valueRange = 10f..100f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Typing Controls (Start / Pause / Resume / Stop)
                when (usbTypingProgress) {
                    is UsbTypingProgress.Typing -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.pauseUsbTyping() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("usb_pause_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusYellow)
                            ) {
                                Icon(Icons.Default.Pause, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pause")
                            }

                            Button(
                                onClick = { viewModel.stopUsbTyping() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("usb_stop_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop")
                            }
                        }
                    }

                    is UsbTypingProgress.Paused -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.resumeUsbTyping() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("usb_resume_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Resume")
                            }

                            Button(
                                onClick = { viewModel.stopUsbTyping() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("usb_stop_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusRed)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop")
                            }
                        }
                    }

                    else -> {
                        Button(
                            onClick = {
                                viewModel.startUsbTyping(editorText, usbDelayMs.toInt())
                            },
                            enabled = isSynced && editorText.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("usb_start_typing_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (!isSynced) "Connect & Sync First" else "Start Typing to PC",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // Step-by-Step Instructions Card: "📱 USB Typing — How to Use"
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHowToUse = !showHowToUse },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "📱 USB Typing — How to Use",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (showHowToUse) "Hide" else "Show",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = showHowToUse) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        InstructionStep(
                            stepNumber = "1",
                            title = "Install the Windows Companion",
                            description = "Download replica-companion.exe and run it on your Windows laptop or PC."
                        ) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { viewModel.shareCompanionFile(context) },
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share .exe", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.shareCompanionDownloadLink(context, "http://127.0.0.1:3000")
                                    },
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Get Link", fontSize = 12.sp)
                                }
                            }
                        }

                        InstructionStep(
                            stepNumber = "2",
                            title = "Connect Your Phone",
                            description = "Connect your Android phone to your PC using a reliable USB data cable."
                        )

                        InstructionStep(
                            stepNumber = "3",
                            title = "Enable USB Debugging",
                            description = "1. Open Phone Settings -> Developer Options.\n2. Turn on USB Debugging.\n3. Approve the computer RSA authorization prompt on your phone screen."
                        )

                        InstructionStep(
                            stepNumber = "4",
                            title = "Open USB Typing",
                            description = "You're already here! Make sure replica-companion.exe is running in a terminal on your PC."
                        )

                        InstructionStep(
                            stepNumber = "5",
                            title = "Check Synchronization",
                            description = "Tap 'Check Connection'. The app verifies handshake & protocol compatibility.\n• ✅ Synced: Communication is verified.\n• 🔄 Connecting: Connection being established.\n• ❌ Disconnected: Check USB cable.\n• ⚠️ Incompatible: Update companion program."
                        )

                        InstructionStep(
                            stepNumber = "6",
                            title = "Start Typing",
                            description = "1. Open Notepad on your PC.\n2. Click inside the typing area.\n3. Tap 'Start Typing to PC' on your phone.\n4. Use Pause, Resume, or Stop whenever needed."
                        )

                        // Troubleshooting notes
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "💡 Troubleshooting Tips:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "• Try another USB data cable or USB port.\n• Make sure USB Debugging is enabled in Developer Options.\n• Run 'adb forward tcp:8989 tcp:8989' if prompted.\n• Ensure replica-companion.exe shows [READY].\n• Important: USB Debugging alone does not make the phone a keyboard—the companion exe is required.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionStep(
    stepNumber: String,
    title: String,
    description: String,
    extraContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stepNumber,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
            extraContent?.invoke()
        }
    }
}
