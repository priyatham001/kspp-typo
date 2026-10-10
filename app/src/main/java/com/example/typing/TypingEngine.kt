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
    data class Typing(
        val currentIndex: Int,
        val totalChars: Int,
        val percent: Float,
        val elapsedMs: Long = 0L,
        val remainingMs: Long = 0L
    ) : TypingState()
    data class Paused(val currentIndex: Int, val totalChars: Int, val percent: Float) : TypingState()
    data class Completed(val totalChars: Int, val durationMs: Long = 0L) : TypingState()
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
    private var startTimeMs: Long = 0L

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
        startTimeMs = System.currentTimeMillis()

        launchTypingLoop()
    }

    /**
     * Restart typing the current text from index 0.
     */
    fun restartTyping(customDelayMs: Long? = null) {
        if (fullText.isNotEmpty()) {
            startTyping(fullText, customDelayMs)
        }
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
            startTimeMs = 0L
        }
    }

    /**
     * Reset to Idle state.
     */
    fun resetToIdle() {
        stopTyping(resetState = false)
        currentIndex = 0
        fullText = ""
        startTimeMs = 0L
        _typingState.value = TypingState.Idle
    }

    /**
     * Safely releases any pressed keys on the host machine.
     */
    private fun releaseAllKeys() {
        try {
            hidManager.sendReport(HidReportBuilder.buildKeyUpReport())
        } catch (_: Exception) {}
    }

    /**
     * Type a single key press directly (used by Keyboard Test mode).
     */
    fun sendDirectKey(keyCode: Byte, modifier: Byte = 0) {
        scope.launch {
            try {
                hidManager.sendReport(HidReportBuilder.buildKeyDownReport(keyCode, modifier))
                delay(12)
                releaseAllKeys()
            } catch (_: Exception) {
                releaseAllKeys()
            }
        }
    }

    /**
     * Type an entire raw string directly for testing (e.g. TEST ALL KEYS).
     */
    fun typeDirectString(text: String, onFinished: (() -> Unit)? = null) {
        typingJob?.cancel()
        typingJob = scope.launch {
            try {
                var prevKey: Byte = -1
                for (char in text) {
                    if (!isActive) break
                    if (char == '\r') continue
                    val stroke = KeyboardMapper.mapChar(char) ?: KeyboardMapper.mapChar(' ')
                    if (stroke != null) {
                        // Consecutive identical keys need a small release gap so host doesn't merge them
                        if (stroke.keyCode == prevKey) {
                            delay(4)
                        }
                        hidManager.sendReport(HidReportBuilder.buildKeyDownReport(stroke.keyCode, stroke.modifier))
                        delay(4)
                        releaseAllKeys()
                        delay(8)
                        prevKey = stroke.keyCode
                    }
                }
                releaseAllKeys()
                onFinished?.invoke()
            } catch (e: CancellationException) {
                releaseAllKeys()
            } catch (e: Exception) {
                releaseAllKeys()
            }
        }
    }

    private fun launchTypingLoop() {
        val total = fullText.length
        typingJob = scope.launch {
            try {
                var lastProgressUpdateTime = 0L
                var previousKeyCode: Byte = -1

                while (isActive && currentIndex < total && !isPaused) {
                    // Check Bluetooth connection status
                    if (hidManager.connectionState.value !is HidConnectionState.Connected) {
                        releaseAllKeys()
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

                    // Map character with safe fallback so auto-typing never aborts mid-stream
                    val stroke = KeyboardMapper.mapChar(effectiveChar)
                        ?: KeyboardMapper.mapChar(KeyboardMapper.fallbackChar(effectiveChar))

                    if (stroke != null) {
                        val currentDelay = _currentDelayMs.value
                        val isRepeatKey = (stroke.keyCode == previousKeyCode && previousKeyCode.toInt() != -1)

                        // If consecutive identical key (e.g. 'ee', 'll', 'oo', '11'):
                        // Host OS HID driver requires distinct release spacing before next identical press.
                        if (isRepeatKey) {
                            val repeatGap = if (currentDelay <= 2L) 3L else (currentDelay / 4).coerceIn(3L, 8L)
                            delay(repeatGap)
                        }

                        // 1. Send Key Down with Modifier
                        val keyDown = HidReportBuilder.buildKeyDownReport(stroke.keyCode, stroke.modifier)
                        val sentDown = hidManager.sendReport(keyDown)
                        if (!sentDown) {
                            delay(2)
                            hidManager.sendReport(keyDown)
                        }

                        // 2. Key hold duration
                        // At Turbo speeds (<= 2ms), release immediately for maximum throughput
                        val holdTime = if (currentDelay <= 2L) {
                            0L
                        } else {
                            (currentDelay / 4).coerceIn(1L, 6L)
                        }
                        if (holdTime > 0) {
                            delay(holdTime)
                        }

                        // 3. Send Key Up
                        val keyUp = HidReportBuilder.buildKeyUpReport()
                        val sentUp = hidManager.sendReport(keyUp)
                        if (!sentUp) {
                            delay(2)
                            hidManager.sendReport(keyUp)
                        }

                        previousKeyCode = stroke.keyCode

                        // 4. Inter-key delay
                        val interKeyDelay = (currentDelay - holdTime).coerceAtLeast(0L)
                        if (interKeyDelay > 0) {
                            delay(interKeyDelay)
                        }
                    }

                    currentIndex++

                    // Update live progress throttled to ~40ms to keep Compose UI at 60 FPS
                    val now = System.currentTimeMillis()
                    val isFinished = (currentIndex >= total)
                    if (isFinished || currentIndex == 1 || now - lastProgressUpdateTime >= 40L) {
                        lastProgressUpdateTime = now
                        val pct = (currentIndex.toFloat() / total) * 100f
                        val elapsed = (now - startTimeMs).coerceAtLeast(0L)
                        val remainingMs = ((total - currentIndex) * _currentDelayMs.value).coerceAtLeast(0L)
                        _typingState.value = TypingState.Typing(currentIndex, total, pct, elapsed, remainingMs)
                    }
                }

                if (currentIndex >= total && !isPaused) {
                    releaseAllKeys()
                    val duration = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(0L)
                    _typingState.value = TypingState.Completed(total, duration)
                    startTimeMs = 0L
                }
            } catch (e: CancellationException) {
                releaseAllKeys()
            } catch (e: Exception) {
                releaseAllKeys()
                _typingState.value = TypingState.Error("Unexpected typing error: ${e.message}", currentIndex)
            }
        }
    }
}
