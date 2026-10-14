package com.example.typing

import com.example.bluetooth.BluetoothHidManager
import com.example.bluetooth.HidConnectionState
import com.example.keyboard.HidReportBuilder
import com.example.keyboard.KeyboardMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class TypingState {
    data object Idle : TypingState()
    data class Preparing(val secondsRemaining: Int, val totalChars: Int) : TypingState()
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
 *
 * Guarantees:
 * - Thread-safe single-runner execution via Mutex (prevents overlapping/interleaved keystrokes).
 * - Exact text fidelity: preserves case, numbers, symbols, spaces, and line breaks.
 * - Releases all modifier keys and key codes after every keystroke.
 * - Safe host Caps Lock synchronization.
 * - Immediate abort and clean key release if Bluetooth connection drops.
 */
class TypingEngine(
    private val hidManager: BluetoothHidManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val _typingState = MutableStateFlow<TypingState>(TypingState.Idle)
    val typingState: StateFlow<TypingState> = _typingState.asStateFlow()

    private val _currentDelayMs = MutableStateFlow(30L)
    val currentDelayMs: StateFlow<Long> = _currentDelayMs.asStateFlow()

    private val typingMutex = Mutex()
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
     * Prevents overlapping runs and verifies Bluetooth connection.
     */
    fun startTyping(text: String, customDelayMs: Long? = null, withCountdown: Boolean = true) {
        if (text.isEmpty()) {
            _typingState.value = TypingState.Error("There is no text to type. Please enter text first.")
            return
        }

        // Verify genuine Bluetooth HID connection
        val connState = hidManager.connectionState.value
        if (connState !is HidConnectionState.Connected || !hidManager.verifyRealConnection()) {
            _typingState.value = TypingState.Error("Please connect the device first.")
            return
        }

        customDelayMs?.let { setDelay(it) }

        scope.launch {
            typingMutex.withLock {
                // Ensure any previous running job is cancelled and cleanly awaited
                val existingJob = typingJob
                if (existingJob != null && existingJob.isActive) {
                    existingJob.cancelAndJoin()
                }
                releaseAllKeys()

                fullText = text
                currentIndex = 0
                isPaused = false
                startTimeMs = 0L

                launchTypingLoopInternal(targetText = text, startIndex = 0, withCountdown = withCountdown)
            }
        }
    }

    /**
     * Skip countdown and begin keystroke streaming immediately.
     */
    fun skipCountdown() {
        if (_typingState.value is TypingState.Preparing) {
            scope.launch {
                typingMutex.withLock {
                    typingJob?.cancelAndJoin()
                    launchTypingLoopInternal(targetText = fullText, startIndex = currentIndex, withCountdown = false)
                }
            }
        }
    }

    /**
     * Restart typing the current text from index 0.
     */
    fun restartTyping(customDelayMs: Long? = null, withCountdown: Boolean = true) {
        val textToType = fullText
        if (textToType.isNotEmpty()) {
            startTyping(textToType, customDelayMs, withCountdown = withCountdown)
        }
    }

    /**
     * Temporarily pause typing. Keeps current position intact and releases active keys.
     */
    fun pauseTyping() {
        scope.launch {
            typingMutex.withLock {
                if (_typingState.value is TypingState.Typing || _typingState.value is TypingState.Preparing) {
                    isPaused = true
                    typingJob?.cancelAndJoin()
                    typingJob = null
                    releaseAllKeys()
                    val total = fullText.length
                    val pct = if (total > 0) (currentIndex.toFloat() / total) * 100f else 0f
                    _typingState.value = TypingState.Paused(currentIndex, total, pct)
                }
            }
        }
    }

    /**
     * Resume typing from the exact paused index without countdown.
     */
    fun resumeTyping() {
        if (_typingState.value is TypingState.Paused) {
            val connState = hidManager.connectionState.value
            if (connState !is HidConnectionState.Connected || !hidManager.verifyRealConnection()) {
                _typingState.value = TypingState.Error("Bluetooth connection lost while paused. Please connect the device first.")
                return
            }
            scope.launch {
                typingMutex.withLock {
                    isPaused = false
                    launchTypingLoopInternal(targetText = fullText, startIndex = currentIndex, withCountdown = false)
                }
            }
        }
    }

    /**
     * Immediately cancel typing operation and reset.
     * Guaranteed termination: cancels active job and releases all modifiers immediately.
     */
    fun stopTyping(resetState: Boolean = true) {
        scope.launch {
            typingMutex.withLock {
                val existing = typingJob
                typingJob = null
                existing?.cancelAndJoin()
                isPaused = false
                releaseAllKeys()

                if (resetState) {
                    val total = fullText.length
                    _typingState.value = TypingState.Stopped(currentIndex, total)
                    startTimeMs = 0L
                }
            }
        }
    }

    /**
     * Reset to Idle state.
     */
    fun resetToIdle() {
        scope.launch {
            typingMutex.withLock {
                val existing = typingJob
                typingJob = null
                existing?.cancelAndJoin()
                currentIndex = 0
                fullText = ""
                startTimeMs = 0L
                isPaused = false
                releaseAllKeys()
                _typingState.value = TypingState.Idle
            }
        }
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
            typingMutex.withLock {
                try {
                    hidManager.sendReport(HidReportBuilder.buildKeyDownReport(keyCode, modifier))
                    delay(12)
                } finally {
                    releaseAllKeys()
                }
            }
        }
    }

    /**
     * Type an entire raw string directly for testing (e.g. TEST ALL KEYS).
     */
    fun typeDirectString(text: String, onFinished: (() -> Unit)? = null) {
        scope.launch {
            typingMutex.withLock {
                val existing = typingJob
                existing?.cancelAndJoin()

                typingJob = scope.launch {
                    try {
                        var prevKey: Byte = -1
                        for (char in text) {
                            if (!isActive) break
                            if (char == '\r') continue
                            val isCapsOn = hidManager.isCapsLockActive.value
                            val stroke = KeyboardMapper.mapChar(char, isCapsOn) ?: KeyboardMapper.mapChar(' ', isCapsOn)
                            if (stroke != null) {
                                if (stroke.keyCode == prevKey) {
                                    delay(8)
                                }
                                hidManager.sendReport(HidReportBuilder.buildKeyDownReport(stroke.keyCode, stroke.modifier))
                                delay(6)
                                releaseAllKeys()
                                delay(10)
                                prevKey = stroke.keyCode
                            }
                        }
                    } finally {
                        releaseAllKeys()
                        onFinished?.invoke()
                    }
                }
            }
        }
    }

    /**
     * Core typing loop. Runs as an isolated coroutine child with local progress tracking.
     */
    private fun launchTypingLoopInternal(
        targetText: String,
        startIndex: Int,
        withCountdown: Boolean = false
    ) {
        val total = targetText.length
        currentIndex = startIndex

        typingJob = scope.launch {
            try {
                // 1. Initial Focus Preparation Countdown (3 seconds)
                if (withCountdown && currentIndex == 0) {
                    for (seconds in 3 downTo 1) {
                        if (!isActive || isPaused) return@launch
                        _typingState.value = TypingState.Preparing(seconds, total)
                        delay(1000)
                    }
                }

                if (startTimeMs == 0L) {
                    startTimeMs = System.currentTimeMillis()
                }

                var lastProgressUpdateTime = 0L
                var previousKeyCode: Byte = -1

                while (isActive && currentIndex < total && !isPaused) {
                    // Check Bluetooth connection status before every keystroke
                    if (hidManager.connectionState.value !is HidConnectionState.Connected) {
                        releaseAllKeys()
                        _typingState.value = TypingState.Error(
                            "Bluetooth connection lost at character $currentIndex of $total. Typing stopped.",
                            currentIndex
                        )
                        return@launch
                    }

                    val char = targetText[currentIndex]

                    // Handle CRLF (\r\n), standalone \r, and \n:
                    // \r\n -> map as single ENTER keystroke without duplicating line break
                    val effectiveChar = if (char == '\r') {
                        if (currentIndex + 1 < total && targetText[currentIndex + 1] == '\n') {
                            currentIndex++ // skip ahead so \n acts as the single ENTER
                            targetText[currentIndex]
                        } else {
                            '\n' // standalone CR acts as ENTER
                        }
                    } else {
                        char
                    }

                    // Map character directly preserving exact letter case and host Caps Lock state
                    val isCapsOn = hidManager.isCapsLockActive.value
                    val stroke = KeyboardMapper.mapChar(effectiveChar, isCapsOn)

                    if (stroke != null) {
                        val currentDelay = _currentDelayMs.value
                        val isRepeatKey = (stroke.keyCode == previousKeyCode && previousKeyCode.toInt() != -1)

                        // If consecutive identical key (e.g. 'ee', 'll', 'oo', '11'):
                        // Host OS HID driver requires distinct release spacing before next identical press.
                        if (isRepeatKey) {
                            val repeatGap = (currentDelay / 4).coerceIn(6L, 14L)
                            delay(repeatGap)
                        }

                        // 1. Send Key Down with exact Modifier (Shift applied only when required)
                        val keyDown = HidReportBuilder.buildKeyDownReport(stroke.keyCode, stroke.modifier)
                        val sentDown = hidManager.sendReport(keyDown)
                        if (!sentDown) {
                            // Retry once; if failed, check connection
                            delay(4)
                            val retrySent = hidManager.sendReport(keyDown)
                            if (!retrySent && !hidManager.verifyRealConnection()) {
                                releaseAllKeys()
                                _typingState.value = TypingState.Error(
                                    "Bluetooth transmission failed at character $currentIndex. Host disconnected.",
                                    currentIndex
                                )
                                return@launch
                            }
                        }

                        // 2. Key hold duration: ensure host OS registers KeyDown before KeyUp
                        val holdTime = if (currentDelay <= 2L) {
                            3L
                        } else {
                            (currentDelay / 3).coerceIn(3L, 12L)
                        }
                        delay(holdTime)

                        // 3. Send Key Up (all 8 bytes zeroed, releasing both key code and modifier)
                        val keyUp = HidReportBuilder.buildKeyUpReport()
                        hidManager.sendReport(keyUp)

                        previousKeyCode = stroke.keyCode

                        // 4. Inter-key delay (gap before the next character)
                        val interKeyDelay = (currentDelay - holdTime).coerceAtLeast(3L)
                        delay(interKeyDelay)
                    } else {
                        // Unmapped character (e.g., unsupported emoji or control code)
                        // Do not modify other text; report or skip gracefully
                        previousKeyCode = -1
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
                    // Allow host OS keyboard buffer a clean settling pause before signalling completion
                    delay(50)
                    val duration = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(0L)
                    _typingState.value = TypingState.Completed(total, duration)
                    startTimeMs = 0L
                }
            } catch (e: CancellationException) {
                releaseAllKeys()
            } catch (e: Exception) {
                releaseAllKeys()
                _typingState.value = TypingState.Error("Unexpected typing error: ${e.message}", currentIndex)
            } finally {
                releaseAllKeys()
            }
        }
    }
}
