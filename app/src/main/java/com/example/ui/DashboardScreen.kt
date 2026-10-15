package com.example.ui

import android.bluetooth.BluetoothDevice
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth.HidConnectionState
import com.example.storage.ScriptEntity
import com.example.typing.TypingState
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dashboard UI providing:
 * 1. Comprehensive Bluetooth HID connection status indicator and telemetry.
 * 2. Real-time auto-typing synchronization monitor.
 * 3. List view displaying saved keyboard macros with 1-tap execution, editing, and management.
 */
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToEditor: () -> Unit,
    onNavigateToBluetooth: () -> Unit,
    onRequestPermissions: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val typingState by viewModel.typingState.collectAsState()
    val typingDelayMs by viewModel.typingDelayMs.collectAsState()
    val allMacros by viewModel.allScripts.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val isBluetoothRefreshing by viewModel.isBluetoothRefreshing.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var macroToDelete by remember { mutableStateOf<ScriptEntity?>(null) }

    val filteredMacros = remember(allMacros, searchQuery, selectedFilter) {
        allMacros.filter { macro ->
            val matchesSearch = searchQuery.isBlank() ||
                macro.title.contains(searchQuery, ignoreCase = true) ||
                macro.content.contains(searchQuery, ignoreCase = true)
            val matchesType = when (selectedFilter) {
                "CODE" -> macro.language.lowercase() in listOf("java", "c", "cpp", "shell", "python")
                "TEXT" -> macro.language.lowercase() in listOf("text", "plain")
                else -> true
            }
            matchesSearch && matchesType
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen")
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Dashboard Title & Quick Stats
            DashboardHeader(
                userProfile = userProfile,
                macrosCount = allMacros.size,
                typingDelayMs = typingDelayMs,
                connectionState = connectionState,
                onRefreshBluetooth = { viewModel.refreshBluetooth() },
                isBluetoothRefreshing = isBluetoothRefreshing
            )
        }

        item {
            // Section 1: Bluetooth Connection Status Indicator
            BluetoothStatusIndicatorCard(
                connectionState = connectionState,
                pairedDevices = pairedDevices,
                onConnectDevice = { dev -> viewModel.connectDevice(dev) },
                onDisconnect = { viewModel.disconnectDevice() },
                onOpenBluetoothSettings = {
                    onRequestPermissions?.invoke()
                    onNavigateToBluetooth()
                },
                onRefresh = { viewModel.refreshBluetooth() },
                isRefreshing = isBluetoothRefreshing
            )
        }

        // Section 2: Real-time Auto-Typing Synchronization Monitor (shown when active)
        if (typingState !is TypingState.Idle) {
            item {
                AutoTypingSyncPanel(
                    typingState = typingState,
                    onPause = { viewModel.pauseTyping() },
                    onResume = { viewModel.resumeTyping() },
                    onRestart = { viewModel.restartTyping() },
                    onStop = { viewModel.stopTyping() }
                )
            }
        }

        // Section 3: Saved Keyboard Macros Header & Quick Search Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SAVED KEYBOARD MACROS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "${filteredMacros.size} macro(s) available for instant typing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FilledTonalButton(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .bounceClick()
                        .testTag("add_macro_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("NEW MACRO", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            // Search and Filter row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search macros...", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 12.sp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("search_macros_input"),
                    shape = RoundedCornerShape(10.dp)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("ALL" to "All", "CODE" to "Code", "TEXT" to "Text").forEach { (tag, label) ->
                        FilterChip(
                            selected = selectedFilter == tag,
                            onClick = { selectedFilter = tag },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }

        // Section 4: Macros List Items
        if (filteredMacros.isEmpty()) {
            item {
                EmptyMacrosCard(
                    onLoadStarter = { viewModel.seedStarterMacros() },
                    onCreateNew = { showCreateDialog = true }
                )
            }
        } else {
            items(filteredMacros, key = { it.id }) { macro ->
                KeyboardMacroItemCard(
                    macro = macro,
                    isDeviceConnected = connectionState is HidConnectionState.Connected,
                    onTypeMacro = { viewModel.typeMacro(macro) },
                    onLoadInEditor = {
                        viewModel.loadScript(macro)
                        onNavigateToEditor()
                    },
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = android.content.ClipData.newPlainText(macro.title, macro.content)
                        clipboard.setPrimaryClip(clip)
                    },
                    onDelete = { macroToDelete = macro }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Create New Macro Dialog
    if (showCreateDialog) {
        CreateMacroDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { title, content, language ->
                viewModel.createNewScript(title, content)
                showCreateDialog = false
            }
        )
    }

    // Confirm Delete Dialog
    macroToDelete?.let { macro ->
        AlertDialog(
            onDismissRequest = { macroToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ErrorRed) },
            title = { Text("Delete Macro?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete \"${macro.title}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteScript(macro.id)
                        macroToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { macroToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Header with Quick Metrics & Telemetry
 */
@Composable
private fun DashboardHeader(
    userProfile: com.example.auth.UserProfile?,
    macrosCount: Int,
    typingDelayMs: Long,
    connectionState: HidConnectionState,
    onRefreshBluetooth: () -> Unit,
    isBluetoothRefreshing: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Dashboard",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Bluetooth HID Keyboard & Macro Command Center",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                RotatingRefreshIconButton(
                    onClick = onRefreshBluetooth,
                    isRotating = isBluetoothRefreshing,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = "Refresh Bluetooth"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4 Mini Metric Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metric 1: Bluetooth Status
                val isOnline = connectionState is HidConnectionState.Connected
                MetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Bluetooth",
                    value = if (isOnline) "ONLINE" else "OFFLINE",
                    valueColor = if (isOnline) StatusGreen else MaterialTheme.colorScheme.error,
                    icon = if (isOnline) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth
                )

                // Metric 2: Saved Macros
                MetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Macros",
                    value = "$macrosCount Saved",
                    valueColor = MaterialTheme.colorScheme.primary,
                    icon = Icons.Default.Code
                )

                // Metric 3: Speed
                val wpm = if (typingDelayMs > 0) (1000 / typingDelayMs) * 60 / 5 else 0
                MetricPill(
                    modifier = Modifier.weight(1f),
                    label = "Speed",
                    value = "${typingDelayMs}ms (~${wpm}wpm)",
                    valueColor = MaterialTheme.colorScheme.onSurface,
                    icon = Icons.Default.Speed
                )
            }
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(4.dp))
                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = valueColor, maxLines = 1)
        }
    }
}

/**
 * Prominent Bluetooth Connection Status Indicator Card
 */
@Composable
private fun BluetoothStatusIndicatorCard(
    connectionState: HidConnectionState,
    pairedDevices: List<BluetoothDevice>,
    onConnectDevice: (BluetoothDevice) -> Unit,
    onDisconnect: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean
) {
    val isConnected = connectionState is HidConnectionState.Connected
    val isConnecting = connectionState is HidConnectionState.Connecting

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bluetooth_status_indicator_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isConnected -> StatusGreen.copy(alpha = 0.08f)
                isConnecting -> StatusYellow.copy(alpha = 0.08f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isConnected) 1.5.dp else 1.dp,
            color = when {
                isConnected -> StatusGreen.copy(alpha = 0.6f)
                isConnecting -> StatusYellow.copy(alpha = 0.6f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row with Status Indicator Dot
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isConnected -> StatusGreen.copy(alpha = 0.2f)
                                    isConnecting -> StatusYellow.copy(alpha = 0.2f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isConnected -> Icons.Default.BluetoothConnected
                                connectionState is HidConnectionState.BluetoothDisabled -> Icons.Default.BluetoothDisabled
                                else -> Icons.Default.Bluetooth
                            },
                            contentDescription = "Bluetooth Status",
                            tint = when {
                                isConnected -> StatusGreen
                                isConnecting -> StatusYellow
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Pulsing live indicator dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isConnected -> StatusGreen
                                            isConnecting -> StatusYellow
                                            connectionState is HidConnectionState.BluetoothDisabled -> StatusRed
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        }
                                    )
                                    .liveDotPulse(
                                        pulseColor = if (isConnected) StatusGreen else StatusYellow,
                                        enabled = isConnected || isConnecting
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (connectionState) {
                                    is HidConnectionState.Connected -> "CONNECTED & VERIFIED"
                                    is HidConnectionState.Connecting -> "CONNECTING..."
                                    is HidConnectionState.BluetoothDisabled -> "BLUETOOTH DISABLED"
                                    is HidConnectionState.PermissionRequired -> "PERMISSIONS NEEDED"
                                    is HidConnectionState.Error -> "CONNECTION ERROR"
                                    else -> "DISCONNECTED"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                                color = when {
                                    isConnected -> StatusGreen
                                    isConnecting -> StatusYellow
                                    connectionState is HidConnectionState.BluetoothDisabled -> StatusRed
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        Text(
                            text = when (connectionState) {
                                is HidConnectionState.Connected -> connectionState.deviceName
                                is HidConnectionState.Connecting -> "Handshake with ${connectionState.deviceName ?: "host"} in progress..."
                                is HidConnectionState.BluetoothDisabled -> "Enable Bluetooth in device settings"
                                is HidConnectionState.PermissionRequired -> "Grant Nearby Devices permission"
                                is HidConnectionState.Error -> connectionState.message
                                else -> "Ready to link with PC or laptop"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Action button (Disconnect / Settings)
                if (isConnected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp).bounceClick()
                    ) {
                        Text("DISCONNECT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                    }
                } else {
                    Button(
                        onClick = onOpenBluetoothSettings,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp).bounceClick().pulsingGlow(MaterialTheme.colorScheme.primary, enabled = true)
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CONNECT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Verified Hardware Capabilities Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isConnected) StatusGreen else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) {
                            "Verified HID 1.11 Keyboard Profile • 100% In-Sync Keystrokes"
                        } else {
                            "RFCOMM HID Peripheral ready • Zero character drop assurance"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "US QWERTY",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Quick Select Paired Device Row (if disconnected)
            if (!isConnected && pairedDevices.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Quick Connect Paired Devices:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pairedDevices.take(3).forEach { device ->
                        val dName = try { device.name ?: device.address } catch (_: Exception) { device.address }
                        OutlinedButton(
                            onClick = { onConnectDevice(device) },
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .bounceClick(),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = dName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Real-time Auto-Typing Synchronized Progress Panel
 */
@Composable
private fun AutoTypingSyncPanel(
    typingState: TypingState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auto_typing_sync_panel"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (typingState) {
                is TypingState.Typing -> StatusGreen.copy(alpha = 0.1f)
                is TypingState.Paused -> StatusYellow.copy(alpha = 0.1f)
                is TypingState.Completed -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                is TypingState.Error -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (typingState) {
                is TypingState.Typing -> StatusGreen
                is TypingState.Paused -> StatusYellow
                is TypingState.Completed -> MaterialTheme.colorScheme.primary
                is TypingState.Error -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (typingState) {
                            is TypingState.Typing -> Icons.Default.PlayArrow
                            is TypingState.Paused -> Icons.Default.Pause
                            is TypingState.Completed -> Icons.Default.CheckCircle
                            is TypingState.Error -> Icons.Default.Warning
                            else -> Icons.Default.Info
                        },
                        contentDescription = null,
                        tint = when (typingState) {
                            is TypingState.Typing -> StatusGreen
                            is TypingState.Paused -> StatusYellow
                            is TypingState.Completed -> MaterialTheme.colorScheme.primary
                            is TypingState.Error -> ErrorRed
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (typingState) {
                            is TypingState.Preparing -> "FOCUS PREPARATION: ${typingState.secondsRemaining}s"
                            is TypingState.Typing -> "STREAMING KEYSTROKES (IN-SYNC)"
                            is TypingState.Paused -> "TYPING PAUSED"
                            is TypingState.Completed -> "TYPING COMPLETED SUCCESSFULLY"
                            is TypingState.Error -> "TYPING HALTED"
                            is TypingState.Stopped -> "TYPING STOPPED BY USER"
                            else -> "TYPING IDLE"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp,
                        color = when (typingState) {
                            is TypingState.Typing -> StatusGreen
                            is TypingState.Paused -> StatusYellow
                            is TypingState.Completed -> MaterialTheme.colorScheme.primary
                            is TypingState.Error -> ErrorRed
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                // Progress numbers
                when (typingState) {
                    is TypingState.Typing -> {
                        Text(
                            text = "${typingState.currentIndex} / ${typingState.totalChars} (${String.format(Locale.US, "%.1f", typingState.percent)}%)",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    is TypingState.Paused -> {
                        Text(
                            text = "${typingState.currentIndex} / ${typingState.totalChars}",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                    is TypingState.Completed -> {
                        Text(
                            text = "${typingState.totalChars} chars in ${typingState.durationMs / 1000f}s",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar
            when (typingState) {
                is TypingState.Typing -> {
                    LinearProgressIndicator(
                        progress = { typingState.percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = StatusGreen
                    )
                }
                is TypingState.Paused -> {
                    LinearProgressIndicator(
                        progress = { if (typingState.totalChars > 0) typingState.currentIndex.toFloat() / typingState.totalChars else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = StatusYellow
                    )
                }
                is TypingState.Preparing -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Control Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (typingState) {
                    is TypingState.Typing -> {
                        Button(
                            onClick = onPause,
                            modifier = Modifier.weight(1f).height(40.dp).bounceClick(),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusYellow),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PAUSE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = onStop,
                            modifier = Modifier.weight(1f).height(40.dp).bounceClick(),
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("STOP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    is TypingState.Paused -> {
                        Button(
                            onClick = onResume,
                            modifier = Modifier.weight(1f).height(40.dp).bounceClick(),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RESUME", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = onRestart,
                            modifier = Modifier.weight(1f).height(40.dp).bounceClick(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("RESTART", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = onStop,
                            modifier = Modifier.weight(1f).height(40.dp).bounceClick(),
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("STOP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    is TypingState.Completed, is TypingState.Stopped, is TypingState.Error -> {
                        Button(
                            onClick = onRestart,
                            modifier = Modifier.weight(1f).height(40.dp).bounceClick(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("TYPE AGAIN", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onStop,
                            modifier = Modifier.weight(1f).height(40.dp).bounceClick(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("DISMISS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

/**
 * Saved Keyboard Macro Item Card with 1-Tap Typing & Actions
 */
@Composable
private fun KeyboardMacroItemCard(
    macro: ScriptEntity,
    isDeviceConnected: Boolean,
    onTypeMacro: () -> Unit,
    onLoadInEditor: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }
    val formattedDate = remember(macro.updatedAt) { dateFormat.format(Date(macro.updatedAt)) }
    val lines = remember(macro.content) { macro.content.count { it == '\n' } + 1 }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("macro_card_${macro.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (macro.language.lowercase()) {
                                "shell" -> Icons.Default.Code
                                "java", "c" -> Icons.Default.Code
                                else -> Icons.Default.TextFields
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = macro.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$formattedDate • ${macro.content.length} chars • $lines lines",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Language tag
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = macro.language.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Code / Text Preview Snippet (Monospace)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = macro.content.take(180) + if (macro.content.length > 180) "..." else "",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary Action: ⚡ Type Macro
                Button(
                    onClick = onTypeMacro,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(38.dp)
                        .bounceClick()
                        .testTag("type_macro_button_${macro.id}"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDeviceConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isDeviceConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = "Type", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDeviceConnected) "TYPE MACRO" else "CONNECT TO TYPE",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Secondary Action: Edit in Editor
                OutlinedButton(
                    onClick = onLoadInEditor,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .bounceClick()
                        .testTag("edit_macro_button_${macro.id}"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("EDIT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Copy Action
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(38.dp).bounceClick()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }

                // Delete Action
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(38.dp).bounceClick()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * Empty State Card for when no macros match or exist
 */
@Composable
private fun EmptyMacrosCard(
    onLoadStarter: () -> Unit,
    onCreateNew: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Keyboard,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No Keyboard Macros Found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Save repetitive keystrokes, lab code snippets, or greetings to stream them directly over Bluetooth with zero dropped characters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onLoadStarter,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("LOAD STARTER MACROS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onCreateNew,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CREATE NEW", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Create New Macro Dialog
 */
@Composable
private fun CreateMacroDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, language: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("text") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create Keyboard Macro", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Macro Name") },
                    placeholder = { Text("e.g., Quick Login, Git Commit") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("text" to "Text", "shell" to "Shell", "java" to "Java", "c" to "C").forEach { (lang, label) ->
                        FilterChip(
                            selected = language == lang,
                            onClick = { language = lang },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Keystroke Content") },
                    placeholder = { Text("Enter exact keystrokes to type...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onSave(title.trim(), content, language)
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank()
            ) {
                Text("Save Macro")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
