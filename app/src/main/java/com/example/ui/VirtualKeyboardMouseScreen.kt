package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth.HidConnectionState
import com.example.ui.theme.StatusGreen
import com.example.usb.UsbConnectionState

enum class InputViewMode {
    KEYBOARD_ONLY,
    MOUSE_ONLY,
    SPLIT
}

@Composable
fun VirtualKeyboardMouseScreen(
    viewModel: MainViewModel,
    onNavigateToConnectivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val btState by viewModel.connectionState.collectAsState()
    val usbState by viewModel.usbConnectionState.collectAsState()

    val isBtConnected = btState is HidConnectionState.Connected
    val isNetSynced = usbState is UsbConnectionState.Synced
    val isConnected = isBtConnected || isNetSynced

    var viewMode by remember { mutableStateOf(InputViewMode.KEYBOARD_ONLY) }
    var isCapsLock by remember { mutableStateOf(false) }
    var isShift by remember { mutableStateOf(false) }
    var isCtrl by remember { mutableStateOf(false) }
    var isAlt by remember { mutableStateOf(false) }

    var lastActionFeedback by remember { mutableStateOf<String?>("Ready") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Top status & mode header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Compact Connection Pill
            Surface(
                shape = CircleShape,
                color = if (isConnected) StatusGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onNavigateToConnectivity() }
                    .testTag("kb_connection_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) StatusGreen else MaterialTheme.colorScheme.error)
                    )
                    Text(
                        text = when {
                            isNetSynced -> "Wi-Fi / USB Connected"
                            isBtConnected -> "Bluetooth HID Connected"
                            else -> "Not Connected (Tap to Connect)"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) StatusGreen else MaterialTheme.colorScheme.error
                    )
                }
            }

            // Mode Selector
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = viewMode == InputViewMode.KEYBOARD_ONLY,
                    onClick = { viewMode = InputViewMode.KEYBOARD_ONLY },
                    label = { Text("Keys", fontSize = 11.sp) },
                    modifier = Modifier.testTag("mode_keys_chip")
                )
                FilterChip(
                    selected = viewMode == InputViewMode.MOUSE_ONLY,
                    onClick = { viewMode = InputViewMode.MOUSE_ONLY },
                    label = { Text("Mouse", fontSize = 11.sp) },
                    modifier = Modifier.testTag("mode_mouse_chip")
                )
                FilterChip(
                    selected = viewMode == InputViewMode.SPLIT,
                    onClick = { viewMode = InputViewMode.SPLIT },
                    label = { Text("Split", fontSize = 11.sp) },
                    modifier = Modifier.testTag("mode_split_chip")
                )
            }
        }

        // Active Content
        when (viewMode) {
            InputViewMode.KEYBOARD_ONLY -> {
                VirtualKeyboardSection(
                    isCapsLock = isCapsLock,
                    isShift = isShift,
                    isCtrl = isCtrl,
                    isAlt = isAlt,
                    onToggleCaps = { isCapsLock = !isCapsLock },
                    onToggleShift = { isShift = !isShift },
                    onToggleCtrl = { isCtrl = !isCtrl },
                    onToggleAlt = { isAlt = !isAlt },
                    onKeyClick = { char ->
                        val effectiveChar = if (isCapsLock xor isShift) char.uppercaseChar() else char.lowercaseChar()
                        viewModel.sendVirtualKey(effectiveChar)
                        lastActionFeedback = "Typed: '$effectiveChar'"
                        if (isShift) isShift = false
                    },
                    onSpecialKeyClick = { key ->
                        viewModel.sendVirtualSpecialKey(key)
                        lastActionFeedback = "Sent: $key"
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            InputViewMode.MOUSE_ONLY -> {
                VirtualMouseSection(
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
            }
            InputViewMode.SPLIT -> {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VirtualMouseSection(
                        viewModel = viewModel,
                        modifier = Modifier.weight(0.42f)
                    )
                    VirtualKeyboardSection(
                        isCapsLock = isCapsLock,
                        isShift = isShift,
                        isCtrl = isCtrl,
                        isAlt = isAlt,
                        onToggleCaps = { isCapsLock = !isCapsLock },
                        onToggleShift = { isShift = !isShift },
                        onToggleCtrl = { isCtrl = !isCtrl },
                        onToggleAlt = { isAlt = !isAlt },
                        onKeyClick = { char ->
                            val effectiveChar = if (isCapsLock xor isShift) char.uppercaseChar() else char.lowercaseChar()
                            viewModel.sendVirtualKey(effectiveChar)
                            if (isShift) isShift = false
                        },
                        onSpecialKeyClick = { key ->
                            viewModel.sendVirtualSpecialKey(key)
                        },
                        compact = true,
                        modifier = Modifier.weight(0.58f)
                    )
                }
            }
        }
    }
}

@Composable
private fun VirtualKeyboardSection(
    isCapsLock: Boolean,
    isShift: Boolean,
    isCtrl: Boolean,
    isAlt: Boolean,
    onToggleCaps: () -> Unit,
    onToggleShift: () -> Unit,
    onToggleCtrl: () -> Unit,
    onToggleAlt: () -> Unit,
    onKeyClick: (Char) -> Unit,
    onSpecialKeyClick: (String) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)
        ) {
            // Function row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KeyButton("Esc", Modifier.weight(1.2f), compact) { onSpecialKeyClick("ESC") }
                KeyButton("Tab", Modifier.weight(1.2f), compact) { onSpecialKeyClick("TAB") }
                KeyButton("F1", Modifier.weight(1f), compact) { onSpecialKeyClick("F1") }
                KeyButton("F2", Modifier.weight(1f), compact) { onSpecialKeyClick("F2") }
                KeyButton("F5", Modifier.weight(1f), compact) { onSpecialKeyClick("F5") }
                KeyButton("⌫ Del", Modifier.weight(1.5f), compact, isAccent = true) { onSpecialKeyClick("BACKSPACE") }
                KeyButton("↵ Enter", Modifier.weight(1.5f), compact, isPrimary = true) { onSpecialKeyClick("ENTER") }
            }

            // Number row: 1 2 3 4 5 6 7 8 9 0 - =
            val numbers = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '-', '=')
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                numbers.forEach { char ->
                    KeyButton(char.toString(), Modifier.weight(1f), compact) { onKeyClick(char) }
                }
            }

            // Row 2: Q W E R T Y U I O P [ ]
            val row2 = listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p', '[', ']')
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row2.forEach { char ->
                    val display = if (isCapsLock xor isShift) char.uppercaseChar().toString() else char.toString()
                    KeyButton(display, Modifier.weight(1f), compact) { onKeyClick(char) }
                }
            }

            // Row 3: Caps A S D F G H J K L ; ' Enter
            val row3 = listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', ';', '\'')
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KeyButton("Caps", Modifier.weight(1.3f), compact, isToggled = isCapsLock) { onToggleCaps() }
                row3.forEach { char ->
                    val display = if (isCapsLock xor isShift) char.uppercaseChar().toString() else char.toString()
                    KeyButton(display, Modifier.weight(1f), compact) { onKeyClick(char) }
                }
                KeyButton("↵", Modifier.weight(1.2f), compact, isPrimary = true) { onSpecialKeyClick("ENTER") }
            }

            // Row 4: Shift Z X C V B N M , . / Shift
            val row4 = listOf('z', 'x', 'c', 'v', 'b', 'n', 'm', ',', '.', '/')
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KeyButton("Shift", Modifier.weight(1.4f), compact, isToggled = isShift) { onToggleShift() }
                row4.forEach { char ->
                    val display = if (isCapsLock xor isShift) char.uppercaseChar().toString() else char.toString()
                    KeyButton(display, Modifier.weight(1f), compact) { onKeyClick(char) }
                }
                KeyButton("Shift", Modifier.weight(1.4f), compact, isToggled = isShift) { onToggleShift() }
            }

            // Bottom modifier row: Ctrl Win Alt Space Arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                KeyButton("Ctrl", Modifier.weight(1.1f), compact, isToggled = isCtrl) { onToggleCtrl() }
                KeyButton("Win", Modifier.weight(1f), compact) { onSpecialKeyClick("WIN") }
                KeyButton("Alt", Modifier.weight(1f), compact, isToggled = isAlt) { onToggleAlt() }
                KeyButton("Space", Modifier.weight(3.5f), compact) { onKeyClick(' ') }
                KeyButton("←", Modifier.weight(1f), compact) { onSpecialKeyClick("LEFT") }
                KeyButton("↑", Modifier.weight(1f), compact) { onSpecialKeyClick("UP") }
                KeyButton("↓", Modifier.weight(1f), compact) { onSpecialKeyClick("DOWN") }
                KeyButton("→", Modifier.weight(1f), compact) { onSpecialKeyClick("RIGHT") }
            }
        }
    }
}

