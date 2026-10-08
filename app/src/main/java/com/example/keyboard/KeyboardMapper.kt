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
     * Map a single character to a KeyStroke.
     * Returns null if the character is not supported on standard US QWERTY.
     */
    fun mapChar(char: Char): KeyStroke? {
        return when (char) {
            // Lowercase letters (a-z)
            in 'a'..'z' -> {
                val offset = char - 'a'
                KeyStroke((KEY_A + offset).toByte(), MOD_NONE)
            }

            // Uppercase letters (A-Z) -> SHIFT + Letter
            in 'A'..'Z' -> {
                val offset = char - 'A'
                KeyStroke((KEY_A + offset).toByte(), MOD_LEFT_SHIFT)
            }

            // Numbers 1-9
            in '1'..'9' -> {
                val offset = char - '1'
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
