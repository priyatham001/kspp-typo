import {
  KEY_0, KEY_1, KEY_2, KEY_3, KEY_4, KEY_5, KEY_6, KEY_7, KEY_8, KEY_9,
  KEY_A, KEY_APOSTROPHE, KEY_BACKSLASH, KEY_COMMA, KEY_DOT, KEY_ENTER,
  KEY_EQUAL, KEY_GRAVE, KEY_LEFTBRACE, KEY_MINUS, KEY_RIGHTBRACE,
  KEY_SEMICOLON, KEY_SLASH, KEY_SPACE, KEY_TAB,
  MOD_LEFT_SHIFT, MOD_NONE
} from './keyboardDescriptor';

export interface KeyStroke {
  keyCode: number;
  modifier: number;
}

export const KeyboardMapper = {
  mapChar(char: string): KeyStroke | null {
    if (char.length !== 1) return null;
    const code = char.charCodeAt(0);

    // Lowercase letters (a-z)
    if (code >= 97 && code <= 122) {
      return { keyCode: KEY_A + (code - 97), modifier: MOD_NONE };
    }

    // Uppercase letters (A-Z) -> SHIFT + Letter
    if (code >= 65 && code <= 90) {
      return { keyCode: KEY_A + (code - 65), modifier: MOD_LEFT_SHIFT };
    }

    // Numbers 1-9
    if (code >= 49 && code <= 57) {
      return { keyCode: KEY_1 + (code - 49), modifier: MOD_NONE };
    }
    if (char === '0') return { keyCode: KEY_0, modifier: MOD_NONE };

    // Shifted Numbers: ! @ # $ % ^ & * ( )
    switch (char) {
      case '!': return { keyCode: KEY_1, modifier: MOD_LEFT_SHIFT };
      case '@': return { keyCode: KEY_2, modifier: MOD_LEFT_SHIFT };
      case '#': return { keyCode: KEY_3, modifier: MOD_LEFT_SHIFT };
      case '$': return { keyCode: KEY_4, modifier: MOD_LEFT_SHIFT };
      case '%': return { keyCode: KEY_5, modifier: MOD_LEFT_SHIFT };
      case '^': return { keyCode: KEY_6, modifier: MOD_LEFT_SHIFT };
      case '&': return { keyCode: KEY_7, modifier: MOD_LEFT_SHIFT };
      case '*': return { keyCode: KEY_8, modifier: MOD_LEFT_SHIFT };
      case '(': return { keyCode: KEY_9, modifier: MOD_LEFT_SHIFT };
      case ')': return { keyCode: KEY_0, modifier: MOD_LEFT_SHIFT };

      // Whitespace and control
      case ' ': return { keyCode: KEY_SPACE, modifier: MOD_NONE };
      case '\t': return { keyCode: KEY_TAB, modifier: MOD_NONE };
      case '\n': return { keyCode: KEY_ENTER, modifier: MOD_NONE };

      // Punctuations and symbols
      case '-': return { keyCode: KEY_MINUS, modifier: MOD_NONE };
      case '_': return { keyCode: KEY_MINUS, modifier: MOD_LEFT_SHIFT };
      case '=': return { keyCode: KEY_EQUAL, modifier: MOD_NONE };
      case '+': return { keyCode: KEY_EQUAL, modifier: MOD_LEFT_SHIFT };

      case '[': return { keyCode: KEY_LEFTBRACE, modifier: MOD_NONE };
      case '{': return { keyCode: KEY_LEFTBRACE, modifier: MOD_LEFT_SHIFT };
      case ']': return { keyCode: KEY_RIGHTBRACE, modifier: MOD_NONE };
      case '}': return { keyCode: KEY_RIGHTBRACE, modifier: MOD_LEFT_SHIFT };

      case '\\': return { keyCode: KEY_BACKSLASH, modifier: MOD_NONE };
      case '|': return { keyCode: KEY_BACKSLASH, modifier: MOD_LEFT_SHIFT };

      case ';': return { keyCode: KEY_SEMICOLON, modifier: MOD_NONE };
      case ':': return { keyCode: KEY_SEMICOLON, modifier: MOD_LEFT_SHIFT };

      case '\'': return { keyCode: KEY_APOSTROPHE, modifier: MOD_NONE };
      case '"': return { keyCode: KEY_APOSTROPHE, modifier: MOD_LEFT_SHIFT };

      case '`': return { keyCode: KEY_GRAVE, modifier: MOD_NONE };
      case '~': return { keyCode: KEY_GRAVE, modifier: MOD_LEFT_SHIFT };

      case ',': return { keyCode: KEY_COMMA, modifier: MOD_NONE };
      case '<': return { keyCode: KEY_COMMA, modifier: MOD_LEFT_SHIFT };

      case '.': return { keyCode: KEY_DOT, modifier: MOD_NONE };
      case '>': return { keyCode: KEY_DOT, modifier: MOD_LEFT_SHIFT };

      case '/': return { keyCode: KEY_SLASH, modifier: MOD_NONE };
      case '?': return { keyCode: KEY_SLASH, modifier: MOD_LEFT_SHIFT };

      default: return null;
    }
  },

  ALL_KEYS_TEST_STRING: `abcdefghijklmnopqrstuvwxyz
ABCDEFGHIJKLMNOPQRSTUVWXYZ
0123456789
!@#$%^&*()-_=+
[]{};:'",.<>/?\`~
`
};