@Composable
private fun KeyButton(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    isPrimary: Boolean = false,
    isAccent: Boolean = false,
    isToggled: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        isToggled -> MaterialTheme.colorScheme.primary
        isPrimary -> MaterialTheme.colorScheme.primaryContainer
        isAccent -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val fg = when {
        isToggled -> MaterialTheme.colorScheme.onPrimary
        isPrimary -> MaterialTheme.colorScheme.onPrimaryContainer
        isAccent -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .height(if (compact) 38.dp else 46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .testTag("key_btn_$text"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = if (compact) 12.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
            maxLines = 1
        )
    }
}

@Composable
private fun VirtualMouseSection(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Touchpad gesture area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                viewModel.sendVirtualMouseClick("LEFT")
                            },
                            onDoubleTap = {
                                viewModel.sendVirtualMouseClick("LEFT")
                                viewModel.sendVirtualMouseClick("LEFT")
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            viewModel.sendVirtualMouseMove(dragAmount.x.toInt(), dragAmount.y.toInt())
                        }
                    }
                    .testTag("mouse_touchpad_surface"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TouchApp,
                        contentDescription = "Touchpad",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Touchpad Trackpad",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "Drag finger to move pointer • Tap to Left Click",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            // Mouse Buttons (Left Click, Middle/Scroll, Right Click)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.sendVirtualMouseClick("LEFT") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.weight(1.5f).height(48.dp).testTag("mouse_left_click_button")
                ) {
                    Text("Left Click", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { viewModel.sendVirtualSpecialKey("SPACE") },
                    modifier = Modifier.weight(1f).height(48.dp).testTag("mouse_mid_click_button")
                ) {
                    Text("Scroll / Space", fontSize = 11.sp)
                }

                Button(
                    onClick = { viewModel.sendVirtualMouseClick("RIGHT") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.weight(1.5f).height(48.dp).testTag("mouse_right_click_button")
                ) {
                    Text("Right Click", color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
