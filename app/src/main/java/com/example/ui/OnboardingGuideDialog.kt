package com.example.ui

import com.example.ScreenTab
import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.bluetooth.HidConnectionState
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow
import com.example.usb.UsbConnectionState
import kotlinx.coroutines.launch

/**
 * Interactive spotlight target for guided walkthrough.
 */
enum class ShowMeTarget(
    val title: String,
    val description: String,
    val targetTab: ScreenTab,
    val tag: String,
    val actionHint: String
) {
    BT_STATUS(
        title = "Bluetooth HID Status Banner",
        description = "Shows live connection to your PC. Tap CONNECT to begin pairing.",
        targetTab = ScreenTab.EDITOR,
        tag = "connection_status_banner",
        actionHint = "Displays current state: Disconnected, Connecting, or Connected to host PC."
    ),
    USB_SHORTCUT(
        title = "USB Cable Typing Shortcut",
        description = "Tap this card to switch to USB mode when you connect via cable.",
        targetTab = ScreenTab.EDITOR,
        tag = "home_usb_typing_shortcut_card",
        actionHint = "Navigates straight to USB Typing screen for cable auto-typing."
    ),
    EDITOR_INPUT(
        title = "Text & Script Editor",
        description = "Paste or type your document, scripts, or text to type into your laptop.",
        targetTab = ScreenTab.EDITOR,
        tag = "editor_text_input",
        actionHint = "Supports up to 50,000+ characters. Characters are typed exactly as entered."
    ),
    TYPE_BUTTON(
        title = "Start Auto-Typing Button",
        description = "Click your PC text window first, then tap this button to stream keystrokes.",
        targetTab = ScreenTab.EDITOR,
        tag = "start_typing_button",
        actionHint = "Gives you a 3-second preparation countdown before typing starts."
    ),
    KEY_TEST(
        title = "Individual Key Testing",
        description = "Send test keystrokes to Windows Notepad to verify communication.",
        targetTab = ScreenTab.TEST,
        tag = "test_all_keys_button",
        actionHint = "Sends test characters to confirm the keyboard driver on Windows."
    ),
    USB_SYNC(
        title = "USB Companion Sync Check",
        description = "Verifies connection with the Windows companion via ADB tunnel.",
        targetTab = ScreenTab.USB_TYPING,
        tag = "usb_check_connection_button",
        actionHint = "Tests bidirectional ping and synchronizes status with PC companion."
    )
}

/**
 * Full-screen Onboarding & Quick-Start Guide Dialog.
 * Walks new users through Bluetooth HID & USB connection step-by-step.
 */
