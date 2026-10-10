package com.example.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

private const val TAG = "UsbTypingManager"
private const val USB_PORT = 8989
private const val PROTOCOL_VERSION = 1

class UsbTypingManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _connectionState = MutableStateFlow<UsbConnectionState>(
        UsbConnectionState.Disconnected("PC Companion Not Detected")
    )
    val connectionState: StateFlow<UsbConnectionState> = _connectionState.asStateFlow()

    private val _isUsbCableConnected = MutableStateFlow(false)
    val isUsbCableConnected: StateFlow<Boolean> = _isUsbCableConnected.asStateFlow()

    private val _typingProgress = MutableStateFlow<UsbTypingProgress>(UsbTypingProgress.Idle)
    val typingProgress: StateFlow<UsbTypingProgress> = _typingProgress.asStateFlow()

    private val _lastLatencyMs = MutableStateFlow(0L)
    val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

    private var activeSocket: Socket? = null
    private var socketReader: BufferedReader? = null
    private var socketWriter: BufferedWriter? = null
    private var serverSocket: ServerSocket? = null

    private var connectionMonitorJob: Job? = null
    private var heartbeatJob: Job? = null
    private var readerJob: Job? = null

    private var activeSessionId = ""
    private var sessionStartTime = 0L

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == "android.hardware.usb.action.USB_STATE") {
                val connected = intent.getBooleanExtra("connected", false)
                _isUsbCableConnected.value = connected
                Log.d(TAG, "USB State changed: connected=$connected")
                if (!connected && _connectionState.value is UsbConnectionState.Synced) {
                    disconnect("USB Cable Disconnected")
                }
            }
        }
    }

    init {
        registerUsbReceiver()
        startConnectionListener()
    }

    private fun registerUsbReceiver() {
        try {
            val filter = IntentFilter("android.hardware.usb.action.USB_STATE")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(usbReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register USB receiver: ${e.message}")
        }
    }

    fun startConnectionListener() {
        connectionMonitorJob?.cancel()
        connectionMonitorJob = scope.launch {
            // Start local server socket to accept forward connections from PC
            launch { runServerSocketListener() }
        }
    }

    private suspend fun runServerSocketListener() = withContext(Dispatchers.IO) {
        try {
            serverSocket?.close()
            serverSocket = ServerSocket(USB_PORT).apply {
                reuseAddress = true
            }
            Log.d(TAG, "Server socket listening on port $USB_PORT")

            while (isActive) {
                try {
                    val client = serverSocket?.accept() ?: break
                    Log.d(TAG, "Incoming connection from ${client.inetAddress}:${client.port}")
                    handleEstablishedSocket(client)
                } catch (e: Exception) {
                    if (!isActive) break
                    Log.d(TAG, "ServerSocket accept exception: ${e.message}")
                    delay(1000)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bind server socket on $USB_PORT: ${e.message}")
        }
    }

    suspend fun checkConnection(targetHost: String = "127.0.0.1", targetPort: Int = USB_PORT): UsbConnectionState =
        withContext(Dispatchers.IO) {
            _connectionState.value = UsbConnectionState.Connecting("Probing companion on $targetHost:$targetPort...")
            val startTime = System.currentTimeMillis()

            // If active socket is already synced, ping it
            activeSocket?.let { sock ->
                if (!sock.isClosed && sock.isConnected) {
                    try {
                        val pingMsg = JSONObject().apply {
                            put("type", "PING")
                            put("timestamp", startTime)
                        }.toString()
                        sendRawLine(pingMsg)
                        val elapsed = System.currentTimeMillis() - startTime
                        _lastLatencyMs.value = elapsed
                        val currentState = _connectionState.value
                        if (currentState is UsbConnectionState.Synced) {
                            val updated = currentState.copy(latencyMs = elapsed)
                            _connectionState.value = updated
                            return@withContext updated
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Active socket ping failed: ${e.message}")
                        closeActiveSocket()
                    }
                }
            }

            // Attempt outgoing connection to PC companion (e.g. adb reverse tcp:8989 tcp:8989)
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(targetHost, targetPort), 2500)
                socket.soTimeout = 4000
                return@withContext handleEstablishedSocket(socket)
            } catch (e: Exception) {
                Log.d(TAG, "Connection probe to $targetHost:$targetPort failed: ${e.message}")
                val state = UsbConnectionState.Disconnected(
                    "Companion not reached. Ensure replica-companion.exe is running on PC."
                )
                _connectionState.value = state
                return@withContext state
            }
        }

    private suspend fun handleEstablishedSocket(socket: Socket): UsbConnectionState =
        withContext(Dispatchers.IO) {
            closeActiveSocket()
            activeSocket = socket
            socketReader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
            socketWriter = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))

            _connectionState.value = UsbConnectionState.Connected(
                host = socket.inetAddress?.hostAddress ?: "127.0.0.1",
                port = socket.port
            )

            // Start reader loop
            startReaderLoop()

            // Perform handshake
            val synTime = System.currentTimeMillis()
            val syn = JSONObject().apply {
                put("type", "HANDSHAKE_SYN")
                put("protocolVersion", PROTOCOL_VERSION)
                put("client", "replica_kspp_android")
                put("appVersion", "1.0.0")
                put("timestamp", synTime)
            }.toString()

            val sent = sendRawLine(syn)
            if (!sent) {
                val err = UsbConnectionState.Error("Failed to transmit handshake SYN to companion")
                _connectionState.value = err
                return@withContext err
            }

            // Wait up to 3 seconds for handshake ACK in reader loop
            var waited = 0
            while (waited < 30) {
                val state = _connectionState.value
                if (state is UsbConnectionState.Synced || state is UsbConnectionState.Incompatible) {
                    startHeartbeat()
                    return@withContext state
                }
                delay(100)
                waited++
            }

            // Default fallback if no ACK
            val timeoutState = UsbConnectionState.Disconnected(
                "Handshake timed out. No response from Windows companion."
            )
            _connectionState.value = timeoutState
            closeActiveSocket()
            return@withContext timeoutState
        }

    private fun startReaderLoop() {
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.IO) {
            try {
                val reader = socketReader ?: return@launch
                while (isActive) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) {
                        processIncomingLine(line)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Socket reader disconnected: ${e.message}")
            } finally {
                disconnect("Connection closed by host")
            }
        }
    }

    private fun processIncomingLine(line: String) {
        try {
            val json = JSONObject(line)
            val type = json.optString("type")

            when (type) {
                "HANDSHAKE_ACK" -> {
                    val proto = json.optInt("protocolVersion", 1)
                    val compVersion = json.optString("companionVersion", "1.0.0")
                    val os = json.optString("os", "Windows")

                    if (proto != PROTOCOL_VERSION) {
                        _connectionState.value = UsbConnectionState.Incompatible(
                            companionVersion = compVersion,
                            reason = "Protocol version mismatch (expected $PROTOCOL_VERSION, got $proto)"
                        )
                    } else {
                        _connectionState.value = UsbConnectionState.Synced(
                            companionVersion = compVersion,
                            protocolVersion = proto,
                            deviceOs = os,
                            latencyMs = _lastLatencyMs.value
                        )
                    }
                }

                "PONG" -> {
                    val state = _connectionState.value
                    if (state is UsbConnectionState.Synced) {
                        _connectionState.value = state.copy(latencyMs = _lastLatencyMs.value)
                    }
                }

                "PROGRESS" -> {
                    val current = json.optInt("currentIndex", 0)
                    val total = json.optInt("totalChars", 1)
                    val pct = json.optDouble("percent", 0.0).toFloat()
                    val elapsed = System.currentTimeMillis() - sessionStartTime
                    val remaining = if (current > 0) ((elapsed.toFloat() / current) * (total - current)).toLong() else 0L

                    _typingProgress.value = UsbTypingProgress.Typing(
                        currentIndex = current,
                        totalChars = total,
                        percent = pct.coerceIn(0f, 1f),
                        elapsedMs = elapsed,
                        remainingMs = remaining.coerceAtLeast(0L)
                    )
                }

                "PAUSED" -> {
                    val cur = _typingProgress.value
                    if (cur is UsbTypingProgress.Typing) {
                        _typingProgress.value = UsbTypingProgress.Paused(
                            currentIndex = cur.currentIndex,
                            totalChars = cur.totalChars,
                            percent = cur.percent
                        )
                    }
                }

                "RESUMED" -> {
                    val cur = _typingProgress.value
                    if (cur is UsbTypingProgress.Paused) {
                        _typingProgress.value = UsbTypingProgress.Typing(
                            currentIndex = cur.currentIndex,
                            totalChars = cur.totalChars,
                            percent = cur.percent
                        )
                    }
                }

                "STOPPED" -> {
                    val cur = _typingProgress.value
                    val typed = if (cur is UsbTypingProgress.Typing) cur.currentIndex else 0
                    val total = if (cur is UsbTypingProgress.Typing) cur.totalChars else 0
                    _typingProgress.value = UsbTypingProgress.Stopped(typed, total)
                }

                "DONE" -> {
                    val total = json.optInt("totalTyped", 0)
                    val elapsed = System.currentTimeMillis() - sessionStartTime
                    _typingProgress.value = UsbTypingProgress.Completed(total, elapsed)
                }

                "ERROR" -> {
                    val msg = json.optString("message", "Unknown companion error")
                    Log.w(TAG, "Companion reported error: $msg")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse line from companion: $line", e)
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3500)
                if (_connectionState.value is UsbConnectionState.Synced) {
                    val t0 = System.currentTimeMillis()
                    val ping = JSONObject().apply {
                        put("type", "PING")
                        put("timestamp", t0)
                    }.toString()

                    val ok = sendRawLine(ping)
                    if (ok) {
                        _lastLatencyMs.value = System.currentTimeMillis() - t0
                    } else {
                        disconnect("Heartbeat transmission failed")
                        break
                    }
                } else {
                    break
                }
            }
        }
    }

    fun startTyping(text: String, delayMs: Int): Boolean {
        if (_connectionState.value !is UsbConnectionState.Synced) {
            Log.w(TAG, "Cannot start typing: Not synced with Windows companion")
            return false
        }

        activeSessionId = "usb_${System.currentTimeMillis()}"
        sessionStartTime = System.currentTimeMillis()
        _typingProgress.value = UsbTypingProgress.Typing(
            currentIndex = 0,
            totalChars = text.length,
            percent = 0f,
            elapsedMs = 0L,
            remainingMs = (text.length * delayMs).toLong()
        )

        val cmd = JSONObject().apply {
            put("type", "CMD_START")
            put("sessionId", activeSessionId)
            put("text", text)
            put("delayMs", delayMs)
        }.toString()

        return sendRawLine(cmd)
    }

    fun pauseTyping(): Boolean {
        val cmd = JSONObject().apply {
            put("type", "CMD_PAUSE")
        }.toString()
        return sendRawLine(cmd)
    }

    fun resumeTyping(): Boolean {
        val cmd = JSONObject().apply {
            put("type", "CMD_RESUME")
        }.toString()
        return sendRawLine(cmd)
    }

    fun stopTyping(): Boolean {
        val cmd = JSONObject().apply {
            put("type", "CMD_STOP")
        }.toString()
        val res = sendRawLine(cmd)
        _typingProgress.value = UsbTypingProgress.Stopped(0, 0)
        return res
    }

    fun resetProgressToIdle() {
        _typingProgress.value = UsbTypingProgress.Idle
    }

    private fun sendRawLine(line: String): Boolean {
        return try {
            val writer = socketWriter ?: return false
            writer.write(line)
            writer.newLine()
            writer.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send line to companion: ${e.message}")
            false
        }
    }

    private fun closeActiveSocket() {
        try {
            socketReader?.close()
            socketWriter?.close()
            activeSocket?.close()
        } catch (_: Exception) {}
        socketReader = null
        socketWriter = null
        activeSocket = null
    }

    fun disconnect(reason: String = "Disconnected") {
        heartbeatJob?.cancel()
        readerJob?.cancel()
        closeActiveSocket()
        _connectionState.value = UsbConnectionState.Disconnected(reason)
        _typingProgress.value = UsbTypingProgress.Idle
    }

    fun cleanup() {
        disconnect("Manager destroyed")
        connectionMonitorJob?.cancel()
        try {
            serverSocket?.close()
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
    }
}
