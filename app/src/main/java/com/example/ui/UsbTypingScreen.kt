package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow
import com.example.usb.UsbConnectionState
import com.example.usb.UsbTypingProgress
import kotlinx.coroutines.launch

@Composable
fun UsbTypingScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val usbState by viewModel.usbConnectionState.collectAsState()
    val isUsbCableConnected by viewModel.isUsbCableConnected.collectAsState()
    val isDeveloperOptionsEnabled by viewModel.isDeveloperOptionsEnabled.collectAsState()
    val isUsbDebuggingEnabled by viewModel.isUsbDebuggingEnabled.collectAsState()
    val diagnosticsReport by viewModel.usbDiagnosticsReport.collectAsState()
    val usbTypingProgress by viewModel.usbTypingProgress.collectAsState()
    val companionInfo by viewModel.companionInfo.collectAsState()
    val editorText by viewModel.editorText.collectAsState()

    var isCheckingConnection by remember { mutableStateOf(false) }
    var showSetupAssistant by remember { mutableStateOf(true) }
    var showDetailedDiagnostics by remember { mutableStateOf(true) }
    var usbDelayMs by remember { mutableFloatStateOf(25f) }

    // Refresh system settings whenever user returns from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUsbSystemSettings()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val scrollState = rememberScrollState()

    val charCount = editorText.length
    val wordCount = remember(editorText) {
        if (editorText.isBlank()) 0 else editorText.trim().split("\\s+".toRegex()).size
    }
    val estimatedDurationSec = remember(charCount, usbDelayMs) {
        (charCount * usbDelayMs.toLong()) / 1000
    }

    val isSynced = usbState is UsbConnectionState.Synced
    val syncedState = usbState as? UsbConnectionState.Synced

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
                        contentDescription = "Connect & Diagnose",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 1. Main Connection & Sync Status Card
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
                // Top status bar
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
                            text = when (usbState) {
                                is UsbConnectionState.Synced -> "SYNCED (READY TO TYPE)"
                                is UsbConnectionState.Connecting -> "PROBING COMPANION..."
                                is UsbConnectionState.Connected -> "SOCKET OPEN (HANDSHAKING)"
                                is UsbConnectionState.Incompatible -> "INCOMPATIBLE COMPANION"
                                is UsbConnectionState.Error -> "CONNECTION ERROR"
                                is UsbConnectionState.Disconnected -> "NOT SYNCED (COMPANION UNREACHED)"
                            },
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            fontSize = 13.sp,
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
                            fontWeight = FontWeight.SemiBold,
                            color = if (isUsbCableConnected) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 8-Stage Real Verification Checklist (Never marks a stage green without verification)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = "REAL-TIME CONNECTION STAGE VERIFICATION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    StatusRowItem(
                        icon = Icons.Default.Cable,
                        label = "1. USB Cable Connected",
                        value = if (isUsbCableConnected) "Verified (USB Power/Data)" else "Not Detected",
                        isGood = isUsbCableConnected
                    )
                    StatusRowItem(
                        icon = Icons.Default.DeveloperMode,
                        label = "2. USB Debugging Enabled",
                        value = when {
                            isUsbDebuggingEnabled -> "Enabled (ADB Active)"
                            isDeveloperOptionsEnabled -> "OFF (Enable in Dev Options)"
                            else -> "OFF (Enable Dev Options first)"
                        },
                        isGood = isUsbDebuggingEnabled
                    )
                    StatusRowItem(
                        icon = Icons.Default.Computer,
                        label = "3. ADB Device Detected",
                        value = when {
                            isSynced -> diagnosticsReport.companionAdbDevices ?: "Detected on PC"
                            isUsbCableConnected && isUsbDebuggingEnabled -> "Waiting for PC adb.exe"
                            else -> "Not Detected"
                        },
                        isGood = isSynced
                    )
                    StatusRowItem(
                        icon = Icons.Default.Security,
                        label = "4. PC Authorized ('Allow USB debugging?')",
                        value = when {
                            isSynced && diagnosticsReport.companionDeviceAuthorized != false -> "Authorized"
                            isUsbCableConnected && isUsbDebuggingEnabled -> "Approve popup on phone / Check PC ADB"
                            else -> "Pending USB Debugging"
                        },
                        isGood = isSynced && diagnosticsReport.companionDeviceAuthorized != false
                    )
                    StatusRowItem(
                        icon = Icons.Default.Terminal,
                        label = "5. Windows Companion Running",
                        value = when (val s = usbState) {
                            is UsbConnectionState.Synced -> "v${s.companionVersion} Active (${s.deviceOs})"
                            is UsbConnectionState.Incompatible -> "Incompatible (${s.companionVersion})"
                            else -> "Not Reached (Run replica-companion.exe)"
                        },
                        isGood = isSynced
                    )
                    StatusRowItem(
                        icon = Icons.Default.Build,
                        label = "6. USB Tunnel Ready (8989 / 8990)",
                        value = when {
                            isSynced -> syncedState?.tunnelMode ?: "Reverse 8989 + Forward 8990 OK"
                            diagnosticsReport.phoneServerListening8990 -> "Phone port 8990 ready; PC 8989 unreached"
                            else -> "Tunnels not established"
                        },
                        isGood = isSynced
                    )
                    StatusRowItem(
                        icon = Icons.Default.Sync,
                        label = "7. Phone and PC Synced (Handshake v1)",
                        value = if (isSynced) "SYN/ACK Verified (${syncedState?.latencyMs ?: 1} ms)" else "Not Synced",
                        isGood = isSynced
                    )
                    StatusRowItem(
                        icon = Icons.Default.Keyboard,
                        label = "8. Ready to Type",
                        value = if (isSynced) "Ready to stream keystrokes" else "Blocked (Complete Sync first)",
                        isGood = isSynced
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Exact Failed Stage & Practical Fix Banner when not synced
                when (val s = usbState) {
                    is UsbConnectionState.Synced -> {
                        Text(
                            text = "Windows Companion v${s.companionVersion} verified (${s.tunnelMode}). Handshake & PING/PONG heartbeat active (${s.latencyMs} ms). Ready to type into Windows.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    is UsbConnectionState.Disconnected -> {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = StatusRed.copy(alpha = 0.08f)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = StatusRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Failed Stage: ${s.failedStage}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = StatusRed
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = s.reason,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "How to Fix:\n${s.fixSuggestion}",
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    is UsbConnectionState.Error -> {
                        Text(
                            text = "Error (${s.failedStage}): ${s.message}\nFix: ${s.fixSuggestion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusRed
                        )
                    }
                    is UsbConnectionState.Incompatible -> {
                        Text(
                            text = "Incompatible Companion: ${s.reason}. Companion version is ${s.companionVersion}, requires ${s.requiredVersion}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    is UsbConnectionState.Connecting -> {
                        Text(text = s.message, style = MaterialTheme.typography.bodySmall)
                    }
                    is UsbConnectionState.Connected -> {
                        Text(
                            text = "TCP socket connected on ${s.host}:${s.port}. Completing HANDSHAKE_SYN / HANDSHAKE_ACK...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Action Row: Connect & Diagnose + Share Companion EXE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                isCheckingConnection = true
                                viewModel.checkUsbConnection()
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
                            Text("Diagnosing...")
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connect & Diagnose", fontWeight = FontWeight.Bold)
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

                // Quick Notepad Test Button when Synced
                if (isSynced) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { viewModel.sendUsbQuickTestMessage() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("usb_quick_test_typing_btn")
                    ) {
                        Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Typing (Focus Windows Notepad First)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // 2. Guided 6-Step Automatic USB Setup Assistant Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("usb_setup_assistant_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSetupAssistant = !showSetupAssistant },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "USB Setup Assistant (6 Steps)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Guided phone & Windows PC setup with direct Settings buttons",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = if (showSetupAssistant) "Hide" else "Show",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = showSetupAssistant) {
                    Column(
                        modifier = Modifier.padding(top = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Step 1: Enable Developer Options
                        SetupAssistantStep(
                            stepNumber = "1",
                            title = "Enable Developer Options",
                            isVerified = isDeveloperOptionsEnabled,
                            statusLabel = if (isDeveloperOptionsEnabled) "Enabled" else "Action Required",
                            description = "Open Android Settings > About Phone and tap 'Build Number' 7 times until you see 'You are now a developer!'."
                        ) {
                            Row(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { openDeviceInfoOrSettings(context) },
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("step1_open_settings_btn")
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Step 2: Enable USB Debugging
                        SetupAssistantStep(
                            stepNumber = "2",
                            title = "Enable USB Debugging",
                            isVerified = isUsbDebuggingEnabled,
                            statusLabel = if (isUsbDebuggingEnabled) "Enabled" else "Turn ON in Dev Options",
                            description = "Open Settings > System > Developer options, scroll down to the Debugging section, and toggle ON 'USB debugging'."
                        ) {
                            Row(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { openDeveloperOptionsSettings(context) },
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("step2_open_dev_options_btn")
                                ) {
                                    Icon(Icons.Default.DeveloperMode, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open Developer Options", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Step 3: Connect to PC
                        SetupAssistantStep(
                            stepNumber = "3",
                            title = "Connect to PC",
                            isVerified = isUsbCableConnected,
                            statusLabel = if (isUsbCableConnected) "Cable Detected" else "Connect USB Cable",
                            description = "Unlock your phone screen and connect it to your Windows PC using a USB data cable (charge-only cables cannot carry ADB data). If prompted for USB mode, select 'File Transfer / MTP' or 'PTP'."
                        )

                        // Step 4: Authorize This PC
                        SetupAssistantStep(
                            stepNumber = "4",
                            title = "Authorize This PC ('Allow USB debugging?')",
                            isVerified = isSynced,
                            statusLabel = if (isSynced) "PC Authorized" else "Requires PC ADB",
                            description = "IMPORTANT: The 'Allow USB debugging?' popup is controlled by Android OS and appears ONLY when Windows ADB (adb.exe) actively communicates with your phone.\n" +
                                "• When the popup appears on your phone, check 'Always allow from this computer' and tap 'Allow'.\n" +
                                "• If the popup does NOT appear: (1) Ensure adb.exe (Android SDK Platform-Tools) is installed alongside replica-companion.exe on your PC; (2) In Developer Options, tap 'Revoke USB debugging authorizations', then unplug and replug the USB cable."
                        ) {
                            Row(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { openDeveloperOptionsSettings(context) },
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("step4_revoke_auth_settings_btn")
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Developer Options (Revoke / Re-auth)", fontSize = 11.sp)
                                }
                            }
                        }

                        // Step 5: Start Windows Companion
                        SetupAssistantStep(
                            stepNumber = "5",
                            title = "Start Windows Companion (v${companionInfo.version})",
                            isVerified = isSynced,
                            statusLabel = if (isSynced) "Running & Connected" else "Run on Windows PC",
                            description = "Run replica-companion.exe on your Windows PC. It automatically finds adb.exe, starts the ADB server, checks 'adb devices', configures 'adb reverse tcp:8989 tcp:8989' and 'adb forward tcp:8990 tcp:8990', and listens for this phone."
                        ) {
                            Row(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { viewModel.shareCompanionFile(context) },
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("step5_share_exe_btn")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Share .exe (${companionInfo.fileSize / 1024} KB)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        viewModel.shareCompanionDownloadLink(context, "http://127.0.0.1:3000")
                                    },
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Get Link", fontSize = 11.sp)
                                }
                            }
                        }

                        // Step 6: Check Synchronization
                        SetupAssistantStep(
                            stepNumber = "6",
                            title = "Check Synchronization",
                            isVerified = isSynced,
                            statusLabel = if (isSynced) "Synced & Verified" else "Tap to Verify",
                            description = "Tap 'Check Connection' below to verify the socket connection, complete the Protocol v1 HANDSHAKE_SYN / HANDSHAKE_ACK exchange, and confirm PING/PONG heartbeat."
                        ) {
                            Row(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            isCheckingConnection = true
                                            viewModel.checkUsbConnection()
                                            isCheckingConnection = false
                                        }
                                    },
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("step6_check_connection_btn")
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Connect & Check Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Windows PC Command Helper & Companion Telemetry Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("usb_diagnostics_commands_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDetailedDiagnostics = !showDetailedDiagnostics },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ADB Diagnostics & Manual PC Commands",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (showDetailedDiagnostics) "Hide" else "Show",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = showDetailedDiagnostics) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSynced) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        "Live Companion Telemetry:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = StatusGreen
                                    )
                                    Text(
                                        "• ADB Path: ${diagnosticsReport.companionAdbPath ?: "Configured on PC"}\n" +
                                            "• ADB Version: ${diagnosticsReport.companionAdbVersion ?: "Active"}\n" +
                                            "• ADB Devices: ${diagnosticsReport.companionAdbDevices ?: "Authorized"}\n" +
                                            "• Reverse (8989): ${diagnosticsReport.companionReverseStatus ?: "Active"}\n" +
                                            "• Forward (8990): ${diagnosticsReport.companionForwardStatus ?: "Active"}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }

                        val manualCommands = "adb devices\nadb reverse tcp:8989 tcp:8989\nadb forward tcp:8990 tcp:8990"
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Windows PowerShell / CMD Tunnel Commands:",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("ADB Commands", manualCommands))
                                            Toast.makeText(context, "ADB commands copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Commands", modifier = Modifier.size(14.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = manualCommands,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Live Typing Progress Card (visible when typing, paused, stopped, or completed)
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

        // 5. Text Editor for USB Typing
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
    }
}

private fun openDeviceInfoOrSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "Open Android Settings > About Phone > Tap Build Number 7 times",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

private fun openDeveloperOptionsSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            Toast.makeText(
                context,
                "Go to System > Developer options > Enable USB debugging",
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            Toast.makeText(
                context,
                "Open Settings > System > Developer options > Enable USB debugging",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

@Composable
private fun StatusRowItem(
    icon: ImageVector,
    label: String,
    value: String,
    isGood: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isGood) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isGood) StatusGreen else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SetupAssistantStep(
    stepNumber: String,
    title: String,
    isVerified: Boolean,
    statusLabel: String,
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
                .background(if (isVerified) StatusGreen else MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isVerified) "✓" else stepNumber,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Step $stepNumber: $title",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isVerified) StatusGreen.copy(alpha = 0.15f)
                            else StatusYellow.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isVerified) StatusGreen else StatusYellow
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
            extraContent?.invoke()
        }
    }
}