@Composable
fun OnboardingGuideDialog(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onNavigateToTab: (ScreenTab) -> Unit,
    onStartShowMe: (ShowMeTarget) -> Unit,
    onOpenTroubleshooting: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    var currentStepIndex by remember { mutableIntStateOf(0) }
    val totalSteps = 4

    val connectionState by viewModel.connectionState.collectAsState()
    val usbConnectionState by viewModel.usbConnectionState.collectAsState()
    val isHidRegistered by viewModel.isHidRegistered.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .testTag("onboarding_guide_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top Bar: Step indicator & Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${currentStepIndex + 1}/$totalSteps",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Quick Setup & Guide",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row {
                        IconButton(
                            onClick = onOpenTroubleshooting,
                            modifier = Modifier.testTag("onboarding_help_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = "Troubleshooting Help",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                viewModel.setTutorialCompleted(true)
                                onDismiss()
                            },
                            modifier = Modifier.testTag("onboarding_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Skip Tutorial",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (currentStepIndex + 1).toFloat() / totalSteps },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Content per step
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    when (currentStepIndex) {
                        0 -> StepWelcomeAndModeSelection(
                            connectionState = connectionState,
                            usbConnectionState = usbConnectionState,
                            onBluetoothChosen = {
                                currentStepIndex = 1
                            },
                            onUsbChosen = {
                                currentStepIndex = 2
                            }
                        )
                        1 -> StepBluetoothSetup(
                            viewModel = viewModel,
                            connectionState = connectionState,
                            isHidRegistered = isHidRegistered,
                            onRequestPermissions = onRequestPermissions,
                            onOpenSettings = { viewModel.openBluetoothSettings(context) },
                            onShowMe = {
                                onDismiss()
                                onStartShowMe(ShowMeTarget.BT_STATUS)
                            }
                        )
                        2 -> StepUsbSetup(
                            viewModel = viewModel,
                            usbConnectionState = usbConnectionState,
                            onOpenSettings = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            onShowMe = {
                                onDismiss()
                                onStartShowMe(ShowMeTarget.USB_SYNC)
                            }
                        )
                        3 -> StepTypingTestAndReady(
                            viewModel = viewModel,
                            connectionState = connectionState,
                            usbConnectionState = usbConnectionState,
                            onTestConnection = {
                                if (connectionState is HidConnectionState.Connected) {
                                    viewModel.testKeyStroke('a')
                                } else {
                                    viewModel.checkUsbConnection()
                                }
                            },
                            onNavigateToTestTab = {
                                onDismiss()
                                onNavigateToTab(ScreenTab.TEST)
                            },
                            onShowMeEditor = {
                                onDismiss()
                                onStartShowMe(ShowMeTarget.TYPE_BUTTON)
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Action Navigation Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStepIndex > 0) {
                        OutlinedButton(
                            onClick = { currentStepIndex-- },
                            modifier = Modifier
                                .bounceClick()
                                .testTag("onboarding_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Back")
                        }
                    } else {
                        TextButton(
                            onClick = {
                                viewModel.setTutorialCompleted(true)
                                onDismiss()
                            },
                            modifier = Modifier
                                .bounceClick()
                                .testTag("onboarding_skip_button")
                        ) {
                            Text("Skip Tutorial", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (currentStepIndex < totalSteps - 1) {
                        Button(
                            onClick = { currentStepIndex++ },
                            modifier = Modifier
                                .bounceClick()
                                .testTag("onboarding_next_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Next", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next", modifier = Modifier.size(16.dp))
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.setTutorialCompleted(true)
                                onDismiss()
                                onNavigateToTab(ScreenTab.EDITOR)
                            },
                            modifier = Modifier
                                .bounceClick()
                                .testTag("onboarding_finish_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Finish", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Finish & Start Typing", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepWelcomeAndModeSelection(
    connectionState: HidConnectionState,
    usbConnectionState: UsbConnectionState,
    onBluetoothChosen: () -> Unit,
    onUsbChosen: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Welcome to replica_kspp",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "\"I replicate keyboard\" — Turn your phone into a physical hardware keyboard for your Windows laptop. Choose how you want to connect:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        // Bluetooth Mode Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBluetoothChosen() }
                .testTag("guide_choose_bluetooth_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Bluetooth, contentDescription = "Bluetooth", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bluetooth HID Keyboard (Recommended)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Zero PC software required. Pairs as a standard wireless keyboard.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Status: ${if (connectionState is HidConnectionState.Connected) "🟢 Connected" else "⚪ Ready to pair"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (connectionState is HidConnectionState.Connected) StatusGreen else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // USB Cable Mode Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onUsbChosen() }
                .testTag("guide_choose_usb_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Usb, contentDescription = "USB", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "USB Cable Typing Mode",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Connects via USB cable + lightweight Windows companion (ADB tunnel).",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Status: ${if (usbConnectionState is UsbConnectionState.Synced) "🟢 Synced" else "⚪ Cable mode"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (usbConnectionState is UsbConnectionState.Synced) StatusGreen else MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun StepBluetoothSetup(
    viewModel: MainViewModel,
    connectionState: HidConnectionState,
    isHidRegistered: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onShowMe: () -> Unit
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Step 1: Bluetooth HID Pairing",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Follow these 3 easy steps to pair your phone to your Windows laptop:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Substep 1: Permissions & Bluetooth ON
        GuideSubstepCard(
            stepNumber = "1",
            title = "Permissions & Bluetooth Enabled",
            description = "Make sure Bluetooth is turned ON and permissions are granted.",
            statusBadge = when (connectionState) {
                is HidConnectionState.BluetoothDisabled -> "Turn ON Bluetooth"
                is HidConnectionState.PermissionRequired -> "Grant Permissions"
                else -> "Ready"
            },
            isBadgeOk = connectionState !is HidConnectionState.BluetoothDisabled && connectionState !is HidConnectionState.PermissionRequired
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (connectionState is HidConnectionState.PermissionRequired) {
                    Button(
                        onClick = onRequestPermissions,
                        modifier = Modifier.testTag("guide_request_bt_permissions_button")
                    ) {
                        Text("Grant Permissions", fontSize = 11.sp)
                    }
                }
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("guide_open_bt_settings_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bluetooth Settings", fontSize = 11.sp)
                }
            }
        }

        // Substep 2: Discoverable & Profile Registration
        GuideSubstepCard(
            stepNumber = "2",
            title = "Make Phone Discoverable as Keyboard",
            description = "Tap 'Make Discoverable' so Windows can scan and detect your phone.",
            statusBadge = if (isHidRegistered) "HID Active" else "Profile Inactive",
            isBadgeOk = isHidRegistered
        ) {
            Button(
                onClick = { viewModel.requestDiscoverability(context) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.testTag("guide_make_discoverable_button")
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = "Discoverable", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Make Discoverable (120s)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Substep 3: Windows Action
        GuideSubstepCard(
            stepNumber = "3",
            title = "Pair on Windows Laptop",
            description = "On your laptop: Open Windows Settings > Bluetooth & devices > Add device > Bluetooth > Click your phone name and confirm the PIN.",
            statusBadge = if (connectionState is HidConnectionState.Connected) "Connected!" else "Awaiting Host",
            isBadgeOk = connectionState is HidConnectionState.Connected
        ) {
            OutlinedButton(
                onClick = onShowMe,
                modifier = Modifier.testTag("guide_show_me_bt_button")
            ) {
                Icon(Icons.Default.Visibility, contentDescription = "Show Me", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Show Me on Screen", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StepUsbSetup(
    viewModel: MainViewModel,
    usbConnectionState: UsbConnectionState,
    onOpenSettings: () -> Unit,
    onShowMe: () -> Unit
) {
    val context = LocalContext.current
    var isChecking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Step 2: USB Cable Typing Setup",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Connect phone to PC with a USB cable for high-speed typing via Windows companion:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GuideSubstepCard(
            stepNumber = "1",
            title = "Enable USB Debugging on Phone",
            description = "Go to Phone Developer options > Turn ON USB debugging. Keep 'Wait for debugger' turned OFF.",
            statusBadge = "Developer Options",
            isBadgeOk = true
        ) {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("guide_open_dev_settings_button")
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Open Developer Options", fontSize = 11.sp)
            }
        }

        GuideSubstepCard(
            stepNumber = "2",
            title = "Run Windows Companion on Laptop",
            description = "Run replica-companion.exe on Windows. It establishes reverse port 8989 and forward port 8990.",
            statusBadge = if (usbConnectionState is UsbConnectionState.Synced) "SYNCED" else "Awaiting Companion",
            isBadgeOk = usbConnectionState is UsbConnectionState.Synced
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.shareCompanionDownloadLink(context) },
                    modifier = Modifier.testTag("guide_share_companion_button")
                ) {
                    Text("Get Companion", fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        if (isChecking) return@Button
                        scope.launch {
                            isChecking = true
                            try {
                                viewModel.checkUsbConnection()
                                kotlinx.coroutines.delay(800L)
                            } finally {
                                isChecking = false
                            }
                        }
                    },
                    modifier = Modifier
                        .bounceClick()
                        .testTag("guide_check_usb_button"),
                    enabled = !isChecking
                ) {
                    RotatingRefreshIcon(
                        isRotating = isChecking,
                        modifier = Modifier.size(14.dp),
                        contentDescription = "Check Connection",
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isChecking) "Checking..." else "Check Connection", fontSize = 11.sp)
                }
            }
        }

        GuideSubstepCard(
            stepNumber = "3",
            title = "Interactive Navigation",
            description = "Navigate to the USB screen to test live typing and monitor telemetry ping.",
            statusBadge = "Ready",
            isBadgeOk = true
        ) {
            OutlinedButton(
                onClick = onShowMe,
                modifier = Modifier.testTag("guide_show_me_usb_button")
            ) {
                Icon(Icons.Default.Visibility, contentDescription = "Show Me", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Show Me USB Screen", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StepTypingTestAndReady(
    viewModel: MainViewModel,
    connectionState: HidConnectionState,
    usbConnectionState: UsbConnectionState,
    onTestConnection: () -> Unit,
    onNavigateToTestTab: () -> Unit,
    onShowMeEditor: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Step 3: Test Keystrokes & Start Typing",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "📝 Important: Windows Focus Rule",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Before tapping TYPE, click inside Windows Notepad, your browser, Word, or code editor on your laptop so the blinking cursor is active there.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }

        // Live connection diagnostic status
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Connection Diagnostic:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (connectionState is HidConnectionState.Connected || usbConnectionState is UsbConnectionState.Synced) StatusGreen
                                else StatusYellow
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            connectionState is HidConnectionState.Connected -> "Bluetooth: CONNECTED (${connectionState.deviceName})"
                            usbConnectionState is UsbConnectionState.Synced -> "USB: SYNCED (${usbConnectionState.companionVersion})"
                            else -> "No active connection detected yet. Follow Steps 1 or 2 above."
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onTestConnection,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("guide_test_connection_action_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Test", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Test Connection", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onNavigateToTestTab,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("guide_open_test_tab_button")
                    ) {
                        Text("Open Key Test Tab", fontSize = 11.sp)
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onShowMeEditor,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("guide_show_me_editor_button")
        ) {
            Icon(Icons.Default.Visibility, contentDescription = "Show Me", modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Show Me How to Auto-Type from Editor")
        }
    }
}

@Composable
private fun GuideSubstepCard(
    stepNumber: String,
    title: String,
    description: String,
    statusBadge: String,
    isBadgeOk: Boolean,
    action: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stepNumber, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isBadgeOk) StatusGreen.copy(alpha = 0.15f) else StatusYellow.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusBadge,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isBadgeOk) StatusGreen else StatusYellow
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)

            Spacer(modifier = Modifier.height(10.dp))
            action()
        }
    }
}

/**
 * Interactive "Show Me" Overlay.
 * Renders an animated tutorial tooltip with spotlight instructions, navigation, and exit.
 */
@Composable
fun InteractiveShowMeOverlay(
    target: ShowMeTarget,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onDone)
            .padding(20.dp)
            .testTag("interactive_show_me_overlay"),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {} // prevent dismissing when tapping card
                .testTag("show_me_tooltip_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = "Show Me",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = target.title,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onDone,
                        modifier = Modifier.size(24.dp).testTag("show_me_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = target.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "💡 Action: ${target.actionHint}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("show_me_back_button")
                    ) {
                        Text("Back", fontSize = 11.sp)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onNext,
                            modifier = Modifier.testTag("show_me_next_button")
                        ) {
                            Text("Next Tip", fontSize = 11.sp)
                        }

                        Button(
                            onClick = onDone,
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            modifier = Modifier.testTag("show_me_done_button")
                        ) {
                            Text("Got It!", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Full-featured Interactive Troubleshooting Bottom Sheet.
 * Provides real Bluetooth and USB diagnosis, action buttons, and guided recovery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleshootingBottomSheet(
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var selectedMode by remember { mutableStateOf("BLUETOOTH") }
    var isDiagnosing by remember { mutableStateOf(false) }
    var diagnosticLog by remember { mutableStateOf<List<String>>(emptyList()) }

    val connectionState by viewModel.connectionState.collectAsState()
    val isHidRegistered by viewModel.isHidRegistered.collectAsState()
    val usbConnectionState by viewModel.usbConnectionState.collectAsState()
    val isUsbCableConnected by viewModel.isUsbCableConnected.collectAsState()

    fun runRealDiagnostics() {
        isDiagnosing = true
        val logs = mutableListOf<String>()

        if (selectedMode == "BLUETOOTH") {
            val adapter = viewModel.hidManager.bluetoothAdapter
            logs.add("1. Bluetooth Adapter: ${if (adapter != null) "Present (Hardware OK)" else "Missing / Not Supported"}")
            logs.add("2. Bluetooth Enabled: ${if (adapter?.isEnabled == true) "YES (ON)" else "NO (OFF)"}")
            logs.add("3. Runtime Permissions: ${if (viewModel.hidManager.hasBluetoothPermissions()) "GRANTED" else "MISSING"}")
            logs.add("4. HID Profile (Profile 19): ${if (isHidRegistered) "REGISTERED & ACTIVE" else "NOT REGISTERED / INACTIVE"}")
            logs.add("5. Connection State: ${connectionState::class.simpleName}")
            val target = viewModel.connectedDevice.value
            logs.add("6. Connected Host: ${target?.let { viewModel.hidManager.getSafeDeviceName(it) } ?: "None"}")
            if (target != null) {
                logs.add("7. Host MAC: ${target.address}")
            }
        } else {
            logs.add("1. USB Cable Connection: ${if (isUsbCableConnected) "Cable Plugged In" else "Cable Not Detected"}")
            logs.add("2. USB State: ${usbConnectionState::class.simpleName}")
            when (val s = usbConnectionState) {
                is UsbConnectionState.Synced -> {
                    logs.add("3. Companion Version: ${s.companionVersion} (Protocol v${s.protocolVersion})")
                    logs.add("4. Latency / Ping: ${s.latencyMs} ms")
                    logs.add("5. Target OS: ${s.deviceOs}")
                }
                is UsbConnectionState.Disconnected -> {
                    logs.add("3. Disconnect Reason: ${s.reason}")
                }
                is UsbConnectionState.Incompatible -> {
                    logs.add("3. Incompatible: ${s.reason}")
                }
                is UsbConnectionState.Error -> {
                    logs.add("3. Error Message: ${s.message}")
                }
                else -> Unit
            }
            logs.add("4. Reverse Port: 8989 (Listening on phone)")
            logs.add("5. Forward Port: 8990 (Dialed to PC)")
        }

        diagnosticLog = logs
        isDiagnosing = false
    }

    LaunchedEffect(selectedMode) {
        runRealDiagnostics()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("troubleshooting_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "Troubleshooting",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Troubleshooting & Diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.testTag("troubleshooting_close_button")) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Mode Selector Tabs
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = selectedMode == "BLUETOOTH",
                    onClick = { selectedMode = "BLUETOOTH" },
                    label = { Text("Bluetooth HID") },
                    leadingIcon = { Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f).testTag("troubleshoot_mode_bt")
                )

                FilterChip(
                    selected = selectedMode == "USB",
                    onClick = { selectedMode = "USB" },
                    label = { Text("USB Typing") },
                    leadingIcon = { Icon(Icons.Default.Usb, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.weight(1f).testTag("troubleshoot_mode_usb")
                )
            }

            // Diagnostic Results Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(14.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Hardware Telemetry",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        TextButton(
                            onClick = {
                                if (isDiagnosing) return@TextButton
                                scope.launch {
                                    isDiagnosing = true
                                    try {
                                        if (selectedMode == "BLUETOOTH") viewModel.refreshBluetooth()
                                        else viewModel.checkUsbConnection()
                                        runRealDiagnostics()
                                        kotlinx.coroutines.delay(650L)
                                    } finally {
                                        isDiagnosing = false
                                    }
                                }
                            },
                            enabled = !isDiagnosing,
                            modifier = Modifier.testTag("troubleshoot_diagnose_button")
                        ) {
                            RotatingRefreshIcon(
                                isRotating = isDiagnosing,
                                modifier = Modifier.size(14.dp),
                                contentDescription = "Diagnose",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isDiagnosing) "Diagnosing..." else "Diagnose Again", fontSize = 11.sp)
                        }
                    }

                    diagnosticLog.forEach { line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Actionable Recovery Fixes based on mode
            if (selectedMode == "BLUETOOTH") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Actionable Fixes for Bluetooth:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        TroubleshootActionItem(
                            title = "Fix: Unpair & Re-pair Device",
                            description = "If Windows says 'Connected' but keystrokes aren't received, unpair phone in Windows Bluetooth settings and re-pair.",
                            actionLabel = "Open BT Settings",
                            onClick = { viewModel.openBluetoothSettings(context) }
                        )

                        TroubleshootActionItem(
                            title = "Fix: Restart Profile Registration",
                            description = "Forces Android Bluetooth stack to unbind and re-register Profile 19 (HID Device).",
                            actionLabel = "Retry Registration",
                            onClick = {
                                viewModel.refreshBluetooth()
                                runRealDiagnostics()
                            }
                        )

                        TroubleshootActionItem(
                            title = "Fix: Broadcast Discovery",
                            description = "Makes the phone visible to Windows PC scan for 120 seconds.",
                            actionLabel = "Make Discoverable",
                            onClick = { viewModel.requestDiscoverability(context) }
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Actionable Fixes for USB Typing:", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        TroubleshootActionItem(
                            title = "Fix: Developer Options / USB Debugging",
                            description = "Ensure USB debugging is ON, and 'Wait for debugger' is OFF. Select debug app should be Nothing.",
                            actionLabel = "Open Dev Settings",
                            onClick = {
                                try {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
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
                        )

                        TroubleshootActionItem(
                            title = "Fix: Verify ADB Tunneling & Companion",
                            description = "Make sure replica-companion.exe is running on PC. Try reconnecting the USB cable to re-prompt authorization.",
                            actionLabel = "Retry Sync",
                            onClick = {
                                viewModel.checkUsbConnection()
                                runRealDiagnostics()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("troubleshoot_back_to_connection_button")
            ) {
                Text("Back to Connection", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TroubleshootActionItem(
    title: String,
    description: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(actionLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
