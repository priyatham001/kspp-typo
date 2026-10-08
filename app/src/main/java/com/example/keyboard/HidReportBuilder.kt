package com.example.keyboard

/**
 * Builds valid 8-byte standard USB/Bluetooth HID Keyboard Input Reports.
 *
 * Report Structure (8 bytes):
 * Byte 0: Modifier bits (Ctrl, Shift, Alt, GUI)
 * Byte 1: Reserved (0x00)
 * Byte 2: Key Code 1
 * Byte 3: Key Code 2
 * Byte 4: Key Code 3
 * Byte 5: Key Code 4
 * Byte 6: Key Code 5
 * Byte 7: Key Code 6
 */
object HidReportBuilder {

    const val REPORT_SIZE = 8

    /**
     * Builds a single key press report with optional modifiers.
     */
    fun buildKeyDownReport(keyCode: Byte, modifier: Byte = KeyboardDescriptor.MOD_NONE): ByteArray {
        return byteArrayOf(
            modifier,                     // Byte 0: Modifier mask
            0x00.toByte(),                // Byte 1: Reserved
            keyCode,                      // Byte 2: Key code 1
            0x00.toByte(),                // Byte 3: Key code 2
            0x00.toByte(),                // Byte 4: Key code 3
            0x00.toByte(),                // Byte 5: Key code 4
            0x00.toByte(),                // Byte 6: Key code 5
            0x00.toByte()                 // Byte 7: Key code 6
        )
    }

    /**
     * Builds a release report (all keys released, all modifiers zeroed).
     * Used after every key press to ensure modifiers and keys never get stuck.
     */
    fun buildKeyUpReport(): ByteArray {
        return ByteArray(REPORT_SIZE) { 0x00.toByte() }
    }
}
