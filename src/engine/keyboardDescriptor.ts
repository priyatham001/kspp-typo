/**
 * Standard USB / Bluetooth HID Keyboard Report Descriptor and Key Codes.
 * Implements standard US QWERTY Boot Keyboard profile according to USB HID 1.11 specs.
 */

export const MOD_NONE = 0x00;
export const MOD_LEFT_CTRL = 0x01;
export const MOD_LEFT_SHIFT = 0x02;
export const MOD_LEFT_ALT = 0x04;
export const MOD_LEFT_GUI = 0x08;
export const MOD_RIGHT_CTRL = 0x10;
export const MOD_RIGHT_SHIFT = 0x20;
export const MOD_RIGHT_ALT = 0x40;
export const MOD_RIGHT_GUI = 0x80;

export const KEY_NONE = 0x00;
export const KEY_A = 0x04;
export const KEY_B = 0x05;
export const KEY_C = 0x06;
export const KEY_D = 0x07;
export const KEY_E = 0x08;
export const KEY_F = 0x09;
export const KEY_G = 0x0a;
export const KEY_H = 0x0b;
export const KEY_I = 0x0c;
export const KEY_J = 0x0d;
export const KEY_K = 0x0e;
export const KEY_L = 0x0f;
export const KEY_M = 0x10;
export const KEY_N = 0x11;
export const KEY_O = 0x12;
export const KEY_P = 0x13;
export const KEY_Q = 0x14;
export const KEY_R = 0x15;
export const KEY_S = 0x16;
export const KEY_T = 0x17;
export const KEY_U = 0x18;
export const KEY_V = 0x19;
export const KEY_W = 0x1a;
export const KEY_X = 0x1b;
export const KEY_Y = 0x1c;
export const KEY_Z = 0x1d;

export const KEY_1 = 0x1e;
export const KEY_2 = 0x1f;
export const KEY_3 = 0x20;
export const KEY_4 = 0x21;
export const KEY_5 = 0x22;
export const KEY_6 = 0x23;
export const KEY_7 = 0x24;
export const KEY_8 = 0x25;
export const KEY_9 = 0x26;
export const KEY_0 = 0x27;

export const KEY_ENTER = 0x28;
export const KEY_ESC = 0x29;
export const KEY_BACKSPACE = 0x2a;
export const KEY_TAB = 0x2b;
export const KEY_SPACE = 0x2c;
export const KEY_MINUS = 0x2d;       // '-' and '_'
export const KEY_EQUAL = 0x2e;       // '=' and '+'
export const KEY_LEFTBRACE = 0x2f;   // '[' and '{'
export const KEY_RIGHTBRACE = 0x30;  // ']' and '}'
export const KEY_BACKSLASH = 0x31;   // '\' and '|'
export const KEY_SEMICOLON = 0x33;   // ';' and ':'
export const KEY_APOSTROPHE = 0x34;  // '\'' and '"'
export const KEY_GRAVE = 0x35;       // '`' and '~'
export const KEY_COMMA = 0x36;       // ',' and '<'
export const KEY_DOT = 0x37;         // '.' and '>'
export const KEY_SLASH = 0x38;       // '/' and '?'
export const KEY_CAPSLOCK = 0x39;

export const KEY_RIGHT = 0x4f;
export const KEY_LEFT = 0x50;
export const KEY_DOWN = 0x51;
export const KEY_UP = 0x52;
export const KEY_DELETE = 0x4c;

export interface HidReport {
  reportId: number;
  modifier: number;
  reserved: number;
  keys: number[]; // up to 6 key codes
  rawHex: string;
}

export function buildKeyDownReport(keyCode: number, modifier: number = MOD_NONE): HidReport {
  const keys = [keyCode, 0, 0, 0, 0, 0];
  const bytes = [0x01, modifier, 0x00, keyCode, 0, 0, 0, 0, 0];
  const rawHex = bytes.map(b => b.toString(16).padStart(2, '0').toUpperCase()).join(' ');
  return {
    reportId: 0x01,
    modifier,
    reserved: 0x00,
    keys,
    rawHex
  };
}

export function buildKeyUpReport(): HidReport {
  const bytes = [0x01, 0x00, 0x00, 0, 0, 0, 0, 0, 0];
  const rawHex = bytes.map(b => b.toString(16).padStart(2, '0').toUpperCase()).join(' ');
  return {
    reportId: 0x01,
    modifier: 0x00,
    reserved: 0x00,
    keys: [0, 0, 0, 0, 0, 0],
    rawHex
  };
}
