package com.example.keyboard

import com.example.keyboard.KeyboardDescriptor.KEY_0
import com.example.keyboard.KeyboardDescriptor.KEY_1
import com.example.keyboard.KeyboardDescriptor.KEY_2
import com.example.keyboard.KeyboardDescriptor.KEY_3
import com.example.keyboard.KeyboardDescriptor.KEY_4
import com.example.keyboard.KeyboardDescriptor.KEY_5
import com.example.keyboard.KeyboardDescriptor.KEY_6
import com.example.keyboard.KeyboardDescriptor.KEY_7
import com.example.keyboard.KeyboardDescriptor.KEY_8
import com.example.keyboard.KeyboardDescriptor.KEY_9
import com.example.keyboard.KeyboardDescriptor.KEY_A
import com.example.keyboard.KeyboardDescriptor.KEY_APOSTROPHE
import com.example.keyboard.KeyboardDescriptor.KEY_BACKSLASH
import com.example.keyboard.KeyboardDescriptor.KEY_COMMA
import com.example.keyboard.KeyboardDescriptor.KEY_DOT
import com.example.keyboard.KeyboardDescriptor.KEY_ENTER
import com.example.keyboard.KeyboardDescriptor.KEY_EQUAL
import com.example.keyboard.KeyboardDescriptor.KEY_GRAVE
import com.example.keyboard.KeyboardDescriptor.KEY_LEFTBRACE
import com.example.keyboard.KeyboardDescriptor.KEY_MINUS
import com.example.keyboard.KeyboardDescriptor.KEY_RIGHTBRACE
import com.example.keyboard.KeyboardDescriptor.KEY_SEMICOLON
import com.example.keyboard.KeyboardDescriptor.KEY_SLASH
import com.example.keyboard.KeyboardDescriptor.KEY_SPACE
import com.example.keyboard.KeyboardDescriptor.KEY_TAB
import com.example.keyboard.KeyboardDescriptor.MOD_LEFT_SHIFT
import com.example.keyboard.KeyboardDescriptor.MOD_NONE

data class KeyStroke(
    val keyCode: Byte,
    val modifier: Byte = MOD_NONE
)

/**
 * Standard US QWERTY keyboard mapper.
 * Maps characters to physical HID key codes and shift modifier states.
 */
object KeyboardMapper {

    /**
     * Normalize common unicode variants like smart/curly quotes, en/em dashes, NBSP,
     * fullwidth ASCII, accents, and symbols to standard ASCII counterparts.
     */
    fun normalizeChar(char: Char): Char = when (char) {
        // Smart single quotes, apostrophes, backticks
        '‘', '’', '‚', '‛', '′', '‵', '´', '‹', '›' -> '\''
        // Smart double quotes
        '“', '”', '„', '‟', '«', '»', '″', '‶' -> '"'
        // Dashes and hyphens
        '–', '—', '―', '‒', '‐', '‑', '−' -> '-'
        // Non-breaking and unicode spaces
        '\u00A0', '\u2000', '\u2001', '\u2002', '\u2003', '\u2004',
        '\u2005', '\u2006', '\u2007', '\u2008', '\u2009', '\u200A',
        '\u202F', '\u205F', '\u3000' -> ' '
        // Ellipsis
        '…' -> '.'
        // Bullets and list symbols
        '•', '◦', '‣', '⁃', '·' -> '*'
        // Math and programming symbols
        '×' -> '*'
        '÷' -> '/'
        '≠', '¬' -> '!'
        '≤' -> '<'
        '≥' -> '>'
        '±' -> '+'
        '≈' -> '~'
        '→', '⇒' -> '>'
        '←', '⇐' -> '<'
        '|', '｜' -> '|'
        // Common accented characters
        'á', 'à', 'â', 'ä', 'ã', 'å', 'æ' -> 'a'
        'é', 'è', 'ê', 'ë' -> 'e'
        'í', 'ì', 'î', 'ï' -> 'i'
        'ó', 'ò', 'ô', 'ö', 'õ', 'ø' -> 'o'
        'ú', 'ù', 'û', 'ü' -> 'u'
        'ñ' -> 'n'
        'ç' -> 'c'
        'Á', 'À', 'Â', 'Ä', 'Ã', 'Å', 'Æ' -> 'A'
        'É', 'È', 'Ê', 'Ë' -> 'E'
        'Í', 'Ì', 'Î', 'Ï' -> 'I'
        'Ó', 'Ò', 'Ô', 'Ö', 'Õ', 'Ø' -> 'O'
        'Ú', 'Ù', 'Û', 'Ü' -> 'U'
        'Ñ' -> 'N'
        'Ç' -> 'C'
        // Fullwidth ASCII range: \uFF01 ('！') to \uFF5E ('～')
        in '\uFF01'..'\uFF5E' -> (char.code - 0xFEE0).toChar()
        else -> char
    }

