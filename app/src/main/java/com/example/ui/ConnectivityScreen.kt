package com.example.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth.HidConnectionState
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusYellow
import com.example.usb.NetworkConnectionType
import com.example.usb.UsbConnectionState

@Composable
fun ConnectivityScreen(
    viewModel: MainViewModel,
    onRequestBluetoothPermissions: () -> Unit = {},
    onOpenTroubleshooting: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val btState by viewModel.connectionState.collectAsState()
    val usbState by viewModel.usbConnectionState.collectAsState()
    val discoveredServers by viewModel.discoveredServers.collectAsState()
    val isDiscovering by viewModel.isDiscoveringServers.collectAsState()
    val preferredMode by viewModel.preferredConnectionMode.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()

    var showManualIpDialog by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf("192.168.1.") }

    val isBtConnected = btState is HidConnectionState.Connected
    val isNetSynced = usbState is UsbConnectionState.Synced
    val isConnected = isBtConnected || isNetSynced

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Unified Master Connection Status Banner
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isConnected) StatusGreen.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(
                    if (isConnected) StatusGreen.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ),
            modifier = Modifier.fillMaxWidth().testTag("master_connection_status_card")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                if (isConnected) StatusGreen.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surface
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isNetSynced -> Icons.Default.Wifi
                                isBtConnected -> Icons.Default.BluetoothConnected
                                else -> Icons.Default.Sensors
                            },
                            contentDescription = "Status Icon",
                            tint = if (isConnected) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isNetSynced -> "🟢 Connected via Wi-Fi / USB"
                                isBtConnected -> "🟢 Connected via Bluetooth HID"
                                else -> "🔴 Not Connected to Host"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (isConnected) StatusGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = when {
                                isNetSynced -> {
                                    val synced = usbState as UsbConnectionState.Synced
                                    "Server: ${synced.host}:${synced.port} • Ping: ${synced.latencyMs}ms"
                                }
                                isBtConnected -> {
                                    val dev = (btState as HidConnectionState.Connected).deviceName
                                    "Paired Host: $dev"
                                }
                                else -> "Select Wi-Fi/USB or Bluetooth below to connect"
                            },
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isConnected) {
                        OutlinedButton(
                            onClick = {
                                if (isNetSynced) viewModel.usbTypingManager.disconnect("User disconnected")
                                if (isBtConnected) viewModel.hidManager.disconnectDevice()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.testTag("disconnect_active_button")
                        ) {
                            Text("Disconnect", fontSize = 12.sp)
                        }
                    }
                }

                // Transport Preference Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Preference:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilterChip(
                        selected = preferredMode == "AUTO",
                        onClick = { viewModel.setPreferredConnectionMode("AUTO") },
                        label = { Text("Auto (Recommended)", fontSize = 11.sp) },
                        modifier = Modifier.testTag("pref_chip_auto")
                    )
                    FilterChip(
                        selected = preferredMode == "WIFI",
                        onClick = { viewModel.setPreferredConnectionMode("WIFI") },
                        label = { Text("Wi-Fi / USB", fontSize = 11.sp) },
                        modifier = Modifier.testTag("pref_chip_wifi")
                    )
                    FilterChip(
                        selected = preferredMode == "BLUETOOTH",
                        onClick = { viewModel.setPreferredConnectionMode("BLUETOOTH") },
                        label = { Text("Bluetooth", fontSize = 11.sp) },
                        modifier = Modifier.testTag("pref_chip_bt")
                    )
                }
            }
        }

        // 2. Wi-Fi & USB Tethering Section
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth().testTag("wifi_usb_section_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = "Wi-Fi & USB",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Wi-Fi & USB Companion",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Works via Wi-Fi, USB Tethering, Hotspot & ADB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Scan / Refresh Button
                    Button(
                        onClick = { viewModel.startNetworkDiscovery() },
                        enabled = !isDiscovering,
                        modifier = Modifier.testTag("scan_network_button")
                    ) {
                        if (isDiscovering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Scanning...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Refresh", fontSize = 12.sp)
                        }
                    }
                }

                // Discovered Servers List
                if (discoveredServers.isNotEmpty()) {
                    Text(
                        text = "Discovered Companion Servers (${discoveredServers.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        discoveredServers.forEach { server ->
                            val isThisServerSynced = (usbState as? UsbConnectionState.Synced)?.host == server.host

                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isThisServerSynced) StatusGreen.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                ),
                                border = if (isThisServerSynced) {
                                    CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StatusGreen))
                                } else null,
                                modifier = Modifier.fillMaxWidth().testTag("discovered_server_${server.host}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = when (server.connectionType) {
                                                NetworkConnectionType.USB_ADB, NetworkConnectionType.USB_TETHER -> Icons.Default.Usb
                                                else -> Icons.Default.Wifi
                                            },
                                            contentDescription = "Server type",
                                            tint = if (isThisServerSynced) StatusGreen else MaterialTheme.colorScheme.primary
                                        )
                                        Column {
                                            Text(
                                                text = server.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "${server.host}:${server.port} • ${server.latencyMs}ms ping",
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    if (isThisServerSynced) {
                                        Text(
                                            text = "Connected",
                                            color = StatusGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    } else {
                                        Button(
                                            onClick = { viewModel.connectToNetworkHost(server.host, server.port) },
                                            modifier = Modifier.testTag("connect_server_${server.host}")
                                        ) {
                                            Text("Connect", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (!isDiscovering) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Info",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Column {
                                Text(
                                    text = "No companion servers discovered yet.",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Ensure replica-companion.exe is running on PC, then tap Refresh.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Quick Action Buttons (USB Tether Settings + Manual IP)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { openTetherSettings(context) },
                        modifier = Modifier.weight(1f).testTag("open_tether_settings_button")
                    ) {
                        Icon(Icons.Default.SettingsEthernet, contentDescription = "Tethering", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("USB Tethering", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { showManualIpDialog = true },
                        modifier = Modifier.weight(1f).testTag("manual_ip_dialog_button")
                    ) {
                        Icon(Icons.Default.Cable, contentDescription = "Custom IP", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Connect by IP", fontSize = 12.sp)
                    }
                }

                // Instructions Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "💡 Connection Guide for Wi-Fi & USB:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Wi-Fi Mode: Ensure phone & PC are on the same Wi-Fi network, launch replica-companion.exe on PC, and tap Refresh.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• USB Tethering: Connect phone via USB cable, tap 'USB Tethering' above to turn it ON, launch replica-companion.exe, and tap Refresh.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "• USB ADB Mode: Connect USB cable, keep USB Debugging ON, and launch replica-companion.exe on PC.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 3. Bluetooth HID Keyboard Section
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = CardDefaults.outlinedCardBorder(),
            modifier = Modifier.fillMaxWidth().testTag("bluetooth_hid_section_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = "Bluetooth",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Bluetooth HID Keyboard",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Native Bluetooth HID profile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.refreshBluetooth() },
                        modifier = Modifier.testTag("refresh_bt_button")
                    ) {
                        Text("Refresh", fontSize = 12.sp)
                    }
                }

                // OEM Incompatibility / Fallback Banner
                val isOemProblem = viewModel.hidManager.isOemKnownToDisableHid || btState is HidConnectionState.NotSupported
                if (isOemProblem) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = StatusYellow.copy(alpha = 0.15f)),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(StatusYellow.copy(alpha = 0.5f))),
                        modifier = Modifier.fillMaxWidth().testTag("oem_warning_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = StatusYellow,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "OEM Bluetooth HID Notice",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Many device manufacturers (OnePlus, Xiaomi, Motorola, Vivo, Nokia) ship with Bluetooth HID Device profile disabled in ROM. Wi-Fi / USB mode is available and works on all devices!",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                                TextButton(
                                    onClick = {
                                        viewModel.setPreferredConnectionMode("WIFI")
                                        viewModel.startNetworkDiscovery()
                                    },
                                    modifier = Modifier.testTag("switch_to_wifi_button")
                                ) {
                                    Text("Switch to Wi-Fi / USB Mode →", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Paired Bluetooth Devices
                if (pairedDevices.isNotEmpty()) {
                    Text(
                        text = "Paired Devices (${pairedDevices.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        pairedDevices.forEach { dev ->
                            val devName = viewModel.hidManager.getSafeDeviceName(dev)
                            val isThisConnected = (btState as? HidConnectionState.Connected)?.device?.address == dev.address

                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isThisConnected) StatusGreen.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("bt_device_${dev.address}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(devName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(dev.address, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    if (isThisConnected) {
                                        Text("Connected", color = StatusGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    } else {
                                        Button(
                                            onClick = { viewModel.connectDevice(dev) },
                                            modifier = Modifier.testTag("connect_bt_${dev.address}")
                                        ) {
                                            Text("Connect", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bluetooth Discovery & Pairing Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.hidManager.requestDiscoverability(context, 120) },
                        modifier = Modifier.weight(1f).testTag("make_discoverable_button")
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = "Discoverable", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Discoverable", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { viewModel.hidManager.openBluetoothSettings(context) },
                        modifier = Modifier.weight(1f).testTag("open_bt_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Pair New", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pair New PC", fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Manual IP Connect Dialog
    if (showManualIpDialog) {
        AlertDialog(
            onDismissRequest = { showManualIpDialog = false },
            title = { Text("Connect to Companion by IP") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your PC's IP address (e.g. from ipconfig on Windows or USB tether network):",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = manualIpInput,
                        onValueChange = { manualIpInput = it.trim() },
                        label = { Text("PC IP Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("manual_ip_input_field")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showManualIpDialog = false
                        if (manualIpInput.isNotBlank()) {
                            viewModel.connectToNetworkHost(manualIpInput, 8989)
                        }
                    },
                    modifier = Modifier.testTag("manual_ip_confirm_button")
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualIpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun openTetherSettings(context: Context) {
    try {
        val tetherIntent = Intent().apply {
            component = ComponentName("com.android.settings", "com.android.settings.TetherSettings")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(tetherIntent)
    } catch (_: Exception) {
        try {
            val intent = Intent("android.settings.TETHER_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }
}
