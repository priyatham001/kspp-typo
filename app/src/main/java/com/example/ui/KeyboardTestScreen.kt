package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth.HidConnectionState
import com.example.keyboard.KeyboardDescriptor
import com.example.keyboard.KeyboardMapper
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusYellow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardTestScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isTestingAllKeys by viewModel.isTestingAllKeys.collectAsState()
    val scrollState = rememberScrollState()

    var lastSentKey by remember { mutableStateOf<String>("None") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title & status banner
        Column {
            Text(
                text = "Keyboard Test",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tap buttons to send real Bluetooth HID keyboard events to your PC",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Connection reminder
        if (connectionState !is HidConnectionState.Connected) {
            Card(
                colors = CardDefaults.cardColors(containerColor = StatusYellow.copy(alpha = 0.15f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Warning",
                        tint = StatusYellow
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Laptop not connected. Connect via Bluetooth first to receive keystrokes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Big "TEST ALL KEYS" Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Test all keys",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Automated Keyboard Verification",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Types full alphabet (a-z, A-Z), numbers (0-9), and all programming symbols into the active Windows cursor position.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            lastSentKey = "ALL KEYS SEQUENCE"
                            viewModel.runTestAllKeys()
                        },
                        enabled = !isTestingAllKeys && connectionState is HidConnectionState.Connected,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("test_all_keys_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isTestingAllKeys) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Typing Sequence...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("TEST ALL KEYS", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isTestingAllKeys) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { viewModel.cancelTestAllKeys() },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = com.example.ui.theme.ErrorRed)
                        ) {
                            Text("CANCEL", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Realtime Event Display with Verification Notice
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Last Key Event:",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = lastSentKey,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (lastSentKey != "None") {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "● Report Sent via Bluetooth (Host unverified - verify characters appear on PC screen)",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section 1: Letters
        KeySectionHeader("Letters (a - z)")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val letters = ('a'..'z').toList()
            letters.forEach { char ->
                val stroke = KeyboardMapper.mapChar(char)!!
                KeyButton(label = char.uppercase(), onClick = {
                    lastSentKey = "Letter '${char}'"
                    viewModel.sendTestKey(stroke.keyCode, stroke.modifier)
                })
            }
        }

        // Section 2: Numbers
        KeySectionHeader("Numbers (0 - 9)")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val numbers = ('0'..'9').toList()
            numbers.forEach { char ->
                val stroke = KeyboardMapper.mapChar(char)!!
                KeyButton(label = char.toString(), onClick = {
                    lastSentKey = "Number '${char}'"
                    viewModel.sendTestKey(stroke.keyCode, stroke.modifier)
                })
            }
        }

        // Section 3: Programming Symbols
        KeySectionHeader("Programming Symbols")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val symbols = listOf(
                '!', '@', '#', '$', '%', '^', '&', '*', '(', ')',
                '-', '_', '=', '+', '[', ']', '{', '}', '\\', '|',
                ';', ':', '\'', '"', ',', '<', '.', '>', '/', '?',
                '`', '~'
            )
            symbols.forEach { char ->
                val stroke = KeyboardMapper.mapChar(char)
                if (stroke != null) {
                    KeyButton(label = char.toString(), onClick = {
                        lastSentKey = "Symbol '${char}'"
                        viewModel.sendTestKey(stroke.keyCode, stroke.modifier)
                    })
                }
            }
        }

        // Section 4: Whitespace and Control Keys
        KeySectionHeader("Control & Navigation Keys")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            KeyButton(label = "TAB", width = 72.dp, onClick = {
                lastSentKey = "TAB"
                viewModel.sendTestKey(KeyboardDescriptor.KEY_TAB)
            })
            KeyButton(label = "ENTER", width = 84.dp, onClick = {
                lastSentKey = "ENTER"
                viewModel.sendTestKey(KeyboardDescriptor.KEY_ENTER)
            })
            KeyButton(label = "SPACE", width = 90.dp, onClick = {
                lastSentKey = "SPACE"
                viewModel.sendTestKey(KeyboardDescriptor.KEY_SPACE)
            })
            KeyButton(label = "BACKSPACE", width = 100.dp, onClick = {
                lastSentKey = "BACKSPACE"
                viewModel.sendTestKey(KeyboardDescriptor.KEY_BACKSPACE)
            })
            KeyButton(label = "ESC", width = 64.dp, onClick = {
                lastSentKey = "ESC"
                viewModel.sendTestKey(KeyboardDescriptor.KEY_ESC)
            })
        }

        // Section 5: Modifiers and Shortcuts
        KeySectionHeader("Modifier Keys & Shortcuts")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            KeyButton(label = "Ctrl+C", width = 80.dp, onClick = {
                lastSentKey = "Ctrl+C (Copy)"
                viewModel.sendTestKey(KeyboardDescriptor.KEY_C, KeyboardDescriptor.MOD_LEFT_CTRL)
            })
            KeyButton(label = "Ctrl+V", width = 80.dp, onClick = {
                lastSentKey = "Ctrl+V (Paste)"
                viewModel.sendTestKey(KeyboardDescriptor.KEY_V, KeyboardDescriptor.MOD_LEFT_CTRL)
            })
            KeyButton(label = "Ctrl+Z", width = 80.dp, onClick = {
                lastSentKey = "Ctrl+Z (Undo)"
                viewModel.sendTestKey(KeyboardDescriptor.KEY_Z, KeyboardDescriptor.MOD_LEFT_CTRL)
            })
            KeyButton(label = "Ctrl+A", width = 80.dp, onClick = {
                lastSentKey = "Ctrl+A (Select All)"
                viewModel.sendTestKey(KeyboardDescriptor.KEY_A, KeyboardDescriptor.MOD_LEFT_CTRL)
            })
            KeyButton(label = "GUI/Win", width = 84.dp, onClick = {
                lastSentKey = "Windows / GUI Key"
                viewModel.sendTestKey(KeyboardDescriptor.KEY_NONE, KeyboardDescriptor.MOD_LEFT_GUI)
            })
            KeyButton(label = "Alt+Tab", width = 84.dp, onClick = {
                lastSentKey = "Alt+Tab (Switch App)"
                viewModel.sendTestKey(KeyboardDescriptor.KEY_TAB, KeyboardDescriptor.MOD_LEFT_ALT)
            })
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Notepad First Test Guide Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "FIRST TEST (Windows Notepad)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                val steps = listOf(
                    "1. Connect REPLICA to your Windows laptop via Bluetooth.",
                    "2. Open Windows Notepad (notepad.exe) or any text editor.",
                    "3. Click inside the window so the text cursor is blinking.",
                    "4. In this Virtual Keyboard screen, tap letter, number, navigation, and symbol keys.",
                    "5. Press [TEST ALL KEYS] to verify complete US QWERTY mapping.",
                    "6. Confirm every character matches exactly on Windows.",
                    "7. Switch to the Editor tab to stream long documents or source code."
                )

                steps.forEach { step ->
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun KeySectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun KeyButton(
    label: String,
    onClick: () -> Unit,
    width: androidx.compose.ui.unit.Dp = 44.dp
) {
    Box(
        modifier = Modifier
            .size(width = width, height = 44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .testTag("key_btn_$label"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
