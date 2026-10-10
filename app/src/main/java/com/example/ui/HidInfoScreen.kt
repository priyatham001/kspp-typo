package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.typing.TypingState
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow

@Composable
fun HidInfoScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val isHidRegistered by viewModel.isHidRegistered.collectAsState()
    val typingState by viewModel.typingState.collectAsState()
    val typingDelayMs by viewModel.typingDelayMs.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "HID Device Info",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Real-time Bluetooth profile status & hardware diagnostics",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { viewModel.refreshBluetooth() },
                modifier = Modifier.testTag("refresh_hid_info_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Status",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Live Connection & Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionState) {
                                    is HidConnectionState.Connected -> StatusGreen
                                    is HidConnectionState.Connecting -> StatusYellow
                                    else -> if (isHidRegistered) StatusYellow else StatusRed
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when (val s = connectionState) {
                            is HidConnectionState.Connected -> "HOST CONNECTED"
                            is HidConnectionState.Connecting -> "CONNECTING TO HOST"
                            is HidConnectionState.Disconnected -> if (isHidRegistered) "HID READY (AWAITING HOST)" else "DISCONNECTED"
                            is HidConnectionState.Registering -> "REGISTERING PROFILE"
                            is HidConnectionState.BluetoothDisabled -> "BLUETOOTH DISABLED"
                            is HidConnectionState.PermissionRequired -> "PERMISSION REQUIRED"
                            is HidConnectionState.Unavailable -> "BLUETOOTH UNAVAILABLE"
                            is HidConnectionState.NotSupported -> "HID NOT SUPPORTED"
                            is HidConnectionState.Error -> "ERROR"
                        },
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        fontSize = 14.sp,
                        color = when (connectionState) {
                            is HidConnectionState.Connected -> StatusGreen
                            is HidConnectionState.Connecting -> StatusYellow
                            else -> if (isHidRegistered) StatusYellow else StatusRed
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                InfoRow(label = "HID App Registered", value = if (isHidRegistered) "YES (Active in Bluetooth stack)" else "NO / Inactive")
                InfoRow(
                    label = "Connected Host Name",
                    value = when (val s = connectionState) {
                        is HidConnectionState.Connected -> s.deviceName
                        is HidConnectionState.Connecting -> s.deviceName ?: "Negotiating..."
                        else -> "None"
                    }
                )
                InfoRow(label = "Host MAC Address", value = connectedDevice?.address ?: "N/A")
                InfoRow(label = "Paired Devices", value = "${pairedDevices.size} paired devices found")
                InfoRow(
                    label = "Typing Engine",
                    value = when (typingState) {
                        is TypingState.Typing -> "Active (Streaming reports)"
                        is TypingState.Paused -> "Paused"
                        is TypingState.Completed -> "Completed"
                        is TypingState.Stopped -> "Stopped"
                        is TypingState.Error -> "Error"
                        TypingState.Idle -> "Idle"
                    }
                )
                InfoRow(label = "Keystroke Delay", value = "$typingDelayMs ms / character")

                Spacer(modifier = Modifier.height(12.dp))

                // Actions: Discoverability & System Bluetooth Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.requestDiscoverability(context) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Bluetooth, contentDescription = "Discoverable", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Make Discoverable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.openBluetoothSettings(context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bluetooth Settings", fontSize = 11.sp)
                    }
                }
            }
        }

        // HID Descriptor Technical Breakdown Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Descriptor",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "HID Descriptor Specification",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                InfoRow(label = "Profile Standard", value = "USB HID 1.11 / Bluetooth HID Device Profile (19)")
                InfoRow(label = "Usage Page", value = "Generic Desktop (0x01) -> Keyboard (0x06)")
                InfoRow(label = "Report ID", value = "1 (Multiplexed HID Keyboard)")
                InfoRow(label = "Report Size", value = "8 bytes input report")
                InfoRow(label = "Byte 0 (Modifiers)", value = "8-bit bitmask (LCtrl, LShift, LAlt, LGUI, RCtrl, RShift, RAlt, RGUI)")
                InfoRow(label = "Byte 1", value = "Reserved (0x00)")
                InfoRow(label = "Bytes 2 - 7", value = "6-Key Rollover array (Standard Key Usages)")
                InfoRow(label = "Output Report", value = "5 LED status bits (Num Lock, Caps Lock, Scroll Lock)")
                InfoRow(label = "Layout Supported", value = "Standard US QWERTY + Symbol Shift Mapping")
            }
        }

        // Copyable Diagnostics Card
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Diagnostics",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Diagnostic Report",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val summary = viewModel.getDiagnosticsSummary()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("replica_kspp HID Diagnostics", summary)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Diagnostics copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("COPY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(10.dp)
                ) {
                    Text(
                        text = viewModel.getDiagnosticsSummary(),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Troubleshooting & PC Connection Guide Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "Troubleshooting",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Windows PC Connection Guide",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                val tips = listOf(
                    "1. Pair First: On Windows, open Settings > Bluetooth & Devices > Add Device > Bluetooth.",
                    "2. Make Discoverable: Tap [Make Discoverable] above on your phone so Windows can see it.",
                    "3. Select Phone: In Windows, select your Android phone and confirm the pairing PIN on both screens.",
                    "4. Driver Installation: Windows will automatically configure your phone as an HID Keyboard.",
                    "5. Open Target Window: Open Windows Notepad or CodeTantra and click to ensure the cursor is active.",
                    "6. Test Keystrokes: Go to the 'Test' tab in this app and tap keys or 'TEST ALL KEYS' to verify input.",
                    "7. Start Auto-Typing: Go to the 'Editor' tab, load or paste code, and tap [START AUTO-TYPING]."
                )

                tips.forEach { tip ->
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
