import React, { useState } from 'react';
import { Keyboard, Play, Terminal, CheckCircle, Info, Sparkles } from 'lucide-react';
import { KeyboardMapper } from '../engine/keyboardMapper';
import {
  KEY_TAB,
  KEY_ENTER,
  KEY_SPACE,
  KEY_BACKSPACE,
  KEY_ESC,
  HidReport
} from '../engine/keyboardDescriptor';

interface KeyboardTestScreenProps {
  onSendDirectKey: (keyCode: number, modifier?: number, charName?: string) => void;
  onRunTestAllKeys: () => void;
  isTestingAllKeys: boolean;
  lastReport: HidReport | null;
  lastChar: string;
  isConnected: boolean;
}

export const KeyboardTestScreen: React.FC<KeyboardTestScreenProps> = ({
  onSendDirectKey,
  onRunTestAllKeys,
  isTestingAllKeys,
  lastReport,
  lastChar,
  isConnected
}) => {
  const [terminalBuffer, setTerminalBuffer] = useState<string>('Hello REPLICA test stream.');

  const handleKeyClick = (char: string) => {
    const stroke = KeyboardMapper.mapChar(char);
    if (stroke) {
      onSendDirectKey(stroke.keyCode, stroke.modifier, char);
      setTerminalBuffer(prev => prev + char);
    }
  };

  const letters = 'abcdefghijklmnopqrstuvwxyz'.split('');
  const numbers = '0123456789'.split('');
  const symbols = [
    '!', '@', '#', '$', '%', '^', '&', '*', '(', ')',
    '-', '_', '=', '+', '[', ']', '{', '}', '\\', '|',
    ';', ':', '\'', '"', ',', '<', '.', '>', '/', '?',
    '`', '~'
  ];

  return (
    <div className="flex flex-col gap-4 pb-20">
      <div>
        <h2 className="text-xl font-bold text-white">Keyboard Test</h2>
        <p className="text-xs text-gray-400">
          Tap buttons to send real Bluetooth HID keyboard events to your PC
        </p>
      </div>

      {!isConnected && (
        <div className="bg-amber-950/20 border border-amber-500/40 rounded-2xl p-3 flex items-center gap-2.5 text-xs text-amber-300">
          <Info size={18} className="text-amber-400 shrink-0" />
          <span>Laptop not connected. Connect via Bluetooth first to receive keystrokes.</span>
        </div>
      )}

      {/* AUTOMATED KEYBOARD VERIFICATION CARD */}
      <div className="bg-gradient-to-br from-[#111827] to-[#162035] border border-cyan-500/30 rounded-2xl p-4 shadow-lg flex flex-col gap-3">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-lg bg-cyan-950/80 border border-cyan-500/50 flex items-center justify-center text-cyan-400">
            <Sparkles size={18} />
          </div>
          <div>
            <h3 className="text-sm font-bold text-white">Automated Keyboard Verification</h3>
            <p className="text-[11px] text-gray-400">
              Types alphabet (a-z, A-Z), numbers (0-9), and programming symbols into cursor position.
            </p>
          </div>
        </div>

        <button
          onClick={onRunTestAllKeys}
          disabled={isTestingAllKeys}
          className="w-full py-3 px-4 rounded-xl bg-gradient-to-r from-blue-600 to-cyan-600 hover:from-blue-500 hover:to-cyan-500 active:scale-98 text-white font-bold text-xs shadow-md shadow-cyan-600/30 flex items-center justify-center gap-2 transition-all disabled:opacity-50"
        >
          {isTestingAllKeys ? (
            <>
              <span className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
              Typing Test Sequence...
            </>
          ) : (
            <>
              <Play size={15} className="fill-white" />
              TEST ALL KEYS
            </>
          )}
        </button>
      </div>

      {/* REAL-TIME HID REPORT & EVENT DISPLAY */}
      <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-4 shadow-md flex flex-col gap-2.5">
        <div className="flex items-center justify-between">
          <span className="text-xs text-gray-400 font-semibold">Last Key Event:</span>
          <div className="px-3 py-1 rounded-lg bg-[#0b0f19] border border-[#1f293d] font-mono text-xs font-bold text-cyan-400">
            {lastChar ? `Key '${lastChar}'` : 'None'}
          </div>
        </div>

        {lastReport && (
          <div className="bg-[#0b0f19] p-2.5 rounded-xl border border-[#1f293d] font-mono text-[11px] flex flex-col gap-1">
            <div className="flex justify-between text-gray-400 text-[10px]">
              <span>8-Byte HID Report Packet:</span>
              <span className="text-emerald-400">Report ID: 0x01</span>
            </div>
            <div className="text-cyan-300 font-bold tracking-wider">{lastReport.rawHex}</div>
          </div>
        )}
      </div>

      {/* VIRTUAL TERMINAL BUFFER PREVIEW */}
      <div className="bg-[#0b0f19] border border-[#1f293d] rounded-2xl p-3 font-mono text-xs shadow-inner">
        <div className="flex items-center justify-between text-[11px] text-gray-500 mb-1 border-b border-gray-800 pb-1">
          <span className="flex items-center gap-1.5 text-cyan-400 font-bold">
            <Terminal size={13} />
            Virtual Host Screen
          </span>
          <button
            onClick={() => setTerminalBuffer('')}
            className="text-[10px] text-gray-400 hover:text-white"
          >
            Clear Screen
          </button>
        </div>
        <div className="min-h-12 text-emerald-400 break-all select-all font-mono whitespace-pre-wrap">
          {terminalBuffer || '> Waiting for keystrokes...'}
        </div>
      </div>

      {/* SECTION 1: LETTERS (a - z) */}
      <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-4 flex flex-col gap-2.5">
        <span className="text-xs font-bold text-gray-300 uppercase tracking-wide">Letters (a - z)</span>
        <div className="grid grid-cols-7 sm:grid-cols-9 gap-1.5">
          {letters.map((char) => (
            <button
              key={char}
              onClick={() => handleKeyClick(char)}
              className="h-10 rounded-xl bg-[#0b0f19] hover:bg-[#162035] active:scale-95 border border-[#1f293d] text-gray-200 font-mono text-sm font-bold flex items-center justify-center transition-all shadow-sm"
            >
              {char.toUpperCase()}
            </button>
          ))}
        </div>
      </div>

      {/* SECTION 2: NUMBERS (0 - 9) */}
      <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-4 flex flex-col gap-2.5">
        <span className="text-xs font-bold text-gray-300 uppercase tracking-wide">Numbers (0 - 9)</span>
        <div className="grid grid-cols-5 sm:grid-cols-10 gap-1.5">
          {numbers.map((num) => (
            <button
              key={num}
              onClick={() => handleKeyClick(num)}
              className="h-10 rounded-xl bg-[#0b0f19] hover:bg-[#162035] active:scale-95 border border-[#1f293d] text-cyan-400 font-mono text-sm font-bold flex items-center justify-center transition-all shadow-sm"
            >
              {num}
            </button>
          ))}
        </div>
      </div>

      {/* SECTION 3: PROGRAMMING SYMBOLS */}
      <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-4 flex flex-col gap-2.5">
        <span className="text-xs font-bold text-gray-300 uppercase tracking-wide">Programming Symbols</span>
        <div className="grid grid-cols-6 sm:grid-cols-8 gap-1.5">
          {symbols.map((sym) => (
            <button
              key={sym}
              onClick={() => handleKeyClick(sym)}
              className="h-10 rounded-xl bg-[#0b0f19] hover:bg-[#162035] active:scale-95 border border-[#1f293d] text-amber-300 font-mono text-sm font-bold flex items-center justify-center transition-all shadow-sm"
            >
              {sym}
            </button>
          ))}
        </div>
      </div>

      {/* SECTION 4: CONTROL & NAVIGATION KEYS */}
      <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-4 flex flex-col gap-2.5">
        <span className="text-xs font-bold text-gray-300 uppercase tracking-wide">Control &amp; Navigation Keys</span>
        <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
          <button
            onClick={() => {
              onSendDirectKey(KEY_TAB, 0, 'TAB');
              setTerminalBuffer(prev => prev + '\t');
            }}
            className="py-2.5 px-3 rounded-xl bg-[#0b0f19] hover:bg-[#162035] active:scale-95 border border-[#1f293d] text-white font-mono text-xs font-bold"
          >
            TAB
          </button>
          <button
            onClick={() => {
              onSendDirectKey(KEY_ENTER, 0, 'ENTER');
              setTerminalBuffer(prev => prev + '\n');
            }}
            className="py-2.5 px-3 rounded-xl bg-[#0b0f19] hover:bg-[#162035] active:scale-95 border border-[#1f293d] text-emerald-400 font-mono text-xs font-bold"
          >
            ENTER
          </button>
          <button
            onClick={() => {
              onSendDirectKey(KEY_SPACE, 0, 'SPACE');
              setTerminalBuffer(prev => prev + ' ');
            }}
            className="py-2.5 px-3 rounded-xl bg-[#0b0f19] hover:bg-[#162035] active:scale-95 border border-[#1f293d] text-cyan-400 font-mono text-xs font-bold"
          >
            SPACE
          </button>
          <button
            onClick={() => {
              onSendDirectKey(KEY_BACKSPACE, 0, 'BACKSPACE');
              setTerminalBuffer(prev => prev.slice(0, -1));
            }}
            className="py-2.5 px-3 rounded-xl bg-[#0b0f19] hover:bg-[#162035] active:scale-95 border border-[#1f293d] text-red-400 font-mono text-xs font-bold"
          >
            BACKSPACE
          </button>
          <button
            onClick={() => {
              onSendDirectKey(KEY_ESC, 0, 'ESC');
            }}
            className="py-2.5 px-3 rounded-xl bg-[#0b0f19] hover:bg-[#162035] active:scale-95 border border-[#1f293d] text-gray-300 font-mono text-xs font-bold"
          >
            ESC
          </button>
        </div>
      </div>

      {/* NOTEPAD FIRST TEST GUIDE */}
      <div className="bg-[#111827] border border-[#1f293d] rounded-2xl p-4 flex flex-col gap-2">
        <h3 className="text-sm font-bold text-cyan-400">FIRST TEST (Windows Notepad)</h3>
        <ol className="text-xs text-gray-400 space-y-1.5 pl-4 list-decimal leading-relaxed">
          <li>Connect REPLICA to your Windows laptop via Bluetooth HID.</li>
          <li>Open Windows Notepad (<code className="text-cyan-300 font-mono">notepad.exe</code>) or CodeTantra.</li>
          <li>Click inside the window so the text cursor is blinking.</li>
          <li>In this Virtual Keyboard screen, tap letter, number, and symbol keys.</li>
          <li>Press <strong>[TEST ALL KEYS]</strong> to verify complete US QWERTY mapping.</li>
          <li>Confirm every character matches exactly on Windows.</li>
          <li>Switch to the Editor tab to stream long documents or source code.</li>
        </ol>
      </div>
    </div>
  );
};
