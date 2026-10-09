package com.example.typing

import com.example.bluetooth.BluetoothHidManager
import com.example.bluetooth.HidConnectionState
import com.example.keyboard.HidReportBuilder
import com.example.keyboard.KeyboardMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.min

sealed class TypingState {
    data object Idle : TypingState()
    data class Typing(val currentIndex: Int, val totalChars: Int, val percent: Float) : TypingState()
    data class Paused(val currentIndex: Int, val totalChars: Int, val percent: Float) : TypingState()
    data class Completed(val totalChars: Int) : TypingState()
    data class Stopped(val stoppedAtIndex: Int, val totalChars: Int) : TypingState()
    data class Error(val message: String, val atIndex: Int = 0) : TypingState()
}

/**
 * Universal Auto-Typing Engine that streams ANY user-provided plain text character by character
 * via genuine Bluetooth HID keyboard reports.
 * Does not use clipboard, companion apps, or web sockets.
 */
class TypingEngine(
    private val hidManager: BluetoothHidManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val _typingState = MutableStateFlow<TypingState>(TypingState.Idle)
    val typingState: StateFlow<TypingState> = _typingState.asStateFlow()

    private val _currentDelayMs = MutableStateFlow(30L)
    val currentDelayMs: StateFlow<Long> = _currentDelayMs.asStateFlow()

    private var typingJob: Job? = null
    private var fullText: String = ""
    private var currentIndex: Int = 0
    private var isPaused: Boolean = false

    fun setDelay(delayMs: Long) {
        _currentDelayMs.value = delayMs.coerceIn(0L, 500L)
    }

    /**
     * Start typing text from index 0.
     */
    fun startTyping(text: String, customDelayMs: Long? = null) {
        if (text.isEmpty()) {
            _typingState.value = TypingState.Error("There is no text to type. Please enter or load text first.")
            return
        }

        val connState = hidManager.connectionState.value
        if (connState !is HidConnectionState.Connected) {
            _typingState.value = TypingState.Error("No HID keyboard connection. Connect to your Windows laptop first.")
            return
        }

        customDelayMs?.let { setDelay(it) }

        stopTyping(resetState = false)
        fullText = text
        currentIndex = 0
        isPaused = false

        launchTypingLoop()
    }

    /**
     * Temporarily pause typing. Keeps current position intact and releases active keys.
     */
    fun pauseTyping() {
        if (_typingState.value is TypingState.Typing) {
            isPaused = true
            typingJob?.cancel()
            typingJob = null
            // Send key up release report immediately
            hidManager.sendReport(HidReportBuilder.buildKeyUpReport())
            val total = fullText.length
            val pct = if (total > 0) (currentIndex.toFloat() / total) * 100f else 0f
            _typingState.value = TypingState.Paused(currentIndex, total, pct)
        }
    }

    /**
     * Resume typing from the exact paused index.
     */
    fun resumeTyping() {
        if (_typingState.value is TypingState.Paused) {
            val connState = hidManager.connectionState.value
            if (connState !is HidConnectionState.Connected) {
                _typingState.value = TypingState.Error("Bluetooth connection lost while paused. Reconnect to resume.")
                return
            }
            isPaused = false
            launchTypingLoop()
        }
    }

    /**
     * Immediately cancel typing operation and reset.
     * Guaranteed highest priority: terminates jobs and releases all modifiers immediately.
     */
    fun stopTyping(resetState: Boolean = true) {
        typingJob?.cancel()
        typingJob = null
        isPaused = false

        // Always release any active key press & modifiers
        hidManager.sendReport(HidReportBuilder.buildKeyUpReport())

        if (resetState) {
            val total = fullText.length
            _typingState.value = TypingState.Stopped(currentIndex, total)
        }
    }

    /**
     * Reset to Idle state.
     */
    fun resetToIdle() {
        stopTyping(resetState = false)
        currentIndex = 0
        fullText = ""
        _typingState.value = TypingState.Idle
    }

    /**
     * Type a single key press directly (used by Keyboard Test mode).
     */
    fun sendDirectKey(keyCode: Byte, modifier: Byte = 0) {
        scope.launch {
            hidManager.sendReport(HidReportBuilder.buildKeyDownReport(keyCode, modifier))
            delay(15)
            hidManager.sendReport(HidReportBuilder.buildKeyUpReport())
        }
    }

    /**
     * Type an entire raw string directly for testing (e.g. TEST ALL KEYS).
     */
    fun typeDirectString(text: String, onFinished: (() -> Unit)? = null) {
        scope.launch {
            for (char in text) {
                if (char == '\r') continue
                val stroke = KeyboardMapper.mapChar(char)
                if (stroke != null) {
                    hidManager.sendReport(HidReportBuilder.buildKeyDownReport(stroke.keyCode, stroke.modifier))
                    delay(10)
                    hidManager.sendReport(HidReportBuilder.buildKeyUpReport())
                    delay(20)
                }
            }
            hidManager.sendReport(HidReportBuilder.buildKeyUpReport())
            onFinished?.invoke()
        }
    }

    private fun launchTypingLoop() {
        val total = fullText.length
        typingJob = scope.launch {
            try {
                while (isActive && currentIndex < total && !isPaused) {
                    // Check Bluetooth connection status
                    if (hidManager.connectionState.value !is HidConnectionState.Connected) {
                        hidManager.sendReport(HidReportBuilder.buildKeyUpReport())
                        _typingState.value = TypingState.Error("Bluetooth connection lost. Typing stopped.", currentIndex)
                        return@launch
                    }

                    val char = fullText[currentIndex]

                    // Handle CRLF, LF, CR newlines:
                    // \r\n -> send exactly ONE ENTER action
                    // Standalone \r -> send ONE ENTER action
                    // \n -> send ONE ENTER action
                    val effectiveChar = if (char == '\r') {
                        if (currentIndex + 1 < total && fullText[currentIndex + 1] == '\n') {
                            currentIndex++ // advance so \n is processed as the single ENTER
                            fullText[currentIndex]
                        } else {
                            '\n' // standalone CR maps to ENTER
                        }
                    } else {
                        char
                    }

                    val stroke = KeyboardMapper.mapChar(effectiveChar)
                    if (stroke == null) {
                        hidManager.sendReport(HidReportBuilder.buildKeyUpReport())
                        _typingState.value = TypingState.Error(
                            "Unsupported character: '$effectiveChar'\nPosition: $currentIndex\nThis character cannot be transmitted using the current US QWERTY HID keyboard mapping.",
                            currentIndex
                        )
                        return@launch
                    }

                    // 1. Send Key Down with Modifier
                    val keyDown = HidReportBuilder.buildKeyDownReport(stroke.keyCode, stroke.modifier)
                    val sentDown = hidManager.sendReport(keyDown)
                    if (!sentDown) {
                        delay(5)
                        hidManager.sendReport(keyDown)
                    }

                    // 2. Key hold duration
                    val holdTime = min(10L, (_currentDelayMs.value / 2).coerceAtLeast(3L))
                    delay(holdTime)

                    // 3. Send Key Up
                    val keyUp = HidReportBuilder.buildKeyUpReport()
                    hidManager.sendReport(keyUp)

                    // 4. Delay between keystrokes
                    val interKeyDelay = (_currentDelayMs.value - holdTime).coerceAtLeast(0L)
                    if (interKeyDelay > 0) {
                        delay(interKeyDelay)
                    }

                    currentIndex++

                    // Update live progress
                    val pct = (currentIndex.toFloat() / total) * 100f
                    _typingState.value = TypingState.Typing(currentIndex, total, pct)
                }

                if (currentIndex >= total && !isPaused) {
                    hidManager.sendReport(HidReportBuilder.buildKeyUpReport())
                    _typingState.value = TypingState.Completed(total)
                }
            } catch (e: CancellationException) {
                hidManager.sendReport(HidReportBuilder.buildKeyUpReport())
            } catch (e: Exception) {
                hidManager.sendReport(HidReportBuilder.buildKeyUpReport())
                _typingState.value = TypingState.Error("Unexpected typing error: ${e.message}", currentIndex)
            }
        }
    }
}