    /**
     * Map a single character to a KeyStroke.
     * Returns null if the character is not supported on standard US QWERTY.
     */
    fun mapChar(char: Char): KeyStroke? {
        val normalized = normalizeChar(char)
        return when (normalized) {
            // Lowercase letters (a-z)
            in 'a'..'z' -> {
                val offset = normalized - 'a'
                KeyStroke((KEY_A + offset).toByte(), MOD_NONE)
            }

            // Uppercase letters (A-Z) -> SHIFT + Letter
            in 'A'..'Z' -> {
                val offset = normalized - 'A'
                KeyStroke((KEY_A + offset).toByte(), MOD_LEFT_SHIFT)
            }

            // Numbers 1-9
            in '1'..'9' -> {
                val offset = normalized - '1'
                KeyStroke((KEY_1 + offset).toByte(), MOD_NONE)
            }
            '0' -> KeyStroke(KEY_0, MOD_NONE)

            // Shifted Numbers: Symbols ! @ # $ % ^ & * ( )
            '!' -> KeyStroke(KEY_1, MOD_LEFT_SHIFT)
            '@' -> KeyStroke(KEY_2, MOD_LEFT_SHIFT)
            '#' -> KeyStroke(KEY_3, MOD_LEFT_SHIFT)
            '$' -> KeyStroke(KEY_4, MOD_LEFT_SHIFT)
            '%' -> KeyStroke(KEY_5, MOD_LEFT_SHIFT)
            '^' -> KeyStroke(KEY_6, MOD_LEFT_SHIFT)
            '&' -> KeyStroke(KEY_7, MOD_LEFT_SHIFT)
            '*' -> KeyStroke(KEY_8, MOD_LEFT_SHIFT)
            '(' -> KeyStroke(KEY_9, MOD_LEFT_SHIFT)
            ')' -> KeyStroke(KEY_0, MOD_LEFT_SHIFT)

            // Whitespace and control
            ' ' -> KeyStroke(KEY_SPACE, MOD_NONE)
            '\t' -> KeyStroke(KEY_TAB, MOD_NONE)
            '\n' -> KeyStroke(KEY_ENTER, MOD_NONE)

            // Punctuations and symbols
            '-' -> KeyStroke(KEY_MINUS, MOD_NONE)
            '_' -> KeyStroke(KEY_MINUS, MOD_LEFT_SHIFT)
            '=' -> KeyStroke(KEY_EQUAL, MOD_NONE)
            '+' -> KeyStroke(KEY_EQUAL, MOD_LEFT_SHIFT)

            '[' -> KeyStroke(KEY_LEFTBRACE, MOD_NONE)
            '{' -> KeyStroke(KEY_LEFTBRACE, MOD_LEFT_SHIFT)
            ']' -> KeyStroke(KEY_RIGHTBRACE, MOD_NONE)
            '}' -> KeyStroke(KEY_RIGHTBRACE, MOD_LEFT_SHIFT)

            '\\' -> KeyStroke(KEY_BACKSLASH, MOD_NONE)
            '|' -> KeyStroke(KEY_BACKSLASH, MOD_LEFT_SHIFT)

            ';' -> KeyStroke(KEY_SEMICOLON, MOD_NONE)
            ':' -> KeyStroke(KEY_SEMICOLON, MOD_LEFT_SHIFT)

            '\'' -> KeyStroke(KEY_APOSTROPHE, MOD_NONE)
            '"' -> KeyStroke(KEY_APOSTROPHE, MOD_LEFT_SHIFT)

            '`' -> KeyStroke(KEY_GRAVE, MOD_NONE)
            '~' -> KeyStroke(KEY_GRAVE, MOD_LEFT_SHIFT)

            ',' -> KeyStroke(KEY_COMMA, MOD_NONE)
            '<' -> KeyStroke(KEY_COMMA, MOD_LEFT_SHIFT)

            '.' -> KeyStroke(KEY_DOT, MOD_NONE)
            '>' -> KeyStroke(KEY_DOT, MOD_LEFT_SHIFT)

            '/' -> KeyStroke(KEY_SLASH, MOD_NONE)
            '?' -> KeyStroke(KEY_SLASH, MOD_LEFT_SHIFT)

            else -> null
        }
    }

    /**
     * Scans text and identifies any unsupported characters along with their index.
     */
    fun findUnsupportedChars(text: String): List<Pair<Int, Char>> {
        val unsupported = mutableListOf<Pair<Int, Char>>()
        text.forEachIndexed { index, char ->
            if (char == '\r') return@forEachIndexed
            if (mapChar(char) == null) {
                unsupported.add(index to char)
            }
        }
        return unsupported
    }

    /**
     * Safe fallback for any character: returns normalized character if supported,
     * or a safe substitute (space) so auto-typing never crashes or stalls.
     */
    fun fallbackChar(char: Char): Char {
        val normalized = normalizeChar(char)
        if (mapChar(normalized) != null) return normalized
        return ' '
    }

    /**
     * Replaces unsupported characters with standard equivalents or removes them.
     */
    fun cleanText(text: String): String {
        val sb = StringBuilder()
        for (char in text) {
            if (char == '\r') continue
            val normalized = normalizeChar(char)
            if (mapChar(normalized) != null) {
                sb.append(normalized)
            } else {
                sb.append(' ')
            }
        }
        return sb.toString()
    }

    /**
     * Complete test string covering all letters, numbers, and symbols.
     */
    val ALL_KEYS_TEST_STRING = buildString {
        append("abcdefghijklmnopqrstuvwxyz\n")
        append("ABCDEFGHIJKLMNOPQRSTUVWXYZ\n")
        append("0123456789\n")
        append("!@#$%^&*()-_=+\n")
        append("[]{};:'\",.<>/?\\`~\n")
    }
}
