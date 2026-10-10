package com.example

import com.example.keyboard.HidReportBuilder
import com.example.keyboard.KeyboardDescriptor
import com.example.keyboard.KeyboardMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testKeyboardMapping_letters() {
        val aStroke = KeyboardMapper.mapChar('a')
        assertNotNull(aStroke)
        assertEquals(KeyboardDescriptor.KEY_A, aStroke!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_NONE, aStroke.modifier)

        val capitalAStroke = KeyboardMapper.mapChar('A')
        assertNotNull(capitalAStroke)
        assertEquals(KeyboardDescriptor.KEY_A, capitalAStroke!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_LEFT_SHIFT, capitalAStroke.modifier)
    }

    @Test
    fun testKeyboardMapping_numbersAndShiftedSymbols() {
        val oneStroke = KeyboardMapper.mapChar('1')
        assertNotNull(oneStroke)
        assertEquals(KeyboardDescriptor.KEY_1, oneStroke!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_NONE, oneStroke.modifier)

        val exclamationStroke = KeyboardMapper.mapChar('!')
        assertNotNull(exclamationStroke)
        assertEquals(KeyboardDescriptor.KEY_1, exclamationStroke!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_LEFT_SHIFT, exclamationStroke.modifier)

        val atStroke = KeyboardMapper.mapChar('@')
        assertNotNull(atStroke)
        assertEquals(KeyboardDescriptor.KEY_2, atStroke!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_LEFT_SHIFT, atStroke.modifier)

        val hashStroke = KeyboardMapper.mapChar('#')
        assertNotNull(hashStroke)
        assertEquals(KeyboardDescriptor.KEY_3, hashStroke!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_LEFT_SHIFT, hashStroke.modifier)
    }

    @Test
    fun testKeyboardMapping_bracketsAndPunctuation() {
        // [ and {
        val openBracket = KeyboardMapper.mapChar('[')
        assertNotNull(openBracket)
        assertEquals(KeyboardDescriptor.KEY_LEFTBRACE, openBracket!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_NONE, openBracket.modifier)

        val openBrace = KeyboardMapper.mapChar('{')
        assertNotNull(openBrace)
        assertEquals(KeyboardDescriptor.KEY_LEFTBRACE, openBrace!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_LEFT_SHIFT, openBrace.modifier)

        // ] and }
        val closeBracket = KeyboardMapper.mapChar(']')
        assertNotNull(closeBracket)
        assertEquals(KeyboardDescriptor.KEY_RIGHTBRACE, closeBracket!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_NONE, closeBracket.modifier)

        val closeBrace = KeyboardMapper.mapChar('}')
        assertNotNull(closeBrace)
        assertEquals(KeyboardDescriptor.KEY_RIGHTBRACE, closeBrace!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_LEFT_SHIFT, closeBrace.modifier)

        // ; and :
        val colon = KeyboardMapper.mapChar(':')
        assertNotNull(colon)
        assertEquals(KeyboardDescriptor.KEY_SEMICOLON, colon!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_LEFT_SHIFT, colon.modifier)

        // ' and "
        val doubleQuote = KeyboardMapper.mapChar('"')
        assertNotNull(doubleQuote)
        assertEquals(KeyboardDescriptor.KEY_APOSTROPHE, doubleQuote!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_LEFT_SHIFT, doubleQuote.modifier)

        // , and <
        val lessThan = KeyboardMapper.mapChar('<')
        assertNotNull(lessThan)
        assertEquals(KeyboardDescriptor.KEY_COMMA, lessThan!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_LEFT_SHIFT, lessThan.modifier)

        // . and >
        val greaterThan = KeyboardMapper.mapChar('>')
        assertNotNull(greaterThan)
        assertEquals(KeyboardDescriptor.KEY_DOT, greaterThan!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_LEFT_SHIFT, greaterThan.modifier)

        // / and ?
        val question = KeyboardMapper.mapChar('?')
        assertNotNull(question)
        assertEquals(KeyboardDescriptor.KEY_SLASH, question!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_LEFT_SHIFT, question.modifier)

        // Backslash and Pipe
        val backslash = KeyboardMapper.mapChar('\\')
        assertNotNull(backslash)
        assertEquals(KeyboardDescriptor.KEY_BACKSLASH, backslash!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_NONE, backslash.modifier)

        val pipe = KeyboardMapper.mapChar('|')
        assertNotNull(pipe)
        assertEquals(KeyboardDescriptor.KEY_BACKSLASH, pipe!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_LEFT_SHIFT, pipe.modifier)
    }

    @Test
    fun testKeyboardMapping_whitespace() {
        val newline = KeyboardMapper.mapChar('\n')
        assertNotNull(newline)
        assertEquals(KeyboardDescriptor.KEY_ENTER, newline!!.keyCode)

        val tab = KeyboardMapper.mapChar('\t')
        assertNotNull(tab)
        assertEquals(KeyboardDescriptor.KEY_TAB, tab!!.keyCode)

        val space = KeyboardMapper.mapChar(' ')
        assertNotNull(space)
        assertEquals(KeyboardDescriptor.KEY_SPACE, space!!.keyCode)
    }

    @Test
    fun testUnsupportedCharacterDetection() {
        // Non-ASCII currency, symbols, and accented characters not in US QWERTY must return null
        val copyright = KeyboardMapper.mapChar('©')
        assertNull(copyright)

        val rupee = KeyboardMapper.mapChar('₹')
        assertNull(rupee)

        val euro = KeyboardMapper.mapChar('€')
        assertNull(euro)

        val accented = KeyboardMapper.mapChar('é')
        assertNull(accented)
    }

    @Test
    fun testHidReportBuilder_structure() {
        val keyDown = HidReportBuilder.buildKeyDownReport(
            KeyboardDescriptor.KEY_A,
            KeyboardDescriptor.MOD_LEFT_SHIFT
        )
        assertEquals(8, keyDown.size)
        assertEquals(KeyboardDescriptor.MOD_LEFT_SHIFT, keyDown[0])
        assertEquals(0x00.toByte(), keyDown[1]) // Reserved
        assertEquals(KeyboardDescriptor.KEY_A, keyDown[2])
        assertEquals(0x00.toByte(), keyDown[3])

        val keyUp = HidReportBuilder.buildKeyUpReport()
        assertEquals(8, keyUp.size)
        for (i in 0 until 8) {
            assertEquals(0x00.toByte(), keyUp[i])
        }
    }

    @Test
    fun testReplicateKeyboardMotto() {
        val motto = "I replicate keyboard"
        for (char in motto) {
            val stroke = KeyboardMapper.mapChar(char)
            assertNotNull(stroke)
        }
    }

    @Test
    fun testUnicodeSmartQuoteNormalization() {
        // Smart quotes from mobile devices should normalize cleanly
        val leftSingle = KeyboardMapper.mapChar('‘')
        assertNotNull(leftSingle)
        assertEquals(KeyboardDescriptor.KEY_APOSTROPHE, leftSingle!!.keyCode)

        val rightDouble = KeyboardMapper.mapChar('”')
        assertNotNull(rightDouble)
        assertEquals(KeyboardDescriptor.KEY_APOSTROPHE, rightDouble!!.keyCode)
        assertEquals(KeyboardDescriptor.MOD_LEFT_SHIFT, rightDouble.modifier)

        val enDash = KeyboardMapper.mapChar('–')
        assertNotNull(enDash)
        assertEquals(KeyboardDescriptor.KEY_MINUS, enDash!!.keyCode)

        val textWithSmartQuotes = "“Hello world!” – said ‘Replica’"
        val unsupported = KeyboardMapper.findUnsupportedChars(textWithSmartQuotes)
        assertTrue(unsupported.isEmpty())
    }

    @Test
    fun testCleanText_removesOrConvertsUnsupportedChars() {
        val mixedText = "Code: © 2026 “replica_kspp” – ₹99"
        val cleaned = KeyboardMapper.cleanText(mixedText)
        assertTrue(cleaned.contains("\"replica_kspp\""))
        assertTrue(cleaned.contains("-"))
        // Unsupported glyphs like © or ₹ should be removed, leaving only valid HID characters
        for (c in cleaned) {
            assertNotNull(KeyboardMapper.mapChar(c))
        }
    }

    @Test
    fun testUsbConnectionState_defaultsAndProperties() {
        val disconnected = com.example.usb.UsbConnectionState.Disconnected("No USB cable")
        assertEquals("No USB cable", disconnected.reason)

        val synced = com.example.usb.UsbConnectionState.Synced(
            companionVersion = "1.0.1",
            protocolVersion = 1,
            latencyMs = 12L,
            deviceOs = "Windows"
        )
        assertEquals("1.0.1", synced.companionVersion)
        assertEquals(1, synced.protocolVersion)
        assertEquals(12L, synced.latencyMs)
        assertEquals("Windows", synced.deviceOs)

        val incompatible = com.example.usb.UsbConnectionState.Incompatible(
            companionVersion = "0.9.0",
            requiredVersion = "1.0.0+",
            reason = "Protocol mismatch"
        )
        assertEquals("0.9.0", incompatible.companionVersion)
        assertTrue(incompatible.reason.contains("Protocol mismatch"))
    }

    @Test
    fun testCompanionInfo_modelDefaults() {
        val info = com.example.usb.CompanionInfo()
        assertEquals("1.1.0", info.version)
        assertEquals(1, info.protocolVersion)
        assertEquals("replica-companion.exe", info.fileName)
        assertTrue(info.fileSize > 0)
        assertNotNull(info.sha256)
    }

    @Test
    fun testUsbTypingProgress_progressCalculations() {
        val typing = com.example.usb.UsbTypingProgress.Typing(
            currentIndex = 50,
            totalChars = 100,
            percent = 0.5f,
            elapsedMs = 1500L,
            remainingMs = 1500L
        )
        assertEquals(50, typing.currentIndex)
        assertEquals(100, typing.totalChars)
        assertEquals(0.5f, typing.percent, 0.001f)
        assertEquals(1500L, typing.elapsedMs)
    }
}
