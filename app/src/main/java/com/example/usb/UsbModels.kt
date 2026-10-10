package com.example.usb

enum class UsbStageStatus {
    VERIFIED,
    WARNING,
    FAILED,
    UNKNOWN
}

data class UsbDiagnosticStep(
    val id: String,
    val title: String,
    val status: UsbStageStatus,
    val detail: String,
    val fixInstruction: String? = null
)

data class UsbDiagnosticsReport(
    val cableOrUsbDetected: Boolean = false,
    val developerOptionsEnabled: Boolean = false,
    val usbDebuggingEnabled: Boolean = false,
    val phoneServerListening8990: Boolean = false,
    val reverseTunnel8989Reachable: Boolean = false,
    val forwardTunnel8990Connected: Boolean = false,
    val handshakeCompleted: Boolean = false,
    val heartbeatVerified: Boolean = false,
    val companionAdbPath: String? = null,
    val companionAdbVersion: String? = null,
    val companionAdbDevices: String? = null,
    val companionDeviceAuthorized: Boolean? = null,
    val companionReverseStatus: String? = null,
    val companionForwardStatus: String? = null,
    val failedStage: String? = null,
    val actionableFix: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isReadyToType: Boolean
        get() = handshakeCompleted && heartbeatVerified && (reverseTunnel8989Reachable || forwardTunnel8990Connected)
}

sealed class UsbConnectionState {
    data class Disconnected(
        val reason: String = "PC Companion Not Detected",
        val failedStage: String = "Companion Socket (127.0.0.1:8989)",
        val fixSuggestion: String = "Ensure USB Debugging is ON, PC is authorized ('Always allow'), and replica-companion.exe is running on Windows."
    ) : UsbConnectionState()

    data class Connecting(
        val message: String = "Connecting to companion on port 8989..."
    ) : UsbConnectionState()

    data class Connected(
        val host: String = "127.0.0.1",
        val port: Int = 8989
    ) : UsbConnectionState()

    data class Synced(
        val companionVersion: String = "1.1.0",
        val protocolVersion: Int = 1,
        val latencyMs: Long = 0L,
        val deviceOs: String = "Windows",
        val host: String = "127.0.0.1",
        val port: Int = 8989,
        val tunnelMode: String = "Reverse (8989) + Forward (8990)",
        val adbStatus: String = "Authorized & Active"
    ) : UsbConnectionState()

    data class Incompatible(
        val companionVersion: String,
        val requiredVersion: String = "1.0.0+",
        val reason: String = "Protocol or version mismatch. Please update the Windows companion."
    ) : UsbConnectionState()

    data class Error(
        val message: String,
        val failedStage: String = "Protocol / Socket Error",
        val fixSuggestion: String = "Check USB cable, restart replica-companion.exe, or run 'adb reverse tcp:8989 tcp:8989' on PC."
    ) : UsbConnectionState()
}

sealed class UsbTypingProgress {
    data object Idle : UsbTypingProgress()
    data class Typing(
        val currentIndex: Int,
        val totalChars: Int,
        val percent: Float,
        val elapsedMs: Long = 0L,
        val remainingMs: Long = 0L
    ) : UsbTypingProgress()
    data class Paused(
        val currentIndex: Int,
        val totalChars: Int,
        val percent: Float
    ) : UsbTypingProgress()
    data class Completed(
        val totalChars: Int,
        val elapsedMs: Long
    ) : UsbTypingProgress()
    data class Stopped(
        val charsTyped: Int,
        val totalChars: Int
    ) : UsbTypingProgress()
}

data class CompanionInfo(
    val version: String = "1.1.0",
    val protocolVersion: Int = 1,
    val minAppVersion: String = "1.0.0",
    val fileName: String = "replica-companion.exe",
    val fileSize: Long = 437196L,
    val sha256: String? = "61170807a9f46930974ac2b0335f5aaa695f9112b95d6fa99b18755b1e474e9a",
    val downloadUrl: String = "/download/replica-companion.exe",
    val uploadedBy: String = "admin",
    val lastUpdated: Long = System.currentTimeMillis(),
    val releaseNotes: String = "Official Windows companion program v1.1.0 with automatic ADB discovery, authorization diagnostics, dual-tunnel (8989/8990) auto-recovery, and multi-threaded Unicode typing."
)

