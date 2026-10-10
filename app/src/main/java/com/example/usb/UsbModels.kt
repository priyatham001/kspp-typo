package com.example.usb

sealed class UsbConnectionState {
    data class Disconnected(val reason: String = "PC Companion Not Detected") : UsbConnectionState()
    data class Connecting(val message: String = "Connecting to companion on port 8989...") : UsbConnectionState()
    data class Connected(val host: String = "127.0.0.1", val port: Int = 8989) : UsbConnectionState()
    data class Synced(
        val companionVersion: String = "1.0.1",
        val protocolVersion: Int = 1,
        val latencyMs: Long = 0L,
        val deviceOs: String = "Windows",
        val host: String = "127.0.0.1",
        val port: Int = 8989
    ) : UsbConnectionState()
    data class Incompatible(
        val companionVersion: String,
        val requiredVersion: String = "1.0.0+",
        val reason: String = "Protocol or version mismatch. Please update the Windows companion."
    ) : UsbConnectionState()
    data class Error(val message: String) : UsbConnectionState()
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
    val fileSize: Long = 265080L,
    val sha256: String? = "dbf6b872f48b431d897492a094a03f6a27ae9e84b1f1f5fe4110f04326f790df",
    val downloadUrl: String = "/download/replica-companion.exe",
    val uploadedBy: String = "admin",
    val lastUpdated: Long = System.currentTimeMillis(),
    val releaseNotes: String = "Official Windows companion program for USB auto-typing into Windows applications."
)
